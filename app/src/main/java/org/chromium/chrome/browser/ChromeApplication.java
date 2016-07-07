// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import com.google.ipc.invalidation.external.client.android.service.AndroidLogger;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ApplicationStateListener;
import org.chromium.base.BuildInfo;
import org.chromium.base.CommandLineInitUtil;
import org.chromium.base.PathUtils;
import org.chromium.base.ResourceExtractor;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.accessibility.FontSizePrefs;
import org.chromium.chrome.browser.banners.AppBannerManager;
import org.chromium.chrome.browser.banners.AppDetailsDelegate;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.datausage.ExternalDataUseObserver;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.document.IncognitoDocumentActivity;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.feedback.EmptyFeedbackReporter;
import org.chromium.chrome.browser.feedback.FeedbackReporter;
import org.chromium.chrome.browser.firstrun.ForcedSigninProcessor;
import org.chromium.chrome.browser.gsa.GSAHelper;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.identity.UniqueIdentificationGeneratorFactory;
import org.chromium.chrome.browser.identity.UuidBasedUniqueIdentificationGenerator;
import org.chromium.chrome.browser.init.InvalidStartupDialog;
import org.chromium.chrome.browser.invalidation.UniqueIdInvalidationClientNameGenerator;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.metrics.VariationsSession;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.net.qualityprovider.ExternalEstimateProviderAndroid;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.notifications.NotificationUIManager;
import org.chromium.chrome.browser.omaha.RequestGenerator;
import org.chromium.chrome.browser.omaha.UpdateInfoBarHelper;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.physicalweb.PhysicalWebBleClient;
import org.chromium.chrome.browser.policy.PolicyAuditor;
import org.chromium.chrome.browser.preferences.AccessibilityPreferences;
import org.chromium.chrome.browser.preferences.LocationSettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.autofill.AutofillPreferences;
import org.chromium.chrome.browser.preferences.password.SavePasswordsPreferences;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferences;
import org.chromium.chrome.browser.preferences.website.SingleWebsitePreferences;
import org.chromium.chrome.browser.printing.PrintingControllerFactory;
import org.chromium.chrome.browser.rlz.RevenueStats;
import org.chromium.chrome.browser.services.AccountsChangedReceiver;
import org.chromium.chrome.browser.services.AndroidEduOwnerCheckCallback;
import org.chromium.chrome.browser.services.GoogleServicesManager;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.smartcard.EmptyPKCS11AuthenticationManager;
import org.chromium.chrome.browser.smartcard.PKCS11AuthenticationManager;
import org.chromium.chrome.browser.sync.SyncController;
import org.chromium.chrome.browser.tab.AuthenticatorNavigationInterceptor;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegateImpl;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelSelector;
import org.chromium.chrome.browser.tabmodel.document.StorageDelegate;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.content.app.ContentApplication;
import org.chromium.content.browser.BrowserStartupController;
import org.chromium.content.browser.ChildProcessLauncher;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.content.browser.DownloadController;
import org.chromium.policy.AppRestrictionsProvider;
import org.chromium.policy.CombinedPolicyProvider;
import org.chromium.policy.CombinedPolicyProvider.PolicyChangeListener;
import org.chromium.printing.PrintingController;
import org.chromium.sync.signin.AccountManagerDelegate;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.SystemAccountManagerDelegate;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.ActivityWindowAndroid;
import org.chromium.ui.base.ResourceBundle;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * Basic application functionality that should be shared among all browser applications that use
 * chrome layer.
 */
public class ChromeApplication extends ContentApplication {
    public static final String COMMAND_LINE_FILE = "chrome-command-line";

    private static final String TAG = "ChromiumApplication";
    private static final String PREF_BOOT_TIMESTAMP =
            "com.google.android.apps.chrome.ChromeMobileApplication.BOOT_TIMESTAMP";
    private static final long BOOT_TIMESTAMP_MARGIN_MS = 1000;
    private static final String PREF_LOCALE = "locale";
    private static final float FLOAT_EPSILON = 0.001f;
    private static final String PRIVATE_DATA_DIRECTORY_SUFFIX = "chrome";
    private static final String DEV_TOOLS_SERVER_SOCKET_PREFIX = "chrome";
    private static final String SESSIONS_UUID_PREF_KEY = "chromium.sync.sessions.id";

