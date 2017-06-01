// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.ActivityManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.StrictMode;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsService;
import android.support.customtabs.CustomTabsSessionToken;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.RemoteViews;

import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.prerender.ExternalPrerenderHandler;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.content.browser.ChildProcessCreationParams;
import org.chromium.content.browser.ChildProcessLauncher;
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
public class CustomTabsConnection {
    private static final String TAG = "ChromeConnection";
    private static final String LOG_SERVICE_REQUESTS = "custom-tabs-log-service-requests";

    @VisibleForTesting
    static final String PAGE_LOAD_METRICS_CALLBACK = "NavigationMetrics";

    // For testing only, DO NOT USE.
    @VisibleForTesting
    static final String DEBUG_OVERRIDE_KEY =
            "android.support.customtabs.maylaunchurl.DEBUG_OVERRIDE";
    private static final int NO_OVERRIDE = 0;
    @VisibleForTesting
    static final int NO_PRERENDERING = 1;
    @VisibleForTesting
    static final int PREFETCH_ONLY = 2;

    private static AtomicReference<CustomTabsConnection> sInstance = new AtomicReference<>();

    /** Holds the parameters for the current speculation. */
    @VisibleForTesting
    static final class SpeculationParams {
        public final CustomTabsSessionToken session;
        public final String url;

        // Only for prerender.
        public final WebContents webContents;
        public final String referrer;
        public final Bundle extras;

        public final boolean prefetchOnly;

        static SpeculationParams forPrerender(CustomTabsSessionToken session, String url,
                WebContents webcontents, String referrer, Bundle extras) {
            return new SpeculationParams(session, url, webcontents, referrer, extras, false);
        }

        static SpeculationParams forPrefetch(CustomTabsSessionToken session, String url) {
            return new SpeculationParams(session, url, null, null, null, true);
        }

        private SpeculationParams(CustomTabsSessionToken session, String url,
                WebContents webContents, String referrer, Bundle extras, boolean prefetchOnly) {
            this.session = session;
            this.url = url;
            this.webContents = webContents;
            this.referrer = referrer;
            this.extras = extras;
            this.prefetchOnly = prefetchOnly;
        }
    }

    @VisibleForTesting
    SpeculationParams mSpeculation;
    protected final Application mApplication;
    protected final ClientManager mClientManager;
    private final boolean mLogRequests;
    private final AtomicBoolean mWarmupHasBeenCalled = new AtomicBoolean();
    private final AtomicBoolean mWarmupHasBeenFinished = new AtomicBoolean();
    private ExternalPrerenderHandler mExternalPrerenderHandler;

    /**
     * <strong>DO NOT CALL</strong>
     * Public to be instanciable from {@link ChromeApplication}. This is however
     * intended to be private.
     */
    public CustomTabsConnection(Application application) {
        super();
        mApplication = application;
        mClientManager = new ClientManager(mApplication);
        mLogRequests = CommandLine.getInstance().hasSwitch(LOG_SERVICE_REQUESTS);
    }

    /**
     * @return The unique instance of ChromeCustomTabsConnection.
     */
    @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
    public static CustomTabsConnection getInstance(Application application) {
        if (sInstance.get() == null) {
            ChromeApplication chromeApplication = (ChromeApplication) application;
            chromeApplication.initCommandLine();
            sInstance.compareAndSet(null, chromeApplication.createCustomTabsConnection());
        }
        return sInstance.get();
    }

    /**
     * If service requests logging is enabled, logs that a call was made.
     *
     * No rate-limiting, can be spammy if the app is misbehaved.
     *
     * @param name Call name to log.
     * @param The return value for the logged call.
     */
    void logCall(String name, Object result) {
        if (mLogRequests) {
            Log.w(TAG, "%s = %b, Calling UID = %d", name, result, Binder.getCallingUid());
        }
    }

    public boolean newSession(CustomTabsSessionToken session) {
        boolean success = newSessionInternal(session);
        logCall("newSession()", success);
        return success;
    }

