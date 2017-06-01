// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ApplicationStateListener;
import org.chromium.base.ContextUtils;
import org.chromium.base.LocaleUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.accessibility.FontSizePrefs;
import org.chromium.chrome.browser.browsing_data.BrowsingDataType;
import org.chromium.chrome.browser.browsing_data.TimePeriod;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.metrics.VariationsSession;
import org.chromium.chrome.browser.notifications.NotificationPlatformBridge;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.content.browser.ChildProcessLauncher;

import java.lang.ref.WeakReference;

/**
 * Tracks the foreground session state for the Chrome activities.
 */
public class ChromeActivitySessionTracker {

    private static final String PREF_LOCALE = "locale";

    private static ChromeActivitySessionTracker sInstance;

    private final PowerBroadcastReceiver mPowerBroadcastReceiver = new PowerBroadcastReceiver();

    // Used to trigger variation changes (such as seed fetches) upon application foregrounding.
    private VariationsSession mVariationsSession;

    private ChromeApplication mApplication;
    private boolean mIsInitialized;
    private boolean mIsStarted;
    private boolean mIsFinishedCachingNativeFlags;

    /**
     * @return The activity session tracker for Chrome.
     */
    public static ChromeActivitySessionTracker getInstance() {
        ThreadUtils.assertOnUiThread();

        if (sInstance == null) sInstance = new ChromeActivitySessionTracker();
        return sInstance;
    }

    /**
     * Constructor exposed for extensibility only.
     * @see #getInstance()
     */
    protected ChromeActivitySessionTracker() {
        mApplication = (ChromeApplication) ContextUtils.getApplicationContext();
    }

    /**
     * Handle any initialization that occurs once native has been loaded.
     */
    public void initializeWithNative() {
        ThreadUtils.assertOnUiThread();

        if (mIsInitialized) return;
        mIsInitialized = true;
        assert !mIsStarted;

        ApplicationStatus.registerApplicationStateListener(createApplicationStateListener());
        mVariationsSession = mApplication.createVariationsSession();
    }

    /**
     * Each top-level activity (those extending {@link ChromeActivity}) should call this during
     * its onStart phase. When called for the first time, this marks the beginning of a foreground
     * session and calls onForegroundSessionStart(). Subsequent calls are noops until
     * onForegroundSessionEnd() is called, to handle changing top-level Chrome activities in one
     * foreground session.
     */
    public void onStartWithNative() {
        ThreadUtils.assertOnUiThread();

        if (mIsStarted) return;
        mIsStarted = true;

        assert mIsInitialized;

        onForegroundSessionStart();
        cacheNativeFlags();
    }

    /**
     * Called when a top-level Chrome activity (ChromeTabbedActivity, FullscreenActivity) is
     * started in foreground. It will not be called again when other Chrome activities take over
     * (see onStart()), that is, when correct activity calls startActivity() for another Chrome
     * activity.
     */
    private void onForegroundSessionStart() {
        UmaUtils.recordForegroundStartTime();
        ChildProcessLauncher.onBroughtToForeground();
        updatePasswordEchoState();
        FontSizePrefs.getInstance(mApplication).onSystemFontScaleChanged();
        updateAcceptLanguages();
        mVariationsSession.start(mApplication);
        mPowerBroadcastReceiver.onForegroundSessionStart();

        // Track the ratio of Chrome startups that are caused by notification clicks.
        // TODO(johnme): Add other reasons (and switch to recordEnumeratedHistogram).
        RecordHistogram.recordBooleanHistogram(
                "Startup.BringToForegroundReason",
                NotificationPlatformBridge.wasNotificationRecentlyClicked());
    }

    /**
     * Called when last of Chrome activities is stopped, ending the foreground session. This will
     * not be called when a Chrome activity is stopped because another Chrome activity takes over.
     * This is ensured by ActivityStatus, which switches to track new activity when its started and
     * will not report the old one being stopped (see createStateListener() below).
     */
    private void onForegroundSessionEnd() {
        if (!mIsStarted) return;
        ChromeApplication.flushPersistentData();
        mIsStarted = false;
        mPowerBroadcastReceiver.onForegroundSessionEnd();

        ChildProcessLauncher.onSentToBackground();
        IntentHandler.clearPendingReferrer();
        IntentHandler.clearPendingIncognitoUrl();

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

    private void onForegroundActivityDestroyed() {
        if (ApplicationStatus.isEveryActivityDestroyed()) {
            // These will all be re-initialized when a new Activity starts / upon next use.
            PartnerBrowserCustomizations.destroy();
            ShareHelper.clearSharedImages();
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
     * Update the accept languages after changing Android locale setting. Doing so kills the
     * Activities but it doesn't kill the Application, so this should be called in
     * {@link #onStart} instead of {@link #initialize}.
     */
    private void updateAcceptLanguages() {
        PrefServiceBridge instance = PrefServiceBridge.getInstance();
        String localeString = LocaleUtils.getDefaultLocaleListString();
        if (hasLocaleChanged(localeString)) {
            instance.resetAcceptLanguages(localeString);
            // Clear cache so that accept-languages change can be applied immediately.
            // TODO(changwan): The underlying BrowsingDataRemover::Remove() is an asynchronous call.
            // So cache-clearing may not be effective if URL rendering can happen before
            // OnBrowsingDataRemoverDone() is called, in which case we may have to reload as well.
            // Check if it can happen.
            instance.clearBrowsingData(
                    null, new int[]{ BrowsingDataType.CACHE }, TimePeriod.ALL_TIME);
        }
    }

    private boolean hasLocaleChanged(String newLocale) {
        String previousLocale = ContextUtils.getAppSharedPreferences().getString(
                PREF_LOCALE, "");

        if (!previousLocale.equals(newLocale)) {
            SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
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
        boolean systemEnabled = Settings.System.getInt(mApplication.getContentResolver(),
                Settings.System.TEXT_SHOW_PASSWORD, 1) == 1;
        if (PrefServiceBridge.getInstance().getPasswordEchoEnabled() == systemEnabled) return;

        PrefServiceBridge.getInstance().setPasswordEchoEnabled(systemEnabled);
    }

    /**
     * Caches flags that are needed by Activities that launch before the native library is loaded
     * and stores them in SharedPreferences. Because this function is called during launch after the
     * library has loaded, they won't affect the next launch until Chrome is restarted.
     */
    private void cacheNativeFlags() {
        if (mIsFinishedCachingNativeFlags) return;
        FeatureUtilities.cacheNativeFlags();
        mIsFinishedCachingNativeFlags = true;
    }

    /**
     * @return The PowerBroadcastReceiver for the browser process.
     */
    @VisibleForTesting
    public PowerBroadcastReceiver getPowerBroadcastReceiverForTesting() {
        return mPowerBroadcastReceiver;
    }
}
