// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.bottombar.OverlayContentDelegate;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel;

/**
 * The delegate that provides global management functionality for Contextual Search.
 */
public interface ContextualSearchManagementDelegate {

    /**
     * @return The ChromeActivity that associated with the manager.
     */
    ChromeActivity getChromeActivity();

    /**
     * @return Whether the Search Panel is showing.
     */
    boolean isShowingSearchPanel();

    /**
     * @return Whether the Opt-out promo is available to be be shown in the panel.
     */
    boolean isPromoAvailable();

    /**
     * Called when the promo Panel gets closed, to log the outcome.
     */
    void logPromoOutcome();

    /**
     * Promotes the current Content View Core in the Contextual Search Panel to its own Tab.
     */
    void promoteToTab();

    /**
     * Sets the handle to the ContextualSearchPanel.
     * @param delegate The ContextualSearchPanel.
     */
    void setContextualSearchPanel(ContextualSearchPanel panel);

    /**
     * Gets whether the device is running in compatibility mode for Contextual Search.
     * If so, a new tab showing search results should be opened instead of showing the panel.
     * @return whether the device is running in compatibility mode.
     */
    boolean isRunningInCompatibilityMode();

    /**
     * Opens the resolved search URL in a new tab. It is used when Contextual Search is in
     * compatibility mode.
     */
    void openResolvedSearchUrlInNewTab();

    /**
     * Preserves the Base Page's selection next time it loses focus.
     */
    void preserveBasePageSelectionOnNextLossOfFocus();

    /**
     * Dismisses the Contextual Search bar completely.  This will hide any panel that's currently
     * showing as well as any bar that's peeking.
     */
    void dismissContextualSearchBar();

    /**
     * Notifies that the Contextual Search Panel did get closed.
     * @param reason The reason the panel is closing.
     */
    void onCloseContextualSearch(StateChangeReason reason);

    /**
     * This is called on navigation of the contextual search pane This is called on navigation
     * of the contextual search panel.
     * @param isFailure If the request resulted in an error page.
     */
    void onContextualSearchRequestNavigation(boolean isFailure);

    /**
     * @return An OverlayContentDelegate to watch events on the panel's content.
     */
    OverlayContentDelegate getOverlayContentDelegate();
}
