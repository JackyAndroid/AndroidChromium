// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import org.chromium.chrome.browser.contextualsearch.ContextualSearchBlacklist.BlacklistReason;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchSelectionController.SelectionType;

/**
 * Defines the interface between a {@link ContextualSearchSelectionController} and the code that
 * handles callbacks.
 */
interface ContextualSearchSelectionHandler {
    /**
     * Handle a scroll event on the base page.
     */
    public void handleScroll();

    /**
     * Handle a valid tap gesture on the base page.
     */
    public void handleValidTap();

    /**
     * Handle an invalid tap gesture on the base page.
     */
    public void handleInvalidTap();

    /**
     * Handle a new selection of the given type, created at the given x,y position.
     */
    public void handleSelection(String selection, boolean selectionValid, SelectionType type,
            float x, float y);

    /**
     * Handle a modification to the selection, done at the given x,y position.
     * @param selection The new selection.
     * @param selectionValid Whether the new selection is valid.
     * @param x The x position of the adjustment.
     * @param y The y position of the adjustment.
     */
    public void handleSelectionModification(
            String selection, boolean selectionValid, float x, float y);

    /**
     * Handle a dismissal of the selection on the base page.
     */
    public void handleSelectionDismissal();

    /**
     * Handles the suppression of the current selection.
     * @param reason The reason why the selection was blacklisted. If the returned reason
     *               is BlacklistReason.NONE, it means the selection was not blacklisted.
     */
    public void handleSelectionSuppression(BlacklistReason reason);

    /**
     * Handle suppression of a Tap gesture.
     */
    public void handleSuppressedTap();

    /**
     * Handle updating metrics to reflect that a Tap gesture <i>would</i> be suppressed
     * for the given heuristics.
     * @param tapHeuristics The set of heuristics that would suppress the Tap.
     */
    public void handleMetricsForWouldSuppressTap(ContextualSearchHeuristics tapHeuristics);
}
