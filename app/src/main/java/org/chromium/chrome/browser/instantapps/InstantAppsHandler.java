// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.instantapps;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.StrictMode;
import android.os.SystemClock;
import android.provider.Browser;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.FieldTrialList;
import org.chromium.base.Log;
import org.chromium.base.metrics.CachedMetrics.EnumeratedHistogramSample;
import org.chromium.base.metrics.CachedMetrics.TimesHistogramSample;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.content_public.browser.WebContents;

import java.util.concurrent.TimeUnit;

/** A launcher for Instant Apps. */
public class InstantAppsHandler {
    private static final String TAG = "InstantAppsHandler";

    private static final Object INSTANCE_LOCK = new Object();
    private static InstantAppsHandler sInstance;

    private static final String CUSTOM_APPS_INSTANT_APP_EXTRA =
            "android.support.customtabs.extra.EXTRA_ENABLE_INSTANT_APPS";

    private static final String INSTANT_APP_START_TIME_EXTRA =
            "org.chromium.chrome.INSTANT_APP_START_TIME";

    // TODO(mariakhomenko): Depend directly on the constants once we roll to v8 libraries.
    private static final String DO_NOT_LAUNCH_EXTRA =
            "com.google.android.gms.instantapps.DO_NOT_LAUNCH_INSTANT_APP";

    protected static final String IS_REFERRER_TRUSTED_EXTRA =
            "com.google.android.gms.instantapps.IS_REFERRER_TRUSTED";

    protected static final String IS_USER_CONFIRMED_LAUNCH_EXTRA =
            "com.google.android.gms.instantapps.IS_USER_CONFIRMED_LAUNCH";

    protected static final String TRUSTED_REFERRER_PKG_EXTRA =
            "com.google.android.gms.instantapps.TRUSTED_REFERRER_PKG";

    public static final String IS_GOOGLE_SEARCH_REFERRER =
            "com.google.android.gms.instantapps.IS_GOOGLE_SEARCH_REFERRER";

    private static final String BROWSER_LAUNCH_REASON =
            "com.google.android.gms.instantapps.BROWSER_LAUNCH_REASON";

    /** Finch experiment name. */
    private static final String INSTANT_APPS_EXPERIMENT_NAME = "InstantApps";

    /** Finch experiment group which is enabled for instant apps. */
    private static final String INSTANT_APPS_ENABLED_ARM = "InstantAppsEnabled";

    /** Finch experiment group which is disabled for instant apps. */
    private static final String INSTANT_APPS_DISABLED_ARM = "InstantAppsDisabled";

    /** A histogram to record how long each handleIntent() call took. */
    private static final TimesHistogramSample sHandleIntentDuration = new TimesHistogramSample(
            "Android.InstantApps.HandleIntentDuration", TimeUnit.MILLISECONDS);

    /** A histogram to record how long the fallback intent roundtrip was. */
    private static final TimesHistogramSample sFallbackIntentTimes = new TimesHistogramSample(
            "Android.InstantApps.FallbackDuration", TimeUnit.MILLISECONDS);

    /** A histogram to record how long the GMS Core API call took. */
    private static final TimesHistogramSample sInstantAppsApiCallTimes = new TimesHistogramSample(
            "Android.InstantApps.ApiCallDuration2", TimeUnit.MILLISECONDS);

    // Only two possible call sources for fallback intents, set boundary at n+1.
    private static final int SOURCE_BOUNDARY = 3;

    private static final EnumeratedHistogramSample sFallbackCallSource =
            new EnumeratedHistogramSample("Android.InstantApps.CallSource", SOURCE_BOUNDARY);

    /**
     * A histogram to record how long the GMS Core API call took when the instant app was found.
     */
    private static final TimesHistogramSample sInstantAppsApiCallTimesHasApp =
            new TimesHistogramSample("Android.InstantApps.ApiCallDurationWithApp",
                    TimeUnit.MILLISECONDS);

    /**
     * A histogram to record how long the GMS Core API call took when the instant app was not found.
     */
    private static final TimesHistogramSample sInstantAppsApiCallTimesNoApp =
            new TimesHistogramSample("Android.InstantApps.ApiCallDurationWithoutApp",
                    TimeUnit.MILLISECONDS);