    private boolean newSessionInternal(CustomTabsSessionToken session) {
        ClientManager.DisconnectCallback onDisconnect = new ClientManager.DisconnectCallback() {
            @Override
            public void run(CustomTabsSessionToken session) {
                cancelPrerender(session);
            }
        };
        PostMessageHandler handler = new PostMessageHandler(session);
        return mClientManager.newSession(session, Binder.getCallingUid(), onDisconnect, handler);
    }

    /** Warmup activities that should only happen once. */
    @SuppressFBWarnings("DM_EXIT")
    private static void initializeBrowser(final Application app) {
        ThreadUtils.assertOnUiThread();
        try {
            ChromeBrowserInitializer.getInstance(app).handleSynchronousStartup();
        } catch (ProcessInitException e) {
            Log.e(TAG, "ProcessInitException while starting the browser process.");
            // Cannot do anything without the native library, and cannot show a
            // dialog to the user.
            System.exit(-1);
        }
        final Context context = app.getApplicationContext();
        final ChromeApplication chrome = (ChromeApplication) context;
        ChildProcessCreationParams.set(chrome.getChildProcessCreationParams());
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ChildProcessLauncher.warmUp(context);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        ChromeBrowserInitializer.initNetworkChangeNotifier(context);
        WarmupManager.getInstance().initializeViewHierarchy(
                context, R.layout.custom_tabs_control_container, R.layout.custom_tabs_toolbar);
    }

    public boolean warmup(long flags) {
        boolean success = warmupInternal(true);
        logCall("warmup()", success);
        return success;
    }

    /**
     * @return Whether {@link CustomTabsConnection#warmup(long)} has been called.
     */
    public static boolean hasWarmUpBeenFinished(Application application) {
        return getInstance(application).mWarmupHasBeenFinished.get();
    }

