// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.physicalweb.PhysicalWeb;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.components.minidump_uploader.util.CrashReportingPermissionManager;

/**
 * Reads, writes, and migrates preferences related to network usage and privacy.
 */
public class PrivacyPreferencesManager implements CrashReportingPermissionManager{
    static final String DEPRECATED_PREF_CRASH_DUMP_UPLOAD = "crash_dump_upload";
    static final String DEPRECATED_PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR =
            "crash_dump_upload_no_cellular";
    private static final String DEPRECATED_PREF_CELLULAR_EXPERIMENT = "cellular_experiment";

    public static final String PREF_METRICS_REPORTING = "metrics_reporting";
    private static final String PREF_METRICS_IN_SAMPLE = "in_metrics_sample";
    private static final String PREF_NETWORK_PREDICTIONS = "network_predictions";
    private static final String PREF_BANDWIDTH_OLD = "prefetch_bandwidth";
    private static final String PREF_BANDWIDTH_NO_CELLULAR_OLD = "prefetch_bandwidth_no_cellular";
    private static final String ALLOW_PRERENDER_OLD = "allow_prefetch";
    private static final String PREF_PHYSICAL_WEB = "physical_web";
    private static final int PHYSICAL_WEB_OFF = 0;
    private static final int PHYSICAL_WEB_ON = 1;
    private static final int PHYSICAL_WEB_ONBOARDING = 2;

    private static PrivacyPreferencesManager sInstance;

    private final Context mContext;
    private final SharedPreferences mSharedPreferences;

    private boolean mCrashUploadingDisabledByCommandLine;

    @VisibleForTesting
    PrivacyPreferencesManager(Context context) {
        mContext = context;
        mSharedPreferences = ContextUtils.getAppSharedPreferences();

        // We default the command line flag to disable uploads unless altered on deferred startup
        // to prevent unwanted uploads at startup. If the command line flag to enable uploading is
        // turned on, the other conditions (e.g. user/network preferences) for when to upload apply.
        // This currently applies to only crash reporting and is ignored for metrics reporting.
        mCrashUploadingDisabledByCommandLine = true;
        migrateUsageAndCrashPreferences();
    }

    public static PrivacyPreferencesManager getInstance() {
        if (sInstance == null) {
            sInstance = new PrivacyPreferencesManager(ContextUtils.getApplicationContext());
        }
        return sInstance;
    }

    public void migrateUsageAndCrashPreferences() {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        if (mSharedPreferences.contains(DEPRECATED_PREF_CRASH_DUMP_UPLOAD)) {
            String crashDumpNeverUpload = "crash_dump_never_upload";
            setUsageAndCrashReporting(
                    !mSharedPreferences
                             .getString(DEPRECATED_PREF_CRASH_DUMP_UPLOAD, crashDumpNeverUpload)
                             .equals(crashDumpNeverUpload));

            // Remove both this preference and the related one. If the related one is not removed
            // now, later migrations could read from it and clobber the state.
            editor.remove(DEPRECATED_PREF_CRASH_DUMP_UPLOAD);
            if (mSharedPreferences.contains(DEPRECATED_PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR)) {
                editor.remove(DEPRECATED_PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR);
            }
        } else if (mSharedPreferences.contains(DEPRECATED_PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR)) {
            setUsageAndCrashReporting(mSharedPreferences.getBoolean(
                    DEPRECATED_PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR, false));
            editor.remove(DEPRECATED_PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR);
        }

        if (mSharedPreferences.contains(DEPRECATED_PREF_CELLULAR_EXPERIMENT)) {
            editor.remove(DEPRECATED_PREF_CELLULAR_EXPERIMENT);
        }
        editor.apply();
    }

