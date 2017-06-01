// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.os.Handler;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchBlacklist.BlacklistReason;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.ui.touch_selection.SelectionEventType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controls selection gesture interaction for Contextual Search.
 */
public class ContextualSearchSelectionController {

    /**
     * The type of selection made by the user.
     */
    public enum SelectionType {
        UNDETERMINED,
        TAP,
        LONG_PRESS
    }

    // The number of milliseconds to wait for a selection change after a tap before considering
    // the tap invalid.  This can't be too small or the subsequent taps may not have established
    // a new selection in time.  This is because selectWordAroundCaret doesn't always select.
    // TODO(donnd): Fix in Blink, crbug.com/435778.
    private static final int INVALID_IF_NO_SELECTION_CHANGE_AFTER_TAP_MS = 50;

    // The default navigation-detection-delay in milliseconds.
    private static final int TAP_NAVIGATION_DETECTION_DELAY = 16;

    private static final String CONTAINS_WORD_PATTERN = "(\\w|\\p{L}|\\p{N})+";
    // A URL is:
    //   1:    scheme://
    //   1+:   any word char, _ or -
    //   1+:   . followed by 1+ of any word char, _ or -
    //   0-1:  0+ of any word char or .,@?^=%&:/~#- followed by any word char or @?^-%&/~+#-
    // TODO(twellington): expand accepted schemes?
    private static final Pattern URL_PATTERN = Pattern.compile("((http|https|file|ftp|ssh)://)"
            + "([\\w_-]+(?:(?:\\.[\\w_-]+)+))([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?");

    // Max selection length must be limited or the entire request URL can go past the 2K limit.
    private static final int MAX_SELECTION_LENGTH = 100;

    private final ChromeActivity mActivity;
    private final ContextualSearchSelectionHandler mHandler;
    private final Runnable mHandleInvalidTapRunnable;
    private final Handler mRunnableHandler;
    private final float mPxToDp;
    private final Pattern mContainsWordPattern;

    private String mSelectedText;
    private SelectionType mSelectionType;
    private boolean mWasTapGestureDetected;
    // Reflects whether the last tap was valid and whether we still have a tap-based selection.
    private ContextualSearchTapState mLastTapState;
    private TapSuppressionHeuristics mTapHeuristics;
    private boolean mIsWaitingForInvalidTapDetection;
    private boolean mIsSelectionEstablished;
    private boolean mShouldHandleSelectionModification;
    private boolean mDidExpandSelection;

    // Position of the selection.
    private float mX;
    private float mY;

    // The time of the most last scroll activity, or 0 if none.
    private long mLastScrollTimeNs;

    // Tracks whether a Context Menu has just been shown and the UX has been dismissed.
    // The selection may be unreliable until the next reset.  See crbug.com/628436.
    private boolean mIsContextMenuShown;

    private class ContextualSearchGestureStateListener extends GestureStateListener {
        @Override
        public void onScrollStarted(int scrollOffsetY, int scrollExtentY) {
            mHandler.handleScroll();
        }

        @Override
        public void onScrollEnded(int scrollOffsetY, int scrollExtentY) {
            mLastScrollTimeNs = System.nanoTime();
        }

        @Override
        public void onScrollUpdateGestureConsumed() {
            // The onScrollEnded notification is unreliable, so mark time during scroll updates too.
            // See crbug.com/600863.
            mLastScrollTimeNs = System.nanoTime();
        }

        // TODO(donnd): Remove this once we get notification of the selection changing
        // after a tap-select gets a subsequent tap nearby.  Currently there's no
        // notification in this case.
        // See crbug.com/444114.
        @Override
        public void onSingleTap(boolean consumed) {
            // We may be notified that a tap has happened even when the system consumed the event.
            // This is being used to support tapping on an existing selection to show the selection
            // handles.  We should process this tap unless we have already shown the selection
            // handles (have a long-press selection) and the tap was consumed.
            if (!(consumed && mSelectionType == SelectionType.LONG_PRESS)) {
                scheduleInvalidTapNotification();
            }
        }
    }