    /**
     * Starts as much as possible in anticipation of a future navigation.
     *
     * @param mayCreatesparewebcontents true if warmup() can create a spare renderer.
     * @return true for success.
     */
    private boolean warmupInternal(final boolean mayCreateSpareWebContents) {
        // Here and in mayLaunchUrl(), don't do expensive work for background applications.
        if (!isCallerForegroundOrSelf()) return false;
        mClientManager.recordUidHasCalledWarmup(Binder.getCallingUid());
        final boolean initialized = !mWarmupHasBeenCalled.compareAndSet(false, true);
        final int uid = Binder.getCallingUid();
        // The call is non-blocking and this must execute on the UI thread, post a task.
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!initialized) initializeBrowser(mApplication);
                if (mayCreateSpareWebContents && mSpeculation == null
                        && !SysUtils.isLowEndDevice()) {
                    WarmupManager.getInstance().createSpareWebContents();
                    // The throttling database uses shared preferences, that can cause a StrictMode
                    // violation on the first access. Make sure that this access is not in
                    // mayLauchUrl.
                    RequestThrottler.getForUid(mApplication, uid);

                    Profile profile = Profile.getLastUsedProfile();
                    new ResourcePrefetchPredictor(profile).startInitialization();
                }
                mWarmupHasBeenFinished.set(true);
            }
        });
        return true;
    }

    /** @return the URL converted to string, or null if it's invalid. */
    private static String checkAndConvertUri(Uri uri) {
        if (uri == null) return null;
        // Don't do anything for unknown schemes. Not having a scheme is allowed, as we allow
        // "www.example.com".
        String scheme = uri.normalizeScheme().getScheme();
        boolean allowedScheme = scheme == null || scheme.equals("http") || scheme.equals("https");
        if (!allowedScheme) return null;
        return uri.toString();
    }

    /**
     * High confidence mayLaunchUrl() call, that is:
     * - Tries to prerender if possible.
     * - An empty URL cancels the current prerender if any.
     * - If prerendering is not possible, makes sure that there is a spare renderer.
     */
    private void highConfidenceMayLaunchUrl(CustomTabsSessionToken session,
            int uid, String url, Bundle extras, List<Bundle> otherLikelyBundles) {
        ThreadUtils.assertOnUiThread();
        if (TextUtils.isEmpty(url)) {
            cancelPrerender(session);
            return;
        }

        WarmupManager warmupManager = WarmupManager.getInstance();
        Profile profile = Profile.getLastUsedProfile();

        url = DataReductionProxySettings.getInstance().maybeRewriteWebliteUrl(url);
        int debugOverrideValue = NO_OVERRIDE;
        if (extras != null) debugOverrideValue = extras.getInt(DEBUG_OVERRIDE_KEY, NO_OVERRIDE);

        boolean didStartPrerender = false, didStartPrefetch = false;
        boolean mayPrerender = mayPrerender(session);
        if (mayPrerender) {
            if (debugOverrideValue == PREFETCH_ONLY) {
                didStartPrefetch = new ResourcePrefetchPredictor(profile).startPrefetching(url);
                if (didStartPrefetch) mSpeculation = SpeculationParams.forPrefetch(session, url);
            } else if (debugOverrideValue != NO_PRERENDERING) {
                didStartPrerender = prerenderUrl(session, url, extras, uid);
            }
        }
        preconnectUrls(otherLikelyBundles);
        if (!didStartPrefetch) warmupManager.maybePreconnectUrlAndSubResources(profile, url);
        if (!didStartPrerender) warmupManager.createSpareWebContents();
    }

    /**
     * Low confidence mayLaunchUrl() call, that is:
     * - Preconnects to the ordered list of URLs.
     * - Makes sure that there is a spare renderer.
     */
    @VisibleForTesting
    boolean lowConfidenceMayLaunchUrl(List<Bundle> likelyBundles) {
        ThreadUtils.assertOnUiThread();
        if (!preconnectUrls(likelyBundles)) return false;
        WarmupManager.getInstance().createSpareWebContents();
        return true;
    }

    private boolean preconnectUrls(List<Bundle> likelyBundles) {
        boolean atLeastOneUrl = false;
        if (likelyBundles == null) return false;
        WarmupManager warmupManager = WarmupManager.getInstance();
        Profile profile = Profile.getLastUsedProfile();
        for (Bundle bundle : likelyBundles) {
            Uri uri;
            try {
                uri = IntentUtils.safeGetParcelable(bundle, CustomTabsService.KEY_URL);
            } catch (ClassCastException e) {
                continue;
            }
            String url = checkAndConvertUri(uri);
            if (url != null) {
                warmupManager.maybePreconnectUrlAndSubResources(profile, url);
                atLeastOneUrl = true;
            }
        }
        return atLeastOneUrl;
    }

    public boolean mayLaunchUrl(CustomTabsSessionToken session, Uri url, Bundle extras,
            List<Bundle> otherLikelyBundles) {
        boolean success = mayLaunchUrlInternal(session, url, extras, otherLikelyBundles);
        logCall("mayLaunchUrl()", success);
        return success;
    }

    private boolean mayLaunchUrlInternal(final CustomTabsSessionToken session, Uri url,
            final Bundle extras, final List<Bundle> otherLikelyBundles) {
        final boolean lowConfidence =
                (url == null || TextUtils.isEmpty(url.toString())) && otherLikelyBundles != null;
        final String urlString = checkAndConvertUri(url);
        if (url != null && urlString == null && !lowConfidence) return false;

        // Things below need the browser process to be initialized.

        // Forbids warmup() from creating a spare renderer, as prerendering wouldn't reuse
        // it. Checking whether prerendering is enabled requires the native library to be loaded,
        // which is not necessarily the case yet.
        if (!warmupInternal(false)) return false; // Also does the foreground check.

        final int uid = Binder.getCallingUid();
        // TODO(lizeb): Also throttle low-confidence mode.
        if (!lowConfidence
                && !mClientManager.updateStatsAndReturnWhetherAllowed(session, uid, urlString)) {
            return false;
        }
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (lowConfidence) {
                    lowConfidenceMayLaunchUrl(otherLikelyBundles);
                } else {
                    highConfidenceMayLaunchUrl(session, uid, urlString, extras, otherLikelyBundles);
                }
            }
        });
        return true;
    }

    public Bundle extraCommand(String commandName, Bundle args) {
        return null;
    }

    public boolean updateVisuals(final CustomTabsSessionToken session, Bundle bundle) {
        final Bundle actionButtonBundle = IntentUtils.safeGetBundle(bundle,
                CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE);
        boolean result = true;
        if (actionButtonBundle != null) {
            final int id = IntentUtils.safeGetInt(actionButtonBundle, CustomTabsIntent.KEY_ID,
                    CustomTabsIntent.TOOLBAR_ACTION_BUTTON_ID);
            final Bitmap bitmap = CustomButtonParams.parseBitmapFromBundle(actionButtonBundle);
            final String description = CustomButtonParams
                    .parseDescriptionFromBundle(actionButtonBundle);
            if (bitmap != null && description != null) {
                try {
                    result &= ThreadUtils.runOnUiThreadBlocking(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return CustomTabActivity.updateCustomButton(session, id,
                                    bitmap, description);
                        }
                    });
                } catch (ExecutionException e) {
                    result = false;
                }
            }
        }
        if (bundle.containsKey(CustomTabsIntent.EXTRA_REMOTEVIEWS)) {
            final RemoteViews remoteViews = IntentUtils.safeGetParcelable(bundle,
                    CustomTabsIntent.EXTRA_REMOTEVIEWS);
            final int[] clickableIDs = IntentUtils.safeGetIntArray(bundle,
                    CustomTabsIntent.EXTRA_REMOTEVIEWS_VIEW_IDS);
            final PendingIntent pendingIntent = IntentUtils.safeGetParcelable(bundle,
                    CustomTabsIntent.EXTRA_REMOTEVIEWS_PENDINGINTENT);
            try {
                result &= ThreadUtils.runOnUiThreadBlocking(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return CustomTabActivity.updateRemoteViews(session,
                                remoteViews, clickableIDs, pendingIntent);
                    }
                });
            } catch (ExecutionException e) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Sends a request to look for a valid postMessage origin that is associated with the app
     * owning the given {@link CustomTabsSessionToken}.
     * @param session The session that the request is associated with
     * @return Whether the validation request was accepted.
     */
    public boolean validatePostMessageOrigin(CustomTabsSessionToken session) {
        boolean success = validatePostMessageOriginInternal(session);
        logCall("validatePostMessageOrigin()", success);
        return success;
    }

    private boolean validatePostMessageOriginInternal(final CustomTabsSessionToken session) {
        if (!mWarmupHasBeenCalled.get()) return false;
        if (!isCallerForegroundOrSelf()) return false;
        final int uid = Binder.getCallingUid();
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                // If the API is not enabled, we don't set the post message origin, which will
                // avoid PostMessageHandler initialization and disallow postMessage calls.
                if (!ChromeFeatureList.isEnabled(ChromeFeatureList.CCT_POST_MESSAGE_API)) return;
                mClientManager.setPostMessageOriginForSession(
                        session, acquireOriginForSession(session, uid));
            }
        });
        return true;
    }

    /**
     * Acquire the origin for the client that owns the given session.
     * @param session The session to use for getting client information.
     * @param clientUID The UID for the client controlling the session.
     * @return The validated origin {@link Uri} for the given session's client.
     */
    protected Uri acquireOriginForSession(CustomTabsSessionToken session, int clientUID) {
        if (clientUID == Process.myUid()) return Uri.EMPTY;
        return null;
    }

    public int postMessage(CustomTabsSessionToken session, String message, Bundle extras) {
        int result;
        if (!mWarmupHasBeenCalled.get()) result = CustomTabsService.RESULT_FAILURE_DISALLOWED;
        if (!isCallerForegroundOrSelf() && !CustomTabActivity.isActiveSession(session)) {
            result = CustomTabsService.RESULT_FAILURE_DISALLOWED;
        }
        // If called before a validatePostMessageOrigin, the post message origin will be invalid and
        // will return a failure result here.
        result = mClientManager.postMessage(session, message);
        logCall("postMessage", result);
        return result;
    }

    /**
     * See
     * {@link ClientManager#resetPostMessageHandlerForSession(CustomTabsSessionToken, WebContents)}.
     */
    public void resetPostMessageHandlerForSession(
            CustomTabsSessionToken session, WebContents webContents) {
        mClientManager.resetPostMessageHandlerForSession(session, webContents);
    }

    /**
     * Registers a launch of a |url| for a given |session|.
     *
     * This is used for accounting.
     */
    void registerLaunch(CustomTabsSessionToken session, String url) {
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
     * Note that this methods accepts URLs that don't exactly match the initially
     * prerendered URL. More precisely, the #fragment is ignored. In this case,
     * the client needs to navigate to the correct URL after the WebContents
     * swap. This can be tested using {@link UrlUtilities#urlsFragmentsDiffer()}.
     *
     * @param session The Binder object identifying a session.
     * @param url The URL the WebContents is for.
     * @param referrer The referrer to use for |url|.
     * @return The prerendered WebContents, or null.
     */
    WebContents takePrerenderedUrl(CustomTabsSessionToken session, String url, String referrer) {
        ThreadUtils.assertOnUiThread();
        if (mSpeculation == null || session == null || !session.equals(mSpeculation.session)) {
            return null;
        }

        if (mSpeculation.prefetchOnly) {
            Profile profile = Profile.getLastUsedProfile();
            new ResourcePrefetchPredictor(profile).stopPrefetching(mSpeculation.url);
            mSpeculation = null;
            return null;
        }

        WebContents webContents = mSpeculation.webContents;
        String prerenderedUrl = mSpeculation.url;
        String prerenderReferrer = mSpeculation.referrer;
        if (referrer == null) referrer = "";
        boolean ignoreFragments = mClientManager.getIgnoreFragmentsForSession(session);
        boolean urlsMatch = TextUtils.equals(prerenderedUrl, url)
                || (ignoreFragments
                        && UrlUtilities.urlsMatchIgnoringFragments(prerenderedUrl, url));
        WebContents result = null;
        if (urlsMatch && TextUtils.equals(prerenderReferrer, referrer)) {
            result = webContents;
            mSpeculation = null;
        } else {
            cancelPrerender(session);
        }
        if (!mClientManager.usesDefaultSessionParameters(session) && webContents != null) {
            RecordHistogram.recordBooleanHistogram(
                    "CustomTabs.NonDefaultSessionPrerenderMatched", result != null);
        }

        return result;
    }

    /** Returns the URL prerendered for a session, or null. */
    String getPrerenderedUrl(CustomTabsSessionToken session) {
        if (mSpeculation == null || session == null || !session.equals(mSpeculation.session)) {
            return null;
        }
        return mSpeculation.webContents != null ? mSpeculation.url : null;
    }

    /** See {@link ClientManager#getReferrerForSession(CustomTabsSessionToken)} */
    public Referrer getReferrerForSession(CustomTabsSessionToken session) {
        return mClientManager.getReferrerForSession(session);
    }

    /** @see ClientManager#shouldHideDomainForSession(CustomTabsSessionToken) */
    public boolean shouldHideDomainForSession(CustomTabsSessionToken session) {
        return mClientManager.shouldHideDomainForSession(session);
    }

    /** @see ClientManager#shouldPrerenderOnCellularForSession(CustomTabsSessionToken) */
    public boolean shouldPrerenderOnCellularForSession(CustomTabsSessionToken session) {
        return mClientManager.shouldPrerenderOnCellularForSession(session);
    }

    /** @see ClientManager#shouldSendNavigationInfoForSession(CustomTabsSessionToken) */
    public boolean shouldSendNavigationInfoForSession(CustomTabsSessionToken session) {
        return mClientManager.shouldSendNavigationInfoForSession(session);
    }

    /** See {@link ClientManager#getClientPackageNameForSession(CustomTabsSessionToken)} */
    public String getClientPackageNameForSession(CustomTabsSessionToken session) {
        return mClientManager.getClientPackageNameForSession(session);
    }

    @VisibleForTesting
    void setIgnoreUrlFragmentsForSession(CustomTabsSessionToken session, boolean value) {
        mClientManager.setIgnoreFragmentsForSession(session, value);
    }

    @VisibleForTesting
    boolean getIgnoreUrlFragmentsForSession(CustomTabsSessionToken session) {
        return mClientManager.getIgnoreFragmentsForSession(session);
    }

    @VisibleForTesting
    void setShouldPrerenderOnCellularForSession(CustomTabsSessionToken session, boolean value) {
        mClientManager.setPrerenderCellularForSession(session, value);
    }

    /**
     * See {@link ClientManager#setSendNavigationInfoForSession(CustomTabsSessionToken, boolean)}.
     */
    void setSendNavigationInfoForSession(CustomTabsSessionToken session, boolean send) {
        mClientManager.setSendNavigationInfoForSession(session, send);
    }

    /**
     * Extracts the creator package name from the intent.
     * @param intent The intent to get the package name from.
     * @return the package name which can be null.
     */
    String extractCreatorPackage(Intent intent) {
        return null;
    }

    /**
     * Shows a toast about any possible sign in issues encountered during custom tab startup.
     * @param session The session that corresponding custom tab is assigned.
     * @param intent The intent that launched the custom tab.
     */
    void showSignInToastIfNecessary(CustomTabsSessionToken session, Intent intent) { }

    /**
     * Sends a callback using {@link CustomTabsCallback} about the first run result if necessary.
     * @param intent The initial VIEW intent that initiated first run.
     * @param resultOK Whether first run was successful.
     */
    public void sendFirstRunCallbackIfNecessary(Intent intent, boolean resultOK) { }

    /**
     * Sends the navigation info that was captured to the client callback.
     * @param session The session to use for getting client callback.
     * @param url The current url for the tab.
     * @param title The current title for the tab.
     * @param screenshot A screenshot of the tab contents.
     */
    public void sendNavigationInfo(
            CustomTabsSessionToken session, String url, String title, Bitmap screenshot) { }

    /**
     * Notifies the application of a navigation event.
     *
     * Delivers the {@link CustomTabsConnectionCallback#onNavigationEvent}
     * callback to the application.
     *
     * @param session The Binder object identifying the session.
     * @param navigationEvent The navigation event code, defined in {@link CustomTabsCallback}
     * @return true for success.
     */
    boolean notifyNavigationEvent(CustomTabsSessionToken session, int navigationEvent) {
        CustomTabsCallback callback = mClientManager.getCallbackForSession(session);
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
     * Notifies the application of a page load metric.
     *
     * TODD(lizeb): Move this to a proper method in {@link CustomTabsCallback} once one is
     * available.
     *
     * @param session Session identifier.
     * @param metricName Name of the page load metric.
     * @param offsetMs Offset in ms from navigationStart.
     */
    boolean notifyPageLoadMetric(CustomTabsSessionToken session, String metricName, long offsetMs) {
        CustomTabsCallback callback = mClientManager.getCallbackForSession(session);
        if (callback == null) return false;
        Bundle args = new Bundle();
        args.putLong(metricName, offsetMs);
        try {
            callback.extraCallback(PAGE_LOAD_METRICS_CALLBACK, args);
        } catch (Exception e) {
            // Pokemon exception handling, see above and crbug.com/517023.
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
    boolean keepAliveForSession(CustomTabsSessionToken session, Intent intent) {
        return mClientManager.keepAliveForSession(session, intent);
    }

    /**
     * Lets the lifetime of the process linked to a given sessionId be managed normally.
     *
     * Without a matching call to {@link keepAliveForSessionId}, this is a no-op.
     *
     * @param session The Binder object identifying the session.
     */
    void dontKeepAliveForSession(CustomTabsSessionToken session) {
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
        // Reading from /proc does not cause disk IO, but strict mode doesn't like it.
        // crbug.com/567143
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
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
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
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
            do {
                ActivityManager am =
                        (ActivityManager) mApplication.getSystemService(Context.ACTIVITY_SERVICE);
                // Extra paranoia here and below, some L 5.0.x devices seem to throw NPE somewhere
                // in this code.
                // See https://crbug.com/654705.
                if (am == null) break;
                List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
                if (running == null) break;
                for (ActivityManager.RunningAppProcessInfo rpi : running) {
                    if (rpi == null) continue;
                    boolean matchingUid = rpi.uid == uid;
                    boolean isForeground = rpi.importance
                            == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
                    useWorkaround &= !matchingUid;
                    if (matchingUid && isForeground) return true;
                }
            } while (false);
        }
        return useWorkaround ? !isBackgroundProcess(Binder.getCallingPid()) : false;
    }

    @VisibleForTesting
    void cleanupAll() {
        ThreadUtils.assertOnUiThread();
        mClientManager.cleanupAll();
    }

    /**
     * Handle any clean up left after a session is destroyed.
     * @param session The session that has been destroyed.
     */
    @VisibleForTesting
    void cleanUpSession(final CustomTabsSessionToken session) {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mClientManager.cleanupSession(session);
            }
        });
    }

    private boolean mayPrerender(CustomTabsSessionToken session) {
        if (!DeviceClassManager.enablePrerendering()) return false;
        // TODO(yusufo): The check for prerender in PrivacyManager now checks for the network
        // connection type as well, we should either change that or add another check for custom
        // tabs. Then PrivacyManager should be used to make the below check.
        if (!PrefServiceBridge.getInstance().getNetworkPredictionEnabled()) return false;
        if (DataReductionProxySettings.getInstance().isDataReductionProxyEnabled()) return false;
        ConnectivityManager cm =
                (ConnectivityManager) mApplication.getApplicationContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        return !cm.isActiveNetworkMetered() || shouldPrerenderOnCellularForSession(session);
    }

    /** Cancels a prerender for a given session, or any session if null. */
    void cancelPrerender(CustomTabsSessionToken session) {
        ThreadUtils.assertOnUiThread();
        if (mSpeculation != null && (session == null || session.equals(mSpeculation.session))
                && mSpeculation.webContents != null) {
            mExternalPrerenderHandler.cancelCurrentPrerender();
            mSpeculation.webContents.destroy();
            mSpeculation = null;
        }
    }

    /**
     * Tries to request a prerender for a given URL.
     *
     * @param session Session the request comes from.
     * @param url URL to prerender.
     * @param extras extra parameters.
     * @param uid UID of the caller.
     * @return true if a prerender has been initiated.
     */
    private boolean prerenderUrl(
            CustomTabsSessionToken session, String url, Bundle extras, int uid) {
        ThreadUtils.assertOnUiThread();
        // Ignores mayPrerender() for an empty URL, since it cancels an existing prerender.
        if (!mayPrerender(session) && !TextUtils.isEmpty(url)) return false;
        if (!mWarmupHasBeenCalled.get()) return false;
        // Last one wins and cancels the previous prerender.
        cancelPrerender(null);
        if (TextUtils.isEmpty(url)) return false;
        boolean throttle = !shouldPrerenderOnCellularForSession(session);
        if (throttle && !mClientManager.isPrerenderingAllowed(uid)) return false;

        // A prerender will be requested. Time to destroy the spare WebContents.
        WarmupManager.getInstance().destroySpareWebContents();

        Intent extrasIntent = new Intent();
        if (extras != null) extrasIntent.putExtras(extras);
        if (IntentHandler.getExtraHeadersFromIntent(extrasIntent) != null) return false;
        if (mExternalPrerenderHandler == null) {
            mExternalPrerenderHandler = new ExternalPrerenderHandler();
        }
        Rect contentBounds = ExternalPrerenderHandler.estimateContentSize(mApplication, true);
        Context context = mApplication.getApplicationContext();
        String referrer = IntentHandler.getReferrerUrlIncludingExtraHeaders(extrasIntent, context);
        if (referrer == null && getReferrerForSession(session) != null) {
            referrer = getReferrerForSession(session).getUrl();
        }
        if (referrer == null) referrer = "";
        Pair<WebContents, WebContents> webContentsPair = mExternalPrerenderHandler.addPrerender(
                Profile.getLastUsedProfile(), url, referrer,
                contentBounds,
                shouldPrerenderOnCellularForSession(session));
        if (webContentsPair == null) return false;
        WebContents dummyWebContents = webContentsPair.first;
        if (webContentsPair.second != null) {
            mClientManager.resetPostMessageHandlerForSession(session, webContentsPair.second);
        }
        if (throttle) mClientManager.registerPrerenderRequest(uid, url);
        mSpeculation = SpeculationParams.forPrerender(
                session, url, dummyWebContents, referrer, extras);

        RecordHistogram.recordBooleanHistogram("CustomTabs.PrerenderSessionUsesDefaultParameters",
                mClientManager.usesDefaultSessionParameters(session));

        return true;
    }

    @VisibleForTesting
    void resetThrottling(Context context, int uid) {
        mClientManager.resetThrottling(uid);
    }

    @VisibleForTesting
    void ban(Context context, int uid) {
        mClientManager.ban(uid);
    }
}