    /** @return The singleton instance of {@link InstantAppsHandler}. */
    public static InstantAppsHandler getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                Context appContext = ContextUtils.getApplicationContext();
                sInstance = ((ChromeApplication) appContext).createInstantAppsHandler();
            }
        }
        return sInstance;
    }

    /**
     * Check the cached value to figure out if the feature is enabled. We have to use the cached
     * value because native library hasn't yet been loaded.
     * @param context The application context.
     * @return Whether the feature is enabled.
     */
    protected boolean isEnabled(Context context) {
        // Will go away once the feature is enabled for everyone by default.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return ChromePreferenceManager.getInstance(context).getCachedInstantAppsEnabled();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Record how long the handleIntent() method took.
     * @param startTime The timestamp for handleIntent start time.
     */
    private void recordHandleIntentDuration(long startTime) {
        sHandleIntentDuration.record(SystemClock.elapsedRealtime() - startTime);
    }

    /**
     * Record the amount of time spent in the instant apps API call.
     * @param startTime The time at which we started doing computations.
     */
    protected void recordInstantAppsApiCallTime(long startTime) {
        sInstantAppsApiCallTimes.record(SystemClock.elapsedRealtime() - startTime);
    }

    /**
     * Record the amount of time spent in the Instant Apps API call.
     * @param startTime The time at which we started doing computations.
     * @param hasApp Whether the API has found an Instant App during the call.
     */
    protected void recordInstantAppsApiCallTime(long startTime, boolean hasApp) {
        if (hasApp) {
            sInstantAppsApiCallTimesHasApp.record(SystemClock.elapsedRealtime() - startTime);
        } else {
            sInstantAppsApiCallTimesNoApp.record(SystemClock.elapsedRealtime() - startTime);
        }
    }

    /**
     * In the case where Chrome is called through the fallback mechanism from Instant Apps,
     * record the amount of time the whole trip took and which UI took the user back to Chrome,
     * if any.
     * @param intent The current intent.
     */
    private void maybeRecordFallbackStats(Intent intent) {
        Long startTime = IntentUtils.safeGetLongExtra(intent, INSTANT_APP_START_TIME_EXTRA, 0);
        if (startTime > 0) {
            sFallbackIntentTimes.record(SystemClock.elapsedRealtime() - startTime);
            intent.removeExtra(INSTANT_APP_START_TIME_EXTRA);
        }
        int callSource = IntentUtils.safeGetIntExtra(intent, BROWSER_LAUNCH_REASON, 0);
        if (callSource > 0 && callSource < SOURCE_BOUNDARY) {
            sFallbackCallSource.record(callSource);
            intent.removeExtra(BROWSER_LAUNCH_REASON);
        } else if (callSource >= SOURCE_BOUNDARY) {
            Log.e(TAG, "Unexpected call source constant for Instant Apps: " + callSource);
        }
    }

    /**
     * Cache whether the Instant Apps feature is enabled.
     * This should only be called with the native library loaded.
     */
    public void cacheInstantAppsEnabled() {
        Context context = ContextUtils.getApplicationContext();
        boolean isEnabled = false;
        boolean wasEnabled = isEnabled(context);
        CommandLine instance = CommandLine.getInstance();
        if (instance.hasSwitch(ChromeSwitches.DISABLE_APP_LINK)) {
            isEnabled = false;
        } else if (instance.hasSwitch(ChromeSwitches.ENABLE_APP_LINK)) {
            isEnabled = true;
        } else {
            String experiment = FieldTrialList.findFullName(INSTANT_APPS_EXPERIMENT_NAME);
            if (INSTANT_APPS_DISABLED_ARM.equals(experiment)) {
                isEnabled = false;
            } else if (INSTANT_APPS_ENABLED_ARM.equals(experiment)) {
                isEnabled = true;
            }
        }

        if (isEnabled != wasEnabled) {
            ChromePreferenceManager.getInstance(context).setCachedInstantAppsEnabled(isEnabled);
        }
    }

    /** Handle incoming intent. */
    public boolean handleIncomingIntent(Context context, Intent intent,
            boolean isCustomTabsIntent) {
        long startTimeStamp = SystemClock.elapsedRealtime();
        boolean result = handleIncomingIntentInternal(context, intent, isCustomTabsIntent,
                startTimeStamp);
        recordHandleIntentDuration(startTimeStamp);
        return result;
    }

    private boolean handleIncomingIntentInternal(
            Context context, Intent intent, boolean isCustomTabsIntent, long startTime) {
        boolean isEnabled = isEnabled(context);
        if (!isEnabled || (isCustomTabsIntent && !IntentUtils.safeGetBooleanExtra(
                intent, CUSTOM_APPS_INSTANT_APP_EXTRA, false))) {
            Log.i(TAG, "Not handling with Instant Apps. Enabled? " + isEnabled);
            return false;
        }

        if (IntentUtils.safeGetBooleanExtra(intent, DO_NOT_LAUNCH_EXTRA, false)) {
            maybeRecordFallbackStats(intent);
            Log.i(TAG, "Not handling with Instant Apps (DO_NOT_LAUNCH_EXTRA)");
            return false;
        }

        if (IntentUtils.safeGetBooleanExtra(
                intent, IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, false)
                || IntentUtils.safeHasExtra(intent, ShortcutHelper.EXTRA_SOURCE)
                || isIntentFromChrome(context, intent)
                || (IntentHandler.getUrlFromIntent(intent) == null)) {
            Log.i(TAG, "Not handling with Instant Apps (other)");
            return false;
        }

        // Used to search for the intent handlers. Needs null component to return correct results.
        Intent intentCopy = new Intent(intent);
        intentCopy.setComponent(null);
        Intent selector = intentCopy.getSelector();
        if (selector != null) selector.setComponent(null);

        if (!(isCustomTabsIntent || isChromeDefaultHandler(context))
                || ExternalNavigationDelegateImpl.isPackageSpecializedHandler(
                        context, null, intentCopy)) {
            // Chrome is not the default browser or a specialized handler exists.
            Log.i(TAG, "Not handling with Instant Apps because Chrome is not default or "
                    + "there's a specialized handler");
            return false;
        }

        Intent callbackIntent = new Intent(intent);
        callbackIntent.putExtra(DO_NOT_LAUNCH_EXTRA, true);
        callbackIntent.putExtra(INSTANT_APP_START_TIME_EXTRA, startTime);

        return tryLaunchingInstantApp(context, intent, isCustomTabsIntent, callbackIntent);
    }

    /**
     * Attempts to launch an Instant App, if possible.
     * @param context The activity context.
     * @param intent The incoming intent.
     * @param isCustomTabsIntent Whether the intent is for a CustomTab.
     * @param fallbackIntent The intent that will be launched by Instant Apps in case of failure to
     *        load.
     * @return Whether an Instant App was launched.
     */
    protected boolean tryLaunchingInstantApp(
            Context context, Intent intent, boolean isCustomTabsIntent, Intent fallbackIntent) {
        return false;
    }

    /**
     * Evaluate a navigation for whether it should launch an Instant App or show the Instant
     * App banner.
     * @return Whether an Instant App intent was started.
     */
    public boolean handleNavigation(Context context, String url, Uri referrer,
            WebContents webContents) {
        if (!isEnabled(context)) return false;

        if (InstantAppsSettings.isInstantAppDefault(webContents, url)) {
            return launchInstantAppForNavigation(context, url, referrer);
        }
        return startCheckForInstantApps(context, url, referrer, webContents);
    }

    /**
     * Checks if an Instant App banner should be shown for the page we are loading.
     */
    protected boolean startCheckForInstantApps(Context context, String url, Uri referrer,
            WebContents webContents) {
        return false;
    }

    /**
     * Launches an Instant App immediately, if possible.
     */
    protected boolean launchInstantAppForNavigation(Context context, String url, Uri referrer) {
        return false;
    }

    /**
     * @return Whether the intent was fired from Chrome. This happens when the user gets a
     *         disambiguation dialog and chooses to stay in Chrome.
     */
    private boolean isIntentFromChrome(Context context, Intent intent) {
        return context.getPackageName().equals(IntentUtils.safeGetStringExtra(
                intent, Browser.EXTRA_APPLICATION_ID))
                // We shouldn't leak internal intents with authentication tokens
                || IntentHandler.wasIntentSenderChrome(intent, context);
    }

    /** @return Whether Chrome is the default browser on the device. */
    private boolean isChromeDefaultHandler(Context context) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return ChromePreferenceManager.getInstance(context).getCachedChromeDefaultBrowser();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Launches the Instant App from the infobar banner.
     */
    public void launchFromBanner(InstantAppsBannerData data) {
        if (data.getIntent() == null) return;

        Intent iaIntent = data.getIntent();
        if (data.getReferrer() != null) {
            iaIntent.putExtra(Intent.EXTRA_REFERRER, data.getReferrer());
            iaIntent.putExtra(IS_REFERRER_TRUSTED_EXTRA, true);
        }

        Context appContext = ContextUtils.getApplicationContext();
        iaIntent.putExtra(TRUSTED_REFERRER_PKG_EXTRA, appContext.getPackageName());
        iaIntent.putExtra(IS_USER_CONFIRMED_LAUNCH_EXTRA, true);

        try {
            appContext.startActivity(iaIntent);
            InstantAppsSettings.setInstantAppDefault(data.getWebContents(), data.getUrl());
        } catch (Exception e) {
            Log.e(TAG, "Could not launch instant app intent", e);
        }
    }

    /**
     * Gets the instant app intent for the given URL if one exists.
     *
     * @param url The URL whose instant app this is associated with.
     * @return An instant app intent for the URL if one exists.
     */
    public Intent getInstantAppIntentForUrl(String url) {
        return null;
    }
}
