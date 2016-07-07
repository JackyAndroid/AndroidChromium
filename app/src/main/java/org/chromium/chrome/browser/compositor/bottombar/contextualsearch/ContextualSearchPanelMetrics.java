// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.PanelState;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchUma;

/**
 * This class is responsible for all the logging related to Contextual Search.
 */
public class ContextualSearchPanelMetrics {

    // Flags for logging.
    private boolean mDidSearchInvolvePromo;
    private boolean mWasSearchContentViewSeen;
    private boolean mIsPromoActive;
    private boolean mHasExpanded;
    private boolean mHasMaximized;
    private boolean mHasExitedPeeking;
    private boolean mHasExitedExpanded;
    private boolean mHasExitedMaximized;
    private boolean mIsSerpNavigation;
    private boolean mWasActivatedByTap;
    private boolean mIsSearchPanelFullyPreloaded;
    private long mSearchStartTimeNs;
    private long mSearchViewStartTimeNs;

    /**
     * Log information when the panel's state has changed.
     * @param fromState The state the panel is transitioning from.
     * @param toState The state that the panel is transitioning to.
     * @param reason The reason for the state change.
     */
    public void onPanelStateChanged(PanelState fromState, PanelState toState,
            StateChangeReason reason) {
        // Note: the logging within this function includes the promo, unless specifically
        // excluded.
        boolean isStartingSearch = isStartingNewContextualSearch(toState, reason);
        boolean isEndingSearch = isEndingContextualSearch(fromState, toState, isStartingSearch);
        boolean isChained = isStartingSearch && isOngoingContextualSearch(fromState);
        boolean isSameState = fromState == toState;
        boolean isFirstExitFromPeeking = fromState == PanelState.PEEKED && !mHasExitedPeeking
                && (!isSameState || isStartingSearch);
        boolean isFirstExitFromExpanded = fromState == PanelState.EXPANDED && !mHasExitedExpanded
                && !isSameState;
        boolean isFirstExitFromMaximized = fromState == PanelState.MAXIMIZED && !mHasExitedMaximized
                && !isSameState;
        boolean isFirstSearchView = isFirstExitFromPeeking && toState != PanelState.CLOSED;
        // This variable is needed for logging and gets reset in an isStartingSearch block below,
        // so a local copy is created before the reset.
        boolean isSearchPanelFullyPreloaded = mIsSearchPanelFullyPreloaded;

        if (isEndingSearch) {
            if (!mDidSearchInvolvePromo) {
                // Measure duration only when the promo is not involved.
                long durationMs = (System.nanoTime() - mSearchStartTimeNs) / 1000000;
                ContextualSearchUma.logDuration(mWasSearchContentViewSeen, isChained, durationMs);
            }
            if (mIsPromoActive) {
                // The user is exiting still in the promo, without choosing an option.
                ContextualSearchUma.logPromoSeen(mWasSearchContentViewSeen, mWasActivatedByTap);
            } else {
                ContextualSearchUma.logResultsSeen(mWasSearchContentViewSeen, mWasActivatedByTap);
            }
        }
        if (isStartingSearch) {
            mSearchStartTimeNs = System.nanoTime();
            mSearchViewStartTimeNs = 0;
            mIsSearchPanelFullyPreloaded = false;
            mWasActivatedByTap = reason == StateChangeReason.TEXT_SELECT_TAP;
        }
        if (isFirstSearchView) {
            onSearchPanelFirstView();
        }

        // Log state changes. We only log the first transition to a state within a contextual
        // search. Note that when a user clicks on a link on the search content view, this will
        // trigger a transition to MAXIMIZED (SERP_NAVIGATION) followed by a transition to
        // CLOSED (TAB_PROMOTION). For the purpose of logging, the reason for the second transition
        // is reinterpreted to SERP_NAVIGATION, in order to distinguish it from a tab promotion
        // caused when tapping on the Search Bar when the Panel is maximized.
        StateChangeReason reasonForLogging =
                mIsSerpNavigation ? StateChangeReason.SERP_NAVIGATION : reason;
        if (isStartingSearch || isEndingSearch
                || (!mHasExpanded && toState == PanelState.EXPANDED)
                || (!mHasMaximized && toState == PanelState.MAXIMIZED)) {
            ContextualSearchUma.logFirstStateEntry(fromState, toState, reasonForLogging);
        }
        // Note: CLOSED / UNDEFINED state exits are detected when a search that is not chained is
        // starting.
        if ((isStartingSearch && !isChained) || isFirstExitFromPeeking || isFirstExitFromExpanded
                || isFirstExitFromMaximized) {
            ContextualSearchUma.logFirstStateExit(fromState, toState, reasonForLogging);
        }

        // We can now modify the state.
        if (isFirstExitFromPeeking) {
            mHasExitedPeeking = true;
        } else if (isFirstExitFromExpanded) {
            mHasExitedExpanded = true;
        } else if (isFirstExitFromMaximized) {
            mHasExitedMaximized = true;
        }

        if (toState == PanelState.EXPANDED) {
            mHasExpanded = true;
        } else if (toState == PanelState.MAXIMIZED) {
            mHasMaximized = true;
        }
        if (reason == StateChangeReason.SERP_NAVIGATION) {
            mIsSerpNavigation = true;
        }

        if (isEndingSearch) {
            if (mHasMaximized || mHasExpanded) {
                ContextualSearchUma.logSerpLoadedOnClose(isSearchPanelFullyPreloaded);
            }
            mDidSearchInvolvePromo = false;
            mWasSearchContentViewSeen = false;
            mHasExpanded = false;
            mHasMaximized = false;
            mHasExitedPeeking = false;
            mHasExitedExpanded = false;
            mHasExitedMaximized = false;
            mIsSerpNavigation = false;
        }

        // TODO(manzagop): When the user opts in, we should replay his actions for the current
        // contextual search for the standard (non promo) UMA histograms.
    }

