// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.util.Pair;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.PanelState;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Centralizes UMA data collection for Contextual Search. All calls must be made from the UI thread.
 */
public class ContextualSearchUma {
    // Constants to use for the original selection gesture
    private static final boolean LONG_PRESS = false;
    private static final boolean TAP = true;

    // Constants used to log UMA "enum" histograms about the Contextual Search's preference state.
    private static final int PREFERENCE_UNINITIALIZED = 0;
    private static final int PREFERENCE_ENABLED = 1;
    private static final int PREFERENCE_DISABLED = 2;
    private static final int PREFERENCE_HISTOGRAM_BOUNDARY = 3;

    // Constants used to log UMA "enum" histograms about whether search results were seen.
    private static final int RESULTS_SEEN = 0;
    private static final int RESULTS_NOT_SEEN = 1;
    private static final int RESULTS_SEEN_BOUNDARY = 2;

    // Constants used to log UMA "enum" histograms about whether the selection is valid.
    private static final int SELECTION_VALID = 0;
    private static final int SELECTION_INVALID = 1;
    private static final int SELECTION_BOUNDARY = 2;

    // Constants used to log UMA "enum" histograms about a request's outcome.
    private static final int REQUEST_NOT_FAILED = 0;
    private static final int REQUEST_FAILED = 1;
    private static final int REQUEST_BOUNDARY = 2;

    // Constants used to log UMA "enum" histograms about the panel's state transitions.
    // Entry code: first entry into CLOSED.
    private static final int ENTER_CLOSED_FROM_OTHER = 0;
    private static final int ENTER_CLOSED_FROM_PEEKED_BACK_PRESS = 1;
    private static final int ENTER_CLOSED_FROM_PEEKED_BASE_PAGE_SCROLL = 2;
    private static final int ENTER_CLOSED_FROM_PEEKED_TEXT_SELECT_TAP = 3;
    private static final int ENTER_CLOSED_FROM_EXPANDED_BACK_PRESS = 4;
    private static final int ENTER_CLOSED_FROM_EXPANDED_BASE_PAGE_TAP = 5;
    private static final int ENTER_CLOSED_FROM_EXPANDED_FLING = 6;
    private static final int ENTER_CLOSED_FROM_MAXIMIZED_BACK_PRESS = 7;
    private static final int ENTER_CLOSED_FROM_MAXIMIZED_FLING = 8;
    private static final int ENTER_CLOSED_FROM_MAXIMIZED_TAB_PROMOTION = 9;
    private static final int ENTER_CLOSED_FROM_MAXIMIZED_SERP_NAVIGATION = 10;
    private static final int ENTER_CLOSED_FROM_BOUNDARY = 11;

    // Entry code: first entry into PEEKED.
    private static final int ENTER_PEEKED_FROM_OTHER = 0;
    private static final int ENTER_PEEKED_FROM_CLOSED_TEXT_SELECT_TAP = 1;
    private static final int ENTER_PEEKED_FROM_CLOSED_EXT_SELECT_LONG_PRESS = 2;
    private static final int ENTER_PEEKED_FROM_PEEKED_TEXT_SELECT_TAP = 3;
    private static final int ENTER_PEEKED_FROM_PEEKED_TEXT_SELECT_LONG_PRESS = 4;
    private static final int ENTER_PEEKED_FROM_EXPANDED_SEARCH_BAR_TAP = 5;
    private static final int ENTER_PEEKED_FROM_EXPANDED_SWIPE = 6;
    private static final int ENTER_PEEKED_FROM_EXPANDED_FLING = 7;
    private static final int ENTER_PEEKED_FROM_MAXIMIZED_SWIPE = 8;
    private static final int ENTER_PEEKED_FROM_MAXIMIZED_FLING = 9;
    private static final int ENTER_PEEKED_FROM_BOUNDARY = 10;

    // Entry code: first entry into EXPANDED.
    private static final int ENTER_EXPANDED_FROM_OTHER = 0;
    private static final int ENTER_EXPANDED_FROM_PEEKED_SEARCH_BAR_TAP = 1;
    private static final int ENTER_EXPANDED_FROM_PEEKED_SWIPE = 2;
    private static final int ENTER_EXPANDED_FROM_PEEKED_FLING = 3;
    private static final int ENTER_EXPANDED_FROM_MAXIMIZED_SWIPE = 4;
    private static final int ENTER_EXPANDED_FROM_MAXIMIZED_FLING = 5;
    private static final int ENTER_EXPANDED_FROM_BOUNDARY = 6;

    // Entry code: first entry into MAXIMIZED.
    private static final int ENTER_MAXIMIZED_FROM_OTHER = 0;
    private static final int ENTER_MAXIMIZED_FROM_PEEKED_SWIPE = 1;
    private static final int ENTER_MAXIMIZED_FROM_PEEKED_FLING = 2;
    private static final int ENTER_MAXIMIZED_FROM_EXPANDED_SWIPE = 3;
    private static final int ENTER_MAXIMIZED_FROM_EXPANDED_FLING = 4;
    private static final int ENTER_MAXIMIZED_FROM_EXPANDED_SERP_NAVIGATION = 5;
    private static final int ENTER_MAXIMIZED_FROM_BOUNDARY = 6;

