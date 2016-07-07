// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import android.content.Context;
import android.view.MotionEvent;

import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilterHost;
import org.chromium.chrome.browser.contextualsearch.SwipeRecognizer;

/**
 * A {@link EventFilter} used to filter events in the Reader Mode Bar, when displayed
 * in the StaticLayout.
 */
public class ReaderModeStaticEventFilter extends EventFilter {
    /**
     * The @{link ReaderModePanelSelector} that gives access to a panel controlling Reader Mode UI.
     */
    private final ReaderModePanelSelector mReaderModePanelSelector;

    /**
     * The @{link SwipeRecognizer} that recognizes directional swipe gestures.
     */
    private final SwipeRecognizer mSwipeRecognizer;

    private final ReaderModeTapHandler mTapHandler;

    /**
     * Interface to handle taps on the reader mode bar.
     */
    public interface ReaderModeTapHandler {
        /**
         * Handle a tap event on the reader mode bar.
         * @param time The time of the tap event.
         * @param x The x position of the tap event.
         * @param y The y position of the tap event.
         */
        void handleTapReaderModeBar(long time, float x, float y);
    }

    /**
     * Interface to get the currently active Reader Mode Panel if any.
     */
    public interface ReaderModePanelSelector {
        /**
         * @return Currently active reader mode panel, or null.
         */
        ReaderModePanel getActiveReaderModePanel();
    }

    /**
     * Constructs a {@link ReaderModeStaticEventFilter}.
     *
     * @param context The current Android {@link Context}.
     * @param host The @{link EventFilterHost} associated to this filter.
     * @param readerModePanelSelector The @{link ReaderModePanelSelector} to access an active panel.
     * @param swipeHandler The @{link EdgeSwipeHandler} for Reader Mode events.
     */
    public ReaderModeStaticEventFilter(Context context, EventFilterHost host,
            ReaderModePanelSelector readerModePanelSelector, EdgeSwipeHandler swipeHandler,
            ReaderModeTapHandler tapHandler) {
        super(context, host);

        mReaderModePanelSelector = readerModePanelSelector;
        mSwipeRecognizer = new SwipeRecognizerImpl(context);
        mSwipeRecognizer.setSwipeHandler(swipeHandler);
        mTapHandler = tapHandler;
    }

    @Override
    protected boolean onInterceptTouchEventInternal(MotionEvent event, boolean isKeyboardShowing) {
        ReaderModePanel readerModePanel = mReaderModePanelSelector.getActiveReaderModePanel();
        return readerModePanel != null && readerModePanel.isShowing()
                && readerModePanel.isYCoordinateInsideReaderModePanel(
                        readerModePanel.getFullscreenY(event.getY()) * mPxToDp);
    }

    @Override
    protected boolean onTouchEventInternal(MotionEvent event) {
        mSwipeRecognizer.onTouchEvent(event);
        return true;
    }

    private class SwipeRecognizerImpl extends SwipeRecognizer {
        public SwipeRecognizerImpl(Context context) {
            super(context);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            if (mTapHandler == null) return true;
            ReaderModePanel readerModePanel = mReaderModePanelSelector.getActiveReaderModePanel();
            if (readerModePanel == null) return true;

            mTapHandler.handleTapReaderModeBar(event.getEventTime(),
                    event.getX() * mPxToDp,
                    readerModePanel.getFullscreenY(event.getY()) * mPxToDp);
            return true;
        }
    }
}
