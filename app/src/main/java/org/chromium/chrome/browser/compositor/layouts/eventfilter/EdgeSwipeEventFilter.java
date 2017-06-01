// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.eventfilter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;

import org.chromium.chrome.browser.tabmodel.TabModelSelector;

import java.util.ArrayList;

/**
 * An {@link EdgeSwipeEventFilter} triggers a edge swipe gesture or forward the events to its host
 * view.
 */
@SuppressLint("RtlHardcoded")
public class EdgeSwipeEventFilter extends EventFilter {
    private static final boolean TAB_SWIPING_ENABLED = true;

    private static final long MAX_ACCUMULATE_DURATION_MS = 200;
    private static final double SIDE_SWIPE_ANGLE_THRESHOLD_DEGREES = 45;
    private static final double TAN_SIDE_SWIPE_ANGLE_THRESHOLD = Math.tan(Math.toRadians(
            SIDE_SWIPE_ANGLE_THRESHOLD_DEGREES));
    // The distance that always initiate a side swipe.
    private static final float GUTTER_DISTANCE_DP = 6.4f;
    // The maximum distance the user can initiate a side swipe in.
    private static final float SWIPE_REGION_DP = 30;
    // The distance that an scroll event has to cover in Y to be marked as trustworthy.
    private static final float ACCUMULATE_THRESHOLD_DO = 12;
    // The divider constant for the exponential function used for side swipe.
    private static final double SWIPE_TIME_CONSTANT_DP = 30;

    public enum ScrollDirection {
        UNKNOWN,
        LEFT,
        RIGHT,
        DOWN,
        UP,
    }

    private boolean mEnableTabSwiping;
    private ScrollDirection mScrollDirection;

    private final double mSwipeTimeConstantPx;

    private final GestureDetector mGestureDetector;
    private TabModelSelector mTabModelSelector;
    private final EdgeSwipeHandler mEdgeSwipeHandler;
    private boolean mEdgeSwipeStarted;

    private boolean mInLongPress = false;
    private boolean mInDoubleTap = false;
    private boolean mScrollStarted;

    // This flag is used to for accumulating events when the motion at the beginning of a scroll
    // can not be trusted and we need more events to make a better angle and speed estimate.
    private boolean mAccumulatingEvents = false;
    private final ArrayList<MotionEvent> mAccumulatedEvents = new ArrayList<MotionEvent>();
    private boolean mPropagateEventsToHostView;

    /**
     * Creates a {@link EdgeSwipeEventFilter} captures event either in edge swipe gestures or
     * propagate them.
     * @param context          A {@link Context} instance.
     * @param host             The {@link EventFilterHost} where the event is coming from.
     * @param edgeSwipeHandler The {@link EdgeSwipeHandler} that is going to get notified.
     */
    public EdgeSwipeEventFilter(
            Context context, EventFilterHost host, EdgeSwipeHandler edgeSwipeHandler) {
        super(context, host, false);
        mEnableTabSwiping = TAB_SWIPING_ENABLED;
        mScrollDirection = ScrollDirection.UNKNOWN;

        mSwipeTimeConstantPx = SWIPE_TIME_CONSTANT_DP / mPxToDp;

        mGestureDetector = new GestureDetector(context, new ViewScrollerGestureDetector());
        mGestureDetector.setIsLongpressEnabled(true);

        mEdgeSwipeHandler = edgeSwipeHandler;
    }

    /**
     * Enables or disables edge swiping on the device.  This determines whether or not swipes that
     * originate from the edges of the screen are caught and handled by this object or not.
     * @param enable Whether or not to enable edge swiping.
     */
    public void enableTabSwiping(boolean enable) {
        mEnableTabSwiping = TAB_SWIPING_ENABLED && enable;
    }