    /**
     * Migrate and delete old preferences.  Note that migration has to happen in Android-specific
     * code because we need to access ALLOW_PRERENDER sharedPreference.
     * TODO(bnc) https://crbug.com/394845. This change is planned for M38. After a year or so, it
     * would be worth considering removing this migration code (also removing accessors in
     * PrefServiceBridge and pref_service_bridge), and reverting to default for users
     * who had set preferences but have not used Chrome for a year. This change would be subject to
     * privacy review.
     */
    public void migrateNetworkPredictionPreferences() {
        PrefServiceBridge prefService = PrefServiceBridge.getInstance();

        // See if PREF_NETWORK_PREDICTIONS is an old boolean value.
        boolean predictionOptionIsBoolean = false;
        try {
            mSharedPreferences.getString(PREF_NETWORK_PREDICTIONS, "");
        } catch (ClassCastException ex) {
            predictionOptionIsBoolean = true;
        }

        // Nothing to do if the user or this migration code has already set the new
        // preference.
        if (!predictionOptionIsBoolean
                && prefService.obsoleteNetworkPredictionOptionsHasUserSetting()) {
            return;
        }

        // Nothing to do if the old preferences are unset.
        if (!predictionOptionIsBoolean
                && !mSharedPreferences.contains(PREF_BANDWIDTH_OLD)
                && !mSharedPreferences.contains(PREF_BANDWIDTH_NO_CELLULAR_OLD)) {
            return;
        }

        // Migrate if the old preferences are at their default values.
        // (Note that for PREF_BANDWIDTH*, if the setting is default, then there is no way to tell
        // whether the user has set it.)
        final String prefBandwidthDefault = BandwidthType.PRERENDER_ON_WIFI.title();
        final String prefBandwidth =
                mSharedPreferences.getString(PREF_BANDWIDTH_OLD, prefBandwidthDefault);
        boolean prefBandwidthNoCellularDefault = true;
        boolean prefBandwidthNoCellular = mSharedPreferences.getBoolean(
                PREF_BANDWIDTH_NO_CELLULAR_OLD, prefBandwidthNoCellularDefault);

        if (!(prefBandwidthDefault.equals(prefBandwidth))
                || (prefBandwidthNoCellular != prefBandwidthNoCellularDefault)) {
            boolean newValue = true;
            // Observe PREF_BANDWIDTH on mobile network capable devices.
            if (isMobileNetworkCapable()) {
                if (mSharedPreferences.contains(PREF_BANDWIDTH_OLD)) {
                    BandwidthType prefetchBandwidthTypePref = BandwidthType.getBandwidthFromTitle(
                            prefBandwidth);
                    if (BandwidthType.NEVER_PRERENDER.equals(prefetchBandwidthTypePref)) {
                        newValue = false;
                    } else if (BandwidthType.PRERENDER_ON_WIFI.equals(prefetchBandwidthTypePref)) {
                        newValue = true;
                    } else if (BandwidthType.ALWAYS_PRERENDER.equals(prefetchBandwidthTypePref)) {
                        newValue = true;
                    }
                }
            // Observe PREF_BANDWIDTH_NO_CELLULAR on devices without mobile network.
            } else {
                if (mSharedPreferences.contains(PREF_BANDWIDTH_NO_CELLULAR_OLD)) {
                    if (prefBandwidthNoCellular) {
                        newValue = true;
                    } else {
                        newValue = false;
                    }
                }
            }
            // Save new value in Chrome PrefService.
            prefService.setNetworkPredictionEnabled(newValue);
        }

        // Delete old sharedPreferences.
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        // Delete PREF_BANDWIDTH and PREF_BANDWIDTH_NO_CELLULAR: just migrated these options.
        if (mSharedPreferences.contains(PREF_BANDWIDTH_OLD)) {
            sharedPreferencesEditor.remove(PREF_BANDWIDTH_OLD);
        }
        if (mSharedPreferences.contains(PREF_BANDWIDTH_NO_CELLULAR_OLD)) {
            sharedPreferencesEditor.remove(PREF_BANDWIDTH_NO_CELLULAR_OLD);
        }
        // Also delete ALLOW_PRERENDER, which was updated based on PREF_BANDWIDTH[_NO_CELLULAR] and
        // network connectivity type, therefore does not carry additional information.
        if (mSharedPreferences.contains(ALLOW_PRERENDER_OLD)) {
            sharedPreferencesEditor.remove(ALLOW_PRERENDER_OLD);
        }
        // Delete bool PREF_NETWORK_PREDICTIONS so that string values can be stored. Note that this
        // SharedPreference carries no information, because it used to be overwritten by
        // kNetworkPredictionEnabled on startup, and now it is overwritten by
        // kNetworkPredictionOptions on startup.
        if (mSharedPreferences.contains(PREF_NETWORK_PREDICTIONS)) {
            sharedPreferencesEditor.remove(PREF_NETWORK_PREDICTIONS);
        }
        sharedPreferencesEditor.apply();
    }

