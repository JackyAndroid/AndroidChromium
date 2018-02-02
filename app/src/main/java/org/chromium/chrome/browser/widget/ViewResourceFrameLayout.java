// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import org.chromium.ui.resources.dynamics.ViewResourceAdapter;

/**
 * Extension to FrameLayout that handles tracking the necessary invalidates to generate
 * a corresponding {@link org.chromium.ui.resources.Resource} for use in the browser compositor.
 */
public class ViewResourceFrameLayout extends FrameLayout {
    private ViewResourceAdapter mResourceAdapter;

    /**
     * Constructs a ViewResourceFrameLayout.
     * <p>
     * This constructor is used when inflating from XML.
     *
     * @param context The context used to build this view.
     * @param attrs The attributes used to determine how to construct this view.
     */
    public ViewResourceFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mResourceAdapter = createResourceAdapter();
    }

    /**
     * @return A {@link ViewResourceAdapter} instance.  This can be overridden for custom behavior.
     */
    protected ViewResourceAdapter createResourceAdapter() {
        return new ViewResourceAdapter(this);
    }

    /**
     * @return The {@link ViewResourceAdapter} that exposes this {@link View} as a CC resource.
     */
    public ViewResourceAdapter getResourceAdapter() {
        return mResourceAdapter;
    }

    /**
     * @return Whether the control container is ready for capturing snapshots.
     */
    protected boolean isReadyForCapture() {
        return true;
    }

    @Override
    public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
        ViewParent retVal = super.invalidateChildInParent(location, dirty);
        if (isReadyForCapture()) mResourceAdapter.invalidate(dirty);
        return retVal;
    }
}