    // Exit code: first exit from CLOSED (or UNDEFINED).
    private static final int EXIT_CLOSED_TO_OTHER = 0;
    private static final int EXIT_CLOSED_TO_PEEKED_TEXT_SELECT_TAP = 1;
    private static final int EXIT_CLOSED_TO_PEEKED_TEXT_SELECT_LONG_PRESS = 2;
    private static final int EXIT_CLOSED_TO_BOUNDARY = 3;

    // Exit code: first exit from PEEKED.
    private static final int EXIT_PEEKED_TO_OTHER = 0;
    private static final int EXIT_PEEKED_TO_CLOSED_BACK_PRESS = 1;
    private static final int EXIT_PEEKED_TO_CLOSED_BASE_PAGE_SCROLL = 2;
    private static final int EXIT_PEEKED_TO_CLOSED_TEXT_SELECT_TAP = 3;
    private static final int EXIT_PEEKED_TO_PEEKED_TEXT_SELECT_TAP = 4;
    private static final int EXIT_PEEKED_TO_PEEKED_TEXT_SELECT_LONG_PRESS = 5;
    private static final int EXIT_PEEKED_TO_EXPANDED_SEARCH_BAR_TAP = 6;
    private static final int EXIT_PEEKED_TO_EXPANDED_SWIPE = 7;
    private static final int EXIT_PEEKED_TO_EXPANDED_FLING = 8;
    private static final int EXIT_PEEKED_TO_MAXIMIZED_SWIPE = 9;
    private static final int EXIT_PEEKED_TO_MAXIMIZED_FLING = 10;
    private static final int EXIT_PEEKED_TO_BOUNDARY = 11;

    // Exit code: first exit from EXPANDED.
    private static final int EXIT_EXPANDED_TO_OTHER = 0;
    private static final int EXIT_EXPANDED_TO_CLOSED_BACK_PRESS = 1;
    private static final int EXIT_EXPANDED_TO_CLOSED_BASE_PAGE_TAP = 2;
    private static final int EXIT_EXPANDED_TO_CLOSED_FLING = 3;
    private static final int EXIT_EXPANDED_TO_PEEKED_SEARCH_BAR_TAP = 4;
    private static final int EXIT_EXPANDED_TO_PEEKED_SWIPE = 5;
    private static final int EXIT_EXPANDED_TO_PEEKED_FLING = 6;
    private static final int EXIT_EXPANDED_TO_MAXIMIZED_SWIPE = 7;
    private static final int EXIT_EXPANDED_TO_MAXIMIZED_FLING = 8;
    private static final int EXIT_EXPANDED_TO_MAXIMIZED_SERP_NAVIGATION = 9;
    private static final int EXIT_EXPANDED_TO_BOUNDARY = 10;

    // Exit code: first exit from MAXIMIZED.
    private static final int EXIT_MAXIMIZED_TO_OTHER = 0;
    private static final int EXIT_MAXIMIZED_TO_CLOSED_BACK_PRESS = 1;
    private static final int EXIT_MAXIMIZED_TO_CLOSED_FLING = 2;
    private static final int EXIT_MAXIMIZED_TO_CLOSED_TAB_PROMOTION = 3;
    private static final int EXIT_MAXIMIZED_TO_CLOSED_SERP_NAVIGATION = 4;
    private static final int EXIT_MAXIMIZED_TO_PEEKED_SWIPE = 5;
    private static final int EXIT_MAXIMIZED_TO_PEEKED_FLING = 6;
    private static final int EXIT_MAXIMIZED_TO_EXPANDED_SWIPE = 7;
    private static final int EXIT_MAXIMIZED_TO_EXPANDED_FLING = 8;
    private static final int EXIT_MAXIMIZED_TO_BOUNDARY = 9;

    // Constants used to log UMA "enum" histograms with details about whether search results
    // were seen, and what the original triggering gesture was.
    private static final int RESULTS_SEEN_FROM_TAP = 0;
    private static final int RESULTS_NOT_SEEN_FROM_TAP = 1;
    private static final int RESULTS_SEEN_FROM_LONG_PRESS = 2;
    private static final int RESULTS_NOT_SEEN_FROM_LONG_PRESS = 3;
    private static final int RESULTS_BY_GESTURE_BOUNDARY = 4;

    // Constants used to log UMA "enum" histograms with details about whether search results
    // were seen, and what the original triggering gesture was.
    private static final int PROMO_ENABLED_FROM_TAP = 0;
    private static final int PROMO_DISABLED_FROM_TAP = 1;
    private static final int PROMO_UNDECIDED_FROM_TAP = 2;
    private static final int PROMO_ENABLED_FROM_LONG_PRESS = 3;
    private static final int PROMO_DISABLED_FROM_LONG_PRESS = 4;
    private static final int PROMO_UNDECIDED_FROM_LONG_PRESS = 5;
    private static final int PROMO_BY_GESTURE_BOUNDARY = 6;

    // Constants used to log UMA "enum" histograms with summary counts for SERP loading times.
    private static final int PREFETCHED_PARIALLY_LOADED = 0;
    private static final int PREFETCHED_FULLY_LOADED = 1;
    private static final int NOT_PREFETCHED = 2;
    private static final int PREFETCH_BOUNDARY = 3;