    private static DocumentTabModelSelector sDocumentTabModelSelector;

    /**
     * This class allows pausing scripts & network connections when we
     * go to the background and resume when we are back in foreground again.
     * TODO(pliard): Get rid of this class once JavaScript timers toggling is done directly on
     * the native side by subscribing to the system monitor events.
     */
    private static class BackgroundProcessing {
        private class SuspendRunnable implements Runnable {
            @Override
            public void run() {
                mSuspendRunnable = null;
                assert !mWebKitTimersAreSuspended;
                mWebKitTimersAreSuspended = true;
                ContentViewStatics.setWebKitSharedTimersSuspended(true);
            }
        }

        private static final int SUSPEND_TIMERS_AFTER_MS = 5 * 60 * 1000;
        private final Handler mHandler = new Handler();
        private boolean mWebKitTimersAreSuspended = false;
        private SuspendRunnable mSuspendRunnable;

        private void onDestroy() {
            if (mSuspendRunnable != null) {
                mHandler.removeCallbacks(mSuspendRunnable);
                mSuspendRunnable = null;
            }
        }

        private void suspendTimers() {
            if (mSuspendRunnable == null) {
                mSuspendRunnable = new SuspendRunnable();
                mHandler.postDelayed(mSuspendRunnable, SUSPEND_TIMERS_AFTER_MS);
            }
        }

        private void startTimers() {
            if (mSuspendRunnable != null) {
                mHandler.removeCallbacks(mSuspendRunnable);
                mSuspendRunnable = null;
            } else if (mWebKitTimersAreSuspended) {
                ContentViewStatics.setWebKitSharedTimersSuspended(false);
                mWebKitTimersAreSuspended = false;
            }
        }
    }

    private final BackgroundProcessing mBackgroundProcessing = new BackgroundProcessing();
    private final PowerBroadcastReceiver mPowerBroadcastReceiver = new PowerBroadcastReceiver();
    private final UpdateInfoBarHelper mUpdateInfoBarHelper = new UpdateInfoBarHelper();

    // Used to trigger variation changes (such as seed fetches) upon application foregrounding.
    private VariationsSession mVariationsSession;

    private DevToolsServer mDevToolsServer;

    private boolean mIsStarted;
    private boolean mInitializedSharedClasses;
    private boolean mIsProcessInitialized;

    private ChromeLifetimeController mChromeLifetimeController;
    private PrintingController mPrintingController;

    /**
     * This is called once per ChromeApplication instance, which get created per process
     * (browser OR renderer).  Don't stick anything in here that shouldn't be called multiple times
     * during Chrome's lifetime.
     */
    @Override
    public void onCreate() {
        UmaUtils.recordMainEntryPointTime();
        super.onCreate();
        UiUtils.setKeyboardShowingDelegate(new UiUtils.KeyboardShowingDelegate() {
            @Override
            public boolean disableKeyboardCheck(Context context, View view) {
                Activity activity = null;
                if (context instanceof Activity) {
                    activity = (Activity) context;
                } else if (view != null && view.getContext() instanceof Activity) {
                    activity = (Activity) view.getContext();
                }

                // For multiwindow mode we do not track keyboard visibility.
                return activity != null && MultiWindowUtils.getInstance().isMultiWindow(activity);
            }
        });

        // Initialize the AccountManagerHelper with the correct AccountManagerDelegate. Must be done
        // only once and before AccountMangerHelper.get(...) is called to avoid using the
        // default AccountManagerDelegate.
        AccountManagerHelper.initializeAccountManagerHelper(this, createAccountManagerDelegate());

        // Set the unique identification generator for invalidations.  The
        // invalidations system can start and attempt to fetch the client ID
        // very early.  We need this generator to be ready before that happens.
        UniqueIdInvalidationClientNameGenerator.doInitializeAndInstallGenerator(this);

        // Set minimum Tango log level. This sets an in-memory static field, and needs to be
        // set in the ApplicationContext instead of an activity, since Tango can be woken up
        // by the system directly though messages from GCM.
        AndroidLogger.setMinimumAndroidLogLevel(Log.WARN);

        // Set up the identification generator for sync. The ID is actually generated
        // in the SyncController constructor.
        UniqueIdentificationGeneratorFactory.registerGenerator(SyncController.GENERATOR_ID,
                new UuidBasedUniqueIdentificationGenerator(this, SESSIONS_UUID_PREF_KEY), false);
    }

