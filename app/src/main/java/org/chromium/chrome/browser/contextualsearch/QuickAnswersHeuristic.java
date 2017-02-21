// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

/**
 * A piece of conditional behavior that supports experimentation and logging.
 */
class QuickAnswersHeuristic extends ContextualSearchHeuristic {
    private boolean mIsConditionSatisfied;
    private boolean mDidAnswer;

    /**
     * Create a heuristic for a Quick Answer that was never activated.
     */
    QuickAnswersHeuristic() {
        mIsConditionSatisfied = false;
        mDidAnswer = false;
    }

    /**
     * Sets whether the condition is satisfied for this heuristic.
     * @param isConditionSatisfied Whether the heuristic condition is satisfied.
     */
    void setContitionSatisified(boolean isConditionSatisfied) {
        mIsConditionSatisfied = isConditionSatisfied;
    }

    /**
     * Sets whether the quick answer does answer the user's question rather than just being a hint.
     * @param didAnswer Whether the quick answer should be enough for the user.
     */
    void setDoesAnswer(boolean didAnswer) {
        mDidAnswer = didAnswer;
    }

    /**
     * Sets whether the condition is satisfied (whether the quick answer is being shown).
     * @param isConditionSatisfied Whether the quick answer is being shown.
     */
    void setConditionSatisfied(boolean isConditionSatisfied) {
        mIsConditionSatisfied = isConditionSatisfied;
    }

    @Override
    protected boolean isConditionSatisfiedAndEnabled() {
        return mIsConditionSatisfied;
    }

    @Override
    protected void logResultsSeen(boolean wasSearchContentViewSeen, boolean wasActivatedByTap) {
        if (wasActivatedByTap) {
            ContextualSearchUma.logQuickAnswerSeen(
                    wasSearchContentViewSeen, mIsConditionSatisfied, mDidAnswer);
        }
    }
}
