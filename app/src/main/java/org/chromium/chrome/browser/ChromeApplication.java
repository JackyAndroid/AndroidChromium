// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLineInitUtil;
import org.chromium.base.ContextUtils;
import org.chromium.base.ResourceExtractor;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.banners.AppDetailsDelegate;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.datausage.ExternalDataUseObserver;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.document.IncognitoDocumentActivity;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.feedback.EmptyFeedbackReporter;
import org.chromium.chrome.browser.feedback.FeedbackReporter;
import org.chromium.chrome.browser.gsa.GSAHelper;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.init.InvalidStartupDialog;
import org.chromium.chrome.browser.instantapps.InstantAppsHandler;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.metrics.VariationsSession;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.net.qualityprovider.ExternalEstimateProviderAndroid;
import org.chromium.chrome.browser.omaha.RequestGenerator;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.physicalweb.PhysicalWebBleClient;
import org.chromium.chrome.browser.physicalweb.PhysicalWebEnvironment;
import org.chromium.chrome.browser.policy.PolicyAuditor;
import org.chromium.chrome.browser.preferences.LocationSettings;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.autofill.AutofillPreferences;
import org.chromium.chrome.browser.preferences.password.SavePasswordsPreferences;
import org.chromium.chrome.browser.preferences.privacy.ClearBrowsingDataPreferences;
import org.chromium.chrome.browser.rlz.RevenueStats;
import org.chromium.chrome.browser.services.AndroidEduOwnerCheckCallback;
import org.chromium.chrome.browser.signin.GoogleActivityController;
import org.chromium.chrome.browser.sync.GmsCoreSyncListener;
import org.chromium.chrome.browser.tab.AuthenticatorNavigationInterceptor;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegateImpl;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelSelector;
import org.chromium.chrome.browser.tabmodel.document.StorageDelegate;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.components.signin.AccountManagerDelegate;
import org.chromium.components.signin.SystemAccountManagerDelegate;
import org.chromium.content.app.ContentApplication;
import org.chromium.content.browser.ChildProcessCreationParams;
import org.chromium.policy.AppRestrictionsProvider;
import org.chromium.policy.CombinedPolicyProvider;
import org.chromium.ui.base.ResourceBundle;

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

    private static DocumentTabModelSelector sDocumentTabModelSelector;

    public ChromeApplication() {
        super();
        ContextUtils.initApplicationContext(this);
    }

    /**
     * This is called during early initialization in order to set up ChildProcessLauncher
     * for certain Chrome packaging configurations
     */
    public ChildProcessCreationParams getChildProcessCreationParams() {
        return null;
    }

    /**
     * This is called once per ChromeApplication instance, which get created per process
     * (browser OR renderer).  Don't stick anything in here that shouldn't be called multiple times
     * during Chrome's lifetime.
     */
    @Override
    public void onCreate() {
        UmaUtils.recordMainEntryPointTime();
        initCommandLine();
        TraceEvent.maybeEnableEarlyTracing();
        TraceEvent.begin("ChromeApplication.onCreate");

        super.onCreate();

        TraceEvent.end("ChromeApplication.onCreate");
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

    @Override
    protected void initializeLibraryDependencies() {
        // The ResourceExtractor is only needed by the browser process, but this will have no
        // impact on the renderer process construction.
        ResourceBundle.initializeLocalePaks(this, R.array.locale_paks);
        ResourceExtractor.setResourcesToExtract(ResourceBundle.getActiveLocaleResources());
    }

    @Override
    public void initCommandLine() {
        CommandLineInitUtil.initCommandLine(this, COMMAND_LINE_FILE);
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
        return new LocationSettings(){};
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
                ClearBrowsingDataPreferences.class.getName());
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

    /**
     * @return A provider of external estimates.
     * @param nativePtr Pointer to the native ExternalEstimateProviderAndroid object.
     */
    public ExternalEstimateProviderAndroid createExternalEstimateProviderAndroid(long nativePtr) {
        return new ExternalEstimateProviderAndroid(nativePtr) {};
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
    public static void removeSessionCookies() {
        long lastKnownBootTimestamp =
                ContextUtils.getAppSharedPreferences().getLong(PREF_BOOT_TIMESTAMP, 0);
        long bootTimestamp = System.currentTimeMillis() - SystemClock.uptimeMillis();
        long difference = bootTimestamp - lastKnownBootTimestamp;

        // Allow some leeway to account for fractions of milliseconds.
        if (Math.abs(difference) > BOOT_TIMESTAMP_MARGIN_MS) {
            nativeRemoveSessionCookies();

            SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(PREF_BOOT_TIMESTAMP, bootTimestamp);
            editor.apply();
        }
    }

    private static native void nativeRemoveSessionCookies();
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
     * @return An instance of {@link CustomTabsConnection}. Should not be called
     * outside of {@link CustomTabsConnection#getInstance()}.
     */
    public CustomTabsConnection createCustomTabsConnection() {
        return new CustomTabsConnection(this);
    }

    /**
     * @return A new {@link PhysicalWebBleClient} instance.
     */
    public PhysicalWebBleClient createPhysicalWebBleClient() {
        return new PhysicalWebBleClient();
    }

    /**
     * @return A new {@link PhysicalWebEnvironment} instance.
     */
    public PhysicalWebEnvironment createPhysicalWebEnvironment() {
        return new PhysicalWebEnvironment();
    }

    public InstantAppsHandler createInstantAppsHandler() {
        return new InstantAppsHandler();
    }

    /**
     * @return An instance of {@link GSAHelper} that handles the start point of chrome's integration
     *         with GSA.
     */
    public GSAHelper createGsaHelper() {
        return new GSAHelper();
    }

    /**
     * @return An instance of {@link LocaleManager} that handles customized locale related logic.
     */
    public LocaleManager createLocaleManager() {
        return new LocaleManager();
    }

   /**
     * Registers various policy providers with the policy manager.
     * Providers are registered in increasing order of precedence so overrides should call this
     * method in the end for this method to maintain the highest precedence.
     * @param combinedProvider The {@link CombinedPolicyProvider} to register the providers with.
     */
    public void registerPolicyProviders(CombinedPolicyProvider combinedProvider) {
        combinedProvider.registerProvider(new AppRestrictionsProvider(getApplicationContext()));
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
     * @return An instance of GmsCoreSyncListener to notify GmsCore of sync encryption key changes.
     *         Will be null if one is unavailable.
     */
    public GmsCoreSyncListener createGmsCoreSyncListener() {
        return null;
    }

    /**
    * @return An instance of GoogleActivityController.
    */
    public GoogleActivityController createGoogleActivityController() {
        return new GoogleActivityController();
    }

    /**
     * @return An instance of AppDetailsDelegate that can be queried about app information for the
     *         App Banner feature.  Will be null if one is unavailable.
     */
    public AppDetailsDelegate createAppDetailsDelegate() {
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
}
