// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

/**
 * A piece of conditional behavior that supports experimentation and logging.
 */
abstract class ContextualSearchHeuristic {
    // Conversion from nanoseconds to milliseconds.
    public static final int NANOSECONDS_IN_A_MILLISECOND = 1000000;

    /**
     * Gets whether this heuristic's condition was satisfied or not if it is enabled.
     * In the case of a Tap heuristic, if the condition is satisfied the Tap is suppressed.
     * This heuristic may be called in logResultsSeen regardless of whether the condition was
     * satisfied.
     * @return True iff this heuristic is enabled and its condition is satisfied.
     */
    protected abstract boolean isConditionSatisfiedAndEnabled();

    /**
     * Optionally logs this heuristic's condition state.  Up to the heuristic to determine exactly
     * what to log and whether to log at all.  Default is to not log anything.
     */
    protected void logConditionState() {}

    /**
     * Optionally logs whether results would have been seen if this heuristic had its condition
     * satisfied, and possibly some associated data for profiling (up to the heuristic to decide).
     * Default is to not log anything.
     * @param wasSearchContentViewSeen Whether the panel contents were seen.
     * @param wasActivatedByTap Whether the panel was activated by a Tap or not.
     */
    protected void logResultsSeen(boolean wasSearchContentViewSeen, boolean wasActivatedByTap) {}

    /**
     * @return Whether this heuristic should be considered when logging aggregate metrics for Tap
     *         suppression.
     */
    protected boolean shouldAggregateLogForTapSuppression() {
        return false;
    }

    /**
     * @return Whether this heuristic's condition would have been satisfied, causing a tap
     *         suppression, if it were enabled through VariationsAssociatedData. If the feature is
     *         enabled through VariationsAssociatedData then this method should return false.
     */
    protected boolean isConditionSatisfiedForAggregateLogging() {
        return false;
    }
}