    private NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo();
    }

    protected boolean isNetworkAvailable() {
        NetworkInfo networkInfo = getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    protected boolean isWiFiOrEthernetNetwork() {
        NetworkInfo networkInfo = getActiveNetworkInfo();
        return networkInfo != null
                && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                        || networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET);
    }

    protected boolean isMobileNetworkCapable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        // Android telephony team said it is OK to continue using getNetworkInfo() for our purposes.
        // We cannot use ConnectivityManager#getAllNetworks() because that one only reports enabled
        // networks. See crbug.com/532455.
        @SuppressWarnings("deprecation")
        NetworkInfo networkInfo = connectivityManager
                .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        return networkInfo != null;
    }

    /**
     * Checks whether prerender should be allowed and updates the preference if it is not set yet.
     * @return Whether prerendering should be allowed.
     */
    public boolean shouldPrerender() {
        if (!DeviceClassManager.enablePrerendering()) return false;
        migrateNetworkPredictionPreferences();
        return PrefServiceBridge.getInstance().canPrefetchAndPrerender();
    }

    /**
     * Sets the usage and crash reporting preference ON or OFF.
     *
     * @param enabled A boolean corresponding whether usage and crash reports uploads are allowed.
     */
    public void setUsageAndCrashReporting(boolean enabled) {
        mSharedPreferences.edit().putBoolean(PREF_METRICS_REPORTING, enabled).apply();
        syncUsageAndCrashReportingPrefs();
    }

    /**
     * Update usage and crash preferences based on Android preferences if possible in case they are
     * out of sync.
     */
    public void syncUsageAndCrashReportingPrefs() {
        if (PrefServiceBridge.isInitialized()) {
            PrefServiceBridge.getInstance().setMetricsReportingEnabled(
                    isUsageAndCrashReportingPermittedByUser());
        }
    }

    /**
     * Sets whether this client is in-sample for usage metrics and crash reporting. See
     * {@link org.chromium.chrome.browser.metrics.UmaUtils#isClientInMetricsSample} for details.
     */
    public void setClientInMetricsSample(boolean inSample) {
        mSharedPreferences.edit().putBoolean(PREF_METRICS_IN_SAMPLE, inSample).apply();
    }

    /**
     * Checks whether this client is in-sample for usage metrics and crash reporting. See
     * {@link org.chromium.chrome.browser.metrics.UmaUtils#isClientInMetricsSample} for details.
     *
     * @returns boolean Whether client is in-sample.
     */
    @Override
    public boolean isClientInMetricsSample() {
        // The default value is true to avoid sampling out crashes that occur before native code has
        // been initialized on first run. We'd rather have some extra crashes than none from that
        // time.
        return mSharedPreferences.getBoolean(PREF_METRICS_IN_SAMPLE, true);
    }

    /**
     * Checks whether uploading of crash dumps is permitted for the available network(s).
     *
     * @return whether uploading crash dumps is permitted.
     */
    @Override
    public boolean isNetworkAvailableForCrashUploads() {
        return isNetworkAvailable() && isWiFiOrEthernetNetwork();
    }

    /**
     * Checks whether uploading of crash dumps is permitted, based on the corresponding command line
     * flag only.
     * TODO(jchinlee): this is not quite a boolean. Depending on other refactoring, change to enum.
     *
     * @return whether uploading of crash dumps is enabled or disabled by a command line flag.
     */
    @Override
    public boolean isCrashUploadDisabledByCommandLine() {
        return mCrashUploadingDisabledByCommandLine;
    }

    /**
     * Checks whether uploading of usage metrics is currently permitted.
     *
     * Note that this function intentionally does not check |mCrashUploadingDisabledByCommandLine|.
     * See http://crbug.com/602703 for more details.
     *
     * @return whether uploading usage metrics is currently permitted.
     */
    @Override
    public boolean isMetricsUploadPermitted() {
        return isNetworkAvailable()
                && (isUsageAndCrashReportingPermittedByUser() || isUploadEnabledForTests());
    }

    /**
     * Checks whether uploading of usage metrics and crash dumps is currently permitted, based on
     * user consent only. This doesn't take network condition or experimental state (i.e. disabling
     * upload) into consideration. A crash dump may be retried if this check passes.
     *
     * @return whether the user has consented to reporting usage metrics and crash dumps.
     */
    @Override
    public boolean isUsageAndCrashReportingPermittedByUser() {
        return mSharedPreferences.getBoolean(PREF_METRICS_REPORTING, false);
    }

    /**
     * Check whether the command line switch is used to force uploading if at all possible. Used by
     * test devices to avoid UI manipulation.
     *
     * @return whether uploading should be enabled if at all possible.
     */
    @Override
    public boolean isUploadEnabledForTests() {
        return CommandLine.getInstance().hasSwitch(ChromeSwitches.FORCE_CRASH_DUMP_UPLOAD);
    }

    /**
     * Provides a way to remove disabling crash uploading entirely.
     * Enable crash uploading based on user's preference when an overriding flag does not exist in
     * commandline.
     * Used to differentiate from tests that trigger crashes intentionally, so these crashes are not
     * uploaded.
     */
    public void enablePotentialCrashUploading() {
        mCrashUploadingDisabledByCommandLine = false;
    }

    /**
     * Sets the Physical Web preference, which enables background scanning for bluetooth beacons
     * and displays a notification when beacons are found.
     *
     * @param enabled A boolean indicating whether to notify on nearby beacons.
     */
    public void setPhysicalWebEnabled(boolean enabled) {
        int state = enabled ? PHYSICAL_WEB_ON : PHYSICAL_WEB_OFF;
        boolean isOnboarding = isPhysicalWebOnboarding();
        mSharedPreferences.edit().putInt(PREF_PHYSICAL_WEB, state).apply();
        if (enabled) {
            if (!isOnboarding) {
                PhysicalWeb.startPhysicalWeb();
            }
        } else {
            PhysicalWeb.stopPhysicalWeb();
        }
    }

    /**
     * Check whether the user is still in the Physical Web onboarding flow.
     *
     * @return boolean {@code true} if onboarding is not yet complete.
     */
    public boolean isPhysicalWebOnboarding() {
        int state = mSharedPreferences.getInt(PREF_PHYSICAL_WEB, PHYSICAL_WEB_ONBOARDING);
        return (state == PHYSICAL_WEB_ONBOARDING);
    }

    /**
     * Check whether Physical Web is configured to notify on nearby beacons.
     *
     * @return boolean {@code true} if the feature is enabled.
     */
    public boolean isPhysicalWebEnabled() {
        int state = mSharedPreferences.getInt(PREF_PHYSICAL_WEB, PHYSICAL_WEB_ONBOARDING);
        return (state == PHYSICAL_WEB_ON);
    }
}
