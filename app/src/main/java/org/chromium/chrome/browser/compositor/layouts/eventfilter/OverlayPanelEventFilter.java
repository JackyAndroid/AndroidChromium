// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.eventfilter;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.PanelState;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelManager;
import org.chromium.chrome.browser.contextualsearch.SwipeRecognizer;
import org.chromium.content.browser.ContentViewCore;

import java.util.ArrayList;

/**
 * The {@link GestureEventFilter} used when an overlay panel is being shown. It filters
 * events that happen in the Content View area and propagates them to the appropriate
 * ContentViewCore via {@link EventFilterHost}.
 */
public class OverlayPanelEventFilter extends GestureEventFilter {

    /**
     * The targets that can handle MotionEvents.
     */
    private enum EventTarget {
        UNDETERMINED,
        PANEL,
        CONTENT_VIEW
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

    /** The OverlayPanel that this filter is for. */
    private final OverlayPanel mPanel;

    /** The {@link GestureDetector} used to distinguish tap and scroll gestures. */
    private final GestureDetector mGestureDetector;

    /** The @{link SwipeRecognizer} that recognizes directional swipe gestures. */
    private final SwipeRecognizer mSwipeRecognizer;

    /**
     * The square of ViewConfiguration.getScaledTouchSlop() in pixels used to calculate whether
     * the finger has moved beyond the established threshold.
     */
    private final float mTouchSlopSquarePx;

    /** The target to propagate events to. */
    private EventTarget mEventTarget;

    /** Whether the code is in the middle of the process of determining the event target. */
    private boolean mIsDeterminingEventTarget;

    /** Whether the event target has been determined. */
    private boolean mHasDeterminedEventTarget;

    /** The previous target the events were propagated to. */
    private EventTarget mPreviousEventTarget;

    /** Whether the event target has changed since the last touch event. */
    private boolean mHasChangedEventTarget;

    /**
     * Whether the event target might change. This will be true in cases we know the overscroll
     * and/or underscroll might happen, which means we'll have to constantly monitor the event
     * targets in order to determine the exact moment the target has changed.
     */
    private boolean mMayChangeEventTarget;

    /** Whether the gesture orientation has been determined. */
    private boolean mHasDeterminedGestureOrientation;

    /** The current gesture orientation. */
    private GestureOrientation mGestureOrientation;

    /** Whether the events are being recorded. */
    private boolean mIsRecordingEvents;

    /** Whether the ACTION_DOWN that initiated the MotionEvent's stream was synthetic. */
    private boolean mWasActionDownEventSynthetic;

    /** The X coordinate of the synthetic ACTION_DOWN MotionEvent. */
    private float mSyntheticActionDownX;

    /** The Y coordinate of the synthetic ACTION_DOWN MotionEvent. */
    private float mSyntheticActionDownY;

    /** The list of recorded events. */
    private final ArrayList<MotionEvent> mRecordedEvents = new ArrayList<MotionEvent>();

    /** The initial Y position of the current gesture. */
    private float mInitialEventY;

    private class SwipeRecognizerImpl extends SwipeRecognizer {
        public SwipeRecognizerImpl(Context context) {
            super(context);
            setSwipeHandler(mPanel);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            mPanel.handleClick(event.getEventTime(), event.getX() * mPxToDp,
                    event.getY() * mPxToDp);
            return true;
        }
    }

    /**
     * Creates a {@link GestureEventFilter} with offset touch events.
     * @param context The {@link Context} for Android.
     * @param host The {@link EventFilterHost} for this event filter.
     * @param panelManager The {@link OverlayPanelManager} responsible for showing panels.
     */
    public OverlayPanelEventFilter(Context context, EventFilterHost host, OverlayPanel panel) {
        super(context, host, panel, false, false);

        mGestureDetector = new GestureDetector(context, new InternalGestureDetector());
        mPanel = panel;

        mSwipeRecognizer = new SwipeRecognizerImpl(context);

        // Store the square of the platform touch slop in pixels to use in the scroll detection.
        // See {@link OverlayPanelEventFilter#isDistanceGreaterThanTouchSlop}.
        float touchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mTouchSlopSquarePx = touchSlopPx * touchSlopPx;

        reset();
    }

