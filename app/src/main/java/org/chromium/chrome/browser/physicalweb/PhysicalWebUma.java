// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.components.location.LocationUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Centralizes UMA data collection for the Physical Web feature.
 */
@ThreadSafe
public class PhysicalWebUma {
    private static final String TAG = "PhysicalWeb";
    private static final String HAS_DEFERRED_METRICS_KEY = "PhysicalWeb.HasDeferredMetrics";
    private static final String OPT_IN_DECLINE_BUTTON_PRESS_COUNT =
            "PhysicalWeb.OptIn.DeclineButtonPressed";
    private static final String OPT_IN_ENABLE_BUTTON_PRESS_COUNT =
            "PhysicalWeb.OptIn.EnableButtonPressed";
    private static final String OPT_IN_HIGH_PRIORITY_NOTIFICATION_COUNT =
            "PhysicalWeb.OptIn.HighPriorityNotificationShown";
    private static final String OPT_IN_MIN_PRIORITY_NOTIFICATION_COUNT =
            "PhysicalWeb.OptIn.MinPriorityNotificationShown";
    private static final String OPT_IN_NOTIFICATION_PRESS_COUNT =
            "PhysicalWeb.OptIn.NotificationPressed";
    private static final String PREFS_FEATURE_DISABLED_COUNT = "PhysicalWeb.Prefs.FeatureDisabled";
    private static final String PREFS_FEATURE_ENABLED_COUNT = "PhysicalWeb.Prefs.FeatureEnabled";
    private static final String PREFS_LOCATION_DENIED_COUNT = "PhysicalWeb.Prefs.LocationDenied";
    private static final String PREFS_LOCATION_GRANTED_COUNT = "PhysicalWeb.Prefs.LocationGranted";
    private static final String PWS_BACKGROUND_RESOLVE_TIMES = "PhysicalWeb.ResolveTime.Background";
    private static final String PWS_FOREGROUND_RESOLVE_TIMES = "PhysicalWeb.ResolveTime.Foreground";
    private static final String PWS_REFRESH_RESOLVE_TIMES = "PhysicalWeb.ResolveTime.Refresh";
    private static final String OPT_IN_NOTIFICATION_PRESS_DELAYS =
            "PhysicalWeb.ReferralDelay.OptInNotification";
    private static final String STANDARD_NOTIFICATION_PRESS_DELAYS =
            "PhysicalWeb.ReferralDelay.StandardNotification";
    private static final String URL_SELECTED_COUNT = "PhysicalWeb.UrlSelected";
    private static final String TOTAL_URLS_INITIAL_COUNTS =
            "PhysicalWeb.TotalUrls.OnInitialDisplay";
    private static final String TOTAL_URLS_REFRESH_COUNTS =
            "PhysicalWeb.TotalUrls.OnRefresh";
    private static final String ACTIVITY_REFERRALS = "PhysicalWeb.ActivityReferral";
    private static final String PHYSICAL_WEB_STATE = "PhysicalWeb.State";
    private static final String LAUNCH_FROM_PREFERENCES = "LaunchFromPreferences";
    private static final String LAUNCH_FROM_DIAGNOSTICS = "LaunchFromDiagnostics";
    private static final String BLUETOOTH = "Bluetooth";
    private static final String DATA_CONNECTION = "DataConnection";
    private static final String LOCATION_PERMISSION = "LocationPermission";
    private static final String LOCATION_SERVICES = "LocationServices";
    private static final String PREFERENCE = "Preference";
    private static final int BOOLEAN_BOUNDARY = 2;
    private static final int TRISTATE_BOUNDARY = 3;

    /**
     * Records a URL selection.
     */
    public static void onUrlSelected(Context context) {
        handleAction(context, URL_SELECTED_COUNT);
    }

    /**
     * Records a tap on the opt-in decline button.
     */
    public static void onOptInDeclineButtonPressed(Context context) {
        handleAction(context, OPT_IN_DECLINE_BUTTON_PRESS_COUNT);
    }

    /**
     * Records a tap on the opt-in enable button.
     */
    public static void onOptInEnableButtonPressed(Context context) {
        handleAction(context, OPT_IN_ENABLE_BUTTON_PRESS_COUNT);
    }

    /**
     * Records a display of a high priority opt-in notification.
     */
    public static void onOptInHighPriorityNotificationShown(Context context) {
        handleAction(context, OPT_IN_HIGH_PRIORITY_NOTIFICATION_COUNT);
    }

    /**
     * Records a display of a min priority opt-in notification.
     */
    public static void onOptInMinPriorityNotificationShown(Context context) {
        handleAction(context, OPT_IN_MIN_PRIORITY_NOTIFICATION_COUNT);
    }

    /**
     * Records a display of the opt-in activity.
     */
    public static void onOptInNotificationPressed(Context context) {
        handleAction(context, OPT_IN_NOTIFICATION_PRESS_COUNT);
    }

