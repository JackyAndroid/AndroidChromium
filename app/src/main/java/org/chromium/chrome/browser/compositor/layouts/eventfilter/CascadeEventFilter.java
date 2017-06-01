// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.eventfilter;

import android.content.Context;
import android.view.MotionEvent;

import java.util.Arrays;

/**
 * A {@link CascadeEventFilter} delegates all the events coming its way to another filter.
 */
public class CascadeEventFilter extends EventFilter {
    private EventFilter[] mDelegates;
    private EventFilter mActiveDelegate = null;

    /**
     * Creates a {@link CascadeEventFilter}.
     *
     * The delegates will be queried in the order specified in this list (0 -> count). Once a
     * delegate takes ownership of the event by returning {@code true} from
     * {@link EventFilter#onInterceptTouchEventInternal(MotionEvent, boolean)} it will get all
     * subsequent events for the same gesture to
     * {@link EventFilter#onTouchEventInternal(MotionEvent)}.
     *
     * @param context   A {@link Context} instance.
     * @param host      The host that is responsible for managing event filter status changes.
     * @param delegates The list of delegates to be given the chance to process the event.
     */
    public CascadeEventFilter(Context context, EventFilterHost host, EventFilter[] delegates) {
        super(context, host, false);
        mDelegates = Arrays.copyOf(delegates, delegates.length);
    }

    @Override
    public boolean onInterceptTouchEventInternal(MotionEvent origEvent, boolean isKeyboardShowing) {
        mActiveDelegate = null;
        MotionEvent offsetEvent = MotionEvent.obtain(origEvent);
        offsetEvent.offsetLocation(mCurrentTouchOffsetX, mCurrentTouchOffsetY);
        for (int i = 0; i < mDelegates.length; ++i) {
            MotionEvent e = mDelegates[i].autoOffsetEvents() ? offsetEvent : origEvent;
            if (mDelegates[i].onInterceptTouchEventInternal(e, isKeyboardShowing)) {
                mActiveDelegate = mDelegates[i];
                offsetEvent.recycle();
                return true;
            }
        }
        offsetEvent.recycle();
        return false;
    }

    @Override
    public boolean onTouchEventInternal(MotionEvent e) {
        if (mActiveDelegate != null) {
            if (mActiveDelegate.autoOffsetEvents()) {
                e.offsetLocation(mCurrentTouchOffsetX, mCurrentTouchOffsetY);
            }
            return mActiveDelegate.onTouchEventInternal(e);
        }
        return false;
    }
}