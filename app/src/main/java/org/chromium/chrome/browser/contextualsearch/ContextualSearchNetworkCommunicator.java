// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import java.net.URL;

import javax.annotation.Nullable;


/**
 * An interface for network communication between the Contextual Search client and server.
 */
public interface ContextualSearchNetworkCommunicator {

    /**
     * Starts a Search Term Resolution request.
     * When the response comes back {@link #handleSearchTermResolutionResponse} will be called.
     * @param selection the current selected text.
     */
    void startSearchTermResolutionRequest(String selection);

    /**
     * Handles a Search Term Resolution response.
     * @param isNetworkUnavailable whether the network is available.
     * @param responseCode the server's HTTP response code.
     * @param searchTerm the term to search for.
     * @param displayText the text to display that describes the search term.
     * @param alternateTerm the alternate search term.
     * @param mid the MID for an entity to use to trigger a Knowledge Panel, or an empty string.
     *            A MID is a unique identifier for an entity in the Search Knowledge Graph.
     * @param doPreventPreload whether to prevent preloading the search result.
     * @param selectionStartAdjust The start offset adjustment of the selection to use to highlight
     *                             the search term.
     * @param selectionEndAdjust The end offset adjustment of the selection to use to highlight
     *                           the search term.
     * @param contextLanguage The language of the context, or the empty string if unknown.
     * @param thumbnailUrl The URL of the thumbnail to display in our UX.
     * @param caption The caption to display.
     * @param quickActionUri The URI for the intent associated with the quick action.
     * @param quickActionCategory The {@link QuickActionCategory} for the quick action.
     */
    void handleSearchTermResolutionResponse(boolean isNetworkUnavailable, int responseCode,
            String searchTerm, String displayText, String alternateTerm, String mid,
            boolean doPreventPreload, int selectionStartAdjust, int selectionEndAdjust,
            String contextLanguage, String thumbnailUrl, String caption,
            String quickActionUri, int quickActionCategory);

    /**
     * @return Whether the device is currently online.
     */
    boolean isOnline();

    // --------------------------------------------------------------------------------------------
    // These are non-network actions that need to be stubbed out for testing.
    // --------------------------------------------------------------------------------------------

    /**
     * Gets the URL of the base page.
     * TODO(donnd): move to another interface, or rename this interface:
     * This is needed to stub out for testing, but has nothing to do with networking.
     * @return The URL of the base page (needed for testing purposes).
     */
    @Nullable URL getBasePageUrl();
}
