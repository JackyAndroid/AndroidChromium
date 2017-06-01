// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.components.variations.VariationsAssociatedData;

/**
 * Provides easy access to data for field trials to do with the Cards UI.
 */
public final class CardsVariationParameters {
    // Tags are limited to 20 characters.
    private static final String TAG = "CardsVariationParams";

    // Also defined in ntp_snippets_constants.cc
    private static final String FIELD_TRIAL_NAME = "NTPSnippets";

    private static final String PARAM_FAVICON_SERVICE_NAME = "favicons_fetch_from_service";
    private static final String PARAM_FIRST_CARD_OFFSET = "first_card_offset";
    private static final String PARAM_FIRST_CARD_ANIMATION_MAX_RUNS =
            "first_card_animation_max_runs";
    private static final String PARAM_SCROLL_BELOW_THE_FOLD = "scroll_below_the_fold";

    private static final String PARAM_DISABLED_VALUE = "off";

    private static final int FIRST_CARD_ANIMATION_DEFAULT_VALUE = 7;

    private CardsVariationParameters() {}

    /**
     * Provides the value of the field trial to offset the peeking card (can be overridden
     * with a command line flag). It will return 0 if there is no such field trial.
     */
    public static int getFirstCardOffsetDp() {
        return getIntValue(PARAM_FIRST_CARD_OFFSET, 0);
    }

    /**
     * Gets the number of times the first card peeking animation should run.
     */
    public static int getFirstCardAnimationMaxRuns() {
        return getIntValue(PARAM_FIRST_CARD_ANIMATION_MAX_RUNS, FIRST_CARD_ANIMATION_DEFAULT_VALUE);
    }

    /**
     * @return Whether the NTP should initially be scrolled below the fold.
     */
    public static boolean isScrollBelowTheFoldEnabled() {
        return Boolean.parseBoolean(VariationsAssociatedData.getVariationParamValue(
                FIELD_TRIAL_NAME, PARAM_SCROLL_BELOW_THE_FOLD));
    }

    public static boolean isFaviconServiceEnabled() {
        return !PARAM_DISABLED_VALUE.equals(VariationsAssociatedData.getVariationParamValue(
                FIELD_TRIAL_NAME, PARAM_FAVICON_SERVICE_NAME));
    }

    private static int getIntValue(String paramName, int defaultValue) {
        // TODO(jkrcal): Get parameter by feature name, not field trial name.
        String value = VariationsAssociatedData.getVariationParamValue(
                FIELD_TRIAL_NAME, paramName);

        if (!TextUtils.isEmpty(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                Log.w(TAG, "Cannot parse %s experiment value, %s.", paramName, value);
            }
        }

        return defaultValue;
    }
}