    /**
     * Records when the user disables the Physical Web fetaure.
     */
    public static void onPrefsFeatureDisabled(Context context) {
        handleAction(context, PREFS_FEATURE_DISABLED_COUNT);
    }

    /**
     * Records when the user enables the Physical Web fetaure.
     */
    public static void onPrefsFeatureEnabled(Context context) {
        handleAction(context, PREFS_FEATURE_ENABLED_COUNT);
    }

    /**
     * Records when the user denies the location permission when enabling the Physical Web from the
     * privacy settings menu.
     */
    public static void onPrefsLocationDenied(Context context) {
        handleAction(context, PREFS_LOCATION_DENIED_COUNT);
    }

    /**
     * Records when the user grants the location permission when enabling the Physical Web from the
     * privacy settings menu.
     */
    public static void onPrefsLocationGranted(Context context) {
        handleAction(context, PREFS_LOCATION_GRANTED_COUNT);
    }

    /**
     * Records a response time from PWS for a resolution during a background scan.
     * @param duration The length of time PWS took to respond.
     */
    public static void onBackgroundPwsResolution(Context context, long duration) {
        handleTime(context, PWS_BACKGROUND_RESOLVE_TIMES, duration, TimeUnit.MILLISECONDS);
    }

    /**
     * Records a response time from PWS for a resolution during a foreground scan that is not
     * explicitly user-initiated through a refresh.
     * @param duration The length of time PWS took to respond.
     */
    public static void onForegroundPwsResolution(Context context, long duration) {
        handleTime(context, PWS_FOREGROUND_RESOLVE_TIMES, duration, TimeUnit.MILLISECONDS);
    }

    /**
     * Records a response time from PWS for a resolution during a foreground scan that is explicitly
     * user-initiated through a refresh.
     * @param duration The length of time PWS took to respond.
     */
    public static void onRefreshPwsResolution(Context context, long duration) {
        handleTime(context, PWS_REFRESH_RESOLVE_TIMES, duration, TimeUnit.MILLISECONDS);
    }

    /**
     * Records number of URLs displayed to a user when the URL list is first displayed.
     * @param numUrls The number of URLs displayed to a user.
     */
    public static void onUrlsDisplayed(Context context, int numUrls) {
        if (LibraryLoader.isInitialized()) {
            RecordHistogram.recordCountHistogram(TOTAL_URLS_INITIAL_COUNTS, numUrls);
        } else {
            storeValue(context, TOTAL_URLS_INITIAL_COUNTS, numUrls);
        }
    }

    /**
     * Records number of URLs displayed to a user when the user refreshes the URL list.
     * @param numUrls The number of URLs displayed to a user.
     */
    public static void onUrlsRefreshed(Context context, int numUrls) {
        if (LibraryLoader.isInitialized()) {
            RecordHistogram.recordCountHistogram(TOTAL_URLS_REFRESH_COUNTS, numUrls);
        } else {
            storeValue(context, TOTAL_URLS_REFRESH_COUNTS, numUrls);
        }
    }

    /**
     * Records a ListUrlActivity referral.
     * @param refer The type of referral.  This enum is listed as PhysicalWebActivityReferer in
     *     histograms.xml.
     */
    public static void onActivityReferral(Context context, int referer) {
        handleEnum(context, ACTIVITY_REFERRALS, referer, ListUrlsActivity.REFERER_BOUNDARY);
        switch (referer) {
            case ListUrlsActivity.NOTIFICATION_REFERER:
                handleTime(context, STANDARD_NOTIFICATION_PRESS_DELAYS,
                        UrlManager.getInstance().getTimeSinceNotificationUpdate(),
                        TimeUnit.MILLISECONDS);
                break;
            case ListUrlsActivity.OPTIN_REFERER:
                handleTime(context, OPT_IN_NOTIFICATION_PRESS_DELAYS,
                        UrlManager.getInstance().getTimeSinceNotificationUpdate(),
                        TimeUnit.MILLISECONDS);
                break;
            case ListUrlsActivity.PREFERENCE_REFERER:
                recordPhysicalWebState(context, LAUNCH_FROM_PREFERENCES);
                break;
            case ListUrlsActivity.DIAGNOSTICS_REFERER:
                recordPhysicalWebState(context, LAUNCH_FROM_DIAGNOSTICS);
                break;
            default:
                break;
        }
    }