    /**
     * Constructs a new Selection controller for the given activity.  Callbacks will be issued
     * through the given selection handler.
     * @param activity The {@link ChromeActivity} to control.
     * @param handler The handler for callbacks.
     */
    public ContextualSearchSelectionController(ChromeActivity activity,
            ContextualSearchSelectionHandler handler) {
        mActivity = activity;
        mHandler = handler;
        mPxToDp = 1.f / mActivity.getResources().getDisplayMetrics().density;

        mRunnableHandler = new Handler();
        mHandleInvalidTapRunnable = new Runnable() {
            @Override
            public void run() {
                onInvalidTapDetectionTimeout();
            }
        };

        mContainsWordPattern = Pattern.compile(CONTAINS_WORD_PATTERN);
    }

    /**
     * Notifies that the base page has started loading a page.
     */
    void onBasePageLoadStarted() {
        resetAllStates();
    }

    /**
     * Notifies that a Context Menu has been shown.
     * Future controller events may be unreliable until the next reset.
     */
    void onContextMenuShown() {
        // Hide the UX.
        mHandler.handleSelectionDismissal();
        mIsContextMenuShown = true;
    }

    /**
     * Notifies that the Contextual Search has ended.
     * @param reason The reason for ending the Contextual Search.
     */
    void onSearchEnded(OverlayPanel.StateChangeReason reason) {
        // If the user explicitly closes the panel after establishing a selection with long press,
        // it should not reappear until a new selection is made. This prevents the panel from
        // reappearing when a long press selection is modified after the user has taken action to
        // get rid of the panel. See crbug.com/489461.
        if (shouldPreventHandlingCurrentSelectionModification(reason)) {
            preventHandlingCurrentSelectionModification();
        }

        // Long press selections should remain visible after ending a Contextual Search.
        if (mSelectionType == SelectionType.TAP) {
            clearSelection();
        }
    }

    /**
     * Returns a new {@code GestureStateListener} that will listen for events in the Base Page.
     * This listener will handle all Contextual Search-related interactions that go through the
     * listener.
     */
    public ContextualSearchGestureStateListener getGestureStateListener() {
        return new ContextualSearchGestureStateListener();
    }

    /**
     * @return the {@link ChromeActivity}.
     */
    ChromeActivity getActivity() {
        // TODO(donnd): don't expose the activity.
        return mActivity;
    }

    /**
     * @return the type of the selection.
     */
    SelectionType getSelectionType() {
        return mSelectionType;
    }

    /**
     * @return the selected text.
     */
    String getSelectedText() {
        return mSelectedText;
    }

    /**
     * @return The Pixel to Device independent Pixel ratio.
     */
    float getPxToDp() {
        return mPxToDp;
    }

    /**
     * @return The time of the most recent scroll, or 0 if none.
     */
    long getLastScrollTime() {
        return mLastScrollTimeNs;
    }

    /**
     * Clears the selection.
     */
    void clearSelection() {
        ContentViewCore baseContentView = getBaseContentView();
        if (baseContentView != null) {
            baseContentView.clearSelection();
        }
        resetAllStates();
    }

    /**
     * Handles a change in the current Selection.
     * @param selection The selection portion of the context.
     */
    void handleSelectionChanged(String selection) {
        if (mDidExpandSelection) {
            mSelectedText = selection;
            mDidExpandSelection = false;
            return;
        }

        if (selection == null || selection.isEmpty()) {
            scheduleInvalidTapNotification();
            // When the user taps on the page it will place the caret in that position, which
            // will trigger a onSelectionChanged event with an empty string.
            if (mSelectionType == SelectionType.TAP) {
                // Since we mostly ignore a selection that's empty, we only need to partially reset.
                resetSelectionStates();
                return;
            }
        }
        if (!selection.isEmpty()) {
            unscheduleInvalidTapNotification();
        }

        mSelectedText = selection;

        if (mWasTapGestureDetected) {
            mSelectionType = SelectionType.TAP;
            handleSelection(selection, mSelectionType);
            mWasTapGestureDetected = false;
        } else {
            boolean isValidSelection = validateSelectionSuppression(selection);
            mHandler.handleSelectionModification(selection, isValidSelection, mX, mY);
        }
    }

