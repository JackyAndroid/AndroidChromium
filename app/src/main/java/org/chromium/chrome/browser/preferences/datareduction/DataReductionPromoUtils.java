// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.datareduction;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PrefServiceBridge.AboutVersionStrings;

/**
 * Helper functions for displaying the various data reduction proxy promos. The promo screens
 * inform users of the benefits of Data Saver.
 */
public class DataReductionPromoUtils {
    /**
     * Keys used to save whether the first run experience or second run promo screen has been shown,
     * the time in milliseconds since epoch it was shown, the Chrome version it was shown in, and
     * whether the user opted out of the data reduction proxy in the FRE promo.
     */
    private static final String SHARED_PREF_DISPLAYED_FRE_OR_SECOND_RUN_PROMO =
            "displayed_data_reduction_promo";
    private static final String SHARED_PREF_DISPLAYED_FRE_OR_SECOND_PROMO_TIME_MS =
            "displayed_data_reduction_promo_time_ms";
    private static final String SHARED_PREF_DISPLAYED_FRE_OR_SECOND_PROMO_VERSION =
            "displayed_data_reduction_promo_version";
    private static final String SHARED_PREF_FRE_PROMO_OPT_OUT = "fre_promo_opt_out";

    /**
     * Keys used to save whether the infobar promo is shown and the Chrome version it was shown in.
     */
    private static final String SHARED_PREF_DISPLAYED_INFOBAR_PROMO =
            "displayed_data_reduction_infobar_promo";
    private static final String SHARED_PREF_DISPLAYED_INFOBAR_PROMO_VERSION =
            "displayed_data_reduction_infobar_promo_version";

    /**
     * Returns whether any of the data reduction proxy promotions can be displayed. Checks if the
     * proxy is allowed by the DataReductionProxyConfig, already on, or if the user is managed. If
     * the data reduction proxy is managed by an administrator's policy, the user should not be
     * given a promotion to enable it.
     *
     * @return Whether the any data reduction proxy promotion has been displayed.
     */
    public static boolean canShowPromos() {
        if (!DataReductionProxySettings.getInstance().isDataReductionProxyPromoAllowed()) {
            return false;
        }
        if (DataReductionProxySettings.getInstance().isDataReductionProxyManaged()) return false;
        if (DataReductionProxySettings.getInstance().isDataReductionProxyEnabled()) return false;
        return true;
    }

    /**
     * Saves shared prefs indicating that the data reduction proxy first run experience or second
     * run promo screen has been displayed at the current time.
     */
    public static void saveFreOrSecondRunPromoDisplayed() {
        AboutVersionStrings versionStrings = PrefServiceBridge.getInstance()
                .getAboutVersionStrings();
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(SHARED_PREF_DISPLAYED_FRE_OR_SECOND_RUN_PROMO, true)
                .putLong(SHARED_PREF_DISPLAYED_FRE_OR_SECOND_PROMO_TIME_MS,
                        System.currentTimeMillis())
                .putString(SHARED_PREF_DISPLAYED_FRE_OR_SECOND_PROMO_VERSION,
                        versionStrings.getApplicationVersion())
                .apply();
    }

    /**
     * Returns whether the data reduction proxy first run experience or second run promo has been
     * displayed before.
     *
     * @return Whether the data reduction proxy promo has been displayed.
     */
    public static boolean getDisplayedFreOrSecondRunPromo() {
        return ContextUtils.getAppSharedPreferences().getBoolean(
                SHARED_PREF_DISPLAYED_FRE_OR_SECOND_RUN_PROMO, false);
    }

    /**
     * Returns the version the data reduction proxy first run experience or second run promo promo
     * was displayed on. If one of the promos has not been displayed, returns an empty string.
     *
     * @return The version the data reduction proxy promo was displayed on.
     */
    public static String getDisplayedFreOrSecondRunPromoVersion() {
        return ContextUtils.getAppSharedPreferences()
                .getString(SHARED_PREF_DISPLAYED_FRE_OR_SECOND_PROMO_VERSION, "");
    }

    /**
     * Saves shared prefs indicating that the data reduction proxy first run experience promo screen
     * was displayed and the user opted out.
     *
     * @param optOut Whether the user opted out of using the data reduction proxy.
     */
    public static void saveFrePromoOptOut(boolean optOut) {
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(SHARED_PREF_FRE_PROMO_OPT_OUT, optOut)
                .apply();
    }

    /**
     * Returns whether the user saw the data reduction proxy first run experience promo and opted
     * out.
     *
     * @return Whether the user opted out of the data reduction proxy first run experience promo.
     */
    public static boolean getOptedOutOnFrePromo() {
        return ContextUtils.getAppSharedPreferences().getBoolean(
                SHARED_PREF_FRE_PROMO_OPT_OUT, false);
    }

    /**
     * Saves shared prefs indicating that the data reduction proxy infobar promo has been displayed
     * at the current time.
     */
    public static void saveInfoBarPromoDisplayed() {
        AboutVersionStrings versionStrings = PrefServiceBridge.getInstance()
                .getAboutVersionStrings();
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(SHARED_PREF_DISPLAYED_INFOBAR_PROMO, true)
                .putString(SHARED_PREF_DISPLAYED_INFOBAR_PROMO_VERSION,
                        versionStrings.getApplicationVersion())
                .apply();
    }

    /**
     * Returns whether the data reduction proxy infobar promo has been displayed before.
     *
     * @return Whether the data reduction proxy infobar promo has been displayed.
     */
    public static boolean getDisplayedInfoBarPromo() {
        return ContextUtils.getAppSharedPreferences().getBoolean(
                SHARED_PREF_DISPLAYED_INFOBAR_PROMO, false);
    }
}