    /**
     * Sets that the contextual search involved the promo.
     */
    public void setDidSearchInvolvePromo() {
        mDidSearchInvolvePromo = true;
    }

    /**
     * Sets that the Search Content View was seen.
     */
    public void setWasSearchContentViewSeen() {
        mWasSearchContentViewSeen = true;
    }

    /**
     * Sets whether the promo is active.
     */
    public void setIsPromoActive(boolean shown) {
        mIsPromoActive = shown;
    }

    /**
     * Gets whether the promo is active.
     */
    private boolean getIsPromoActive() {
        return mIsPromoActive;
    }

    /**
     * Records timing information when the search results have fully loaded.
     * @param wasPrefetch Whether the request was prefetch-enabled.
     */
    public void onSearchResultsLoaded(boolean wasPrefetch) {
        if (mHasExpanded || mHasMaximized) {
            // Already opened, log how long it took.
            assert mSearchViewStartTimeNs != 0;
            long durationMs = (System.nanoTime() - mSearchViewStartTimeNs) / 1000000;
            logSearchPanelLoadDuration(wasPrefetch, durationMs);
        }

        // Not yet opened, wait till an open to log.
        mIsSearchPanelFullyPreloaded = true;
    }

    /**
     * Records timing information when the search panel has been viewed for the first time.
     */
    private void onSearchPanelFirstView() {
        if (mIsSearchPanelFullyPreloaded) {
            // Already fully pre-loaded, record a wait of 0 milliseconds.
            logSearchPanelLoadDuration(true, 0);
        } else {
            // Start a loading timer.
            mSearchViewStartTimeNs = System.nanoTime();
        }
    }

    /**
     * Determine whether a new contextual search is starting.
     * @param toState The contextual search state that will be transitioned to.
     * @param reason The reason for the search state transition.
     * @return Whether a new contextual search is starting.
     */
    private boolean isStartingNewContextualSearch(PanelState toState, StateChangeReason reason) {
        return toState == PanelState.PEEKED
                && (reason == StateChangeReason.TEXT_SELECT_TAP
                        || reason == StateChangeReason.TEXT_SELECT_LONG_PRESS);
    }

    /**
     * Determine whether a contextual search is ending.
     * @param fromState The contextual search state that will be transitioned from.
     * @param toState The contextual search state that will be transitioned to.
     * @param isStartingSearch Whether a new contextual search is starting.
     * @return Whether a contextual search is ending.
     */
    private boolean isEndingContextualSearch(PanelState fromState, PanelState toState,
            boolean isStartingSearch) {
        return isOngoingContextualSearch(fromState)
                && (toState == PanelState.CLOSED || isStartingSearch);
    }

    /**
     * @param fromState The state the panel is transitioning from.
     * @return Whether there is an ongoing contextual search.
     */
    private boolean isOngoingContextualSearch(PanelState fromState) {
        return fromState != PanelState.UNDEFINED && fromState != PanelState.CLOSED;
    }

    /**
     * Logs the duration the user waited for the search panel to fully load, once it was opened.
     * @param wasPrefetch Whether the load included prefetch.
     * @param durationMs The duration to log.
     */
    private void logSearchPanelLoadDuration(boolean wasPrefetch, long durationMs) {
        ContextualSearchUma.logSearchPanelLoadDuration(wasPrefetch, durationMs);
    }
}

