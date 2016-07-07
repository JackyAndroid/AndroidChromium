// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.eventfilter;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel;

import java.util.ArrayList;

/**
 * The {@link GestureEventFilter} used when Contextual Search Layout is being shown. It filters
 * events that happen in the Search Content View area and propagates them to the appropriate
 * Content View Core via {@link EventFilterHost}. Events that happen outside that area are
 * propagated to the {@code ContextualSearchLayout} via {@code LayoutManagerPhone}.
 */
public class ContextualSearchEventFilter extends GestureEventFilter {

    /**
     * The targets that can handle MotionEvents.
     */
    private enum EventTarget {
        UNDETERMINED,
        SEARCH_PANEL,
        SEARCH_CONTENT_VIEW
    }

    /**
     * The direction of the gesture.
     */
    private enum GestureOrientation {
        UNDETERMINED,
        HORIZONTAL,
        VERTICAL
    }

    /**
     * The boost factor that can be applied to prioritize vertical movements over horizontal ones.
     */
    private static final float VERTICAL_DETERMINATION_BOOST = 1.25f;

    /**
     * The shared state of the UI.
     */
    private ContextualSearchPanel mSearchPanel;

    /**
     * The {@link GestureDetector} used to distinguish tap and scroll gestures.
     */
    private final GestureDetector mGestureDetector;

    /**
     * The target to propagate events to.
     */
    private EventTarget mEventTarget;

    /**
     * Whether the code is in the middle of the process of determining the event target.
     */
    private boolean mIsDeterminingEventTarget;

    /**
     * Whether the event target has been determined.
     */
    private boolean mHasDeterminedEventTarget;

    /**
     * The previous target the events were propagated to.
     */
    private EventTarget mPreviousEventTarget;

    /**
     * Whether the event target has changed since the last touch event.
     */
    private boolean mHasChangedEventTarget;

    /**
     * Whether the event target might change. This will be true in cases we know the overscroll
     * and/or underscroll might happen, which means we'll have to constantly monitor the event
     * targets in order to determine the exact moment the target has changed.
     */
    private boolean mMayChangeEventTarget;

    /**
     * Whether the gesture orientation has been determined.
     */
    private boolean mHasDeterminedGestureOrientation;

    /**
     * The current gesture orientation.
     */
    private GestureOrientation mGestureOrientation;

    /**
     * Whether the events are being recorded.
     */
    private boolean mIsRecordingEvents;

    /**
     * Whether the ACTION_DOWN that initiated the MotionEvent's stream was synthetic.
     */
    private boolean mWasActionDownEventSynthetic;

    /**
     * The X coordinate of the synthetic ACTION_DOWN MotionEvent.
     */
    private float mSyntheticActionDownX;

    /**
     * The Y coordinate of the synthetic ACTION_DOWN MotionEvent.
     */
    private float mSyntheticActionDownY;

    /**
     * The list of recorded events.
     */
    private final ArrayList<MotionEvent> mRecordedEvents = new ArrayList<MotionEvent>();

    /**
     * The initial Y position of the current gesture.
     */
    private float mInitialEventY;

    /**
     * The square of ViewConfiguration.getScaledTouchSlop() in pixels used to calculate whether
     * the finger has moved beyond the established threshold.
     */
    private final float mTouchSlopSquarePx;

    /**
     * Creates a {@link GestureEventFilter} with offset touch events.
     */
    public ContextualSearchEventFilter(Context context, EventFilterHost host,
            GestureHandler handler, ContextualSearchPanel contextualSearchPanel) {
        super(context, host, handler, false, false);

        mGestureDetector = new GestureDetector(context, new InternalGestureDetector());
        mSearchPanel = contextualSearchPanel;

        // Store the square of the platform touch slop in pixels to use in the scroll detection.
        // See {@link ContextualSearchEventFilter#isDistanceGreaterThanTouchSlop}.
        float touchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mTouchSlopSquarePx = touchSlopPx * touchSlopPx;

        reset();
    }

    /**
     * Gets the Search Content View's vertical scroll position. If the Search Content View
     * is not available it returns -1.
     * @return The Search Content View scroll position.
     */
    @VisibleForTesting
    protected float getSearchContentViewVerticalScroll() {
        return mSearchPanel.getContentVerticalScroll();
    }

