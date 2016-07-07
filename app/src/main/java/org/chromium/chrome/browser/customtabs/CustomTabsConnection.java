// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.ICustomTabsCallback;
import android.support.customtabs.ICustomTabsService;
import android.text.TextUtils;
import android.view.WindowManager;

import org.chromium.base.FieldTrialList;
import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.prerender.ExternalPrerenderHandler;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.content.browser.ChildProcessLauncher;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.Referrer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of the ICustomTabsConnectionService interface.
 *
 * Note: This class is meant to be package private, and is public to be
 * accessible from {@link ChromeApplication}.
 */
public class CustomTabsConnection extends ICustomTabsService.Stub {
    private static final String TAG = "cr.ChromeConnection";
    @VisibleForTesting
    static final String NO_PRERENDERING_KEY =
            "android.support.customtabs.maylaunchurl.NO_PRERENDERING";

    private static AtomicReference<CustomTabsConnection> sInstance =
            new AtomicReference<CustomTabsConnection>();

    private static final class PrerenderedUrlParams {
        public final IBinder mSession;
        public final WebContents mWebContents;
        public final String mUrl;
        public final String mReferrer;
        public final Bundle mExtras;

        PrerenderedUrlParams(IBinder session, WebContents webContents, String url, String referrer,
                Bundle extras) {
            mSession = session;
            mWebContents = webContents;
            mUrl = url;
            mReferrer = referrer;
            mExtras = extras;
        }
    }

    protected final Application mApplication;
    private final AtomicBoolean mWarmupHasBeenCalled = new AtomicBoolean();
    private final ClientManager mClientManager;
    private ExternalPrerenderHandler mExternalPrerenderHandler;
    private PrerenderedUrlParams mPrerender;
    private WebContents mSpareWebContents;

    /**
     * <strong>DO NOT CALL</strong>
     * Public to be instanciable from {@link ChromeApplication}. This is however
     * intended to be private.
     */
    public CustomTabsConnection(Application application) {
        super();
        mApplication = application;
        mClientManager = new ClientManager(mApplication);
    }

    /**
     * @return The unique instance of ChromeCustomTabsConnection.
     */
    @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
    public static CustomTabsConnection getInstance(Application application) {
        if (sInstance.get() == null) {
            ChromeApplication chromeApplication = (ChromeApplication) application;
            sInstance.compareAndSet(null, chromeApplication.createCustomTabsConnection());
        }
        return sInstance.get();
    }

    @Override
    public boolean newSession(ICustomTabsCallback callback) {
        ClientManager.DisconnectCallback onDisconnect = new ClientManager.DisconnectCallback() {
            @Override
            public void run(IBinder session) {
                cancelPrerender(session);
            }
        };
        return mClientManager.newSession(callback, Binder.getCallingUid(), onDisconnect);
    }

    /** Warmup activities that should only happen once. */
    @SuppressFBWarnings("DM_EXIT")
    private static void initializeBrowser(final ChromeApplication app) {
        ThreadUtils.assertOnUiThread();
        try {
            app.startBrowserProcessesAndLoadLibrariesSync(true);
        } catch (ProcessInitException e) {
            Log.e(TAG, "ProcessInitException while starting the browser process.");
            // Cannot do anything without the native library, and cannot show a
            // dialog to the user.
            System.exit(-1);
        }
        final Context context = app.getApplicationContext();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ChildProcessLauncher.warmUp(context);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        ChromeBrowserInitializer.initNetworkChangeNotifier(context);
        WarmupManager.getInstance().initializeViewHierarchy(
                context, R.layout.custom_tabs_control_container);
    }

    @Override
    public boolean warmup(long flags) {
        return warmup(true);
    }