    /**
     * Each top-level activity (ChromeTabbedActivity, FullscreenActivity) should call this during
     * its onStart phase. When called for the first time, this marks the beginning of a foreground
     * session and calls onForegroundSessionStart(). Subsequent calls are noops until
     * onForegroundSessionEnd() is called, to handle changing top-level Chrome activities in one
     * foreground session.
     */
    public void onStartWithNative() {
        if (mIsStarted) return;
        mIsStarted = true;

        assert mIsProcessInitialized;

        onForegroundSessionStart();
    }

    /**
     * Called when a top-level Chrome activity (ChromeTabbedActivity, FullscreenActivity) is
     * started in foreground. It will not be called again when other Chrome activities take over
     * (see onStart()), that is, when correct activity calls startActivity() for another Chrome
     * activity.
     */
    private void onForegroundSessionStart() {
        ChildProcessLauncher.onBroughtToForeground();
        mBackgroundProcessing.startTimers();
        updatePasswordEchoState();
        updateFontSize();
        updateAcceptLanguages();
        changeAppStatus(true);
        mVariationsSession.start(getApplicationContext());

        mPowerBroadcastReceiver.registerReceiver(this);
        mPowerBroadcastReceiver.runActions(this, true);

        // Track the ratio of Chrome startups that are caused by notification clicks.
        // TODO(johnme): Add other reasons (and switch to recordEnumeratedHistogram).
        RecordHistogram.recordBooleanHistogram(
                "Startup.BringToForegroundReason",
                NotificationUIManager.wasNotificationRecentlyClicked());
    }

    /**
     * Called when last of Chrome activities is stopped, ending the foreground session. This will
     * not be called when a Chrome activity is stopped because another Chrome activity takes over.
     * This is ensured by ActivityStatus, which switches to track new activity when its started and
     * will not report the old one being stopped (see createStateListener() below).
     */
    private void onForegroundSessionEnd() {
        if (!mIsStarted) return;
        mBackgroundProcessing.suspendTimers();
        flushPersistentData();
        mIsStarted = false;
        changeAppStatus(false);

        try {
            mPowerBroadcastReceiver.unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
            // This may happen when onStop get called very early in UI test.
        }

        ChildProcessLauncher.onSentToBackground();
        IntentHandler.clearPendingReferrer();

        if (FeatureUtilities.isDocumentMode(this)) {
            if (sDocumentTabModelSelector != null) {
                RecordHistogram.recordCountHistogram("Tab.TotalTabCount.BeforeLeavingApp",
                        sDocumentTabModelSelector.getTotalTabCount());
            }
        } else {
            int totalTabCount = 0;
            for (WeakReference<Activity> reference : ApplicationStatus.getRunningActivities()) {
                Activity activity = reference.get();
                if (activity instanceof ChromeActivity) {
                    TabModelSelector tabModelSelector =
                            ((ChromeActivity) activity).getTabModelSelector();
                    if (tabModelSelector != null) {
                        totalTabCount += tabModelSelector.getTotalTabCount();
                    }
                }
            }
            RecordHistogram.recordCountHistogram(
                    "Tab.TotalTabCount.BeforeLeavingApp", totalTabCount);
        }
    }

    /**
     * Called after onForegroundSessionEnd() indicating that the activity whose onStop() ended the
     * last foreground session was destroyed.
     */
    private void onForegroundActivityDestroyed() {
        if (ApplicationStatus.isEveryActivityDestroyed()) {
            mBackgroundProcessing.onDestroy();
            stopApplicationActivityTracker();
            PartnerBrowserCustomizations.destroy();
            ShareHelper.clearSharedImages(this);
            CombinedPolicyProvider.get().destroy();
        }
    }