    /**
     * Calculate a Physical Web state.
     * The Physical Web state includes:
     * - The location provider
     * - The location permission
     * - The bluetooth status
     * - The data connection status
     * - The Physical Web preference status
     */
    public static void recordPhysicalWebState(Context context, String actionName) {
        LocationUtils locationUtils = LocationUtils.getInstance();
        handleEnum(context, createStateString(LOCATION_SERVICES, actionName),
                locationUtils.isSystemLocationSettingEnabled() ? 1 : 0, BOOLEAN_BOUNDARY);
        handleEnum(context, createStateString(LOCATION_PERMISSION, actionName),
                locationUtils.hasAndroidLocationPermission() ? 1 : 0, BOOLEAN_BOUNDARY);
        handleEnum(context, createStateString(BLUETOOTH, actionName),
                Utils.getBluetoothEnabledStatus(), TRISTATE_BOUNDARY);
        handleEnum(context, createStateString(DATA_CONNECTION, actionName),
                Utils.isDataConnectionActive() ? 1 : 0, BOOLEAN_BOUNDARY);
        int preferenceState = 2;
        if (!PhysicalWeb.isOnboarding()) {
            preferenceState = PhysicalWeb.isPhysicalWebPreferenceEnabled() ? 1 : 0;
        }
        handleEnum(context, createStateString(PREFERENCE, actionName),
                preferenceState, TRISTATE_BOUNDARY);
    }

    /**
     * Uploads metrics that we have deferred for uploading.
     */
    public static void uploadDeferredMetrics() {
        // Read the metrics.
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        if (prefs.getBoolean(HAS_DEFERRED_METRICS_KEY, false)) {
            AsyncTask.THREAD_POOL_EXECUTOR.execute(new UmaUploader(prefs));
        }
    }

    private static String createStateString(String stateName, String actionName) {
        return PHYSICAL_WEB_STATE + "." + stateName + "." + actionName;
    }

    private static void storeAction(Context context, String key) {
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        int count = prefs.getInt(key, 0);
        prefs.edit()
                .putBoolean(HAS_DEFERRED_METRICS_KEY, true)
                .putInt(key, count + 1)
                .apply();
    }

    private static void storeValue(Context context, String key, Object value) {
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        JSONArray values = null;
        try {
            values = new JSONArray(prefs.getString(key, "[]"));
            values.put(value);
            prefsEditor
                    .putBoolean(HAS_DEFERRED_METRICS_KEY, true)
                    .putString(key, values.toString())
                    .apply();
        } catch (JSONException e) {
            Log.e(TAG, "JSONException when storing " + key + " stats", e);
            prefsEditor.remove(key).apply();
            return;
        }
        prefsEditor.putString(key, values.toString()).apply();
    }

    private static void handleAction(Context context, String key) {
        if (LibraryLoader.isInitialized()) {
            RecordUserAction.record(key);
        } else {
            storeAction(context, key);
        }
    }

    private static void handleTime(Context context, String key, long duration, TimeUnit tu) {
        if (LibraryLoader.isInitialized()) {
            RecordHistogram.recordTimesHistogram(key, duration, tu);
        } else {
            storeValue(context, key, duration);
        }
    }

    private static void handleEnum(Context context, String key, int value, int boundary) {
        if (LibraryLoader.isInitialized()) {
            RecordHistogram.recordEnumeratedHistogram(key, value, boundary);
        } else {
            storeValue(context, key, value);
        }
    }

    private static class UmaUploader implements Runnable {
        SharedPreferences mPrefs;

        UmaUploader(SharedPreferences prefs) {
            mPrefs = prefs;
        }

