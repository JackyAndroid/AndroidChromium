// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.content.Context;
import android.text.TextUtils;

import org.chromium.base.CommandLine;
import org.chromium.base.SysUtils;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.components.variations.VariationsAssociatedData;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * Provides Field Trial support for the Contextual Search application within Chrome for Android.
 */
public class ContextualSearchFieldTrial {
    private static final String FIELD_TRIAL_NAME = "ContextualSearch";
    private static final String ENABLED_PARAM = "enabled";
    private static final String ENABLED_VALUE = "true";

    static final String PROMO_ON_LIMITED_TAPS = "promo_on_limited_taps";
    static final String TAP_TRIGGERED_PROMO_LIMIT = "tap_triggered_promo_limit";
    static final String TAP_RESOLVE_LIMIT_FOR_DECIDED = "tap_resolve_limit_for_decided";
    static final String TAP_PREFETCH_LIMIT_FOR_DECIDED = "tap_prefetch_limit_for_decided";
    static final String TAP_RESOLVE_LIMIT_FOR_UNDECIDED = "tap_resolve_limit_for_undecided";
    static final String TAP_PREFETCH_LIMIT_FOR_UNDECIDED = "tap_prefetch_limit_for_undecided";

    static final String PEEK_PROMO_FORCED = "peek_promo_forced";
    static final String PEEK_PROMO_ENABLED = "peek_promo_enabled";
    static final String PEEK_PROMO_MAX_SHOW_COUNT = "peek_promo_max_show_count";
    static final int PEEK_PROMO_DEFAULT_MAX_SHOW_COUNT = 10;

    static final String DISABLE_EXTRA_SEARCH_BAR_ANIMATIONS = "disable_extra_search_bar_animations";

    private static final int UNLIMITED_TAPS = -1;
    private static final int DEFAULT_TAP_RESOLVE_LIMIT_FOR_DECIDED = UNLIMITED_TAPS;
    private static final int DEFAULT_TAP_PREFETCH_LIMIT_FOR_DECIDED = UNLIMITED_TAPS;
    private static final int DEFAULT_TAP_RESOLVE_LIMIT_FOR_UNDECIDED = 100;
    private static final int DEFAULT_TAP_PREFETCH_LIMIT_FOR_UNDECIDED = 10;

    // Cached values to avoid repeated and redundant JNI operations.
    private static Boolean sEnabled;
    private static Boolean sIsPeekPromoEnabled;
    private static Integer sPeekPromoMaxCount;

    /**
     * Don't instantiate.
     */
    private ContextualSearchFieldTrial() {}

    /**
     * Checks the current Variations parameters associated with the active group as well as the
     * Chrome preference to determine if the service is enabled.
     * @param context Context used to determine whether the device is a tablet or a phone.
     * @return Whether Contextual Search is enabled or not.
     */
    public static boolean isEnabled(Context context) {
        if (sEnabled == null) {
            sEnabled = detectEnabled(context);
        }
        return sEnabled.booleanValue();
    }

    private static boolean detectEnabled(Context context) {
        if (SysUtils.isLowEndDevice()) {
            return false;
        }

        // This is used for instrumentation tests (i.e. it is not a user-flippable flag). We cannot
        // use Variations params because in the test harness, the initialization comes before any
        // native methods are available. And the ContextualSearchManager is initialized very early
        // in the Chrome initialization.
        if (CommandLine.getInstance().hasSwitch(
                    ChromeSwitches.ENABLE_CONTEXTUAL_SEARCH_FOR_TESTING)) {
            return true;
        }

        // Allow this user-flippable flag to disable the feature.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_CONTEXTUAL_SEARCH)) {
            return false;
        }

