// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.os.Handler;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.ui.touch_selection.SelectionEventType;

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
    private static final double RETAP_DISTANCE_SQUARED_DP = Math.pow(75, 2);

    // The default navigation-detection-delay in milliseconds.
    private static final int TAP_NAVIGATION_DETECTION_DELAY = 16;

    private static final String CONTAINS_WORD_PATTERN = "(\\w|\\p{L}|\\p{N})+";

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
    private boolean mWasLastTapValid;
    private boolean mIsWaitingForInvalidTapDetection;
    private boolean mIsSelectionEstablished;
    private boolean mShouldHandleSelectionModification;
    private boolean mDidExpandSelection;

    private float mX;
    private float mY;

    private class ContextualSearchGestureStateListener extends GestureStateListener {
        @Override
        public void onScrollStarted(int scrollOffsetY, int scrollExtentY) {
            mHandler.handleScroll();
        }

        // TODO(donnd): Remove this once we get notification of the selection changing
        // after a tap-select gets a subsequent tap nearby.  Currently there's no
        // notification in this case.
        // See crbug.com/444114.
        @Override
        public void onSingleTap(boolean consumed, int x, int y) {
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
     * Returns a new {@code GestureStateListener} that will listen for events in the Base Page.
     * This listener will handle all Contextual Search-related interactions that go through the
     * listener.
     */
    public ContextualSearchGestureStateListener getGestureStateListener() {
        return new ContextualSearchGestureStateListener();
    }

    /**
     * Temporarily prevents the controller from handling selection modification events on the
     * current selection. Handling will be re-enabled when a new selection is made through either a
     * tap or long press.
     */
    public void preventHandlingCurrentSelectionModification() {
        mShouldHandleSelectionModification = false;
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
            mHandler.handleSelectionModification(selection, isValidSelection(selection), mX, mY);
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
                mWasTapGestureDetected = false;
                mSelectionType = SelectionType.LONG_PRESS;
                shouldHandleSelection = true;
                // Since we're showing pins, we don't care if the previous tap was invalid anymore.
                unscheduleInvalidTapNotification();
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
        mHandler.handleSelection(selection, isValidSelection(selection), type, mX, mY);
    }

    /**
     * Resets all internal state of this class, including the tap state.
     */
    private void resetAllStates() {
        resetSelectionStates();
        mWasLastTapValid = false;
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
     * Handles an unhandled tap gesture.
     */
    void handleShowUnhandledTapUIIfNeeded(int x, int y) {
        mWasTapGestureDetected = false;
        if (mSelectionType != SelectionType.LONG_PRESS && shouldHandleTap(x, y)) {
            mX = x;
            mY = y;
            mWasLastTapValid = true;
            mWasTapGestureDetected = true;
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
        if (!mWasTapGestureDetected) {
            mWasLastTapValid = false;
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

    /**
     * @return whether a tap at the given coordinates should be handled or not.
     */
    private boolean shouldHandleTap(int x, int y) {
        return !mWasLastTapValid || wasTapCloseToPreviousTap(x, y);
    }

    /**
     * Determines whether a tap at the given coordinates is considered "close" to the previous
     * tap.
     */
    private boolean wasTapCloseToPreviousTap(int x, int y) {
        float deltaXDp = (mX - x) * mPxToDp;
        float deltaYDp = (mY - y) * mPxToDp;
        float distanceSquaredDp =  deltaXDp * deltaXDp + deltaYDp * deltaYDp;
        return distanceSquaredDp <= RETAP_DISTANCE_SQUARED_DP;
    }

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

    /** Determines if the given selection is valid or not.
     * @param selection The selection portion of the context.
     * @return whether the given selection is considered a valid target for a search.
     */
    private boolean isValidSelection(String selection) {
        return isValidSelection(selection, getBaseContentView());
    }

    @VisibleForTesting
    boolean isValidSelection(String selection, ContentViewCore baseContentView) {
        if (selection.length() > MAX_SELECTION_LENGTH || !doesContainAWord(selection)) {
            return false;
        }
        return baseContentView != null && !baseContentView.isFocusedNodeEditable();
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
}