        @Override
        public void run() {
            uploadActions(URL_SELECTED_COUNT);
            uploadActions(OPT_IN_DECLINE_BUTTON_PRESS_COUNT);
            uploadActions(OPT_IN_ENABLE_BUTTON_PRESS_COUNT);
            uploadActions(OPT_IN_HIGH_PRIORITY_NOTIFICATION_COUNT);
            uploadActions(OPT_IN_MIN_PRIORITY_NOTIFICATION_COUNT);
            uploadActions(OPT_IN_NOTIFICATION_PRESS_COUNT);
            uploadActions(PREFS_FEATURE_DISABLED_COUNT);
            uploadActions(PREFS_FEATURE_ENABLED_COUNT);
            uploadActions(PREFS_LOCATION_DENIED_COUNT);
            uploadActions(PREFS_LOCATION_GRANTED_COUNT);
            uploadTimes(PWS_BACKGROUND_RESOLVE_TIMES, TimeUnit.MILLISECONDS);
            uploadTimes(PWS_FOREGROUND_RESOLVE_TIMES, TimeUnit.MILLISECONDS);
            uploadTimes(PWS_REFRESH_RESOLVE_TIMES, TimeUnit.MILLISECONDS);
            uploadTimes(STANDARD_NOTIFICATION_PRESS_DELAYS, TimeUnit.MILLISECONDS);
            uploadTimes(OPT_IN_NOTIFICATION_PRESS_DELAYS, TimeUnit.MILLISECONDS);
            uploadCounts(TOTAL_URLS_INITIAL_COUNTS);
            uploadCounts(TOTAL_URLS_REFRESH_COUNTS);
            uploadEnums(ACTIVITY_REFERRALS, ListUrlsActivity.REFERER_BOUNDARY);
            uploadEnums(createStateString(LOCATION_SERVICES, LAUNCH_FROM_DIAGNOSTICS),
                    BOOLEAN_BOUNDARY);
            uploadEnums(createStateString(LOCATION_PERMISSION, LAUNCH_FROM_DIAGNOSTICS),
                    BOOLEAN_BOUNDARY);
            uploadEnums(createStateString(BLUETOOTH, LAUNCH_FROM_DIAGNOSTICS), TRISTATE_BOUNDARY);
            uploadEnums(createStateString(DATA_CONNECTION, LAUNCH_FROM_DIAGNOSTICS),
                    BOOLEAN_BOUNDARY);
            uploadEnums(createStateString(PREFERENCE, LAUNCH_FROM_DIAGNOSTICS), TRISTATE_BOUNDARY);
            uploadEnums(createStateString(LOCATION_SERVICES, LAUNCH_FROM_PREFERENCES),
                    BOOLEAN_BOUNDARY);
            uploadEnums(createStateString(LOCATION_PERMISSION, LAUNCH_FROM_PREFERENCES),
                    BOOLEAN_BOUNDARY);
            uploadEnums(createStateString(BLUETOOTH, LAUNCH_FROM_PREFERENCES), TRISTATE_BOUNDARY);
            uploadEnums(createStateString(DATA_CONNECTION, LAUNCH_FROM_PREFERENCES),
                    BOOLEAN_BOUNDARY);
            uploadEnums(createStateString(PREFERENCE, LAUNCH_FROM_PREFERENCES), TRISTATE_BOUNDARY);
            removePref(HAS_DEFERRED_METRICS_KEY);
        }

        private void removePref(String key) {
            mPrefs.edit()
                    .remove(key)
                    .apply();
        }

        private static Number[] parseJsonNumberArray(String jsonArrayStr) {
            try {
                JSONArray values = new JSONArray(jsonArrayStr);
                Number[] array = new Number[values.length()];
                for (int i = 0; i < values.length(); i++) {
                    Object object = values.get(i);
                    if (!(object instanceof Number)) {
                        return null;
                    }
                    array[i] = (Number) object;
                }
                return array;
            } catch (JSONException e) {
                return null;
            }
        }

        private static Long[] parseJsonLongArray(String jsonArrayStr) {
            Number[] numbers = parseJsonNumberArray(jsonArrayStr);
            if (numbers == null) {
                return null;
            }
            Long[] array = new Long[numbers.length];
            for (int i = 0; i < numbers.length; i++) {
                array[i] = numbers[i].longValue();
            }
            return array;
        }

        private static Integer[] parseJsonIntegerArray(String jsonArrayStr) {
            Number[] numbers = parseJsonNumberArray(jsonArrayStr);
            if (numbers == null) {
                return null;
            }
            Integer[] array = new Integer[numbers.length];
            for (int i = 0; i < numbers.length; i++) {
                array[i] = numbers[i].intValue();
            }
            return array;
        }

        private void uploadActions(String key) {
            int count = mPrefs.getInt(key, 0);
            removePref(key);
            for (int i = 0; i < count; i++) {
                RecordUserAction.record(key);
            }
        }

        private void uploadTimes(final String key, final TimeUnit tu) {
            String jsonTimesStr = mPrefs.getString(key, "[]");
            removePref(key);
            Long[] times = parseJsonLongArray(jsonTimesStr);
            if (times == null) {
                Log.e(TAG, "Error reporting " + key + " with values: " + jsonTimesStr);
                return;
            }
            for (Long time : times) {
                RecordHistogram.recordTimesHistogram(key, time, TimeUnit.MILLISECONDS);
            }
        }

        private void uploadCounts(final String key) {
            String jsonCountsStr = mPrefs.getString(key, "[]");
            removePref(key);
            Integer[] counts = parseJsonIntegerArray(jsonCountsStr);
            if (counts == null) {
                Log.e(TAG, "Error reporting " + key + " with values: " + jsonCountsStr);
                return;
            }
            for (Integer count: counts) {
                RecordHistogram.recordCountHistogram(key, count);
            }
        }

        private void uploadEnums(final String key, int boundary) {
            String jsonEnumsStr = mPrefs.getString(key, "[]");
            removePref(key);
            Integer[] values = parseJsonIntegerArray(jsonEnumsStr);
            if (values == null) {
                Log.e(TAG, "Error reporting " + key + " with values: " + jsonEnumsStr);
                return;
            }
            for (Integer value: values) {
                RecordHistogram.recordEnumeratedHistogram(key, value, boundary);
            }
        }
    }
}