        // Allow this user-flippable flag to enable the feature.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CONTEXTUAL_SEARCH)) {
            return true;
        }

        // Enable contextual search for phones.
        if (!DeviceFormFactor.isTablet(context)) return true;

        if (ChromeVersionInfo.isLocalBuild()) return true;

        return getBooleanParam(ENABLED_PARAM);
    }

    /**
     * @return Whether the promo should be triggered by a limited number of taps.
     */
    public static boolean isPromoLimitedByTapCounts() {
        return getBooleanParam(PROMO_ON_LIMITED_TAPS);
    }

    /**
     * @return The maximum number of times the promo can be triggered by a tap, or
     * {@code ContextualSearchUma#PROMO_TAPS_REMAINING_INVALID} if no value is present in the finch
     * configuration.
     */
    static int getPromoTapTriggeredLimit() {
        return getIntParamValueOrDefault(TAP_TRIGGERED_PROMO_LIMIT, UNLIMITED_TAPS);
    }

    /**
     * @return Whether Search Term Resolution in response to a Tap gesture is limited for decided
     *         users.
     */
    static boolean isTapResolveLimitedForDecided() {
        return getTapResolveLimitForDecided() != ContextualSearchFieldTrial.UNLIMITED_TAPS;
    }

    /**
     * @return Whether prefetch in response to a Tap gesture is limited for decided users.
     */
    static boolean isTapPrefetchLimitedForDecided() {
        return getTapPrefetchLimitForDecided() != ContextualSearchFieldTrial.UNLIMITED_TAPS;
    }

    /**
     * @return Whether Search Term Resolution in response to a Tap gesture is limited for undecided
     *         users.
     */
    static boolean isTapResolveLimitedForUndecided() {
        return getTapResolveLimitForUndecided() != ContextualSearchFieldTrial.UNLIMITED_TAPS;
    }

    /**
     * @return Whether prefetch in response to a Tap gesture is limited for undecided users.
     */
    static boolean isTapPrefetchLimitedForUndecided() {
        return getTapPrefetchLimitForUndecided() != ContextualSearchFieldTrial.UNLIMITED_TAPS;
    }
    /**
     * @return The limit on the number of taps to resolve for decided users, or the default if no
     *         value is present in the Finch configuration.
     */
    static int getTapResolveLimitForDecided() {
        return getIntParamValueOrDefault(TAP_RESOLVE_LIMIT_FOR_DECIDED,
                DEFAULT_TAP_RESOLVE_LIMIT_FOR_DECIDED);
    }

    /**
     * @return The limit on the number of prefetches to issue for decided users, or the default
     *         if no value is present.
     */
    static int getTapPrefetchLimitForDecided() {
        return getIntParamValueOrDefault(TAP_PREFETCH_LIMIT_FOR_DECIDED,
                DEFAULT_TAP_PREFETCH_LIMIT_FOR_DECIDED);
    }

    /**
     * @return The limit on the number of taps to resolve for undecided users, or the default if no
     *         value is present in the Finch configuration.
     */
    static int getTapResolveLimitForUndecided() {
        return getIntParamValueOrDefault(TAP_RESOLVE_LIMIT_FOR_UNDECIDED,
                DEFAULT_TAP_RESOLVE_LIMIT_FOR_UNDECIDED);
    }

    /**
     * @return The limit on the number of prefetches to issue for undecided users, or the default
     *         if no value is present.
     */
    static int getTapPrefetchLimitForUndecided() {
        return getIntParamValueOrDefault(TAP_PREFETCH_LIMIT_FOR_UNDECIDED,
                DEFAULT_TAP_PREFETCH_LIMIT_FOR_UNDECIDED);
    }

    /**
     * @return Whether the Peek Promo is forcibly enabled (used for testing).
     */
    static boolean isPeekPromoForced() {
        return CommandLine.getInstance().hasSwitch(PEEK_PROMO_FORCED);
    }

    /**
     * @return Whether the Peek Promo is enabled.
     */
    static boolean isPeekPromoEnabled() {
        if (sIsPeekPromoEnabled == null) {
            sIsPeekPromoEnabled = getBooleanParam(PEEK_PROMO_ENABLED);
        }
        return sIsPeekPromoEnabled.booleanValue();
    }

    /**
     * @return Whether extra search bar animations are disabled.
     */
    static boolean areExtraSearchBarAnimationsDisabled() {
        return getBooleanParam(DISABLE_EXTRA_SEARCH_BAR_ANIMATIONS);
    }

    /**
     * @return The maximum number of times the Peek Promo should be displayed.
     */
    static int getPeekPromoMaxShowCount() {
        if (sPeekPromoMaxCount == null) {
            sPeekPromoMaxCount = getIntParamValueOrDefault(
                    PEEK_PROMO_MAX_SHOW_COUNT,
                    PEEK_PROMO_DEFAULT_MAX_SHOW_COUNT);
        }
        return sPeekPromoMaxCount.intValue();
    }

    // --------------------------------------------------------------------------------------------
    // Helpers.
    // --------------------------------------------------------------------------------------------

    /**
     * Gets a boolean Finch parameter, assuming the <paramName>="true" format.  Also checks for a
     * command-line switch with the same name, for easy local testing.
     * @param paramName The name of the Finch parameter (or command-line switch) to get a value for.
     * @return Whether the Finch param is defined with a value "true", if there's a command-line
     *         flag present with any value.
     */
    private static boolean getBooleanParam(String paramName) {
        if (CommandLine.getInstance().hasSwitch(paramName)) {
            return true;
        }
        return TextUtils.equals(ENABLED_VALUE,
                VariationsAssociatedData.getVariationParamValue(FIELD_TRIAL_NAME, paramName));
    }

    /**
     * Returns an integer value for a Finch parameter, or the default value if no parameter exists
     * in the current configuration.  Also checks for a command-line switch with the same name.
     * @param paramName The name of the Finch parameter (or command-line switch) to get a value for.
     * @param defaultValue The default value to return when there's no param or switch.
     * @return An integer value -- either the param or the default.
     */
    private static int getIntParamValueOrDefault(String paramName, int defaultValue) {
        String value = CommandLine.getInstance().getSwitchValue(paramName);
        if (TextUtils.isEmpty(value)) {
            value = VariationsAssociatedData.getVariationParamValue(FIELD_TRIAL_NAME, paramName);
        }
        if (!TextUtils.isEmpty(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }
}