    @Override
    public boolean onTouchEventInternal(MotionEvent e) {
        final int action = e.getActionMasked();

        if (!mIsDeterminingEventTarget && action == MotionEvent.ACTION_DOWN) {
            mInitialEventY = e.getY();
            if (mSearchPanel.isCoordinateInsideContent(
                    e.getX() * mPxToDp, mInitialEventY * mPxToDp)) {
                // If the DOWN event happened inside the Search Content View, we'll need
                // to wait until the user has moved the finger beyond a certain threshold,
                // so we can determine the gesture's orientation and consequently be able
                // to tell if the Content View will accept the gesture.
                mIsDeterminingEventTarget = true;
                mMayChangeEventTarget = true;
            } else {
                // If the DOWN event happened outside the Search Content View, then we know
                // that the Search Panel will start handling the event right away.
                setEventTarget(EventTarget.SEARCH_PANEL);
                mMayChangeEventTarget = false;
            }
        }

        // Send the event to the GestureDetector so we can distinguish between scroll and tap.
        mGestureDetector.onTouchEvent(e);

        if (mHasDeterminedEventTarget) {
            // If the event target has been determined, resume pending events, then propagate
            // the current event to the appropriate target.
            resumeAndPropagateEvent(e);
        } else {
            // If the event target has not been determined, we need to record a copy of the event
            // until we are able to determine the event target.
            MotionEvent event = MotionEvent.obtain(e);
            mRecordedEvents.add(event);
            mIsRecordingEvents = true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            reset();
        }

        return true;
    }

    /**
     * Resets the current and previous {@link EventTarget} as well the {@link GestureOrientation}
     * to the UNDETERMINED state.
     */
    private void reset() {
        mEventTarget = EventTarget.UNDETERMINED;
        mIsDeterminingEventTarget = false;
        mHasDeterminedEventTarget = false;

        mPreviousEventTarget = EventTarget.UNDETERMINED;
        mHasChangedEventTarget = false;
        mMayChangeEventTarget = false;

        mWasActionDownEventSynthetic = false;

        mGestureOrientation = GestureOrientation.UNDETERMINED;
        mHasDeterminedGestureOrientation = false;
    }

    /**
     * Resumes pending events then propagates the given event to the current {@link EventTarget}.
     *
     * Resuming events might consist in simply propagating previously recorded events if the
     * EventTarget was UNDETERMINED when the gesture started.
     *
     * For the case where the EventTarget has changed during the course of the gesture, we'll
     * need to simulate a gesture end in the previous target (by simulating an ACTION_CANCEL
     * event) and a gesture start in the new target (by simulating an ACTION_DOWN event).
     *
     * @param e The {@link MotionEvent} to be propagated after resuming the pending events.
     */
    private void resumeAndPropagateEvent(MotionEvent e) {
        if (mIsRecordingEvents) {
            resumeRecordedEvents();
        }

        if (mHasChangedEventTarget) {
            // If the event target has changed since the beginning of the gesture, then we need
            // to send a ACTION_CANCEL to the previous event target to make sure it no longer
            // expects events.
            propagateAndRecycleEvent(copyEvent(e, MotionEvent.ACTION_CANCEL), mPreviousEventTarget);

            // Similarly we need to send an ACTION_DOWN to the new event target so subsequent
            // events can be analyzed properly by the Gesture Detector.
            MotionEvent syntheticActionDownEvent = copyEvent(e, MotionEvent.ACTION_DOWN);

            // Store the synthetic ACTION_DOWN coordinates to prevent unwanted taps from
            // happening. See {@link ContextualSearchEventFilter#propagateEventToSearchContentView}.
            mWasActionDownEventSynthetic = true;
            mSyntheticActionDownX = syntheticActionDownEvent.getX();
            mSyntheticActionDownY = syntheticActionDownEvent.getY()
                    - mSearchPanel.getContentY() / mPxToDp;

            propagateAndRecycleEvent(syntheticActionDownEvent, mEventTarget);

            mHasChangedEventTarget = false;
        }

        propagateEvent(e, mEventTarget);
    }

    /**
     * Resumes recorded events by propagating all of them to the current {@link EventTarget}.
     */
    private void resumeRecordedEvents() {
        for (int i = 0, size = mRecordedEvents.size(); i < size; i++) {
            propagateAndRecycleEvent(mRecordedEvents.get(i), mEventTarget);
        }

        mRecordedEvents.clear();
        mIsRecordingEvents = false;
    }

    /**
     * Propagates the given {@link MotionEvent} to the given {@link EventTarget}, recycling it
     * afterwards. This is intended for synthetic events only, those create by
     * {@link MotionEvent#obtain} or the helper methods
     * {@link ContextualSearchEventFilter#lockEventHorizontallty} and
     * {@link ContextualSearchEventFilter#copyEvent}.
     *
     * @param e The {@link MotionEvent} to be propagated.
     * @param target The {@link EventTarget} to propagate events to.
     */
    private void propagateAndRecycleEvent(MotionEvent e, EventTarget target) {
        propagateEvent(e, target);
        e.recycle();
    }

