// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.text.TextUtils;

import org.chromium.base.CommandLine;
import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.components.variations.VariationsAssociatedData;

/**
 * Provides Field Trial support for the Contextual Search application within Chrome for Android.
 */
public class ContextualSearchFieldTrial {
    private static final String FIELD_TRIAL_NAME = "ContextualSearch";
    private static final String DISABLED_PARAM = "disabled";
    private static final String ENABLED_VALUE = "true";

    static final String MANDATORY_PROMO_ENABLED = "mandatory_promo_enabled";
    static final String MANDATORY_PROMO_LIMIT = "mandatory_promo_limit";
    static final int MANDATORY_PROMO_DEFAULT_LIMIT = 10;

    private static final String PEEK_PROMO_FORCED = "peek_promo_forced";
    @VisibleForTesting
    static final String PEEK_PROMO_ENABLED = "peek_promo_enabled";
    private static final String PEEK_PROMO_MAX_SHOW_COUNT = "peek_promo_max_show_count";
    private static final int PEEK_PROMO_DEFAULT_MAX_SHOW_COUNT = 10;

    private static final String DISABLE_SEARCH_TERM_RESOLUTION = "disable_search_term_resolution";
    private static final String ENABLE_BLACKLIST = "enable_blacklist";

    // Translation.  All these members are private, except for usage by testing.
    // Master switch, needed to enable any translate code for Contextual Search.
    @VisibleForTesting
    static final String ENABLE_TRANSLATION = "enable_translation";
    // Switch to disable translation, but not logging, used for experiment comparison.
    @VisibleForTesting
    static final String DISABLE_FORCE_TRANSLATION_ONEBOX = "disable_force_translation_onebox";
    // Disables translation when we need to auto-detect the source language (when we don't resolve).
    @VisibleForTesting
    static final String DISABLE_AUTO_DETECT_TRANSLATION_ONEBOX =
            "disable_auto_detect_translation_onebox";
    // Disables using the keyboard languages to determine the target language.
    private static final String DISABLE_KEYBOARD_LANGUAGES_FOR_TRANSLATION =
            "disable_keyboard_languages_for_translation";
    // Disables using the accept-languages list to determine the target language.
    private static final String DISABLE_ACCEPT_LANGUAGES_FOR_TRANSLATION =
            "disable_accept_languages_for_translation";
    // Enables usage of English as the target language even when it's the primary UI language.
    private static final String ENABLE_ENGLISH_TARGET_TRANSLATION =
            "enable_english_target_translation";
    // Enables relying on the server to control whether the onebox is actually shown, rather
    // than checking if translation is needed client-side based on source/target languages.
    @VisibleForTesting
    static final String ENABLE_SERVER_CONTROLLED_ONEBOX = "enable_server_controlled_onebox";

    /** Hide Contextual Cards data.*/
    private static final String HIDE_CONTEXTUAL_CARDS_DATA = "hide_contextual_cards_data";

    // Quick Answers.
    private static final String ENABLE_QUICK_ANSWERS = "enable_quick_answers";

    // Tap triggering suppression.
    static final String SUPPRESSION_TAPS = "suppression_taps";
    // Enables collection of recent scroll seen/unseen histograms.
    // TODO(donnd): remove all supporting code once short-lived data collection is done.
    private static final String ENABLE_RECENT_SCROLL_COLLECTION = "enable_recent_scroll_collection";
    // Set non-zero to establish an recent scroll suppression threshold for taps.
    private static final String RECENT_SCROLL_DURATION_MS = "recent_scroll_duration_ms";
    // TODO(donnd): remove all supporting code once short-lived data collection is done.
    private static final String SCREEN_TOP_SUPPRESSION_DPS = "screen_top_suppression_dps";
    private static final String ENABLE_BAR_OVERLAP_COLLECTION = "enable_bar_overlap_collection";
    private static final String BAR_OVERLAP_SUPPRESSION_ENABLED = "enable_bar_overlap_suppression";

    // Safety switch for disabling online-detection.  Also used to disable detection when running
    // tests.
    @VisibleForTesting
    static final String ONLINE_DETECTION_DISABLED = "disable_online_detection";