    /**
     * Gets the Content View's vertical scroll position. If the Content View
     * is not available it returns -1.
     * @return The Content View scroll position.
     */
    @VisibleForTesting
    protected float getContentViewVerticalScroll() {
        return mPanel.getContentVerticalScroll();
    }

    @Override
    public boolean onInterceptTouchEventInternal(MotionEvent e, boolean isKeyboardShowing) {
        if (mPanel.isShowing()
                && (mPanel.isCoordinateInsideOverlayPanel(e.getX() * mPxToDp, e.getY() * mPxToDp)
                // When the Panel is opened, all events should be forwarded to the Panel,
                // even those who are not inside the Panel. This is to prevent any events
                // being forward to the base page when the Panel is expanded.
                || mPanel.isPanelOpened())) {
            return super.onInterceptTouchEventInternal(e, isKeyboardShowing);
        }

        // The event filter will have been recording events before the event target was
        // determined. Clear this cache if the panel is not showing to prevent sending
        // motion events that would start a target's stream with something other than
        // ACTION_DOWN.
        mRecordedEvents.clear();
        reset();

        return false;
    }

    @Override
    public boolean onTouchEventInternal(MotionEvent e) {
        final int action = e.getActionMasked();

        if (mPanel.getPanelState() == PanelState.PEEKED) {
            if (action == MotionEvent.ACTION_DOWN) {
                // To avoid a gray flash of empty content, show the search content
                // view immediately on tap rather than waiting for panel expansion.
                // TODO(pedrosimonetti): Once we implement "side-swipe to dismiss"
                // we'll have to revisit this because we don't want to set the
                // Content View visibility to true when the side-swipe is detected.
                mPanel.notifyBarTouched(e.getX() * mPxToDp);
            }
            mSwipeRecognizer.onTouchEvent(e);
            mGestureDetector.onTouchEvent(e);
            return true;
        }

        if (!mIsDeterminingEventTarget && action == MotionEvent.ACTION_DOWN) {
            mInitialEventY = e.getY();
            if (mPanel.isCoordinateInsideContent(e.getX() * mPxToDp, mInitialEventY * mPxToDp)) {
                // If the DOWN event happened inside the Content View, we'll need
                // to wait until the user has moved the finger beyond a certain threshold,
                // so we can determine the gesture's orientation and consequently be able
                // to tell if the Content View will accept the gesture.
                mIsDeterminingEventTarget = true;
                mMayChangeEventTarget = true;
            } else {
                // If the DOWN event happened outside the Content View, then we know
                // that the Panel will start handling the event right away.
                setEventTarget(EventTarget.PANEL);
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
            // happening. See {@link OverlayPanelEventFilter#propagateEventToContentViewCore}.
            mWasActionDownEventSynthetic = true;
            mSyntheticActionDownX = syntheticActionDownEvent.getX();
            mSyntheticActionDownY =
                    syntheticActionDownEvent.getY() - mPanel.getContentY() / mPxToDp;

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
     * {@link OverlayPanelEventFilter#lockEventHorizontallty} and
     * {@link OverlayPanelEventFilter#copyEvent}.
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
        if (target == EventTarget.PANEL) {
            super.onTouchEventInternal(e);
        } else if (target == EventTarget.CONTENT_VIEW) {
            propagateEventToContentViewCore(e);
        }
    }

    /**
     * Propagates the given {@link MotionEvent} to the {@link ContentViewCore}.
     * @param e The {@link MotionEvent} to be propagated.
     */
    protected void propagateEventToContentViewCore(MotionEvent e) {
        MotionEvent event = e;
        int action = event.getActionMasked();
        boolean isSyntheticEvent = false;
        if (mGestureOrientation == GestureOrientation.HORIZONTAL && !mPanel.isMaximized()) {
            // Ignores multitouch events to prevent the Content View from scrolling.
            if (action == MotionEvent.ACTION_POINTER_UP
                    || action == MotionEvent.ACTION_POINTER_DOWN) {
                return;
            }

            // NOTE(pedrosimonetti): Lock horizontal motion, ignoring all vertical changes,
            // when the Panel is not maximized. This is to prevent the Content View
            // from scrolling when side swiping on the expanded Panel. Also, note that the
            // method {@link OverlayPanelEventFilter#lockEventHorizontallty} will always
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

        final float contentViewOffsetXPx = mPanel.getContentX() / mPxToDp;
        final float contentViewOffsetYPx = mPanel.getContentY() / mPxToDp;

        // Adjust the offset to be relative to the Content View.
        event.offsetLocation(-contentViewOffsetXPx, -contentViewOffsetYPx);

        // Get the container view to propagate the event to.
        ContentViewCore cvc = mPanel.getContentViewCore();
        ViewGroup containerView = cvc == null ? null : cvc.getContainerView();

        boolean wasEventCanceled = false;
        if (mWasActionDownEventSynthetic && action == MotionEvent.ACTION_UP) {
            float deltaX = event.getX() - mSyntheticActionDownX;
            float deltaY = event.getY() - mSyntheticActionDownY;
            // NOTE(pedrosimonetti): If the ACTION_DOWN event was synthetic and the distance
            // between it and the ACTION_UP event was short, then we should synthesize an
            // ACTION_CANCEL event to prevent a Tap gesture from being triggered on the
            // Content View. See crbug.com/408654
            if (!isDistanceGreaterThanTouchSlop(deltaX, deltaY)) {
                event.setAction(MotionEvent.ACTION_CANCEL);
                if (containerView != null) containerView.dispatchTouchEvent(event);
                wasEventCanceled = true;
            }
        } else if (action == MotionEvent.ACTION_DOWN) {
            mPanel.onTouchSearchContentViewAck();
        }

        if (!wasEventCanceled && containerView != null) containerView.dispatchTouchEvent(event);

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
    protected boolean handleSingleTapUp(MotionEvent e) {
        // If the panel is peeking then the panel was already notified in #onTouchEventInternal().
        if (mPanel.getPanelState() == PanelState.PEEKED) return false;

        setEventTarget(mPanel.isCoordinateInsideContent(
                e.getX() * mPxToDp, e.getY() * mPxToDp)
                ? EventTarget.CONTENT_VIEW : EventTarget.PANEL);
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
    protected boolean handleScroll(MotionEvent e1, MotionEvent e2, float distanceY) {
        // TODO(mdjones): It seems impossible that either of the two MotionEvents passed into this
        // function would be null provided the InternalGestureDetector checks them. However, it
        // still seems to be possible...
        if (e1 == null || e2 == null) return false;

        // If the panel is peeking then the swipe recognizer will handle the scroll event.
        if (mPanel.getPanelState() == PanelState.PEEKED) return false;

        // Only determines the gesture orientation if it hasn't been determined yet,
        // affectively "locking" the orientation once the gesture has started.
        if (!mHasDeterminedGestureOrientation && isDistanceGreaterThanTouchSlop(e1, e2)) {
            determineGestureOrientation(e1, e2);
        }

        // Only determines the event target after determining the gesture orientation and
        // if it hasn't been determined yet or if changing the event target during the
        // middle of the gesture is supported. This will allow a smooth transition from
        // swiping the Panel and scrolling the Content View.
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

        boolean shouldPropagateEventsToPanel;
        if (mPanel.isMaximized()) {
            // Allow overscroll in the Content View to move the Panel.
            boolean isMovingDown = distanceY < 0;
            shouldPropagateEventsToPanel = isVertical
                    && isMovingDown
                    && getContentViewVerticalScroll() == 0;
        } else {
            // Only allow horizontal movements to be propagated to the Content View
            // when the Panel is expanded (that is, not maximized).
            shouldPropagateEventsToPanel = isVertical;

            // If the gesture is horizontal, then we know that the event target won't change.
            if (!isVertical) mMayChangeEventTarget = false;
        }

        EventTarget target = shouldPropagateEventsToPanel
                ? EventTarget.PANEL : EventTarget.CONTENT_VIEW;

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
        public void onShowPress(MotionEvent e) {
            mPanel.onShowPress(e.getX() * mPxToDp, e.getY() * mPxToDp);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // TODO(mdjones): Investigate how this is ever the case. The API docs do not say this
            // can happen (https://crbug.com/613069).
            if (e == null) return false;
            return handleSingleTapUp(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (e1 == null || e2 == null) return false;
            return handleScroll(e1, e2, distanceY);
        }
    }
}
