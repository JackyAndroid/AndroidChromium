// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

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
}