    private static final String ENABLE_AMP_AS_SEPARATE_TAB = "enable_amp_as_separate_tab";

    // Cached values to avoid repeated and redundant JNI operations.
    private static Boolean sEnabled;
    private static Boolean sDisableSearchTermResolution;
    private static Boolean sIsMandatoryPromoEnabled;
    private static Integer sMandatoryPromoLimit;
    private static Boolean sIsPeekPromoEnabled;
    private static Integer sPeekPromoMaxCount;
    private static Boolean sIsTranslationEnabled;
    private static Boolean sIsForceTranslationOneboxDisabled;
    private static Boolean sIsAutoDetectTranslationOneboxDisabled;
    private static Boolean sIsAcceptLanguagesForTranslationDisabled;
    private static Boolean sIsKeyboardLanguagesForTranslationDisabled;
    private static Boolean sIsEnglishTargetTranslationEnabled;
    private static Boolean sIsServerControlledOneboxEnabled;
    private static Boolean sIsQuickAnswersEnabled;
    private static Boolean sIsRecentScrollCollectionEnabled;
    private static Integer sRecentScrollDurationMs;
    private static Integer sScreenTopSuppressionDps;
    private static Boolean sIsBarOverlapCollectionEnabled;
    private static Boolean sIsBarOverlapSuppressionEnabled;
    private static Integer sSuppressionTaps;
    private static Boolean sShouldHideContextualCardsData;
    private static Boolean sIsContextualCardsBarIntegrationEnabled;
    private static Boolean sIsOnlineDetectionDisabled;
    private static Boolean sIsAmpAsSeparateTabEnabled;
    private static Boolean sContextualSearchSingleActionsEnabled;

    /**
     * Don't instantiate.
     */
    private ContextualSearchFieldTrial() {}

    /**
     * Checks the current Variations parameters associated with the active group as well as the
     * Chrome preference to determine if the service is enabled.
     * @return Whether Contextual Search is enabled or not.
     */
    public static boolean isEnabled() {
        if (sEnabled == null) {
            sEnabled = detectEnabled();
        }
        return sEnabled.booleanValue();
    }

    private static boolean detectEnabled() {
        if (SysUtils.isLowEndDevice()) {
            return false;
        }

        // Allow this user-flippable flag to disable the feature.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_CONTEXTUAL_SEARCH)) {
            return false;
        }