    /**
     * Handles a notification that a selection event took place.
     * @param eventType The type of event that took place.
     * @param posXPix The x coordinate of the selection start handle.
     * @param posYPix The y coordinate of the selection start handle.
     */
    void handleSelectionEvent(int eventType, float posXPix, float posYPix) {
        boolean shouldHandleSelection = false;
        switch (eventType) {
            case SelectionEventType.SELECTION_HANDLES_SHOWN:
                if (!mIsContextMenuShown) {
                    mWasTapGestureDetected = false;
                    mSelectionType = SelectionType.LONG_PRESS;
                    shouldHandleSelection = true;
                    // Since we're showing pins, we don't care if the previous tap was invalid
                    // anymore.
                    unscheduleInvalidTapNotification();
                }
                break;
            case SelectionEventType.SELECTION_HANDLES_CLEARED:
                mHandler.handleSelectionDismissal();
                resetAllStates();
                break;
            case SelectionEventType.SELECTION_HANDLE_DRAG_STOPPED:
                shouldHandleSelection = mShouldHandleSelectionModification;
                break;
            case SelectionEventType.SELECTION_ESTABLISHED:
                mIsSelectionEstablished = true;
                break;
            case SelectionEventType.SELECTION_DISSOLVED:
                mIsSelectionEstablished = false;
                break;
            default:
        }

        if (shouldHandleSelection) {
            ContentViewCore baseContentView = getBaseContentView();
            if (baseContentView != null) {
                String selection = baseContentView.getSelectedText();
                if (selection != null) {
                    mX = posXPix;
                    mY = posYPix;
                    mSelectedText = selection;
                    handleSelection(selection, SelectionType.LONG_PRESS);
                }
            }
        }
    }

    /**
     * Re-enables selection modification handling and invokes
     * ContextualSearchSelectionHandler.handleSelection().
     * @param selection The text that was selected.
     * @param type The type of selection made by the user.
     */
    private void handleSelection(String selection, SelectionType type) {
        mShouldHandleSelectionModification = true;
        boolean isValidSelection = validateSelectionSuppression(selection);
        mHandler.handleSelection(selection, isValidSelection, type, mX, mY);
    }

    /**
     * Resets all internal state of this class, including the tap state.
     */
    private void resetAllStates() {
        resetSelectionStates();
        mLastTapState = null;
        mLastScrollTimeNs = 0;
        mIsContextMenuShown = false;
    }

    /**
     * Resets all of the internal state of this class that handles the selection.
     */
    private void resetSelectionStates() {
        mSelectionType = SelectionType.UNDETERMINED;
        mSelectedText = null;

        mWasTapGestureDetected = false;
    }

    /**
     * Should be called when a new Tab is selected.
     * Resets all of the internal state of this class.
     */
    void onTabSelected() {
        resetAllStates();
    }