    /**
     * Propagates the given {@link MotionEvent} to the given {@link EventTarget}.
     * @param e The {@link MotionEvent} to be propagated.
     * @param target The {@link EventTarget} to propagate events to.
     */
    private void propagateEvent(MotionEvent e, EventTarget target) {
        if (target == EventTarget.SEARCH_PANEL) {
            super.onTouchEventInternal(e);
        } else if (target == EventTarget.SEARCH_CONTENT_VIEW) {
            propagateEventToSearchContentView(e);
        }
    }

    /**
     * Propagates the given {@link MotionEvent} to the Search Content View.
     * @param e The {@link MotionEvent} to be propagated.
     */
    @VisibleForTesting
    protected void propagateEventToSearchContentView(MotionEvent e) {
        MotionEvent event = e;
        int action = event.getActionMasked();
        boolean isSyntheticEvent = false;
        if (mGestureOrientation == GestureOrientation.HORIZONTAL
                && !mSearchPanel.isMaximized()) {
            // Ignores multitouch events to prevent the Search Result Page from from scrolling.
            if (action == MotionEvent.ACTION_POINTER_UP
                    || action == MotionEvent.ACTION_POINTER_DOWN) {
                return;
            }

            // NOTE(pedrosimonetti): Lock horizontal motion, ignoring all vertical changes,
            // when the Panel is not maximized. This is to prevent the Search Result Page
            // from scrolling when side swiping on the expanded Panel. Also, note that the
            // method {@link ContextualSearchEventFilter#lockEventHorizontallty} will always
            // return an event with a single pointer, which is necessary to prevent
            // the app from crashing when the motion involves multiple pointers.
            // See: crbug.com/486901
            event = MotionEvent.obtain(
                    e.getDownTime(),
                    e.getEventTime(),
                    // NOTE(pedrosimonetti): Use getActionMasked() to make sure we're not
                    // send any pointer information to the event, given that getAction()
                    // may have the pointer Id associated to it.
                    e.getActionMasked(),
                    e.getX(),
                    mInitialEventY,
                    e.getMetaState());

            isSyntheticEvent = true;
        }

        final float contentViewOffsetXPx = mSearchPanel.getContentX() / mPxToDp;
        final float contentViewOffsetYPx = mSearchPanel.getContentY() / mPxToDp;

        // Adjust the offset to be relative to the Search Contents View.
        event.offsetLocation(-contentViewOffsetXPx, -contentViewOffsetYPx);

        boolean wasEventCanceled = false;
        if (mWasActionDownEventSynthetic && action == MotionEvent.ACTION_UP) {
            float deltaX = event.getX() - mSyntheticActionDownX;
            float deltaY = event.getY() - mSyntheticActionDownY;
            // NOTE(pedrosimonetti): If the ACTION_DOWN event was synthetic and the distance
            // between it and the ACTION_UP event was short, then we should synthesize an
            // ACTION_CANCEL event to prevent a Tap gesture from being triggered on the Search
            // Content View. See crbug.com/408654
            if (!isDistanceGreaterThanTouchSlop(deltaX, deltaY)) {
                event.setAction(MotionEvent.ACTION_CANCEL);
                mHost.propagateEvent(event);
                wasEventCanceled = true;
            }
        } else if (action == MotionEvent.ACTION_DOWN) {
            mSearchPanel.onTouchSearchContentViewAck();
        }

        // Propagate the event to the appropriate view
        if (!wasEventCanceled) mHost.propagateEvent(event);

        // Synthetic events should be recycled.
        if (isSyntheticEvent) event.recycle();
    }

    /**
     * Creates a {@link MotionEvent} inheriting from a given |e| event.
     * @param e The {@link MotionEvent} to inherit properties from.
     * @param action The MotionEvent's Action to be used.
     * @return A new {@link MotionEvent}.
     */
    private MotionEvent copyEvent(MotionEvent e, int action) {
        MotionEvent event = MotionEvent.obtain(e);
        event.setAction(action);
        return event;
    }

    /**
     * Handles the tap event, determining the event target.
     * @param e The tap {@link MotionEvent}.
     * @return Whether the event has been consumed.
     */
    private boolean handleSingleTapUp(MotionEvent e) {
        setEventTarget(mSearchPanel.isCoordinateInsideContent(
                e.getX() * mPxToDp, e.getY() * mPxToDp)
                ? EventTarget.SEARCH_CONTENT_VIEW : EventTarget.SEARCH_PANEL);

        return false;
    }