        // Allow this user-flippable flag to enable the feature.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CONTEXTUAL_SEARCH)) {
            return true;
        }

        // Allow disabling the feature remotely.
        if (getBooleanParam(DISABLED_PARAM)) {
            return false;
        }

        return true;
    }

    /**
     * @return Whether the search term resolution is enabled.
     */
    static boolean isSearchTermResolutionEnabled() {
        if (sDisableSearchTermResolution == null) {
            sDisableSearchTermResolution = getBooleanParam(DISABLE_SEARCH_TERM_RESOLUTION);
        }

        if (sDisableSearchTermResolution.booleanValue()) {
            return false;
        }

        return true;
    }

    /**
     * @return Whether the Mandatory Promo is enabled.
     */
    static boolean isMandatoryPromoEnabled() {
        if (sIsMandatoryPromoEnabled == null) {
            sIsMandatoryPromoEnabled = getBooleanParam(MANDATORY_PROMO_ENABLED);
        }
        return sIsMandatoryPromoEnabled.booleanValue();
    }

    /**
     * @return The number of times the Promo should be seen before it becomes mandatory.
     */
    static int getMandatoryPromoLimit() {
        if (sMandatoryPromoLimit == null) {
            sMandatoryPromoLimit = getIntParamValueOrDefault(
                    MANDATORY_PROMO_LIMIT,
                    MANDATORY_PROMO_DEFAULT_LIMIT);
        }
        return sMandatoryPromoLimit.intValue();
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
     * @return Whether the blacklist is enabled.
     */
    static boolean isBlacklistEnabled() {
        return getBooleanParam(ENABLE_BLACKLIST);
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

    /**
     * @return Whether any translate code is enabled.
     */
    static boolean isTranslationEnabled() {
        if (sIsTranslationEnabled == null) {
            sIsTranslationEnabled = getBooleanParam(ENABLE_TRANSLATION);
        }
        return sIsTranslationEnabled.booleanValue();
    }

    /**
     * @return Whether forcing a translation Onebox is disabled.
     */
    static boolean isForceTranslationOneboxDisabled() {
        if (sIsForceTranslationOneboxDisabled == null) {
            sIsForceTranslationOneboxDisabled = getBooleanParam(DISABLE_FORCE_TRANSLATION_ONEBOX);
        }
        return sIsForceTranslationOneboxDisabled.booleanValue();
    }

    /**
     * @return Whether forcing a translation Onebox based on auto-detection of the source language
     *         is disabled.
     */
    static boolean isAutoDetectTranslationOneboxDisabled() {
        if (sIsAutoDetectTranslationOneboxDisabled == null) {
            sIsAutoDetectTranslationOneboxDisabled = getBooleanParam(
                    DISABLE_AUTO_DETECT_TRANSLATION_ONEBOX);
        }
        return sIsAutoDetectTranslationOneboxDisabled.booleanValue();
    }

    /**
     * @return Whether considering accept-languages for translation is disabled.
     */
    static boolean isAcceptLanguagesForTranslationDisabled() {
        if (sIsAcceptLanguagesForTranslationDisabled == null) {
            sIsAcceptLanguagesForTranslationDisabled = getBooleanParam(
                    DISABLE_ACCEPT_LANGUAGES_FOR_TRANSLATION);
        }
        return sIsAcceptLanguagesForTranslationDisabled.booleanValue();
    }

    /**
     * @return Whether considering keyboards for translation is disabled.
     */
    static boolean isKeyboardLanguagesForTranslationDisabled() {
        if (sIsKeyboardLanguagesForTranslationDisabled == null) {
            sIsKeyboardLanguagesForTranslationDisabled =
                    getBooleanParam(DISABLE_KEYBOARD_LANGUAGES_FOR_TRANSLATION);
        }
        return sIsKeyboardLanguagesForTranslationDisabled.booleanValue();
    }

    /**
     * @return Whether English-target translation should be enabled (default is disabled for 'en').
     */
    static boolean isEnglishTargetTranslationEnabled() {
        if (sIsEnglishTargetTranslationEnabled == null) {
            sIsEnglishTargetTranslationEnabled = getBooleanParam(ENABLE_ENGLISH_TARGET_TRANSLATION);
        }
        return sIsEnglishTargetTranslationEnabled.booleanValue();
    }

    /**
     * @return Whether relying on server-control of showing the translation one-box is enabled.
     */
    static boolean isServerControlledOneboxEnabled() {
        if (sIsServerControlledOneboxEnabled == null) {
            sIsServerControlledOneboxEnabled = getBooleanParam(ENABLE_SERVER_CONTROLLED_ONEBOX);
        }
        return sIsServerControlledOneboxEnabled.booleanValue();
    }

    /**
     * @return Whether showing "quick answers" in the Bar is enabled.
     */
    static boolean isQuickAnswersEnabled() {
        if (sIsQuickAnswersEnabled == null) {
            sIsQuickAnswersEnabled = getBooleanParam(ENABLE_QUICK_ANSWERS);
        }
        return sIsQuickAnswersEnabled.booleanValue();
    }

    /**
     * @return Whether collecting metrics for tap triggering after a scroll is enabled.
     */
    static boolean isRecentScrollCollectionEnabled() {
        if (sIsRecentScrollCollectionEnabled == null) {
            sIsRecentScrollCollectionEnabled = getBooleanParam(ENABLE_RECENT_SCROLL_COLLECTION);
        }
        return sIsRecentScrollCollectionEnabled.booleanValue();
    }

    /**
     * Gets the duration to use for suppressing Taps after a recent scroll, or {@code 0} if no
     * suppression is configured.
     * @return The period of time after a scroll when tap triggering is suppressed.
     */
    static int getRecentScrollSuppressionDurationMs() {
        if (sRecentScrollDurationMs == null) {
            sRecentScrollDurationMs = getIntParamValueOrDefault(RECENT_SCROLL_DURATION_MS, 0);
        }
        return sRecentScrollDurationMs.intValue();
    }

    /**
     * Gets a Y value limit that will suppress a Tap near the top of the screen.
     * Any Y value less than the limit will suppress the Tap trigger.
     * @return The Y value triggering limit in DPs, a value of zero will not limit.
     */
    static int getScreenTopSuppressionDps() {
        if (sScreenTopSuppressionDps == null) {
            sScreenTopSuppressionDps = getIntParamValueOrDefault(SCREEN_TOP_SUPPRESSION_DPS, 0);
        }
        return sScreenTopSuppressionDps.intValue();
    }

    /**
     * @return Whether collecting data on Bar overlap is enabled.
     */
    static boolean isBarOverlapCollectionEnabled() {
        if (sIsBarOverlapCollectionEnabled == null) {
            sIsBarOverlapCollectionEnabled = getBooleanParam(ENABLE_BAR_OVERLAP_COLLECTION);
        }
        return sIsBarOverlapCollectionEnabled.booleanValue();
    }

    /**
     * @return Whether triggering is suppressed by a selection nearly overlapping the normal
     *         Bar peeking location.
     */
    static boolean isBarOverlapSuppressionEnabled() {
        if (sIsBarOverlapSuppressionEnabled == null) {
            sIsBarOverlapSuppressionEnabled = getBooleanParam(BAR_OVERLAP_SUPPRESSION_ENABLED);
        }
        return sIsBarOverlapSuppressionEnabled.booleanValue();
    }

    /**
     * @return Whether triggering by Tap is suppressed (through a combination of various signals).
     */
    static boolean isTapSuppressionEnabled() {
        return getSuppressionTaps() > 0;
    }

    /**
     * @return The suppression threshold, expressed as the number of Taps since the last open where
     *         we start suppressing the UX on Tap.
     */
    static int getSuppressionTaps() {
        if (sSuppressionTaps == null) {
            sSuppressionTaps = getIntParamValueOrDefault(SUPPRESSION_TAPS, 0);
        }
        return sSuppressionTaps.intValue();
    }

    /**
     * @return Whether to auto-promote clicks in the AMP carousel into a separate Tab.
     */
    static boolean isAmpAsSeparateTabEnabled() {
        if (sIsAmpAsSeparateTabEnabled == null) {
            sIsAmpAsSeparateTabEnabled = getBooleanParam(ENABLE_AMP_AS_SEPARATE_TAB);
        }
        return sIsAmpAsSeparateTabEnabled;
    }

    // TODO(donnd): Remove once bar-integration is fully landed if still unused (native only).
    static boolean isContextualCardsBarIntegrationEnabled() {
        if (sIsContextualCardsBarIntegrationEnabled == null) {
            sIsContextualCardsBarIntegrationEnabled = getBooleanParam(
                    ChromeSwitches.CONTEXTUAL_SEARCH_CONTEXTUAL_CARDS_BAR_INTEGRATION);
        }
        return sIsContextualCardsBarIntegrationEnabled;
    }

    static boolean shouldHideContextualCardsData() {
        if (sShouldHideContextualCardsData == null) {
            sShouldHideContextualCardsData = getBooleanParam(HIDE_CONTEXTUAL_CARDS_DATA);
        }
        return sShouldHideContextualCardsData;
    }

    /**
     * @return Whether detection of device-online should be disabled (default false).
     */
    static boolean isOnlineDetectionDisabled() {
        // TODO(donnd): Convert to test-only after launch and we have confidence it's robust.
        if (sIsOnlineDetectionDisabled == null) {
            sIsOnlineDetectionDisabled = getBooleanParam(ONLINE_DETECTION_DISABLED);
        }
        return sIsOnlineDetectionDisabled;
    }

    // ---------------
    // Features.
    // ---------------

    /**
     * @return Whether or not single actions based on Contextual Cards is enabled.
     */
    static boolean isContextualSearchSingleActionsEnabled() {
        if (sContextualSearchSingleActionsEnabled == null) {
            sContextualSearchSingleActionsEnabled =
                    ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SEARCH_SINGLE_ACTIONS);
        }

        return sContextualSearchSingleActionsEnabled;
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
