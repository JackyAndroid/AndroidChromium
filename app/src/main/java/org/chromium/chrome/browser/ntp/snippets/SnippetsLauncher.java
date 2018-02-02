// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.content.Context;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeBackgroundService;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;

/**
 * The {@link SnippetsLauncher} singleton is created and owned by the C++ browser.
 *
 * Thread model: This class is to be run on the UI thread only.
 */
public class SnippetsLauncher {
    private static final String TAG = "SnippetsLauncher";

    // Task tags for fetching snippets.
    public static final String TASK_TAG_WIFI = "FetchSnippetsWifi";
    public static final String TASK_TAG_FALLBACK = "FetchSnippetsFallback";
    // TODO(treib): Remove this after M55.
    private static final String OBSOLETE_TASK_TAG_WIFI_CHARGING = "FetchSnippetsWifiCharging";

    // TODO(treib): Remove this after M55.
    private static final String OBSOLETE_TASK_TAG_RESCHEDULE = "RescheduleSnippets";

    // The amount of "flex" to add around the fetching periods, as a ratio of the period.
    private static final double FLEX_FACTOR = 0.1;

    @VisibleForTesting
    public static final String PREF_IS_SCHEDULED = "ntp_snippets.is_scheduled";

    // The instance of SnippetsLauncher currently owned by a C++ SnippetsLauncherAndroid, if any.
    // If it is non-null then the browser is running.
    private static SnippetsLauncher sInstance;

    private GcmNetworkManager mScheduler;

    private boolean mGCMEnabled = true;

    /**
     * Create a SnippetsLauncher object, which is owned by C++.
     * @param context The app context.
     */
    @VisibleForTesting
    @CalledByNative
    public static SnippetsLauncher create(Context context) {
        if (sInstance != null) {
            throw new IllegalStateException("Already instantiated");
        }

        sInstance = new SnippetsLauncher(context);
        return sInstance;
    }

    /**
     * Called when the C++ counterpart is deleted.
     */
    @VisibleForTesting
    @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    @CalledByNative
    public void destroy() {
        assert sInstance == this;
        sInstance = null;
    }

    /**
     * Returns true if the native browser has started and created an instance of {@link
     * SnippetsLauncher}.
     */
    public static boolean hasInstance() {
        return sInstance != null;
    }

    protected SnippetsLauncher(Context context) {
        checkGCM(context);
        mScheduler = GcmNetworkManager.getInstance(context);
    }

    private boolean canUseGooglePlayServices(Context context) {
        return ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                context, new UserRecoverableErrorHandler.Silent());
    }

    private void checkGCM(Context context) {
        // Check to see if Play Services is up to date, and disable GCM if not.
        if (!canUseGooglePlayServices(context)) {
            mGCMEnabled = false;
            Log.i(TAG, "Disabling SnippetsLauncher because Play Services is not up to date.");
        }
    }

    private static PeriodicTask buildFetchTask(
            String tag, long periodSeconds, int requiredNetwork) {
        // Add a bit of "flex" around the target period. This achieves the following:
        // - It makes sure the task doesn't run (significantly) before its initial period has
        //   elapsed. In practice, the scheduler seems to behave like that anyway, but it doesn't
        //   guarantee that, so we shouldn't rely on it.
        // - It gives the scheduler a bit of room to optimize for battery life.
        long effectivePeriodSeconds = (long) (periodSeconds * (1.0 + FLEX_FACTOR));
        long flexSeconds = (long) (periodSeconds * (2.0 * FLEX_FACTOR));
        return new PeriodicTask.Builder()
                .setService(ChromeBackgroundService.class)
                .setTag(tag)
                .setPeriod(effectivePeriodSeconds)
                .setFlex(flexSeconds)
                .setRequiredNetwork(requiredNetwork)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .build();
    }

    private void scheduleOrCancelFetchTask(String taskTag, long period, int requiredNetwork) {
        if (period > 0) {
            mScheduler.schedule(buildFetchTask(taskTag, period, requiredNetwork));
        } else {
            mScheduler.cancelTask(taskTag, ChromeBackgroundService.class);
        }
    }

    @CalledByNative
    private boolean schedule(long periodWifiSeconds, long periodFallbackSeconds) {
        if (!mGCMEnabled) return false;
        Log.i(TAG, "Scheduling: " + periodWifiSeconds + " " + periodFallbackSeconds);

        boolean isScheduled = periodWifiSeconds != 0 || periodFallbackSeconds != 0;
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(PREF_IS_SCHEDULED, isScheduled)
                .apply();

        // Google Play Services may not be up to date, if the application was not installed through
        // the Play Store. In this case, scheduling the task will fail silently.
        try {
            mScheduler.cancelTask(OBSOLETE_TASK_TAG_WIFI_CHARGING, ChromeBackgroundService.class);
            scheduleOrCancelFetchTask(
                    TASK_TAG_WIFI, periodWifiSeconds, Task.NETWORK_STATE_UNMETERED);
            scheduleOrCancelFetchTask(
                    TASK_TAG_FALLBACK, periodFallbackSeconds, Task.NETWORK_STATE_CONNECTED);
            mScheduler.cancelTask(OBSOLETE_TASK_TAG_RESCHEDULE, ChromeBackgroundService.class);
        } catch (IllegalArgumentException e) {
            // Disable GCM for the remainder of this session.
            mGCMEnabled = false;

            ContextUtils.getAppSharedPreferences().edit().remove(PREF_IS_SCHEDULED).apply();
            // Return false so that the failure will be logged.
            return false;
        }
        return true;
    }

    @CalledByNative
    private boolean unschedule() {
        if (!mGCMEnabled) return false;
        Log.i(TAG, "Unscheduling");
        return schedule(0, 0);
    }

    public static boolean shouldRescheduleTasksOnUpgrade() {
        // Reschedule the periodic tasks if they were enabled previously.
        return ContextUtils.getAppSharedPreferences().getBoolean(PREF_IS_SCHEDULED, false);
    }
}

