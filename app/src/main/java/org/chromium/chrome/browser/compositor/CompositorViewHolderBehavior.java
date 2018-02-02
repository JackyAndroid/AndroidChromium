// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.CoordinatorLayout.Behavior;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Default {@link Behavior} for widgets that are children of {@link CompositorViewHolder} and want
 * to handle touch events.
 */
public class CompositorViewHolderBehavior extends Behavior<View> {
    private boolean mShouldIntercept;

    /**
     * Constructs the object programmatically.
     */
    public CompositorViewHolderBehavior() {
        super();
    }

    /**
     * Constructs the object during inflation.
     */
    public CompositorViewHolderBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, View child, MotionEvent ev) {
        mShouldIntercept = child.getVisibility() == View.VISIBLE && isTouchInBound(ev, child);
        return mShouldIntercept;
    }

    @Override
    public boolean onTouchEvent(CoordinatorLayout parent, View child, MotionEvent ev) {
        if (!mShouldIntercept) return false;
        ev.offsetLocation(-child.getX(), -child.getY());
        child.dispatchTouchEvent(ev);
        return true;
    }

    /**
     * @return Whether the {@link MotionEvent} happens within the bound of the given {@link View}.
     */
    private static boolean isTouchInBound(MotionEvent ev, View view) {
        return ev.getX() >= view.getX() && ev.getX() - view.getX() <= view.getWidth()
                && ev.getY() >= view.getY() && ev.getY() - view.getY() <= view.getHeight();
    }
}
