// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.MathUtils;

/**
 * A layout that arranges most visited items in a grid.
 *
 * Intended for use with the new icon-based most visited items.
 */
public class IconMostVisitedLayout extends FrameLayout {

    private static final int MAX_COLUMNS = 4;

    private int mVerticalSpacing;
    private int mMinHorizontalSpacing;
    private int mMaxHorizontalSpacing;
    private int mMaxWidth;
    private int mMaxRows;

    /**
     * @param context The view context in which this item will be shown.
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public IconMostVisitedLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = getResources();
        mVerticalSpacing = res.getDimensionPixelOffset(R.dimen.icon_most_visited_vertical_spacing);
        mMinHorizontalSpacing = res.getDimensionPixelOffset(
                R.dimen.icon_most_visited_min_horizontal_spacing);
        mMaxHorizontalSpacing = res.getDimensionPixelOffset(
                R.dimen.icon_most_visited_max_horizontal_spacing);
        mMaxWidth = res.getDimensionPixelOffset(R.dimen.icon_most_visited_layout_max_width);
    }

    /**
     * Sets the maximum number of rows to display. Any items that don't fit within these rows will
     * be hidden.
     */
    public void setMaxRows(int rows) {
        mMaxRows = rows;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int totalWidth = resolveSize(mMaxWidth, widthMeasureSpec);
        int childCount = getChildCount();
        if (childCount == 0) {
            setMeasuredDimension(totalWidth, resolveSize(0, heightMeasureSpec));
            return;
        }

        // Measure the children.
        for (int i = 0; i < childCount; i++) {
            measureChild(getChildAt(i), MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        }

        // Determine the number of columns that will fit.
        int gridWidth = totalWidth - ApiCompatibilityUtils.getPaddingStart(this)
                - ApiCompatibilityUtils.getPaddingEnd(this);
        int childHeight = getChildAt(0).getMeasuredHeight();
        int childWidth = getChildAt(0).getMeasuredWidth();
        int numColumns = MathUtils.clamp(
                (gridWidth + mMinHorizontalSpacing) / (childWidth + mMinHorizontalSpacing),
                1, MAX_COLUMNS);

        // Ensure column spacing isn't greater than mMaxHorizontalSpacing.
        int gridWidthMinusColumns = Math.max(0, gridWidth - numColumns * childWidth);
        int gridSidePadding = gridWidthMinusColumns - mMaxHorizontalSpacing * (numColumns - 1);

        int gridStart = 0;
        float horizontalSpacing;
        if (gridSidePadding > 0) {
            horizontalSpacing = mMaxHorizontalSpacing;
            gridStart = gridSidePadding / 2;
        } else {
            horizontalSpacing = (float) gridWidthMinusColumns / Math.max(1, numColumns - 1);
        }

        // Limit the number of rows to mMaxRows.
        int visibleChildCount = Math.min(childCount, mMaxRows * numColumns);

        // Arrange the children in a grid.
        int paddingTop = getPaddingTop();
        boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(this);
        for (int i = 0; i < visibleChildCount; i++) {
            View child = getChildAt(i);
            child.setVisibility(View.VISIBLE);
            int row = i / numColumns;
            int column = i % numColumns;
            int childTop = row * (childHeight + mVerticalSpacing);
            int childStart = gridStart + Math.round(column * (childWidth + horizontalSpacing));
            MarginLayoutParams layoutParams = (MarginLayoutParams) child.getLayoutParams();
            layoutParams.setMargins(isRtl ? 0 : childStart, childTop, isRtl ? childStart : 0, 0);
            child.setLayoutParams(layoutParams);
        }

        // Hide the last row if it's incomplete.
        for (int i = visibleChildCount; i < childCount; i++) {
            getChildAt(i).setVisibility(View.GONE);
        }

        int numRows = (visibleChildCount + numColumns - 1) / numColumns;
        int totalHeight = paddingTop + getPaddingBottom() + numRows * childHeight
                + (numRows - 1) * mVerticalSpacing;

        setMeasuredDimension(totalWidth, resolveSize(totalHeight, heightMeasureSpec));
    }
}
