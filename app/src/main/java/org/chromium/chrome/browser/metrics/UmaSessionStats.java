// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;

import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.privacy.CrashReportingPermissionManager;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.components.variations.VariationsAssociatedData;
import org.chromium.content_public.browser.WebContents;
import org.chromium.net.NetworkChangeNotifier;

/**
 * Mainly sets up session stats for chrome. A session is defined as the duration when the
 * application is in the foreground.  Also used to communicate information between Chrome
 * and the framework's MetricService.
 */
public class UmaSessionStats implements NetworkChangeNotifier.ConnectionTypeObserver {
    private static final String SAMSUNG_MULTWINDOW_PACKAGE = "com.sec.feature.multiwindow";

    private static long sNativeUmaSessionStats = 0;

    // TabModelSelector is needed to get the count of open tabs. We want to log the number of open
    // tabs on every page load.
    private TabModelSelector mTabModelSelector;
    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;

    private final Context mContext;
    private final boolean mIsMultiWindowCapable;
    private ComponentCallbacks mComponentCallbacks;

    private boolean mKeyboardConnected = false;
    private final CrashReportingPermissionManager mReportingPermissionManager;

    public UmaSessionStats(Context context) {
        mContext = context;
        mIsMultiWindowCapable = context.getPackageManager().hasSystemFeature(
                SAMSUNG_MULTWINDOW_PACKAGE);
        mReportingPermissionManager = PrivacyPreferencesManager.getInstance(context);
    }

    private void recordPageLoadStats(Tab tab) {
        WebContents webContents = tab.getWebContents();
        boolean isDesktopUserAgent = webContents != null
                && webContents.getNavigationController().getUseDesktopUserAgent();
        nativeRecordPageLoaded(isDesktopUserAgent);
        if (mKeyboardConnected) {
            nativeRecordPageLoadedWithKeyboard();
        }

        // If the session has ended (i.e. chrome is in the background), escape early. Ideally we
        // could track this number as part of either the previous or next session but this isn't
        // possible since the TabSelector is needed to figure out the current number of open tabs.
        if (mTabModelSelector == null) return;

        TabModel regularModel = mTabModelSelector.getModel(false);
        nativeRecordTabCountPerLoad(getTabCountFromModel(regularModel));
    }

    private int getTabCountFromModel(TabModel model) {
        return model == null ? 0 : model.getCount();
    }

    /**
     * Starts a new session for logging.
     * @param tabModelSelector A TabModelSelector instance for recording tab counts on page loads.
     * If null, UmaSessionStats does not record page loads and tab counts.
     */
    public void startNewSession(TabModelSelector tabModelSelector) {
        ensureNativeInitialized();

        mTabModelSelector = tabModelSelector;
        if (mTabModelSelector != null) {
            mComponentCallbacks = new ComponentCallbacks() {
                @Override
                public void onLowMemory() {
                    // Not required
                }

                @Override
                public void onConfigurationChanged(Configuration newConfig) {
                    mKeyboardConnected = newConfig.keyboard != Configuration.KEYBOARD_NOKEYS;
                }
            };
            mContext.registerComponentCallbacks(mComponentCallbacks);
            mKeyboardConnected = mContext.getResources().getConfiguration()
                    .keyboard != Configuration.KEYBOARD_NOKEYS;
            mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(mTabModelSelector) {
                @Override
                public void onPageLoadFinished(Tab tab) {
                    recordPageLoadStats(tab);
                }
            };
        }

        nativeUmaResumeSession(sNativeUmaSessionStats);
        NetworkChangeNotifier.addConnectionTypeObserver(this);
        updatePreferences();
        updateMetricsServiceState();
    }

    private static void ensureNativeInitialized() {
        // Lazily create the native object and the notification handler. These objects are never
        // destroyed.
        if (sNativeUmaSessionStats == 0) {
            sNativeUmaSessionStats = nativeInit();
        }
    }

    /**
     * Logs screen ratio on Samsung MultiWindow devices.
     */
    public void logMultiWindowStats(int windowArea, int displayArea, int instanceCount) {
        if (mIsMultiWindowCapable) {
            if (displayArea == 0) return;
            int areaPercent = (windowArea * 100) / displayArea;
            int safePercent = areaPercent > 0 ? areaPercent : 0;
            nativeRecordMultiWindowSession(safePercent, instanceCount);
        }
    }

    /**
     * Logs the current session.
     */
    public void logAndEndSession() {
        if (mTabModelSelector != null) {
            mContext.unregisterComponentCallbacks(mComponentCallbacks);
            mTabModelSelectorTabObserver.destroy();
            mTabModelSelector = null;
        }

        nativeUmaEndSession(sNativeUmaSessionStats);
        NetworkChangeNotifier.removeConnectionTypeObserver(this);
    }

    public static void logRendererCrash() {
        nativeLogRendererCrash();
    }

    /**
     * Updates the state of the MetricsService to account for the user's preferences.
     */
    public void updateMetricsServiceState() {
        boolean mayRecordStats = !PrivacyPreferencesManager.getInstance(mContext)
                .isNeverUploadCrashDump();
        boolean mayUploadStats = mReportingPermissionManager.isUploadPermitted();

        // Re-start the MetricsService with the given parameters.
        nativeUpdateMetricsServiceState(mayRecordStats, mayUploadStats);
    }

    /**
     * Updating Android preferences according to equivalent native preferences so that the values
     * can be retrieved while native preferences are not accessible.
     */
    private void updatePreferences() {
        // Update cellular experiment preference.
        PrivacyPreferencesManager prefManager = PrivacyPreferencesManager.getInstance(mContext);
        boolean cellularExperiment = TextUtils.equals("true",
                VariationsAssociatedData.getVariationParamValue(
                        "UMA_EnableCellularLogUpload", "Enabled"));
        prefManager.setCellularExperiment(cellularExperiment);

        // Update metrics reporting preference.
        if (cellularExperiment) {
            PrefServiceBridge prefBridge = PrefServiceBridge.getInstance();
            // If the native preference metrics reporting has not been set, then initialize it
            // based on the older android preference.
            if (!prefBridge.hasSetMetricsReporting()) {
                prefBridge.setMetricsReportingEnabled(prefManager.isUploadCrashDumpEnabled());
            }

            // Set new Android preference for usage and crash reporting.
            prefManager.setUsageAndCrashReporting(prefBridge.isMetricsReportingEnabled());
        }
    }

    @Override
    public void onConnectionTypeChanged(int connectionType) {
        updateMetricsServiceState();
    }

    public static void registerExternalExperiment(int studyId, int experimentId) {
        nativeRegisterExternalExperiment(studyId, experimentId);
    }

    public static void registerSyntheticFieldTrial(String trialName, String groupName) {
        nativeRegisterSyntheticFieldTrial(trialName, groupName);
    }

    private static native long nativeInit();
    private native void nativeUpdateMetricsServiceState(boolean mayRecord, boolean mayUpload);
    private native void nativeUmaResumeSession(long nativeUmaSessionStats);
    private native void nativeUmaEndSession(long nativeUmaSessionStats);
    private static native void nativeLogRendererCrash();
    private static native void nativeRegisterExternalExperiment(int studyId,
                                                                int experimentId);
    private static native void nativeRegisterSyntheticFieldTrial(
            String trialName, String groupName);
    private static native void nativeRecordMultiWindowSession(int areaPercent, int instanceCount);
    private static native void nativeRecordTabCountPerLoad(int numTabsOpen);
    private static native void nativeRecordPageLoaded(boolean isDesktopUserAgent);
    private static native void nativeRecordPageLoadedWithKeyboard();

}
