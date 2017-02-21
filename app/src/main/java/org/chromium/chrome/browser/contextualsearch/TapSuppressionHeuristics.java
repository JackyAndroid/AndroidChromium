// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

/**
 * A set of {@link ContextualSearchHeuristic}s that support experimentation and logging.
 */
public class TapSuppressionHeuristics extends ContextualSearchHeuristics {
    private CtrSuppression mCtrSuppression;

    /**
     * Gets all the heuristics needed for Tap suppression.
     * @param selectionController The {@link ContextualSearchSelectionController}.
     * @param previousTapState The state of the previous tap, or {@code null}.
     * @param x The x position of the Tap.
     * @param y The y position of the Tap.
     * @param tapsSinceOpen the number of Tap gestures since the last open of the panel.
     */
    TapSuppressionHeuristics(ContextualSearchSelectionController selectionController,
            ContextualSearchTapState previousTapState, int x, int y, int tapsSinceOpen) {
        super();
        mCtrSuppression = new CtrSuppression(selectionController.getActivity());
        mHeuristics.add(mCtrSuppression);
        RecentScrollTapSuppression scrollTapExperiment =
                new RecentScrollTapSuppression(selectionController);
        mHeuristics.add(scrollTapExperiment);
        TapFarFromPreviousSuppression farFromPreviousHeuristic =
                new TapFarFromPreviousSuppression(selectionController, previousTapState, x, y);
        mHeuristics.add(farFromPreviousHeuristic);
        NearTopTapSuppression tapNearTopSuppression =
                new NearTopTapSuppression(selectionController, y);
        mHeuristics.add(tapNearTopSuppression);
        BarOverlapTapSuppression barOverlapTapSuppression =
                new BarOverlapTapSuppression(selectionController, y);
        mHeuristics.add(barOverlapTapSuppression);
        // General Tap Suppression and Tap Twice.
        TapSuppression tapSuppression =
                new TapSuppression(selectionController, previousTapState, x, y, tapsSinceOpen);
        mHeuristics.add(tapSuppression);
    }

    /**
     * This method should be called to clean up storage when an instance of this class is
     * no longer in use.
     */
    public void destroy() {
        if (mCtrSuppression != null) {
            mCtrSuppression.destroy();
            mCtrSuppression = null;
        }
    }

    /**
     * Logs the results seen for the heuristics and whether they would have had their condition
     * satisfied if enabled.
     * @param wasSearchContentViewSeen Whether the panel contents were seen.
     * @param wasActivatedByTap Whether the panel was activated by a Tap or not.
     */
    @Override
    public void logResultsSeen(boolean wasSearchContentViewSeen, boolean wasActivatedByTap) {
        for (ContextualSearchHeuristic heuristic : mHeuristics) {
            heuristic.logResultsSeen(wasSearchContentViewSeen, wasActivatedByTap);
        }
    }

    /**
     * Logs the condition state for all the Tap suppression heuristics.
     */
    void logConditionState() {
        // TODO(donnd): consider doing this logging automatically in the constructor rather than
        // leaving this an optional separate method.
        for (ContextualSearchHeuristic heuristic : mHeuristics) {
            heuristic.logConditionState();
        }
    }

    /**
     * @return Whether the Tap should be suppressed (due to a heuristic condition being satisfied).
     */
    boolean shouldSuppressTap() {
        for (ContextualSearchHeuristic heuristic : mHeuristics) {
            if (heuristic.isConditionSatisfiedAndEnabled()) return true;
        }
        return false;
    }
}