    /**
     * Starts as much as possible in anticipation of a future navigation.
     *
     * @param mayCreatesparewebcontents true if warmup() can create a spare renderer.
     * @return true for success.
     */
    private boolean warmup(final boolean mayCreateSpareWebContents) {
        // Here and in mayLaunchUrl(), don't do expensive work for background applications.
        if (!isCallerForegroundOrSelf()) return false;
        mClientManager.recordUidHasCalledWarmup(Binder.getCallingUid());
        final boolean initialized = !mWarmupHasBeenCalled.compareAndSet(false, true);
        // The call is non-blocking and this must execute on the UI thread, post a task.
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!initialized) initializeBrowser((ChromeApplication) mApplication);
                if (mayCreateSpareWebContents && mPrerender == null && !SysUtils.isLowEndDevice()) {
                    createSpareWebContents();
                }
            }
        });
        return true;
    }

    /**
     * Creates a spare {@link WebContents}, if none exists.
     *
     * Navigating to "about:blank" forces a lot of initialization to take place
     * here. This improves PLT. This navigation is never registered in the history, as
     * "about:blank" is filtered by CanAddURLToHistory.
     *
     * TODO(lizeb): Replace this with a cleaner method. See crbug.com/521729.
     */
    private void createSpareWebContents() {
        ThreadUtils.assertOnUiThread();
        if (mSpareWebContents != null) return;
        mSpareWebContents = WebContentsFactory.createWebContents(false, false);
        if (mSpareWebContents != null) {
            mSpareWebContents.getNavigationController().loadUrl(new LoadUrlParams("about:blank"));
        }
    }

    @Override
    public boolean mayLaunchUrl(ICustomTabsCallback callback, Uri url, final Bundle extras,
            List<Bundle> otherLikelyBundles) {
        // Don't do anything for unknown schemes. Not having a scheme is
        // allowed, as we allow "www.example.com".
        String scheme = url.normalizeScheme().getScheme();
        if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) return false;
        // Things below need the browser process to be initialized.

        // Forbids warmup() from creating a spare renderer, as prerendering wouldn't reuse
        // it. Checking whether prerendering is enabled requires the native library to be loaded,
        // which is not necessarily the case yet.
        if (!warmup(false)) return false; // Also does the foreground check.

        final IBinder session = callback.asBinder();
        final String urlString = url.toString();
        final boolean noPrerendering =
                extras != null ? extras.getBoolean(NO_PRERENDERING_KEY, false) : false;
        final int uid = Binder.getCallingUid();
        if (!mClientManager.updateStatsAndReturnWhetherAllowed(session, uid, urlString)) {
            return false;
        }
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (TextUtils.isEmpty(urlString)) {
                    cancelPrerender(session);
                    return;
                }
                WarmupManager warmupManager = WarmupManager.getInstance();
                warmupManager.maybePrefetchDnsForUrlInBackground(
                        mApplication.getApplicationContext(), urlString);
                warmupManager.maybePreconnectUrlAndSubResources(
                        Profile.getLastUsedProfile(), urlString);
                if (!noPrerendering && mayPrerender()) {
                    prerenderUrl(session, urlString, extras, uid);
                } else {
                    createSpareWebContents();
                }
            }
        });
        return true;
    }

    @Override
    public Bundle extraCommand(String commandName, Bundle args) {
        return null;
    }

    /**
     * @return a spare WebContents, or null.
     *
     * This WebContents has already navigated to "about:blank". You have to call
     * {@link LoadUrlParams.setShouldReplaceCurrentEntry(true)} for the next
     * navigation to ensure that a back navigation doesn't lead to about:blank.
     *
     * TODO(lizeb): Update this when crbug.com/521729 is fixed.
     */
    WebContents takeSpareWebContents() {
        ThreadUtils.assertOnUiThread();
        WebContents result = mSpareWebContents;
        mSpareWebContents = null;
        return result;
    }

    private void destroySpareWebContents() {
        ThreadUtils.assertOnUiThread();
        WebContents webContents = takeSpareWebContents();
        if (webContents != null) webContents.destroy();
    }

    @Override
    public boolean updateVisuals(final ICustomTabsCallback callback, Bundle bundle) {
        final Bundle actionButtonBundle = IntentUtils.safeGetBundle(bundle,
                CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE);
        if (actionButtonBundle == null) return false;

        final Bitmap bitmap = ActionButtonParams.tryParseBitmapFromBundle(mApplication,
                actionButtonBundle);
        final String description = ActionButtonParams
                .tryParseDescriptionFromBundle(actionButtonBundle);
        if (bitmap == null || description == null) return false;

        try {
            return ThreadUtils.runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return CustomTabActivity.updateActionButton(callback.asBinder(), bitmap,
                            description);
                }
            });
        } catch (ExecutionException e) {
            return false;
        }
    }

    /**
     * Registers a launch of a |url| for a given |session|.
     *
     * This is used for accounting.
     */
    void registerLaunch(IBinder session, String url) {
        mClientManager.registerLaunch(session, url);
    }

    /**
     * Transfers a prerendered WebContents if one exists.
     *
     * This resets the internal WebContents; a subsequent call to this method
     * returns null. Must be called from the UI thread.
     * If a prerender exists for a different URL with the same sessionId or with
     * a different referrer, then this is treated as a mispredict from the
     * client application, and cancels the previous prerender. This is done to
     * avoid keeping resources laying around for too long, but is subject to a
     * race condition, as the following scenario is possible:
     * The application calls:
     * 1. mayLaunchUrl(url1) <- IPC
     * 2. loadUrl(url2) <- Intent
     * 3. mayLaunchUrl(url3) <- IPC
     * If the IPC for url3 arrives before the intent for url2, then this methods
     * cancels the prerender for url3, which is unexpected. On the other
     * hand, not cancelling the previous prerender leads to wasted resources, as
     * a WebContents is lingering. This can be solved by requiring applications
     * to call mayLaunchUrl(null) to cancel a current prerender before 2, that
     * is for a mispredict.
     *
     * @param session The Binder object identifying a session.
     * @param url The URL the WebContents is for.
     * @param referrer The referrer to use for |url|.
     * @return The prerendered WebContents, or null.
     */
    WebContents takePrerenderedUrl(IBinder session, String url, String referrer) {
        ThreadUtils.assertOnUiThread();
        if (mPrerender == null || session == null || !session.equals(mPrerender.mSession)) {
            return null;
        }
        WebContents webContents = mPrerender.mWebContents;
        String prerenderedUrl = mPrerender.mUrl;
        String prerenderReferrer = mPrerender.mReferrer;
        if (referrer == null) referrer = "";
        if (TextUtils.equals(prerenderedUrl, url)
                && TextUtils.equals(prerenderReferrer, referrer)) {
            mPrerender = null;
            return webContents;
        } else {
            cancelPrerender(session);
        }
        return null;
    }

    /** See {@link ClientManager#getReferrerForSession(IBinder)} */
    public Referrer getReferrerForSession(IBinder session) {
        return mClientManager.getReferrerForSession(session);
    }

    /** See {@link ClientManager#getClientPackageNameForSession(IBinder)} */
    public String getClientPackageNameForSession(IBinder session) {
        return mClientManager.getClientPackageNameForSession(session);
    }

    /**
     * Notifies the application of a navigation event.
     *
     * Delivers the {@link ICustomTabsConnectionCallback#onNavigationEvent}
     * callback to the aplication.
     *
     * @param session The Binder object identifying the session.
     * @param navigationEvent The navigation event code, defined in {@link CustomTabsCallback}
     * @return true for success.
     */
    boolean notifyNavigationEvent(IBinder session, int navigationEvent) {
        ICustomTabsCallback callback = mClientManager.getCallbackForSession(session);
        if (callback == null) return false;
        try {
            callback.onNavigationEvent(navigationEvent, null);
        } catch (Exception e) {
            // Catching all exceptions is really bad, but we need it here,
            // because Android exposes us to client bugs by throwing a variety
            // of exceptions. See crbug.com/517023.
            return false;
        }
        return true;
    }

    /**
     * Keeps the application linked with a given session alive.
     *
     * The application is kept alive (that is, raised to at least the current
     * process priority level) until {@link dontKeepAliveForSessionId()} is
     * called.
     *
     * @param session The Binder object identifying the session.
     * @param intent Intent describing the service to bind to.
     * @return true for success.
     */
    boolean keepAliveForSession(IBinder session, Intent intent) {
        return mClientManager.keepAliveForSession(session, intent);
    }

    /**
     * Lets the lifetime of the process linked to a given sessionId be managed normally.
     *
     * Without a matching call to {@link keepAliveForSessionId}, this is a no-op.
     *
     * @param session The Binder object identifying the session.
     */
    void dontKeepAliveForSession(IBinder session) {
        mClientManager.dontKeepAliveForSession(session);
    }

    /**
     * @return the CPU cgroup of a given process, identified by its PID, or null.
     */
    @VisibleForTesting
    static String getSchedulerGroup(int pid) {
        // Android uses two cgroups for the processes: the root cgroup, and the
        // "/bg_non_interactive" one for background processes. The list of
        // cgroups a process is part of can be queried by reading
        // /proc/<pid>/cgroup, which is world-readable.
        String cgroupFilename = "/proc/" + pid + "/cgroup";
        try {
            FileReader fileReader = new FileReader(cgroupFilename);
            BufferedReader reader = new BufferedReader(fileReader);
            try {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    // line format: 2:cpu:/bg_non_interactive
                    String fields[] = line.trim().split(":");
                    if (fields.length == 3 && fields[1].equals("cpu")) return fields[2];
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static boolean isBackgroundProcess(int pid) {
        String schedulerGroup = getSchedulerGroup(pid);
        // "/bg_non_interactive" is from L MR1, "/apps/bg_non_interactive" before.
        return "/bg_non_interactive".equals(schedulerGroup)
                || "/apps/bg_non_interactive".equals(schedulerGroup);
    }

    /**
     * @return true when inside a Binder transaction and the caller is in the
     * foreground or self. Don't use outside a Binder transaction.
     */
    private boolean isCallerForegroundOrSelf() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return true;
        // Starting with L MR1, AM.getRunningAppProcesses doesn't return all the
        // processes. We use a workaround in this case.
        boolean useWorkaround = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            ActivityManager am =
                    (ActivityManager) mApplication.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo rpi : running) {
                boolean matchingUid = rpi.uid == uid;
                boolean isForeground = rpi.importance
                        == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
                useWorkaround &= !matchingUid;
                if (matchingUid && isForeground) return true;
            }
        }
        return useWorkaround ? !isBackgroundProcess(Binder.getCallingPid()) : false;
    }

    @VisibleForTesting
    void cleanupAll() {
        ThreadUtils.assertOnUiThread();
        mClientManager.cleanupAll();
    }

    private boolean mayPrerender() {
        if (FieldTrialList.findFullName("CustomTabs").equals("DisablePrerender")) return false;
        if (!DeviceClassManager.enablePrerendering()) return false;
        ConnectivityManager cm =
                (ConnectivityManager) mApplication.getApplicationContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        return !cm.isActiveNetworkMetered();
    }

    /** Cancels a prerender for a given session, or any session if null. */
    void cancelPrerender(IBinder session) {
        ThreadUtils.assertOnUiThread();
        if (mPrerender != null && (session == null || session.equals(mPrerender.mSession))) {
            mExternalPrerenderHandler.cancelCurrentPrerender();
            mPrerender.mWebContents.destroy();
            mPrerender = null;
        }
    }

    private void prerenderUrl(IBinder session, String url, Bundle extras, int uid) {
        ThreadUtils.assertOnUiThread();
        // TODO(lizeb): Prerendering through ChromePrerenderService is
        // incompatible with prerendering through this service. Remove this
        // limitation, or remove ChromePrerenderService.
        WarmupManager.getInstance().disallowPrerendering();
        // Ignores mayPrerender() for an empty URL, since it cancels an existing prerender.
        if (!mayPrerender() && !TextUtils.isEmpty(url)) return;
        if (!mWarmupHasBeenCalled.get()) return;
        // Last one wins and cancels the previous prerender.
        cancelPrerender(null);
        if (TextUtils.isEmpty(url)) return;
        if (!mClientManager.isPrerenderingAllowed(uid)) return;

        // A prerender will be requested. Time to destroy the spare WebContents.
        destroySpareWebContents();

        Intent extrasIntent = new Intent();
        if (extras != null) extrasIntent.putExtras(extras);
        if (IntentHandler.getExtraHeadersFromIntent(extrasIntent) != null) return;
        if (mExternalPrerenderHandler == null) {
            mExternalPrerenderHandler = new ExternalPrerenderHandler();
        }
        Point contentSize = estimateContentSize();
        Context context = mApplication.getApplicationContext();
        String referrer = IntentHandler.getReferrerUrlIncludingExtraHeaders(extrasIntent, context);
        if (referrer == null && getReferrerForSession(session) != null) {
            referrer = getReferrerForSession(session).getUrl();
        }
        if (referrer == null) referrer = "";
        WebContents webContents = mExternalPrerenderHandler.addPrerender(
                Profile.getLastUsedProfile(), url, referrer, contentSize.x, contentSize.y);
        if (webContents != null) {
            mClientManager.registerPrerenderRequest(uid, url);
            mPrerender = new PrerenderedUrlParams(session, webContents, url, referrer, extras);
        }
    }

    /**
     * Provides an estimate of the contents size.
     *
     * The estimate is likely to be incorrect. This is not a problem, as the aim
     * is to avoid getting a different layout and resources than needed at
     * render time.
     */
    private Point estimateContentSize() {
        // The size is estimated as:
        // X = screenSizeX
        // Y = screenSizeY - top bar - bottom bar - custom tabs bar
        Point screenSize = new Point();
        WindowManager wm = (WindowManager) mApplication.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getSize(screenSize);
        Resources resources = mApplication.getResources();
        int statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android");
        try {
            screenSize.y -=
                    resources.getDimensionPixelSize(R.dimen.custom_tabs_control_container_height);
            screenSize.y -= resources.getDimensionPixelSize(statusBarId);
        } catch (Resources.NotFoundException e) {
            // Nothing, this is just a best effort estimate.
        }
        float density = resources.getDisplayMetrics().density;
        screenSize.x /= density;
        screenSize.y /= density;
        return screenSize;
    }

    @VisibleForTesting
    void resetThrottling(Context context, int uid) {
        mClientManager.resetThrottling(uid);
    }
}