    // Constants used to log UMA "enum" histograms for HTTP / HTTPS.
    private static final int PROTOCOL_IS_HTTP = 0;
    private static final int PROTOCOL_NOT_HTTP = 1;
    private static final int PROTOCOL_BOUNDARY = 2;

    // Constants used to log UMA "enum" histograms for single / multi-word.
    private static final int RESOLVED_SINGLE_WORD = 0;
    private static final int RESOLVED_MULTI_WORD = 1;
    private static final int RESOLVED_BOUNDARY = 2;

    // Constants used to log UMA "enum" histograms for paritally / fully loaded.
    private static final int PARTIALLY_LOADED = 0;
    private static final int FULLY_LOADED = 1;
    private static final int LOADED_BOUNDARY = 2;


    /**
     * Key used in maps from {state, reason} to state entry (exit) logging code.
     */
    static class StateChangeKey {
        final PanelState mState;
        final StateChangeReason mReason;
        final int mHashCode;

        StateChangeKey(PanelState state, StateChangeReason reason) {
            mState = state;
            mReason = reason;
            mHashCode = 31 * state.hashCode() + reason.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof StateChangeKey)) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            StateChangeKey other = (StateChangeKey) obj;
            return mState.equals(other.mState) && mReason.equals(other.mReason);
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }
    }

    // TODO(donnd): switch from using Maps to some method that does not require creation of a key.

    // Entry code map: first entry into CLOSED.
    private static final Map<StateChangeKey, Integer> ENTER_CLOSED_STATE_CHANGE_CODES;
    static {
        Map<StateChangeKey, Integer> codes = new HashMap<StateChangeKey, Integer>();
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.BACK_PRESS),
                ENTER_CLOSED_FROM_PEEKED_BACK_PRESS);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.BASE_PAGE_SCROLL),
                ENTER_CLOSED_FROM_PEEKED_BASE_PAGE_SCROLL);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.TEXT_SELECT_TAP),
                ENTER_CLOSED_FROM_PEEKED_TEXT_SELECT_TAP);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.BACK_PRESS),
                ENTER_CLOSED_FROM_EXPANDED_BACK_PRESS);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.BASE_PAGE_TAP),
                ENTER_CLOSED_FROM_EXPANDED_BASE_PAGE_TAP);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.FLING),
                ENTER_CLOSED_FROM_EXPANDED_FLING);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.BACK_PRESS),
                ENTER_CLOSED_FROM_MAXIMIZED_BACK_PRESS);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.FLING),
                ENTER_CLOSED_FROM_MAXIMIZED_FLING);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.TAB_PROMOTION),
                ENTER_CLOSED_FROM_MAXIMIZED_TAB_PROMOTION);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.SERP_NAVIGATION),
                ENTER_CLOSED_FROM_MAXIMIZED_SERP_NAVIGATION);
        ENTER_CLOSED_STATE_CHANGE_CODES = Collections.unmodifiableMap(codes);
    }

    // Entry code map: first entry into PEEKED.
    private static final Map<StateChangeKey, Integer> ENTER_PEEKED_STATE_CHANGE_CODES;
    static {
        Map<StateChangeKey, Integer> codes = new HashMap<StateChangeKey, Integer>();
        // Note: we don't distinguish entering PEEKED from UNDEFINED / CLOSED.
        codes.put(new StateChangeKey(PanelState.UNDEFINED, StateChangeReason.TEXT_SELECT_TAP),
                ENTER_PEEKED_FROM_CLOSED_TEXT_SELECT_TAP);
        codes.put(new StateChangeKey(PanelState.UNDEFINED,
                StateChangeReason.TEXT_SELECT_LONG_PRESS),
                ENTER_PEEKED_FROM_CLOSED_EXT_SELECT_LONG_PRESS);
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.TEXT_SELECT_TAP),
                ENTER_PEEKED_FROM_CLOSED_TEXT_SELECT_TAP);
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.TEXT_SELECT_LONG_PRESS),
                ENTER_PEEKED_FROM_CLOSED_EXT_SELECT_LONG_PRESS);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.TEXT_SELECT_TAP),
                ENTER_PEEKED_FROM_PEEKED_TEXT_SELECT_TAP);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.TEXT_SELECT_LONG_PRESS),
                ENTER_PEEKED_FROM_PEEKED_TEXT_SELECT_LONG_PRESS);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.SEARCH_BAR_TAP),
                ENTER_PEEKED_FROM_EXPANDED_SEARCH_BAR_TAP);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.SWIPE),
                ENTER_PEEKED_FROM_EXPANDED_SWIPE);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.FLING),
                ENTER_PEEKED_FROM_EXPANDED_FLING);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.SWIPE),
                ENTER_PEEKED_FROM_MAXIMIZED_SWIPE);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.FLING),
                ENTER_PEEKED_FROM_MAXIMIZED_FLING);
        ENTER_PEEKED_STATE_CHANGE_CODES = Collections.unmodifiableMap(codes);
    }

    // Entry code map: first entry into EXPANDED.
    private static final Map<StateChangeKey, Integer> ENTER_EXPANDED_STATE_CHANGE_CODES;
    static {
        Map<StateChangeKey, Integer> codes = new HashMap<StateChangeKey, Integer>();
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.SEARCH_BAR_TAP),
                ENTER_EXPANDED_FROM_PEEKED_SEARCH_BAR_TAP);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.SWIPE),
                ENTER_EXPANDED_FROM_PEEKED_SWIPE);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.FLING),
                ENTER_EXPANDED_FROM_PEEKED_FLING);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.SWIPE),
                ENTER_EXPANDED_FROM_MAXIMIZED_SWIPE);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.FLING),
                ENTER_EXPANDED_FROM_MAXIMIZED_FLING);
        ENTER_EXPANDED_STATE_CHANGE_CODES = Collections.unmodifiableMap(codes);
    }

    // Entry code map: first entry into MAXIMIZED.
    private static final Map<StateChangeKey, Integer> ENTER_MAXIMIZED_STATE_CHANGE_CODES;
    static {
        Map<StateChangeKey, Integer> codes = new HashMap<StateChangeKey, Integer>();
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.SWIPE),
                ENTER_MAXIMIZED_FROM_PEEKED_SWIPE);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.FLING),
                ENTER_MAXIMIZED_FROM_PEEKED_FLING);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.SWIPE),
                ENTER_MAXIMIZED_FROM_EXPANDED_SWIPE);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.FLING),
                ENTER_MAXIMIZED_FROM_EXPANDED_FLING);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.SERP_NAVIGATION),
                ENTER_MAXIMIZED_FROM_EXPANDED_SERP_NAVIGATION);
        ENTER_MAXIMIZED_STATE_CHANGE_CODES = Collections.unmodifiableMap(codes);
    }

    // Exit code map: first exit from CLOSED.
    private static final Map<StateChangeKey, Integer> EXIT_CLOSED_TO_STATE_CHANGE_CODES;
    static {
        Map<StateChangeKey, Integer> codes = new HashMap<StateChangeKey, Integer>();
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.TEXT_SELECT_TAP),
                EXIT_CLOSED_TO_PEEKED_TEXT_SELECT_TAP);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.TEXT_SELECT_LONG_PRESS),
                EXIT_CLOSED_TO_PEEKED_TEXT_SELECT_LONG_PRESS);
        EXIT_CLOSED_TO_STATE_CHANGE_CODES = Collections.unmodifiableMap(codes);
    }

    // Exit code map: first exit from PEEKED.
    private static final Map<StateChangeKey, Integer> EXIT_PEEKED_TO_STATE_CHANGE_CODES;
    static {
        Map<StateChangeKey, Integer> codes = new HashMap<StateChangeKey, Integer>();
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.BACK_PRESS),
                EXIT_PEEKED_TO_CLOSED_BACK_PRESS);
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.BASE_PAGE_SCROLL),
                EXIT_PEEKED_TO_CLOSED_BASE_PAGE_SCROLL);
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.BASE_PAGE_TAP),
                EXIT_PEEKED_TO_CLOSED_TEXT_SELECT_TAP);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.TEXT_SELECT_TAP),
                EXIT_PEEKED_TO_PEEKED_TEXT_SELECT_TAP);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.TEXT_SELECT_LONG_PRESS),
                EXIT_PEEKED_TO_PEEKED_TEXT_SELECT_LONG_PRESS);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.SEARCH_BAR_TAP),
                EXIT_PEEKED_TO_EXPANDED_SEARCH_BAR_TAP);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.SWIPE),
                EXIT_PEEKED_TO_EXPANDED_SWIPE);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.FLING),
                EXIT_PEEKED_TO_EXPANDED_FLING);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.SWIPE),
                EXIT_PEEKED_TO_MAXIMIZED_SWIPE);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.FLING),
                EXIT_PEEKED_TO_MAXIMIZED_FLING);
        EXIT_PEEKED_TO_STATE_CHANGE_CODES = Collections.unmodifiableMap(codes);
    }

    // Exit code map: first exit from EXPANDED.
    private static final Map<StateChangeKey, Integer> EXIT_EXPANDED_TO_STATE_CHANGE_CODES;
    static {
        Map<StateChangeKey, Integer> codes = new HashMap<StateChangeKey, Integer>();
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.BACK_PRESS),
                EXIT_EXPANDED_TO_CLOSED_BACK_PRESS);
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.BASE_PAGE_TAP),
                EXIT_EXPANDED_TO_CLOSED_BASE_PAGE_TAP);
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.FLING),
                EXIT_EXPANDED_TO_CLOSED_FLING);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.SEARCH_BAR_TAP),
                EXIT_EXPANDED_TO_PEEKED_SEARCH_BAR_TAP);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.SWIPE),
                EXIT_EXPANDED_TO_PEEKED_SWIPE);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.FLING),
                EXIT_EXPANDED_TO_PEEKED_FLING);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.SWIPE),
                EXIT_EXPANDED_TO_MAXIMIZED_SWIPE);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.FLING),
                EXIT_EXPANDED_TO_MAXIMIZED_FLING);
        codes.put(new StateChangeKey(PanelState.MAXIMIZED, StateChangeReason.SERP_NAVIGATION),
                EXIT_EXPANDED_TO_MAXIMIZED_SERP_NAVIGATION);
        EXIT_EXPANDED_TO_STATE_CHANGE_CODES = Collections.unmodifiableMap(codes);
    }

    // Exit code map: first exit from MAXIMIZED.
    private static final Map<StateChangeKey, Integer> EXIT_MAXIMIZED_TO_STATE_CHANGE_CODES;
    static {
        Map<StateChangeKey, Integer> codes = new HashMap<StateChangeKey, Integer>();
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.BACK_PRESS),
                EXIT_MAXIMIZED_TO_CLOSED_BACK_PRESS);
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.FLING),
                EXIT_MAXIMIZED_TO_CLOSED_FLING);
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.TAB_PROMOTION),
                EXIT_MAXIMIZED_TO_CLOSED_TAB_PROMOTION);
        codes.put(new StateChangeKey(PanelState.CLOSED, StateChangeReason.SERP_NAVIGATION),
                EXIT_MAXIMIZED_TO_CLOSED_SERP_NAVIGATION);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.SWIPE),
                EXIT_MAXIMIZED_TO_PEEKED_SWIPE);
        codes.put(new StateChangeKey(PanelState.PEEKED, StateChangeReason.FLING),
                EXIT_MAXIMIZED_TO_PEEKED_FLING);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.SWIPE),
                EXIT_MAXIMIZED_TO_EXPANDED_SWIPE);
        codes.put(new StateChangeKey(PanelState.EXPANDED, StateChangeReason.FLING),
                EXIT_MAXIMIZED_TO_EXPANDED_FLING);
        EXIT_MAXIMIZED_TO_STATE_CHANGE_CODES = Collections.unmodifiableMap(codes);
    }

    // "Seen by gesture" code map: logged on first exit from expanded panel, or promo,
    // broken down by gesture.
    private static final Map<Pair<Boolean, Boolean>, Integer> SEEN_BY_GESTURE_CODES;
    static {
        final boolean unseen = false;
        final boolean seen = true;
        Map<Pair<Boolean, Boolean>, Integer> codes = new HashMap<Pair<Boolean, Boolean>, Integer>();
        codes.put(new Pair<Boolean, Boolean>(seen, TAP), RESULTS_SEEN_FROM_TAP);
        codes.put(new Pair<Boolean, Boolean>(unseen, TAP), RESULTS_NOT_SEEN_FROM_TAP);
        codes.put(new Pair<Boolean, Boolean>(seen, LONG_PRESS), RESULTS_SEEN_FROM_LONG_PRESS);
        codes.put(new Pair<Boolean, Boolean>(unseen, LONG_PRESS), RESULTS_NOT_SEEN_FROM_LONG_PRESS);
        SEEN_BY_GESTURE_CODES = Collections.unmodifiableMap(codes);
    }

    // "Promo outcome by gesture" code map: logged on exit from promo, broken down by gesture.
    private static final Map<Pair<Integer, Boolean>, Integer> PROMO_BY_GESTURE_CODES;
    static {
        Map<Pair<Integer, Boolean>, Integer> codes =
                new HashMap<Pair<Integer, Boolean>, Integer>();
        codes.put(new Pair<Integer, Boolean>(PREFERENCE_ENABLED, TAP), PROMO_ENABLED_FROM_TAP);
        codes.put(new Pair<Integer, Boolean>(PREFERENCE_DISABLED, TAP), PROMO_DISABLED_FROM_TAP);
        codes.put(new Pair<Integer, Boolean>(PREFERENCE_UNINITIALIZED, TAP),
                PROMO_UNDECIDED_FROM_TAP);
        codes.put(new Pair<Integer, Boolean>(PREFERENCE_ENABLED, LONG_PRESS),
                PROMO_ENABLED_FROM_LONG_PRESS);
        codes.put(new Pair<Integer, Boolean>(PREFERENCE_DISABLED, LONG_PRESS),
                PROMO_DISABLED_FROM_LONG_PRESS);
        codes.put(new Pair<Integer, Boolean>(PREFERENCE_UNINITIALIZED, LONG_PRESS),
                PROMO_UNDECIDED_FROM_LONG_PRESS);
        PROMO_BY_GESTURE_CODES = Collections.unmodifiableMap(codes);
    }

    /**
     * Logs the state of the Contextual Search preference. This function should be called if the
     * Contextual Search feature is active, and will track the different preference settings
     * (disabled, enabled or uninitialized). Calling more than once is fine.
     */
    public static void logPreferenceState() {
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchPreferenceState",
                getPreferenceValue(), PREFERENCE_HISTOGRAM_BOUNDARY);
    }

    /**
     * Logs the given number of promo taps remaining.  Should be called only for users that
     * are still undecided.
     * @param promoTapsRemaining The number of taps remaining (should not be negative).
     */
    public static void logPromoTapsRemaining(int promoTapsRemaining) {
        if (promoTapsRemaining >= 0) {
            RecordHistogram.recordCountHistogram("Search.ContextualSearchPromoTapsRemaining",
                    promoTapsRemaining);
        }
    }

    /**
     * Logs the historic number of times that a Tap gesture triggered the peeking promo
     * for users that have never opened the panel.  This should be called periodically for
     * undecided users only.
     * @param promoTaps The historic number of taps that have caused the peeking bar for the promo,
     *        for users that have never opened the panel.
     */
    public static void logPromoTapsForNeverOpened(int promoTaps) {
        RecordHistogram.recordCountHistogram("Search.ContextualSearchPromoTapsForNeverOpened",
                promoTaps);
    }

    /**
     * Logs the historic number of times that a Tap gesture triggered the peeking promo before
     * the user ever opened the panel.  This should be called periodically for all users.
     * @param promoTaps The historic number of taps that have caused the peeking bar for the promo
     *        before the first open of the panel, for all users that have ever opened the panel.
     */
    public static void logPromoTapsBeforeFirstOpen(int promoTaps) {
        RecordHistogram.recordCountHistogram("Search.ContextualSearchPromoTapsBeforeFirstOpen",
                promoTaps);
    }

    /**
     * Records the total count of times the promo panel has *ever* been opened.  This should only
     * be called when the user is still undecided.
     * @param count The total historic count of times the panel has ever been opened for the
     *        current user.
     */
    public static void logPromoOpenCount(int count) {
        RecordHistogram.recordCountHistogram("Search.ContextualSearchPromoOpenCount", count);
    }

    /**
     * Logs the number of taps that have been counted since the user last opened the panel, for
     * undecided users.
     * @param tapsSinceOpen The number of taps to log.
     */
    public static void logTapsSinceOpenForUndecided(int tapsSinceOpen) {
        RecordHistogram.recordCountHistogram("Search.ContextualSearchTapsSinceOpenUndecided",
                tapsSinceOpen);
    }

    /**
     * Logs the number of taps that have been counted since the user last opened the panel, for
     * decided users.
     * @param tapsSinceOpen The number of taps to log.
     */
    public static void logTapsSinceOpenForDecided(int tapsSinceOpen) {
        RecordHistogram.recordCountHistogram("Search.ContextualSearchTapsSinceOpenDecided",
                tapsSinceOpen);
    }

    /**
     * Logs whether the Search Term was single or multiword.
     * @param isSingleWord Whether the resolved search term is a single word or not.
     */
    public static void logSearchTermResolvedWords(boolean isSingleWord) {
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchResolvedTermWords",
                isSingleWord ? RESOLVED_SINGLE_WORD : RESOLVED_MULTI_WORD, RESOLVED_BOUNDARY);
    }

    /**
     * Logs whether the base page was using the HTTP protocol or not.
     * @param isHttpBasePage Whether the base page was using the HTTP protocol or not (should
     *        be false for HTTPS or other URIs).
     */
    public static void logBasePageProtocol(boolean isHttpBasePage) {
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchBasePageProtocol",
                isHttpBasePage ? PROTOCOL_IS_HTTP : PROTOCOL_NOT_HTTP, PROTOCOL_BOUNDARY);
    }

    /**
     * Logs changes to the Contextual Search preference, aside from those resulting from the first
     * run flow.
     * @param enabled Whether the preference is being enabled or disabled.
     */
    public static void logPreferenceChange(boolean enabled) {
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchPreferenceStateChange",
                enabled ? PREFERENCE_ENABLED : PREFERENCE_DISABLED, PREFERENCE_HISTOGRAM_BOUNDARY);
    }

    /**
     * Logs the outcome of the promo (first run flow).
     * Logs multiple histograms; with and without the originating gesture.
     * @param wasTap Whether the gesture that originally caused the panel to show was a Tap.
     */
    public static void logPromoOutcome(boolean wasTap) {
        int preferenceCode = getPreferenceValue();
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchFirstRunFlowOutcome",
                preferenceCode, PREFERENCE_HISTOGRAM_BOUNDARY);
        int preferenceByGestureCode = getPromoByGestureStateCode(preferenceCode, wasTap);
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchPromoOutcomeByGesture",
                preferenceByGestureCode, PROMO_BY_GESTURE_BOUNDARY);
    }

    /**
     * Logs the duration of a Contextual Search panel being viewed by the user.
     * @param wereResultsSeen Whether search results were seen.
     * @param isChained Whether the Contextual Search ended with the start of another.
     * @param durationMs The duration of the contextual search in milliseconds.
     */
    public static void logDuration(boolean wereResultsSeen, boolean isChained, long durationMs) {
        if (wereResultsSeen) {
            RecordHistogram.recordTimesHistogram("Search.ContextualSearchDurationSeen",
                    durationMs, TimeUnit.MILLISECONDS);
        } else if (isChained) {
            RecordHistogram.recordTimesHistogram("Search.ContextualSearchDurationUnseenChained",
                    durationMs, TimeUnit.MILLISECONDS);
        } else {
            RecordHistogram.recordTimesHistogram("Search.ContextualSearchDurationUnseen",
                    durationMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Log the duration of finishing loading the SERP after the panel is opened.
     * @param wasPrefetch Whether the request was prefetch-enabled or not.
     * @param durationMs The duration of loading the SERP till completely loaded, in milliseconds.
     *        Note that this value will be 0 when the SERP is prefetched and the user waits a
     *        while before opening the panel.
     */
    public static void logSearchPanelLoadDuration(boolean wasPrefetch, long durationMs) {
        if (wasPrefetch) {
            RecordHistogram.recordMediumTimesHistogram("Search.ContextualSearchDurationPrefetched",
                    durationMs, TimeUnit.MILLISECONDS);
        } else {
            RecordHistogram.recordMediumTimesHistogram(
                    "Search.ContextualSearchDurationNonPrefetched", durationMs,
                    TimeUnit.MILLISECONDS);
        }

       // Also record a summary histogram with counts for each possibility.
        int code = !wasPrefetch ? NOT_PREFETCHED
                : (durationMs == 0 ? PREFETCHED_FULLY_LOADED : PREFETCHED_PARIALLY_LOADED);
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchPrefetchSummary",
                code, PREFETCH_BOUNDARY);
    }

    /**
     * Logs whether the promo was seen.
     * Logs multiple histograms, with and without the original triggering gesture.
     * @param wasPanelSeen Whether the panel was seen.
     * @param wasTap Whether the gesture that originally caused the panel to show was a Tap.
     */
    public static void logPromoSeen(boolean wasPanelSeen, boolean wasTap) {
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchFirstRunPanelSeen",
                wasPanelSeen ? RESULTS_SEEN : RESULTS_NOT_SEEN, RESULTS_SEEN_BOUNDARY);
        logHistogramByGesture(wasPanelSeen, wasTap, "Search.ContextualSearchPromoSeenByGesture");
    }

    /**
     * Logs whether search results were seen.
     * Logs multiple histograms; with and without the original triggering gesture.
     * @param wasPanelSeen Whether the panel was seen.
     * @param wasTap Whether the gesture that originally caused the panel to show was a Tap.
     */
    public static void logResultsSeen(boolean wasPanelSeen, boolean wasTap) {
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchResultsSeen",
                wasPanelSeen ? RESULTS_SEEN : RESULTS_NOT_SEEN, RESULTS_SEEN_BOUNDARY);
        logHistogramByGesture(wasPanelSeen, wasTap, "Search.ContextualSearchResultsSeenByGesture");
    }

    /**
     * Logs whether a selection is valid.
     * @param isSelectionValid Whether the selection is valid.
     */
    public static void logSelectionIsValid(boolean isSelectionValid) {
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchSelectionValid",
                isSelectionValid ? SELECTION_VALID : SELECTION_INVALID, SELECTION_BOUNDARY);
    }

    /**
     * Logs whether a normal priority search request failed.
     * @param isFailure Whether the request failed.
     */
    public static void logNormalPrioritySearchRequestOutcome(boolean isFailure) {
        RecordHistogram.recordEnumeratedHistogram(
                "Search.ContextualSearchNormalPrioritySearchRequestStatus",
                isFailure ? REQUEST_FAILED : REQUEST_NOT_FAILED, REQUEST_BOUNDARY);
    }

    /**
     * Logs whether a low priority search request failed.
     * @param isFailure Whether the request failed.
     */
    public static void logLowPrioritySearchRequestOutcome(boolean isFailure) {
        RecordHistogram.recordEnumeratedHistogram(
                "Search.ContextualSearchLowPrioritySearchRequestStatus",
                isFailure ? REQUEST_FAILED : REQUEST_NOT_FAILED, REQUEST_BOUNDARY);
    }

    /**
     * Logs whether a fallback search request failed.
     * @param isFailure Whether the request failed.
     */
    public static void logFallbackSearchRequestOutcome(boolean isFailure) {
        RecordHistogram.recordEnumeratedHistogram(
                "Search.ContextualSearchFallbackSearchRequestStatus",
                isFailure ? REQUEST_FAILED : REQUEST_NOT_FAILED, REQUEST_BOUNDARY);
    }

    /**
     * Logs whether the SERP was fully loaded when an opened panel was closed.
     * @param fullyLoaded Whether the SERP had finished loading before the panel was closed.
     */
    public static void logSerpLoadedOnClose(boolean fullyLoaded) {
        RecordHistogram.recordEnumeratedHistogram("Search.ContextualSearchSerpLoadedOnClose",
                fullyLoaded ? FULLY_LOADED : PARTIALLY_LOADED, LOADED_BOUNDARY);
    }

    /**
     * Logs how a state was entered for the first time within a Contextual Search.
     * @param fromState The state to transition from.
     * @param toState The state to transition to.
     * @param reason The reason for the state transition.
     */
    public static void logFirstStateEntry(PanelState fromState, PanelState toState,
            StateChangeReason reason) {
        int code;
        switch (toState) {
            case CLOSED:
                code = getStateChangeCode(fromState, reason,
                        ENTER_CLOSED_STATE_CHANGE_CODES, ENTER_CLOSED_FROM_OTHER);
                RecordHistogram.recordEnumeratedHistogram(
                        "Search.ContextualSearchEnterClosed",
                        code, ENTER_CLOSED_FROM_BOUNDARY);
                break;
            case PEEKED:
                code = getStateChangeCode(fromState, reason,
                        ENTER_PEEKED_STATE_CHANGE_CODES, ENTER_PEEKED_FROM_OTHER);
                RecordHistogram.recordEnumeratedHistogram(
                        "Search.ContextualSearchEnterPeeked",
                        code, ENTER_PEEKED_FROM_BOUNDARY);
                break;
            case EXPANDED:
                code = getStateChangeCode(fromState, reason,
                        ENTER_EXPANDED_STATE_CHANGE_CODES, ENTER_EXPANDED_FROM_OTHER);
                RecordHistogram.recordEnumeratedHistogram(
                        "Search.ContextualSearchEnterExpanded",
                        code, ENTER_EXPANDED_FROM_BOUNDARY);
                break;
            case MAXIMIZED:
                code = getStateChangeCode(fromState, reason,
                        ENTER_MAXIMIZED_STATE_CHANGE_CODES, ENTER_MAXIMIZED_FROM_OTHER);
                RecordHistogram.recordEnumeratedHistogram(
                        "Search.ContextualSearchEnterMaximized",
                        code, ENTER_MAXIMIZED_FROM_BOUNDARY);
                break;
            default:
                break;
        }
    }

    /**
     * Logs how a state was exited for the first time within a Contextual Search.
     * @param fromState The state to transition from.
     * @param toState The state to transition to.
     * @param reason The reason for the state transition.
     */
    public static void logFirstStateExit(PanelState fromState, PanelState toState,
            StateChangeReason reason) {
        int code;
        switch (fromState) {
            case UNDEFINED:
            case CLOSED:
                code = getStateChangeCode(toState, reason,
                        EXIT_CLOSED_TO_STATE_CHANGE_CODES, EXIT_CLOSED_TO_OTHER);
                RecordHistogram.recordEnumeratedHistogram(
                        "Search.ContextualSearchExitClosed", code, EXIT_CLOSED_TO_BOUNDARY);
                break;
            case PEEKED:
                code = getStateChangeCode(toState, reason,
                        EXIT_PEEKED_TO_STATE_CHANGE_CODES, EXIT_PEEKED_TO_OTHER);
                RecordHistogram.recordEnumeratedHistogram(
                        "Search.ContextualSearchExitPeeked", code, EXIT_PEEKED_TO_BOUNDARY);
                break;
            case EXPANDED:
                code = getStateChangeCode(toState, reason,
                        EXIT_EXPANDED_TO_STATE_CHANGE_CODES, EXIT_EXPANDED_TO_OTHER);
                RecordHistogram.recordEnumeratedHistogram(
                        "Search.ContextualSearchExitExpanded", code, EXIT_EXPANDED_TO_BOUNDARY);
                break;
            case MAXIMIZED:
                code = getStateChangeCode(toState, reason,
                        EXIT_MAXIMIZED_TO_STATE_CHANGE_CODES, EXIT_MAXIMIZED_TO_OTHER);
                RecordHistogram.recordEnumeratedHistogram(
                        "Search.ContextualSearchExitMaximized", code, EXIT_MAXIMIZED_TO_BOUNDARY);
                break;
            default:
                break;
        }
    }

    /**
     * Gets the state-change code for the given parameters by doing a lookup in the given map.
     * @param state The panel state.
     * @param reason The reason the state changed.
     * @param stateChangeCodes The map of state and reason to code.
     * @param defaultCode The code to return if the given values are not found in the map.
     * @return The code to write into an enum histogram, based on the given map.
     */
    private static int getStateChangeCode(PanelState state, StateChangeReason reason,
            Map<StateChangeKey, Integer> stateChangeCodes, int defaultCode) {
        Integer code = stateChangeCodes.get(new StateChangeKey(state, reason));
        if (code != null) {
            return code;
        }
        return defaultCode;
    }

    /**
     * Gets the panel-seen code for the given parameters by doing a lookup in the seen-by-gesture
     * map.
     * @param wasPanelSeen Whether the panel was seen.
     * @param wasTap Whether the gesture that originally caused the panel to show was a Tap.
     * @return The code to write into a panel-seen histogram.
     */
    private static int getPanelSeenByGestureStateCode(boolean wasPanelSeen, boolean wasTap) {
        return SEEN_BY_GESTURE_CODES.get(new Pair<Boolean, Boolean>(wasPanelSeen, wasTap));
    }

    /**
     * Gets the promo-outcome code for the given parameter by doing a lookup in the
     * promo-by-gesture map.
     * @param preferenceValue The code for the current preference value.
     * @param wasTap Whether the gesture that originally caused the panel to show was a Tap.
     * @return The code to write into a promo-outcome histogram.
     */
    private static int getPromoByGestureStateCode(int preferenceValue, boolean wasTap) {
        return PROMO_BY_GESTURE_CODES.get(new Pair<Integer, Boolean>(preferenceValue, wasTap));
    }

    /**
     * @return The code for the Contextual Search preference.
     */
    private static int getPreferenceValue() {
        PrefServiceBridge preferences = PrefServiceBridge.getInstance();
        if (preferences.isContextualSearchUninitialized()) {
            return PREFERENCE_UNINITIALIZED;
        } else if (preferences.isContextualSearchDisabled()) {
            return PREFERENCE_DISABLED;
        }
        return PREFERENCE_ENABLED;
    }

    /**
     * Logs to a seen-by-gesture histogram of the given name.
     * @param wasPanelSeen Whether the panel was seen.
     * @param wasTap Whether the gesture that originally caused the panel to show was a Tap.
     * @param histogramName The full name of the histogram to log to.
     */
    private static void logHistogramByGesture(boolean wasPanelSeen, boolean wasTap,
            String histogramName) {
        RecordHistogram.recordEnumeratedHistogram(histogramName,
                getPanelSeenByGestureStateCode(wasPanelSeen, wasTap),
                RESULTS_BY_GESTURE_BOUNDARY);
    }
}
