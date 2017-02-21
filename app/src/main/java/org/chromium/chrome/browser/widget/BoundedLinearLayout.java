// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import org.chromium.chrome.R;

/**
 * A LinearLayout that can be constrained to a maximum size.
 *
 * Example:
 *   <org.chromium.chrome.browser.widget.BoundedLinearLayout
 *       xmlns:android="http://schemas.android.com/apk/res/android"
 *       xmlns:chrome="http://schemas.android.com/apk/res-auto"
 *       android:layout_width="match_parent"
 *       android:layout_height="match_parent"
 *       chrome:maxWidth="692dp" >
 *     ...
 */
public class BoundedLinearLayout extends LinearLayout {

    private static final int NOT_SPECIFIED = -1;

    private final int mMaxWidth;
    private final int mMaxHeight;

    /**
     * Constructor for inflating from XML.
     */
    public BoundedLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BoundedView);
        int maxWidth = a.getDimensionPixelSize(R.styleable.BoundedView_maxWidth, NOT_SPECIFIED);
        int maxHeight = a.getDimensionPixelSize(R.styleable.BoundedView_maxHeight, NOT_SPECIFIED);
        a.recycle();

        // Treat 0 or below as being unconstrained.
        mMaxWidth = maxWidth <= 0 ? NOT_SPECIFIED : maxWidth;
        mMaxHeight = maxHeight <= 0 ? NOT_SPECIFIED : maxHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Limit width to mMaxWidth.
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (mMaxWidth != NOT_SPECIFIED && widthSize > mMaxWidth) {
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            if (widthMode == MeasureSpec.UNSPECIFIED) widthMode = MeasureSpec.AT_MOST;
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(mMaxWidth, widthMode);
        }
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (mMaxHeight != NOT_SPECIFIED && heightSize > mMaxHeight) {
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (heightMode == MeasureSpec.UNSPECIFIED) heightMode = MeasureSpec.AT_MOST;
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(mMaxHeight, heightMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