    /**
     * Sets the current {@link TabModelSelector}.
     */
    public void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelector = tabModelSelector;
    }

    /**
     * Called whenever the beginning of a side swipe scroll is detected.  This lets inheriting
     * classes trigger any side swipe behavior they want.
     * @param direction The direction the scroll can move.
     * @param x         The horizontal coordinate the swipe started at in dp.
     * @param y         The vertical coordinate the swipe started at in dp.
     */
    private void scrollStarted(ScrollDirection direction, float x, float y) {
        if (mEdgeSwipeHandler != null) {
            mEdgeSwipeHandler.swipeStarted(direction, x, y);
            mEdgeSwipeStarted = true;
        }
    }

    /**
     * Called whenever the end of a side swipe scroll is detected.  This lets inheriting classes
     * clean up any side swipe behavior they want.
     */
    private void scrollFinished() {
        if (mEdgeSwipeHandler != null && mEdgeSwipeStarted) {
            mEdgeSwipeHandler.swipeFinished();
        }
        mEdgeSwipeStarted = false;
    }

    /**
     * Called whenever a side swipe scroll event is triggered.  This gives the new delta X that
     * represents how much scroll took place.
     * @param x  The horizontal coordinate the swipe is currently at in dp.
     * @param y  The vertical coordinate the swipe is currently at in dp.
     * @param dx The horizontal delta since the last update in dp.
     * @param dy The vertical delta since the last update in dp.
     * @param tx The total horizontal distance since the start of the scroll.
     * @param ty The total vertical distance since the start of the scroll.
     */
    private void scrollUpdated(float x, float y, float dx, float dy, float tx, float ty) {
        if (mEdgeSwipeHandler != null && mEdgeSwipeStarted) {
            mEdgeSwipeHandler.swipeUpdated(x, y, dx, dy, tx, ty);
        }
    }

    /**
     * Called whenever a fling occurs on the container view.
     * @param x  The horizontal coordinate the swipe is currently at in dp.
     * @param y  The vertical coordinate the swipe is currently at in dp.
     * @param tx The total horizontal distance since the start of the scroll/fling.
     * @param ty The total vertical distance since the start of the scroll/fling.
     * @param vx The velocity in the X direction of the fling.
     * @param vy The velocity in the Y direction of the fling.
     */
    private void flingOccurred(float x, float y, float tx, float ty, float vx, float vy) {
        if (mEdgeSwipeHandler != null && mEdgeSwipeStarted) {
            mEdgeSwipeHandler.swipeFlingOccurred(x, y, tx, ty, vx, vy);
        }
    }

    /**
     * @return Whether or not the user is currently side scrolling.
     */
    protected boolean isSideScrolling() {
        return mScrollDirection == ScrollDirection.LEFT
                || mScrollDirection == ScrollDirection.RIGHT;
    }

    /**
     * @return Whether or not the user is currently down scrolling.
     */
    protected boolean isDownScrolling() {
        return mScrollDirection == ScrollDirection.DOWN;
    }

    /**
     * Check whether the scroll event has a fast enough speed to trigger a side swipe.
     * It uses an exponential function to make it progressively harder to trigger side swipes
     * as the scroll start moves away from the edge of the screen.
     * @param e1 The DOWN event that started the scroll.
     * @param e2 The MOVE event that comes after the DOWN event.
     * @return   Whether this scroll should initiate a side swipe.
     */
    private boolean checkForFastScroll(MotionEvent e1, MotionEvent e2) {
        float dt = e2.getEventTime() - e1.getEventTime();
        if (dt <= 0) return false;

        float dist;
        switch (mScrollDirection) {
            case RIGHT:
                dist = calculateBiasedPosition(
                        e1.getX() + mCurrentTouchOffsetX,
                        e2.getX() + mCurrentTouchOffsetX, dt);
                break;
            case LEFT:
                dist = mHost.getViewportWidth() * mPxToDp
                        - calculateBiasedPosition(
                                e1.getX() + mCurrentTouchOffsetX,
                                e2.getX() + mCurrentTouchOffsetX, dt);
                break;
            case DOWN:
                dist = calculateBiasedPosition(
                        e1.getY() + mCurrentTouchOffsetY,
                        e2.getY() + mCurrentTouchOffsetY, dt);
                break;
            default:
                dist = GUTTER_DISTANCE_DP;
                break;
        }

        return dist < GUTTER_DISTANCE_DP;
    }

    private float calculateBiasedPosition(float p1, float p2, float dt) {
        assert dt > 0.f;
        float speed = Math.abs((p2 - p1) * mPxToDp / dt);
        float boost = (float) (Math.signum(p2 - p1)
                * (mSwipeTimeConstantPx * (Math.exp(speed) - 1.f)));
        return p1 * mPxToDp - boost;
    }

    /**
     * Propagates all collected touch events to the {@link EventFilterHost} once it is determined a
     * side swipe is not being performed.
     * @return Whether all the events were propagated successfully.
     */
    private boolean propagateAccumulatedEventsAndClear() {
        boolean success = true;
        for (int i = 0; i < mAccumulatedEvents.size(); i++) {
            success = mHost.propagateEvent(mAccumulatedEvents.get(i)) && success;
        }
        mAccumulatedEvents.clear();
        mAccumulatingEvents = false;
        return success;
    }

    @Override
    public boolean onInterceptTouchEventInternal(MotionEvent e, boolean isKeyboardShowing) {
        if (mTabModelSelector == null) return false;

        mPropagateEventsToHostView = true;

        mInLongPress = false;
        final int count = mTabModelSelector.getCurrentModel().getCount();
        final int action = e.getActionMasked();
        if (mEnableTabSwiping
                && !isKeyboardShowing
                && action == MotionEvent.ACTION_DOWN
                && count > 0
                && mScrollDirection == ScrollDirection.UNKNOWN) {
            ScrollDirection direction = ScrollDirection.UNKNOWN;
            if ((e.getX() + mCurrentTouchOffsetX) * mPxToDp < SWIPE_REGION_DP) {
                direction = ScrollDirection.RIGHT;
            } else if (mHost.getViewportWidth() * mPxToDp
                    - (e.getX() + mCurrentTouchOffsetX) * mPxToDp < SWIPE_REGION_DP) {
                direction = ScrollDirection.LEFT;
            } else if ((e.getY() + mCurrentTouchOffsetY) * mPxToDp < SWIPE_REGION_DP) {
                direction = ScrollDirection.DOWN;
            }

            // Check if we have a new direction and that it's supported.
            if (direction != ScrollDirection.UNKNOWN
                    && (mEdgeSwipeHandler == null || mEdgeSwipeHandler.isSwipeEnabled(direction))) {
                mScrollDirection = direction;
            }

            if (mScrollDirection != ScrollDirection.UNKNOWN) mPropagateEventsToHostView = false;
        }

        return true;
    }

    @Override
    public boolean onTouchEventInternal(MotionEvent e) {
        if (mPropagateEventsToHostView) {
            mHost.propagateEvent(e);
            return true;
        }

        // If more than one pointer are down, we should forward these events to the
        // ContentView.
        final int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN
                && e.getPointerCount() == 2
                && !mScrollStarted) {
            mPropagateEventsToHostView = true;
            // Some type of multi-touch that should go to the view.
            mScrollDirection = ScrollDirection.UNKNOWN;
            MotionEvent cancelEvent = MotionEvent.obtain(
                    e.getDownTime(),
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_CANCEL,
                    0, 0, 0);
            mGestureDetector.onTouchEvent(cancelEvent);
            propagateAccumulatedEventsAndClear();
            mHost.propagateEvent(e);
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            if (mInLongPress || mInDoubleTap) {
                mHost.propagateEvent(e);
                mInLongPress = false;
                mInDoubleTap = false;
            }
        }

        if (mScrollDirection != ScrollDirection.UNKNOWN) {
            if (mAccumulatingEvents) {
                mAccumulatedEvents.add(MotionEvent.obtain(e));
            }
            mGestureDetector.onTouchEvent(e);

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                scrollFinished();
                propagateAccumulatedEventsAndClear();

                mScrollDirection = ScrollDirection.UNKNOWN;
            }
        }

        return true;
    }

    /**
     * This class handles all the touch events and tries to direct them to the
     * right place.
     */
    private class ViewScrollerGestureDetector extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            if (mInDoubleTap) {
                // If it is the 2nd ACTION_DOWN event for a double tap, forward the down event
                // to ContentView.
                return mHost.propagateEvent(e);
            }
            mAccumulatedEvents.add(MotionEvent.obtain(e));
            mScrollStarted = false;
            return false;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            propagateAccumulatedEventsAndClear();
            return mHost.propagateEvent(e);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // Double tap took place on the 2nd ACTION_DOWN event. It is called right before
            // onDown(). Set mInDoubleTap to true so that onDown() will forward the event.
            mInDoubleTap = true;
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            // GestureDetectorProxy will correctly use the down time of this event to recognize
            // it as a long press. The up event should be also forwarded when received.
            propagateAccumulatedEventsAndClear();
            mInLongPress = true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            final float x = e2.getX() * mPxToDp;
            final float y = e2.getY() * mPxToDp;
            final float dx = -distanceX * mPxToDp;
            final float dy = -distanceY * mPxToDp;
            final float tx = (e2.getRawX() - e1.getRawX()) * mPxToDp;
            final float ty = (e2.getRawY() - e1.getRawY()) * mPxToDp;

            if (mScrollStarted) {
                scrollUpdated(x, y, dx, dy, tx, ty);
                return true;
            }

            final boolean horizontal = mScrollDirection != ScrollDirection.DOWN;
            final float mainAxisAbsDelta = Math.abs(horizontal ? tx : ty);
            final float offAxisAbsDelta = Math.abs(horizontal ? ty : tx);

            if (mAccumulatingEvents) {
                final long lastAccumulatedEventTime =
                        mAccumulatedEvents.get(mAccumulatedEvents.size() - 1).getEventTime();
                final long firstAccumulatedEventTime = mAccumulatedEvents.get(0).getEventTime();
                final long elapsedTime = lastAccumulatedEventTime - firstAccumulatedEventTime;

                // For deciding when to stop accumulating, we wait until we are out of a box
                // defined by where the scroll event started which is (e1.getRawX, e1.getRawY).
                if (offAxisAbsDelta < ACCUMULATE_THRESHOLD_DO
                        && mainAxisAbsDelta < SWIPE_REGION_DP
                        && elapsedTime <= MAX_ACCUMULATE_DURATION_MS) {
                    return true;
                }
            } else {
                // mAccumulatingEvents false, so onTouch didn't record e2.
                mAccumulatedEvents.add(MotionEvent.obtain(e2));
            }

            // We start accumulating events only if the current two events are not trustworthy, ie
            // they represent a tiny motion in both directions. e1 is always added in onDown().
            // If mAccumulatingEvents was already true e2 was added in onTouch.
            if (!mAccumulatingEvents
                    && offAxisAbsDelta < ACCUMULATE_THRESHOLD_DO
                    && mainAxisAbsDelta < ACCUMULATE_THRESHOLD_DO) {
                mAccumulatingEvents = true;
                return true;
            }

            if (!checkForFastScroll(e1, e2)
                    || offAxisAbsDelta > mainAxisAbsDelta * TAN_SIDE_SWIPE_ANGLE_THRESHOLD) {
                // Re-send the down event to the ContentView as it was cancelled during the
                // event intercepting. If we're in a long press the event was already forwarded.
                propagateAccumulatedEventsAndClear();
                mScrollDirection = ScrollDirection.UNKNOWN;
                mPropagateEventsToHostView = true;
            } else {
                scrollStarted(mScrollDirection, x, y);
                mScrollStarted = true;
                mAccumulatedEvents.clear();
                mAccumulatingEvents = false;
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
            final float x = e2.getX() * mPxToDp;
            final float y = e2.getY() * mPxToDp;
            final float tx = (e2.getRawX() - e1.getRawX()) * mPxToDp;
            final float ty = (e2.getRawY() - e1.getRawY()) * mPxToDp;
            final float vx = velocityX * mPxToDp;
            final float vy = velocityY * mPxToDp;
            flingOccurred(x, y, tx, ty, vx, vy);
            return false;
        }
    }
}