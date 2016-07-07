// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.net.spdyproxy;

import android.content.Context;
import android.preference.PreferenceManager;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Entry point to manage all data reduction proxy configuration details.
 */
public class DataReductionProxySettings {

    /**
     * Data structure to hold the original content length before data reduction and the received
     * content length after data reduction.
     */
    public static class ContentLengths {
        private final long mOriginal;
        private final long mReceived;

        @CalledByNative("ContentLengths")
        public static ContentLengths create(long original, long received) {
            return new ContentLengths(original, received);
        }

        private ContentLengths(long original, long received) {
            mOriginal = original;
            mReceived = received;
        }

        public long getOriginal() {
            return mOriginal;
        }

        public long getReceived() {
            return mReceived;
        }
    }

    @VisibleForTesting
    public static final String DATA_REDUCTION_PROXY_ENABLED_KEY = "Data Reduction Proxy Enabled";

    private static DataReductionProxySettings sSettings;

    private static final String DATA_REDUCTION_ENABLED_PREF = "BANDWIDTH_REDUCTION_PROXY_ENABLED";

    /**
     * Returns whether the data reduction proxy is enabled.
     *
     * The knowledge of the data reduction proxy status is needed before the
     * native library is loaded.
     *
     * Note that the returned value can be out-of-date if the Data Reduction
     * Proxy is enabled/disabled from the native side without going through the
     * UI. The discrepancy will however be fixed at the next launch, so the
     * value returned here can be wrong (both false-positive and false-negative)
     * right after such a change.
     *
     * @param context The application context.
     * @return Whether the data reduction proxy is enabled.
     */
    public static boolean isEnabledBeforeNativeLoad(Context context) {
        // TODO(lizeb): Add a listener for the native preference change to keep
        // both in sync and avoid the false-positives and false-negatives.
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            DATA_REDUCTION_ENABLED_PREF, false);
    }

    /**
     * Reconciles the Java-side data reduction proxy state with the native one.
     *
     * The data reduction proxy state needs to be accessible before the native
     * library has been loaded, from Java. This is possible through
     * isEnabledBeforeNativeLoad(). Once the native library has been loaded, the
     * Java preference has to be updated.
     * This method must be called early at startup, but once the native library
     * has been loaded.
     *
     * @param context The application context.
     */
    public static void reconcileDataReductionProxyEnabledState(Context context) {
        ThreadUtils.assertOnUiThread();
        boolean enabled = getInstance().isDataReductionProxyEnabled();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(DATA_REDUCTION_ENABLED_PREF, enabled).apply();
    }

    /**
     * Returns a singleton instance of the settings object.
     *
     * Needs the native library to be loaded, otherwise it will crash.
     */
    public static DataReductionProxySettings getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sSettings == null) {
            sSettings = new DataReductionProxySettings();
        }
        return sSettings;
    }

    private final long mNativeDataReductionProxySettings;

    private DataReductionProxySettings() {
        // Note that this technically leaks the native object, however,
        // DataReductionProxySettings is a singleton that lives forever and there's no clean
        // shutdown of Chrome on Android
        mNativeDataReductionProxySettings = nativeInit();
    }

    /** Returns true if the SPDY proxy is allowed to be used. */
    public boolean isDataReductionProxyAllowed() {
        return nativeIsDataReductionProxyAllowed(mNativeDataReductionProxySettings);
    }

    /** Returns true if the SPDY proxy promo is allowed to be shown. */
    public boolean isDataReductionProxyPromoAllowed() {
        return nativeIsDataReductionProxyPromoAllowed(mNativeDataReductionProxySettings);
    }

    /** Returns true if proxy alternative field trial is running. */
    public boolean isIncludedInAltFieldTrial() {
        return nativeIsIncludedInAltFieldTrial(mNativeDataReductionProxySettings);
    }

    /**
     * Sets the preference on whether to enable/disable the SPDY proxy. This will zero out the
     * data reduction statistics if this is the first time the SPDY proxy has been enabled.
     */
    public void setDataReductionProxyEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(DATA_REDUCTION_ENABLED_PREF, enabled).apply();
        nativeSetDataReductionProxyEnabled(mNativeDataReductionProxySettings, enabled);
    }

    /** Returns true if the Data Reduction Proxy proxy is enabled. */
    public boolean isDataReductionProxyEnabled() {
        return nativeIsDataReductionProxyEnabled(mNativeDataReductionProxySettings);
    }

    /**
     * Returns true if the Data Reduction Proxy proxy can be used for the given url. This method
     * does not take into account the proxy config or proxy retry list, so it can return true even
     * when the proxy will not be used.
     */
    public boolean canUseDataReductionProxy(String url) {
        return nativeCanUseDataReductionProxy(mNativeDataReductionProxySettings, url);
    }

    /**
     * Returns true if the Data Reduction Proxy's Lo-Fi mode was enabled on the last main frame
     * request.
     */
    public boolean wasLoFiModeActiveOnMainFrame() {
        return nativeWasLoFiModeActiveOnMainFrame(mNativeDataReductionProxySettings);
    }

    /**
     * Returns true if a "Load image" context menu request has not been made since the last main
     * frame request.
     */
    public boolean wasLoFiLoadImageRequestedBefore() {
        return nativeWasLoFiLoadImageRequestedBefore(mNativeDataReductionProxySettings);
    }

    /**
     * Records that a "Load image" context menu request has been made.
     */
    public void setLoFiLoadImageRequested() {
        nativeSetLoFiLoadImageRequested(mNativeDataReductionProxySettings);
    }

    /**
     * Counts the number of times the Lo-Fi snackbar has been shown.
     *  */
    public void incrementLoFiSnackbarShown() {
        nativeIncrementLoFiSnackbarShown(mNativeDataReductionProxySettings);
    }

    /**
     * Counts the number of requests to reload the page with images from the Lo-Fi snackbar. If the
     * user requests the page with images a certain number of times, then Lo-Fi is disabled for the
     * session.
     *  */
    public void incrementLoFiUserRequestsForImages() {
        nativeIncrementLoFiUserRequestsForImages(mNativeDataReductionProxySettings);
    }

    /** Returns true if the SPDY proxy is managed by an administrator's policy. */
    public boolean isDataReductionProxyManaged() {
        return nativeIsDataReductionProxyManaged(mNativeDataReductionProxySettings);
    }

    /**
     * Returns the time that the data reduction statistics were last updated.
     * @return The last update time in milliseconds since the epoch.
     */
    public long getDataReductionLastUpdateTime()  {
        return nativeGetDataReductionLastUpdateTime(mNativeDataReductionProxySettings);
    }

    /**
     * Returns aggregate original and received content lengths.
     * @return The content lengths.
     */
    public ContentLengths getContentLengths() {
        return nativeGetContentLengths(mNativeDataReductionProxySettings);
    }

    /**
     * Retrieves the history of daily totals of bytes that would have been
     * received if no data reducing mechanism had been applied.
     * @return The history of daily totals
     */
    public long[] getOriginalNetworkStatsHistory() {
        return nativeGetDailyOriginalContentLengths(mNativeDataReductionProxySettings);
    }

    /**
     * Retrieves the history of daily totals of bytes that were received after
     * applying a data reducing mechanism.
     * @return The history of daily totals
     */
    public long[] getReceivedNetworkStatsHistory() {
        return nativeGetDailyReceivedContentLengths(mNativeDataReductionProxySettings);
    }

    /**
     * Determines if the data reduction proxy is currently unreachable.
     * @return true if the data reduction proxy is unreachable.
     */
    public boolean isDataReductionProxyUnreachable() {
        return nativeIsDataReductionProxyUnreachable(mNativeDataReductionProxySettings);
    }

    /**
     * @return The data reduction settings as a string percentage.
     */
    public String getContentLengthPercentSavings() {
        ContentLengths length = getContentLengths();

        double savings = 0;
        if (length.getOriginal() > 0L  && length.getOriginal() > length.getReceived()) {
            savings = (length.getOriginal() - length.getReceived()) / (double) length.getOriginal();
        }
        NumberFormat percentageFormatter = NumberFormat.getPercentInstance(Locale.getDefault());
        return percentageFormatter.format(savings);
    }

    public Map<String, String> toFeedbackMap() {
        Map<String, String> map = new HashMap<>();
        map.put(DATA_REDUCTION_PROXY_ENABLED_KEY, String.valueOf(isDataReductionProxyEnabled()));
        map.put("Data Reduction Proxy HTTP Proxies",
                nativeGetHttpProxyList(mNativeDataReductionProxySettings));
        map.put("Data Reduction Proxy HTTPS Proxies",
                nativeGetHttpsProxyList(mNativeDataReductionProxySettings));
        map.put("Data Reduction Proxy Last Bypass",
                nativeGetLastBypassEvent(mNativeDataReductionProxySettings));
        return map;
    }

    private native long nativeInit();
    private native boolean nativeIsDataReductionProxyAllowed(
            long nativeDataReductionProxySettingsAndroid);
    private native boolean nativeIsDataReductionProxyPromoAllowed(
            long nativeDataReductionProxySettingsAndroid);
    private native boolean nativeIsIncludedInAltFieldTrial(
            long nativeDataReductionProxySettingsAndroid);
    private native boolean nativeIsDataReductionProxyEnabled(
            long nativeDataReductionProxySettingsAndroid);
    private native boolean nativeCanUseDataReductionProxy(
            long nativeDataReductionProxySettingsAndroid, String url);
    private native boolean nativeWasLoFiModeActiveOnMainFrame(
            long nativeDataReductionProxySettingsAndroid);
    private native boolean nativeWasLoFiLoadImageRequestedBefore(
            long nativeDataReductionProxySettingsAndroid);
    private native void nativeSetLoFiLoadImageRequested(
            long nativeDataReductionProxySettingsAndroid);
    private native void nativeIncrementLoFiSnackbarShown(
            long nativeDataReductionProxySettingsAndroid);
    private native void nativeIncrementLoFiUserRequestsForImages(
            long nativeDataReductionProxySettingsAndroid);
    private native boolean nativeIsDataReductionProxyManaged(
            long nativeDataReductionProxySettingsAndroid);
    private native void nativeSetDataReductionProxyEnabled(
            long nativeDataReductionProxySettingsAndroid, boolean enabled);
    private native long nativeGetDataReductionLastUpdateTime(
            long nativeDataReductionProxySettingsAndroid);
    private native ContentLengths nativeGetContentLengths(
            long nativeDataReductionProxySettingsAndroid);
    private native long[] nativeGetDailyOriginalContentLengths(
            long nativeDataReductionProxySettingsAndroid);
    private native long[] nativeGetDailyReceivedContentLengths(
            long nativeDataReductionProxySettingsAndroid);
    private native boolean nativeIsDataReductionProxyUnreachable(
            long nativeDataReductionProxySettingsAndroid);
    private native String nativeGetHttpProxyList(long nativeDataReductionProxySettingsAndroid);
    private native String nativeGetHttpsProxyList(long nativeDataReductionProxySettingsAndroid);
    private native String nativeGetLastBypassEvent(long nativeDataReductionProxySettingsAndroid);
}
