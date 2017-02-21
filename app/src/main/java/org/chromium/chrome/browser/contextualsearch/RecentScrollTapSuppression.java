// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

/**
 * Heuristic for Tap suppression after a recent scroll action.
 * Handles logging of results seen and activation.
 */
public class RecentScrollTapSuppression extends ContextualSearchHeuristic {
    // TODO(twellington): Use this condition rather than an experiment threshold for suppression
    // once feature is enabled by default.
    private static final int DEFAULT_RECENT_SCROLL_SUPPRESSION_DURATION_MS = 300;

    private final int mExperimentThresholdMs;
    private final int mDurationSinceRecentScrollMs;
    private final boolean mIsConditionSatisfied;
    private final boolean mIsEnabled;

    /**
     * Constructs a Tap suppression heuristic that handles a Tap after a recent scroll.
     * This logs activation data that includes whether it activated for a threshold specified
     * by an experiment. This also logs Results-seen data to profile when results are seen relative
     * to a recent scroll.
     * @param selectionController The {@link ContextualSearchSelectionController}.
     */
    RecentScrollTapSuppression(ContextualSearchSelectionController selectionController) {
        long recentScrollTimeNs = selectionController.getLastScrollTime();
        if (recentScrollTimeNs > 0) {
            mDurationSinceRecentScrollMs =
                    (int) ((System.nanoTime() - recentScrollTimeNs) / NANOSECONDS_IN_A_MILLISECOND);
        } else {
            mDurationSinceRecentScrollMs = 0;
        }

        mExperimentThresholdMs = ContextualSearchFieldTrial.getRecentScrollSuppressionDurationMs();

        // If the configured threshold is 0, then suppression is not enabled.
        mIsEnabled = mExperimentThresholdMs > 0;

        int conditionThreshold = mIsEnabled ? mExperimentThresholdMs
                : DEFAULT_RECENT_SCROLL_SUPPRESSION_DURATION_MS;
        mIsConditionSatisfied = mDurationSinceRecentScrollMs > 0
                && mDurationSinceRecentScrollMs < conditionThreshold;
    }

    @Override
    protected boolean isConditionSatisfiedAndEnabled() {
        return mIsEnabled && mIsConditionSatisfied;
    }

    @Override
    protected void logConditionState() {
        if (mIsEnabled) {
            ContextualSearchUma.logRecentScrollSuppression(mIsConditionSatisfied);
        }
    }

    @Override
    protected void logResultsSeen(boolean wasSearchContentViewSeen, boolean wasActivatedByTap) {
        if (wasActivatedByTap && mDurationSinceRecentScrollMs > 0
                && ContextualSearchFieldTrial.isRecentScrollCollectionEnabled()) {
            ContextualSearchUma.logRecentScrollDuration(
                    mDurationSinceRecentScrollMs, wasSearchContentViewSeen);
        }
    }

    @Override
    protected boolean shouldAggregateLogForTapSuppression() {
        return true;
    }

    @Override
    protected boolean isConditionSatisfiedForAggregateLogging() {
        return !mIsEnabled && mIsConditionSatisfied;
    }
}