    private ApplicationStateListener createApplicationStateListener() {
        return new ApplicationStateListener() {
            @Override
            public void onApplicationStateChange(int newState) {
                if (newState == ApplicationState.HAS_STOPPED_ACTIVITIES) {
                    onForegroundSessionEnd();
                } else if (newState == ApplicationState.HAS_DESTROYED_ACTIVITIES) {
                    onForegroundActivityDestroyed();
                }
            }
        };
    }

    /**
     * Returns a new instance of VariationsSession.
     */
    public VariationsSession createVariationsSession() {
        return new VariationsSession();
    }

    /**
     * Return a {@link AuthenticatorNavigationInterceptor} for the given {@link Tab}.
     * This can be null if there are no applicable interceptor to be built.
     */
    @SuppressWarnings("unused")
    public AuthenticatorNavigationInterceptor createAuthenticatorNavigationInterceptor(Tab tab) {
        return null;
    }

    /**
     * Starts the application activity tracker.
     */
    protected void startApplicationActivityTracker() {}

    /**
     * Stops the application activity tracker.
     */
    protected void stopApplicationActivityTracker() {}

    /**
     * Initiate AndroidEdu device check.
     * @param callback Callback that should receive the results of the AndroidEdu device check.
     */
    public void checkIsAndroidEduDevice(final AndroidEduOwnerCheckCallback callback) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                callback.onSchoolCheckDone(false);
            }
        });
    }

    @CalledByNative
    protected void showAutofillSettings() {
        PreferencesLauncher.launchSettingsPage(this,
                AutofillPreferences.class.getName());
    }

    @CalledByNative
    protected void showPasswordSettings() {
        PreferencesLauncher.launchSettingsPage(this,
                SavePasswordsPreferences.class.getName());
    }

    /**
     * Opens the single origin settings page for the given URL.
     *
     * @param url The URL to show the single origin settings for. This is a complete url
     *            including scheme, domain, port, path, etc.
     */
    protected void showSingleOriginSettings(String url) {
        Bundle fragmentArgs = SingleWebsitePreferences.createFragmentArgsForSite(url);
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                this, SingleWebsitePreferences.class.getName());
        intent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArgs);
        startActivity(intent);
    }

    @Override
    protected void initializeLibraryDependencies() {
        // The ResourceExtractor is only needed by the browser process, but this will have no
        // impact on the renderer process construction.
        ResourceBundle.initializeLocalePaks(this, R.array.locale_paks);
        if (!BuildInfo.hasLanguageApkSplits(this)) {
            ResourceExtractor.setResourcesToExtract(ResourceBundle.getActiveLocaleResources());
        }
        PathUtils.setPrivateDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX, this);
    }

    /**
     * The host activity should call this after the native library has loaded to ensure classes
     * shared by Activities in the same process are properly initialized.
     */
    public void initializeSharedClasses() {
        if (mInitializedSharedClasses) return;
        mInitializedSharedClasses = true;

        ForcedSigninProcessor.start(this);
        AccountsChangedReceiver.addObserver(new AccountsChangedReceiver.AccountsChangedObserver() {
            @Override
            public void onAccountsChanged(Context context, Intent intent) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ForcedSigninProcessor.start(ChromeApplication.this);
                    }
                });
            }
        });
        GoogleServicesManager.get(this).onMainActivityStart();
        RevenueStats.getInstance();

        getPKCS11AuthenticationManager().initialize(ChromeApplication.this);

        mDevToolsServer = new DevToolsServer(DEV_TOOLS_SERVER_SOCKET_PREFIX);
        mDevToolsServer.setRemoteDebuggingEnabled(
                true, DevToolsServer.Security.ALLOW_DEBUG_PERMISSION);

        startApplicationActivityTracker();

        DownloadController.setDownloadNotificationService(
                DownloadManagerService.getDownloadManagerService(this));

        if (ApiCompatibilityUtils.isPrintingSupported()) {
            mPrintingController = PrintingControllerFactory.create(getApplicationContext());
        }
    }

    /**
     * For extending classes to carry out tasks that initialize the browser process.
     * Should be called almost immediately after the native library has loaded to initialize things
     * that really, really have to be set up early.  Avoid putting any long tasks here.
     */
    public void initializeProcess() {
        if (mIsProcessInitialized) return;
        mIsProcessInitialized = true;
        assert !mIsStarted;

        DataReductionProxySettings.reconcileDataReductionProxyEnabledState(getApplicationContext());

        mVariationsSession = createVariationsSession();
        removeSessionCookies();
        ApplicationStatus.registerApplicationStateListener(createApplicationStateListener());
        AppBannerManager.setAppDetailsDelegate(createAppDetailsDelegate());
        mChromeLifetimeController = new ChromeLifetimeController();

        PrefServiceBridge.getInstance().migratePreferences(this);
    }

    @Override
    public void initCommandLine() {
        CommandLineInitUtil.initCommandLine(this, COMMAND_LINE_FILE);
    }

    /**
     * Start the browser process asynchronously. This will set up a queue of UI
     * thread tasks to initialize the browser process.
     *
     * Note that this can only be called on the UI thread.
     *
     * @param callback the callback to be called when browser startup is complete.
     * @throws ProcessInitException
     */
    public void startChromeBrowserProcessesAsync(BrowserStartupController.StartupCallback callback)
            throws ProcessInitException {
        assert ThreadUtils.runningOnUiThread() : "Tried to start the browser on the wrong thread";
        // The policies are used by browser startup, so we need to register the policy providers
        // before starting the browser process.
        registerPolicyProviders(CombinedPolicyProvider.get());
        Context applicationContext = getApplicationContext();
        BrowserStartupController.get(applicationContext, LibraryProcessType.PROCESS_BROWSER)
                .startBrowserProcessesAsync(callback);
    }

    /**
     * Loads native Libraries synchronously and starts Chrome browser processes.
     * Must be called on the main thread. Makes sure the process is initialized as a
     * Browser process instead of a ContentView process.
     *
     * @param initGoogleServicesManager when true the GoogleServicesManager is initialized.
     */
    public void startBrowserProcessesAndLoadLibrariesSync(boolean initGoogleServicesManager)
            throws ProcessInitException {
        ThreadUtils.assertOnUiThread();
        initCommandLine();
        Context context = getApplicationContext();
        LibraryLoader libraryLoader = LibraryLoader.get(LibraryProcessType.PROCESS_BROWSER);
        libraryLoader.ensureInitialized(context);
        libraryLoader.asyncPrefetchLibrariesToMemory();
        // The policies are used by browser startup, so we need to register the policy providers
        // before starting the browser process.
        registerPolicyProviders(CombinedPolicyProvider.get());
        BrowserStartupController.get(context, LibraryProcessType.PROCESS_BROWSER)
                .startBrowserProcessesSync(false);
        if (initGoogleServicesManager) {
            GoogleServicesManager.get(getApplicationContext());
        }
    }

    /**
     * Shows an error dialog following a startup error, and then exits the application.
     * @param e The exception reported by Chrome initialization.
     */
    public static void reportStartupErrorAndExit(final ProcessInitException e) {
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (ApplicationStatus.getStateForActivity(activity) == ActivityState.DESTROYED) {
            return;
        }
        InvalidStartupDialog.show(activity, e.getErrorCode());
    }

    /**
     * Returns an instance of LocationSettings to be installed as a singleton.
     */
    public LocationSettings createLocationSettings() {
        // Using an anonymous subclass as the constructor is protected.
        // This is done to deter instantiation of LocationSettings elsewhere without using the
        // getInstance() helper method.
        return new LocationSettings(this){};
    }

    /**
     * @return The Application's PowerBroadcastReceiver.
     */
    @VisibleForTesting
    public PowerBroadcastReceiver getPowerBroadcastReceiver() {
        return mPowerBroadcastReceiver;
    }

    /**
     * Opens the UI to clear browsing data.
     * @param tab The tab that triggered the request.
     */
    @CalledByNative
    protected void openClearBrowsingData(Tab tab) {
        Activity activity = tab.getWindowAndroid().getActivity().get();
        if (activity == null) {
            Log.e(TAG,
                    "Attempting to open clear browsing data for a tab without a valid activity");
            return;
        }
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(activity,
                PrivacyPreferences.class.getName());
        Bundle arguments = new Bundle();
        arguments.putBoolean(PrivacyPreferences.SHOW_CLEAR_BROWSING_DATA_EXTRA, true);
        intent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, arguments);
        activity.startActivity(intent);
    }

    /**
     * @return Whether parental controls are enabled.  Returning true will disable
     *         incognito mode.
     */
    @CalledByNative
    protected boolean areParentalControlsEnabled() {
        return PartnerBrowserCustomizations.isIncognitoDisabled();
    }

    // TODO(yfriedman): This is too widely available. Plumb this through ChromeNetworkDelegate
    // instead.
    protected PKCS11AuthenticationManager getPKCS11AuthenticationManager() {
        return EmptyPKCS11AuthenticationManager.getInstance();
    }

    /**
     * @return A provider of external estimates.
     * @param nativePtr Pointer to the native ExternalEstimateProviderAndroid object.
     */
    public ExternalEstimateProviderAndroid createExternalEstimateProviderAndroid(long nativePtr) {
        return new ExternalEstimateProviderAndroid();
    }

    /**
     * @return An external observer of data use.
     * @param nativePtr Pointer to the native ExternalDataUseObserver object.
     */
    public ExternalDataUseObserver createExternalDataUseObserver(long nativePtr) {
        return new ExternalDataUseObserver(nativePtr);
    }

    /**
     * @return The user agent string of Chrome.
     */
    public static String getBrowserUserAgent() {
        return nativeGetBrowserUserAgent();
    }

    /**
     * The host activity should call this during its onPause() handler to ensure
     * all state is saved when the app is suspended.  Calling ChromiumApplication.onStop() does
     * this for you.
     */
    public static void flushPersistentData() {
        try {
            TraceEvent.begin("ChromiumApplication.flushPersistentData");
            nativeFlushPersistentData();
        } finally {
            TraceEvent.end("ChromiumApplication.flushPersistentData");
        }
    }

    /**
     * Removes all session cookies (cookies with no expiration date) after device reboots.
     * This function will incorrectly clear cookies when Daylight Savings Time changes the clock.
     * Without a way to get a monotonically increasing system clock, the boot timestamp will be off
     * by one hour.  However, this should only happen at most once when the clock changes since the
     * updated timestamp is immediately saved.
     */
    protected void removeSessionCookies() {
        long lastKnownBootTimestamp =
                PreferenceManager.getDefaultSharedPreferences(this).getLong(PREF_BOOT_TIMESTAMP, 0);
        long bootTimestamp = System.currentTimeMillis() - SystemClock.uptimeMillis();
        long difference = bootTimestamp - lastKnownBootTimestamp;

        // Allow some leeway to account for fractions of milliseconds.
        if (Math.abs(difference) > BOOT_TIMESTAMP_MARGIN_MS) {
            nativeRemoveSessionCookies();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(PREF_BOOT_TIMESTAMP, bootTimestamp);
            editor.apply();
        }
    }

    protected void changeAppStatus(boolean inForeground) {
        nativeChangeAppStatus(inForeground);
    }

    private static native void nativeRemoveSessionCookies();
    private static native void nativeChangeAppStatus(boolean inForeground);
    private static native String nativeGetBrowserUserAgent();
    private static native void nativeFlushPersistentData();

    /**
     * @return An instance of {@link FeedbackReporter} to report feedback.
     */
    public FeedbackReporter createFeedbackReporter() {
        return new EmptyFeedbackReporter();
    }

    /**
     * @return An instance of ExternalAuthUtils to be installed as a singleton.
     */
    public ExternalAuthUtils createExternalAuthUtils() {
        return new ExternalAuthUtils();
    }

    /**
     * Returns a new instance of HelpAndFeedback.
     */
    public HelpAndFeedback createHelpAndFeedback() {
        return new HelpAndFeedback();
    }

    /**
     * @return A new ActivityWindowAndroid instance.
     */
    public ActivityWindowAndroid createActivityWindowAndroid(Activity activity) {
        if (activity instanceof ChromeActivity) return new ChromeWindow((ChromeActivity) activity);
        return new ActivityWindowAndroid(activity);
    }

    /**
     * @return An instance of {@link CustomTabsConnection}. Should not be called
     * outside of {@link CustomTabsConnection#getInstance()}.
     */
    public CustomTabsConnection createCustomTabsConnection() {
        return new CustomTabsConnection(this);
    }

    /**
     * @return A new PhysicalWebBleClient instance.
     */
    public PhysicalWebBleClient createPhysicalWebBleClient() {
        return new PhysicalWebBleClient();
    }

    /**
     * @return Instance of printing controller that is shared among all chromium activities. May
     *         return null if printing is not supported on the platform.
     */
    public PrintingController getPrintingController() {
        return mPrintingController;
    }

    /**
     * @return The UpdateInfoBarHelper used to inform the user about updates.
     */
    public UpdateInfoBarHelper getUpdateInfoBarHelper() {
        // TODO(aurimas): make UpdateInfoBarHelper have its own static instance.
        return mUpdateInfoBarHelper;
    }

    /**
     * @return An instance of {@link GSAHelper} that handles the start point of chrome's integration
     *         with GSA.
     */
    public GSAHelper createGsaHelper() {
        return new GSAHelper();
    }

   /**
     * Registers various policy providers with the policy manager.
     * Providers are registered in increasing order of precedence so overrides should call this
     * method in the end for this method to maintain the highest precedence.
     * @param combinedProvider The {@link CombinedPolicyProvider} to register the providers with.
     */
    protected void registerPolicyProviders(CombinedPolicyProvider combinedProvider) {
        combinedProvider.registerProvider(new AppRestrictionsProvider(getApplicationContext()));
    }

    /**
     * Add a listener to be notified upon policy changes.
     */
    public void addPolicyChangeListener(PolicyChangeListener listener) {
        CombinedPolicyProvider.get().addPolicyChangeListener(listener);
    }

    /**
     * Remove a listener to be notified upon policy changes.
     */
    public void removePolicyChangeListener(PolicyChangeListener listener) {
        CombinedPolicyProvider.get().removePolicyChangeListener(listener);
    }

    /**
     * @return An instance of PolicyAuditor that notifies the policy system of the user's activity.
     * Only applicable when the user has a policy active, that is tracking the activity.
     */
    public PolicyAuditor getPolicyAuditor() {
        // This class has a protected constructor to prevent accidental instantiation.
        return new PolicyAuditor() {};
    }

    /**
     * @return An instance of MultiWindowUtils to be installed as a singleton.
     */
    public MultiWindowUtils createMultiWindowUtils() {
        return new MultiWindowUtils();
    }

    /**
     * @return An instance of RequestGenerator to be used for Omaha XML creation.  Will be null if
     *         a generator is unavailable.
     */
    public RequestGenerator createOmahaRequestGenerator() {
        return null;
    }

    /**
     * @return An instance of AppDetailsDelegate that can be queried about app information for the
     *         App Banner feature.  Will be null if one is unavailable.
     */
    protected AppDetailsDelegate createAppDetailsDelegate() {
        return null;
    }

    /**
     * Returns the Singleton instance of the DocumentTabModelSelector.
     * TODO(dfalcantara): Find a better place for this once we differentiate between activity and
     *                    application-level TabModelSelectors.
     * @return The DocumentTabModelSelector for the application.
     */
    @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
    public static DocumentTabModelSelector getDocumentTabModelSelector() {
        ThreadUtils.assertOnUiThread();
        if (sDocumentTabModelSelector == null) {
            ActivityDelegateImpl activityDelegate = new ActivityDelegateImpl(
                    DocumentActivity.class, IncognitoDocumentActivity.class);
            sDocumentTabModelSelector = new DocumentTabModelSelector(activityDelegate,
                    new StorageDelegate(), new TabDelegate(false), new TabDelegate(true));
        }
        return sDocumentTabModelSelector;
    }

    /**
     * @return Whether or not the Singleton has been initialized.
     */
    @VisibleForTesting
    public static boolean isDocumentTabModelSelectorInitializedForTests() {
        return sDocumentTabModelSelector != null;
    }

    /**
     * @return An instance of RevenueStats to be installed as a singleton.
     */
    public RevenueStats createRevenueStatsInstance() {
        return new RevenueStats();
    }

    /**
     * Creates a new {@link AccountManagerDelegate}.
     * @return the created {@link AccountManagerDelegate}.
     */
    public AccountManagerDelegate createAccountManagerDelegate() {
        return new SystemAccountManagerDelegate(this);
    }

    /**
     * Update the font size after changing the Android accessibility system setting.  Doing so kills
     * the Activities but it doesn't kill the ChromeApplication, so this should be called in
     * {@link #onStart} instead of {@link #initialize}.
     */
    private void updateFontSize() {
        // This method is currently broken. http://crbug.com/439108
        // Skip it (with the consequence of not updating the text scaling factor when the user
        // changes system font size) rather than incurring the broken behavior.
        // TODO(newt): fix this.
        if (true) return;

        FontSizePrefs fontSizePrefs = FontSizePrefs.getInstance(getApplicationContext());

        // Set font scale factor as the product of the system and browser scale settings.
        float browserTextScale = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getFloat(AccessibilityPreferences.PREF_TEXT_SCALE, 1.0f);
        float fontScale = getResources().getConfiguration().fontScale * browserTextScale;

        float scaleDelta = Math.abs(fontScale - fontSizePrefs.getFontScaleFactor());
        if (scaleDelta >= FLOAT_EPSILON) {
            fontSizePrefs.setFontScaleFactor(fontScale);
        }

        // If force enable zoom has not been manually set, set it automatically based on
        // font scale factor.
        boolean shouldForceZoom =
                fontScale >= AccessibilityPreferences.FORCE_ENABLE_ZOOM_THRESHOLD_MULTIPLIER;
        if (!fontSizePrefs.getUserSetForceEnableZoom()
                && fontSizePrefs.getForceEnableZoom() != shouldForceZoom) {
            fontSizePrefs.setForceEnableZoom(shouldForceZoom);
        }
    }

    /**
     * Update the accept languages after changing Android locale setting. Doing so kills the
     * Activities but it doesn't kill the ChromeApplication, so this should be called in
     * {@link #onStart} instead of {@link #initialize}.
     */
    private void updateAcceptLanguages() {
        PrefServiceBridge instance = PrefServiceBridge.getInstance();
        String localeString = Locale.getDefault().toString();  // ex) en_US, de_DE, zh_CN_#Hans
        if (hasLocaleChanged(localeString)) {
            instance.resetAcceptLanguages(localeString);
            // Clear cache so that accept-languages change can be applied immediately.
            // TODO(changwan): The underlying BrowsingDataRemover::Remove() is an asynchronous call.
            // So cache-clearing may not be effective if URL rendering can happen before
            // OnBrowsingDataRemoverDone() is called, in which case we may have to reload as well.
            // Check if it can happen.
            instance.clearBrowsingData(null, false, true /* cache */, false, false, false);
        }
    }

    private boolean hasLocaleChanged(String newLocale) {
        String previousLocale = PreferenceManager.getDefaultSharedPreferences(this).getString(
                PREF_LOCALE, "");

        if (!previousLocale.equals(newLocale)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_LOCALE, newLocale);
            editor.apply();
            return true;
        }
        return false;
    }

    /**
     * Honor the Android system setting about showing the last character of a password for a short
     * period of time.
     */
    private void updatePasswordEchoState() {
        boolean systemEnabled = Settings.System.getInt(
                getApplicationContext().getContentResolver(),
                Settings.System.TEXT_SHOW_PASSWORD, 1) == 1;
        if (PrefServiceBridge.getInstance().getPasswordEchoEnabled() == systemEnabled) return;

        PrefServiceBridge.getInstance().setPasswordEchoEnabled(systemEnabled);
    }
}
