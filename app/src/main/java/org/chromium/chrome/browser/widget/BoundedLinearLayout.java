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
 * A LinearLayout that can be constrained to a maximum width.
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

    private static final int NO_MAX_WIDTH = -1;

    private final int mMaxWidth;

    /**
     * Constructor for inflating from XML.
     */
    public BoundedLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BoundedView);
        mMaxWidth = a.getDimensionPixelSize(R.styleable.BoundedView_maxWidth, NO_MAX_WIDTH);
        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Limit width to mMaxWidth.
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (mMaxWidth != NO_MAX_WIDTH && widthSize > mMaxWidth) {
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            if (widthMode == MeasureSpec.UNSPECIFIED) widthMode = MeasureSpec.AT_MOST;
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(mMaxWidth, widthMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