    /**
     * Handles the scroll event, determining the gesture orientation and event target,
     * when appropriate.
     * @param e1 The first down {@link MotionEvent} that started the scrolling.
     * @param e2 The move {@link MotionEvent} that triggered the current scroll.
     * @param distanceY The distance along the Y axis that has been scrolled since the last call
     *                  to handleScroll.
     * @return Whether the event has been consumed.
     */
    private boolean handleScroll(MotionEvent e1, MotionEvent e2, float distanceY) {
        // Only determines the gesture orientation if it hasn't been determined yet,
        // affectively "locking" the orientation once the gesture has started.
        if (!mHasDeterminedGestureOrientation && isDistanceGreaterThanTouchSlop(e1, e2)) {
            determineGestureOrientation(e1, e2);
        }

        // Only determines the event target after determining the gesture orientation and
        // if it hasn't been determined yet or if changing the event target during the
        // middle of the gesture is supported. This will allow a smooth transition from
        // swiping the Panel and scrolling the Search Content View.
        final boolean mayChangeEventTarget = mMayChangeEventTarget && e2.getPointerCount() == 1;
        if (mHasDeterminedGestureOrientation
                && (!mHasDeterminedEventTarget || mayChangeEventTarget)) {
            determineEventTarget(distanceY);
        }

        return false;
    }

    /**
     * Determines the gesture orientation.
     * @param e1 The first down {@link MotionEvent} that started the scrolling.
     * @param e2 The move {@link MotionEvent} that triggered the current scroll.
     */
    private void determineGestureOrientation(MotionEvent e1, MotionEvent e2) {
        float deltaX = Math.abs(e2.getX() - e1.getX());
        float deltaY = Math.abs(e2.getY() - e1.getY());
        mGestureOrientation = deltaY * VERTICAL_DETERMINATION_BOOST > deltaX
                ? GestureOrientation.VERTICAL : GestureOrientation.HORIZONTAL;
        mHasDeterminedGestureOrientation = true;
    }

    /**
     * Determines the target to propagate events to. This will not only update the
     * {@code mEventTarget} but also save the previous target and determine whether the
     * target has changed.
     * @param distanceY The distance along the Y axis that has been scrolled since the last call
     *                  to handleScroll.
     */
    private void determineEventTarget(float distanceY) {
        boolean isVertical = mGestureOrientation == GestureOrientation.VERTICAL;

        boolean shouldPropagateEventsToSearchPanel;
        if (mSearchPanel.isMaximized()) {
            // Allow overscroll in the Search Content View to move the Search Panel instead
            // of scrolling the Search Result Page.
            boolean isMovingDown = distanceY < 0;
            shouldPropagateEventsToSearchPanel = isVertical
                    && isMovingDown
                    && getSearchContentViewVerticalScroll() == 0;
        } else {
            // Only allow horizontal movements to be propagated to the Search Content View
            // when the Panel is expanded (that is, not maximized).
            shouldPropagateEventsToSearchPanel = isVertical;

            // If the gesture is horizontal, then we know that the event target won't change.
            if (!isVertical) mMayChangeEventTarget = false;
        }

        EventTarget target = shouldPropagateEventsToSearchPanel
                ? EventTarget.SEARCH_PANEL : EventTarget.SEARCH_CONTENT_VIEW;

        if (target != mEventTarget) {
            mPreviousEventTarget = mEventTarget;
            setEventTarget(target);

            mHasChangedEventTarget = mEventTarget != mPreviousEventTarget
                    && mPreviousEventTarget != EventTarget.UNDETERMINED;
        }
    }

    /**
     * Sets the {@link EventTarget}.
     * @param target The {@link EventTarget} to be set.
     */
    private void setEventTarget(EventTarget target) {
        mEventTarget = target;

        mIsDeterminingEventTarget = false;
        mHasDeterminedEventTarget = true;
    }

    /**
     * @param e1 The first down {@link MotionEvent} that started the scrolling.
     * @param e2 The move {@link MotionEvent} that triggered the current scroll.
     * @return Whether the distance is greater than the touch slop threshold.
     */
    private boolean isDistanceGreaterThanTouchSlop(MotionEvent e1, MotionEvent e2) {
        float deltaX = e2.getX() - e1.getX();
        float deltaY = e2.getY() - e1.getY();
        // Check if the distance between the events |e1| and |e2| is greater than the touch slop.
        return isDistanceGreaterThanTouchSlop(deltaX, deltaY);
    }

    /**
     * @param deltaX The delta X in pixels.
     * @param deltaY The delta Y in pixels.
     * @return Whether the distance is greater than the touch slop threshold.
     */
    private boolean isDistanceGreaterThanTouchSlop(float deltaX, float deltaY) {
        return deltaX * deltaX + deltaY * deltaY > mTouchSlopSquarePx;
    }

    /**
     * Internal GestureDetector class that is responsible for determining the event target.
     */
    private class InternalGestureDetector extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return handleSingleTapUp(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return handleScroll(e1, e2, distanceY);
        }
    }
}
