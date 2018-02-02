// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.eventfilter;

import android.content.Context;
import android.graphics.RectF;
import android.view.MotionEvent;

/**
 * A {@link AreaGestureEventFilter} intercepts all events that start in a specific Rect on the
 * screen.
 */
public class AreaGestureEventFilter extends GestureEventFilter {
    private final RectF mTriggerRect = new RectF();

    /**
     * Creates a {@link AreaGestureEventFilter}.
     * @param context       The context to build the gesture handler under.
     * @param host          The host of this EventFilter.
     * @param handler       The handler to be notified of gesture events.
     * @param triggerRect   The area that events should be stolen from in dp.
     */
    public AreaGestureEventFilter(Context context, EventFilterHost host, GestureHandler handler,
            RectF triggerRect) {
        this(context, host, handler, triggerRect, true);
    }

    /**
     * Creates a {@link AreaGestureEventFilter}.
     * @param context       The context to build the gesture handler under.
     * @param host          The host of this EventFilter.
     * @param handler       The handler to be notified of gesture events.
     * @param triggerRect   The area that events should be stolen from in dp.
     * @param autoOffset    Whether or not to offset touch events.
     */
    public AreaGestureEventFilter(Context context, EventFilterHost host, GestureHandler handler,
            RectF triggerRect, boolean autoOffset) {
        super(context, host, handler, autoOffset);
        setEventArea(triggerRect);
    }

    /**
     * Creates a {@link AreaGestureEventFilter}.
     * @param context               The context to build the gesture handler under.
     * @param host                  The host of this EventFilter.
     * @param handler               The handler to be notified of gesture events.
     * @param triggerRect           The area that events should be stolen from in dp.
     * @param autoOffset            Whether or not to offset touch events.
     * @param useDefaultLongPress   Whether or not to use the default long press behavior.
     */
    public AreaGestureEventFilter(Context context, EventFilterHost host, GestureHandler handler,
            RectF triggerRect, boolean autoOffset, boolean useDefaultLongPress) {
        super(context, host, handler, autoOffset, useDefaultLongPress);
        setEventArea(triggerRect);
    }

    /**
     * @param rect The area that events should be stolen from in dp.
     */
    public void setEventArea(RectF rect) {
        if (rect == null) {
            mTriggerRect.setEmpty();
        } else {
            mTriggerRect.set(rect);
        }
    }

    @Override
    public boolean onInterceptTouchEventInternal(MotionEvent e, boolean isKeyboardShowing) {
        if (mTriggerRect.contains(e.getX() * mPxToDp, e.getY() * mPxToDp)) {
            return super.onInterceptTouchEventInternal(e, isKeyboardShowing);
        }
        return false;
    }
}
