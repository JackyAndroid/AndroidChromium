// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import org.chromium.base.metrics.RecordUserAction;

/**
 * Heuristic for general Tap suppression that factors in a variety of signals.
 */
class TapSuppression extends ContextualSearchHeuristic {
    private static final int TIME_THRESHOLD_MILLISECONDS = 3000;
    private static final int TAP_RADIUS_DPS = 30;

    private final boolean mIsTapSuppressionEnabled;
    private final int mExperimentThresholdTaps;
    private final int mTapsSinceOpen;
    private final float mPxToDp;
    private final boolean mIsSecondTap;
    private final boolean mIsConditionSatisfied; // whether to suppress or not.

    /**
     * Constructs a heuristic to decide if a Tap should be suppressed or not.
     * Combines various signals to determine suppression, including whether the previous
     * Tap was suppressed for any reason.
     * @param controller The Selection Controller.
     * @param previousTapState The specifics regarding the previous Tap.
     * @param x The x coordinate of the current tap.
     * @param y The y coordinate of the current tap.
     * @param tapsSinceOpen the number of Tap gestures since the last open of the panel.
     */
    TapSuppression(ContextualSearchSelectionController controller,
            ContextualSearchTapState previousTapState, int x, int y, int tapsSinceOpen) {
        mIsTapSuppressionEnabled = ContextualSearchFieldTrial.isTapSuppressionEnabled();
        mExperimentThresholdTaps = ContextualSearchFieldTrial.getSuppressionTaps();
        mPxToDp = controller.getPxToDp();
        mTapsSinceOpen = tapsSinceOpen;
        mIsSecondTap = previousTapState != null && previousTapState.wasSuppressed()
                && !shouldHandleFirstTap();

        if (mIsSecondTap) {
            boolean shouldHandle = shouldHandleSecondTap(previousTapState, x, y);
            mIsConditionSatisfied = !shouldHandle;
        } else {
            mIsConditionSatisfied = !shouldHandleFirstTap();
            if (mIsConditionSatisfied && mIsTapSuppressionEnabled) {
                RecordUserAction.record("ContextualSearch.TapSuppressed.TapThresholdExceeded");
            }
        }
    }

    @Override
    protected boolean isConditionSatisfiedAndEnabled() {
        return mIsTapSuppressionEnabled && mIsConditionSatisfied;
    }

    @Override
    protected void logResultsSeen(boolean wasSearchContentViewSeen, boolean wasActivatedByTap) {
        // TODO(donnd): consider logging counter-factual data rather than checking if enabled.
        if (wasActivatedByTap && mIsTapSuppressionEnabled) {
            ContextualSearchUma.logTapSuppressionResultsSeen(
                    wasSearchContentViewSeen, mIsSecondTap);
        }
    }

    /**
     * @return Whether a first tap should be handled or not.
     */
    private boolean shouldHandleFirstTap() {
        return mTapsSinceOpen < mExperimentThresholdTaps;
    }

    /**
     * Determines whether a second tap at the given coordinates should be handled.
     * @param tapState The specifics regarding the previous Tap.
     * @param x The x coordinate of the current tap.
     * @param y The y coordinate of the current tap.
     * @return whether a second tap at the given coordinates should be handled or not.
     */
    private boolean shouldHandleSecondTap(ContextualSearchTapState tapState, int x, int y) {
        // The second tap needs to be close to the first tap in both time and space.
        // Recent enough?
        if (System.nanoTime() - tapState.tapTimeNanoseconds()
                > (long) TIME_THRESHOLD_MILLISECONDS * NANOSECONDS_IN_A_MILLISECOND) {
            return false;
        }

        // Within our radius?
        float deltaXDp = (tapState.getX() - x) * mPxToDp;
        float deltaYDp = (tapState.getY() - y) * mPxToDp;
        // Use x^2 * y^2 = r^2
        float distanceSquaredDp = deltaXDp * deltaXDp + deltaYDp * deltaYDp;
        return distanceSquaredDp <= TAP_RADIUS_DPS * TAP_RADIUS_DPS;
    }
}