    /**
     * Handles an unhandled tap gesture.
     */
    void handleShowUnhandledTapUIIfNeeded(int x, int y) {
        mWasTapGestureDetected = false;
        // TODO(donnd): shouldn't we check == TAP here instead of LONG_PRESS?
        // TODO(donnd): refactor to avoid needing a new handler API method as suggested by Pedro.
        if (mSelectionType != SelectionType.LONG_PRESS) {
            mWasTapGestureDetected = true;
            long tapTimeNanoseconds = System.nanoTime();
            // TODO(donnd): add a policy method to get adjusted tap count.
            ChromePreferenceManager prefs = ChromePreferenceManager.getInstance(mActivity);
            int adjustedTapsSinceOpen = prefs.getContextualSearchTapCount()
                    - prefs.getContextualSearchTapQuickAnswerCount();
            // Explicitly destroy the old heuristics so native code can dispose data.
            if (mTapHeuristics != null) mTapHeuristics.destroy();
            mTapHeuristics =
                    new TapSuppressionHeuristics(this, mLastTapState, x, y, adjustedTapsSinceOpen);
            // TODO(donnd): Move to be called when the panel closes to work with states that change.
            mTapHeuristics.logConditionState();
            // Tell the manager what it needs in order to log metrics on whether the tap would have
            // been suppressed if each of the heuristics were satisfied.
            mHandler.handleMetricsForWouldSuppressTap(mTapHeuristics);
            mX = x;
            mY = y;
            boolean shouldSuppressTap = mTapHeuristics.shouldSuppressTap();
            if (shouldSuppressTap) {
                mHandler.handleSuppressedTap();
            } else {
                // TODO(donnd): Find a better way to determine that a navigation will be triggered
                // by the tap, or merge with other time-consuming actions like gathering surrounding
                // text or detecting page mutations.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mHandler.handleValidTap();
                    }
                }, TAP_NAVIGATION_DETECTION_DELAY);
            }
            // Remember the tap state for subsequent tap evaluation.
            mLastTapState =
                    new ContextualSearchTapState(x, y, tapTimeNanoseconds, shouldSuppressTap);
        } else {
            // Long press; reset last tap state.
            mLastTapState = null;
            mHandler.handleInvalidTap();
        }
    }

    /**
     * @return The Base Page's {@link ContentViewCore}, or {@code null} if there is no current tab.
     */
    ContentViewCore getBaseContentView() {
        Tab currentTab = mActivity.getActivityTab();
        return currentTab != null ? currentTab.getContentViewCore() : null;
    }

    /**
     * Expands the current selection by the specified amounts.
     * @param selectionStartAdjust The start offset adjustment of the selection to use to highlight
     *                             the search term.
     * @param selectionEndAdjust The end offset adjustment of the selection to use to highlight
     *                           the search term.
     */
    void adjustSelection(int selectionStartAdjust, int selectionEndAdjust) {
        // TODO(donnd): add code to verify that the selection is still valid before changing it.
        // crbug.com/508354

        if (selectionStartAdjust == 0 && selectionEndAdjust == 0) return;
        ContentViewCore basePageContentView = getBaseContentView();
        if (basePageContentView != null && basePageContentView.getWebContents() != null) {
            mDidExpandSelection = true;
            basePageContentView.getWebContents().adjustSelectionByCharacterOffset(
                    selectionStartAdjust, selectionEndAdjust);
        }
    }

    // ============================================================================================
    // Invalid Tap Notification
    // ============================================================================================

    /**
     * Schedules a notification to check if the tap was invalid.
     * When we call selectWordAroundCaret it selects nothing in cases where the tap was invalid.
     * We have no way to know other than scheduling a notification to check later.
     * This allows us to hide the bar when there's no selection.
     */
    private void scheduleInvalidTapNotification() {
        // TODO(donnd): Fix selectWordAroundCaret to we can tell if it selects, instead
        // of using a timer here!  See crbug.com/435778.
        mRunnableHandler.postDelayed(mHandleInvalidTapRunnable,
                INVALID_IF_NO_SELECTION_CHANGE_AFTER_TAP_MS);
    }

    /**
     * Un-schedules all pending notifications to check if a tap was invalid.
     */
    private void unscheduleInvalidTapNotification() {
        mRunnableHandler.removeCallbacks(mHandleInvalidTapRunnable);
        mIsWaitingForInvalidTapDetection = true;
    }

    /**
     * Notify's the system that tap gesture has been completed.
     */
    private void onInvalidTapDetectionTimeout() {
        mHandler.handleInvalidTap();
        mIsWaitingForInvalidTapDetection = false;
    }

    // ============================================================================================
    // Selection Modification
    // ============================================================================================

    /**
     * This method checks whether the selection modification should be handled. This method
     * is needed to allow modifying selections that are occluded by the Panel.
     * See crbug.com/489461.
     *
     * @param reason The reason the panel is closing.
     * @return Whether the selection modification should be handled.
     */
    private boolean shouldPreventHandlingCurrentSelectionModification(
            OverlayPanel.StateChangeReason reason) {
        return getSelectionType() == SelectionType.LONG_PRESS
                && (reason == OverlayPanel.StateChangeReason.BACK_PRESS
                || reason == OverlayPanel.StateChangeReason.BASE_PAGE_SCROLL
                || reason == OverlayPanel.StateChangeReason.SWIPE
                || reason == OverlayPanel.StateChangeReason.FLING
                || reason == OverlayPanel.StateChangeReason.CLOSE_BUTTON);
    }

    /**
     * Temporarily prevents the controller from handling selection modification events on the
     * current selection. Handling will be re-enabled when a new selection is made through either a
     * tap or long press.
     */
    private void preventHandlingCurrentSelectionModification() {
        mShouldHandleSelectionModification = false;
    }

    // ============================================================================================
    // Misc.
    // ============================================================================================

    /**
     * @return whether a tap gesture has been detected, for testing.
     */
    @VisibleForTesting
    boolean wasAnyTapGestureDetected() {
        return mIsWaitingForInvalidTapDetection;
    }

    /**
     * @return whether the selection has been established, for testing.
     */
    @VisibleForTesting
    boolean isSelectionEstablished() {
        return mIsSelectionEstablished;
    }

    /**
     * Evaluates whether the given selection is valid and notifies the handler about potential
     * selection suppression.
     * TODO(pedrosimonetti): substitute this once the system supports suppressing selections.
     * @param selection The given selection.
     * @return Whether the selection is valid.
     */
    private boolean validateSelectionSuppression(String selection) {
        boolean isValid = isValidSelection(selection);

        if (mSelectionType == SelectionType.TAP) {
            BlacklistReason reason =
                    ContextualSearchBlacklist.findReasonToSuppressSelection(selection);

            mHandler.handleSelectionSuppression(reason);

            // Only really suppress if enabled by field trial. Currently we can't prevent a
            // selection from being issued, so we end up clearing the selection immediately
            // afterwards, which does not look great.
            // TODO(pedrosimonetti): actually suppress selection once the system supports it.
            if (ContextualSearchFieldTrial.isBlacklistEnabled() && reason != BlacklistReason.NONE) {
                isValid = false;
            }
        }

        return isValid;
    }

    /** Determines if the given selection is valid or not.
     * @param selection The selection portion of the context.
     * @return whether the given selection is considered a valid target for a search.
     */
    private boolean isValidSelection(String selection) {
        return isValidSelection(selection, getBaseContentView());
    }

    @VisibleForTesting
    boolean isValidSelection(String selection, ContentViewCore baseContentView) {
        if (selection.length() > MAX_SELECTION_LENGTH) {
            return false;
        }

        if (!doesContainAWord(selection)) {
            return false;
        }

        if (baseContentView != null && baseContentView.isFocusedNodeEditable()) {
            return false;
        }

        return true;
    }

    /**
     * Determines if the given selection contains a word or not.
     * @param selection The the selection to check for a word.
     * @return Whether the selection contains a word anywhere within it or not.
     */
    @VisibleForTesting
    public boolean doesContainAWord(String selection) {
        return mContainsWordPattern.matcher(selection).find();
    }

    /**
     * @param selectionContext The String including the surrounding text and the selection.
     * @param startOffset The offset to the start of the selection (inclusive).
     * @param endOffset The offset to the end of the selection (non-inclusive).
     * @return Whether the selection is part of URL. A valid URL is:
     *         0-1:  schema://
     *         1+:   any word char, _ or -
     *         1+:   . followed by 1+ of any word char, _ or -
     *         0-1:  0+ of any word char or .,@?^=%&:/~#- followed by any word char or @?^-%&/~+#-
     */
    public static boolean isSelectionPartOfUrl(String selectionContext, int startOffset,
            int endOffset) {
        Matcher matcher = URL_PATTERN.matcher(selectionContext);

        // Starts are inclusive and ends are non-inclusive for both GSAContext & matcher.
        while (matcher.find()) {
            if (startOffset >= matcher.start() && endOffset <= matcher.end()) {
                return true;
            }
        }

        return false;
    }
}
