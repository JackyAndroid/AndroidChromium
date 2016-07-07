// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * A layout that arranges most visited items in a grid. All items must be the same size.
 */
public class MostVisitedLayout extends FrameLayout {

    private int mHorizontalSpacing;
    private int mVerticalSpacing;
    private int mTwoColumnMinWidth;
    private int mThreeColumnMinWidth;

    /**
     * The ideal width of a child. The children may need be stretched or shrunk a bit to fill the
     * available width.
     */
    private int mDefaultChildWidth;

    private Drawable mEmptyTileDrawable;
    private int mNumEmptyTiles;
    private int mEmptyTileTop;
    private int mFirstEmptyTileLeft;

    /**
     * @param context The view context in which this item will be shown.
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public MostVisitedLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);

        Resources res = getResources();
        mHorizontalSpacing = res.getDimensionPixelOffset(R.dimen.most_visited_spacing);
        mVerticalSpacing = mHorizontalSpacing;
        mDefaultChildWidth = res.getDimensionPixelSize(R.dimen.most_visited_tile_width);
        mTwoColumnMinWidth = res.getDimensionPixelOffset(R.dimen.most_visited_two_column_min_width);
        mThreeColumnMinWidth = res.getDimensionPixelOffset(
                R.dimen.most_visited_three_column_min_width);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Determine the number of columns in the grid.
        final int totalWidth = MeasureSpec.getSize(widthMeasureSpec);
        int childWidth = mDefaultChildWidth;
        int childWidthWithSpacing = childWidth + mHorizontalSpacing;

        int numColumns;
        if (totalWidth + mHorizontalSpacing >= 3 * childWidthWithSpacing) {
            numColumns = 3;
        } else if (totalWidth < mTwoColumnMinWidth) {
            numColumns = 1;
        } else {
            numColumns = totalWidth < mThreeColumnMinWidth ? 2 : 3;

            // Resize the tiles to make them fill the entire available width.
            childWidthWithSpacing = Math.max(mHorizontalSpacing,
                    (totalWidth + mHorizontalSpacing) / numColumns);
            childWidth = childWidthWithSpacing - mHorizontalSpacing;
        }

        int childCount = getChildCount();
        int childHeight = 0;
        if (childCount > 0) {
            // Measure the children.
            int childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).measure(childWidthSpec, childHeightSpec);
            }
            childHeight = getChildAt(0).getMeasuredHeight();
        }

        // Arrange the children in a grid.
        int childStart = 0;
        int childTop = 0;
        int column = 0;
        boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(this);
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            MarginLayoutParams layoutParams = (MarginLayoutParams) child.getLayoutParams();
            layoutParams.setMargins(isRtl ? 0 : childStart, childTop,
                    isRtl ? childStart : 0, 0);
            child.setLayoutParams(layoutParams);
            column++;
            if (column == numColumns) {
                column = 0;
                childStart = 0;
                childTop += childHeight + mVerticalSpacing;
            } else {
                childStart += childWidthWithSpacing;
            }
        }

        // Fill the rest of the current row with empty tiles.
        if (column != 0) {
            mNumEmptyTiles = numColumns - column;
            mEmptyTileTop = childTop + getPaddingTop();
            mFirstEmptyTileLeft = isRtl ? 0 : childStart;
        } else {
            mNumEmptyTiles = 0;
        }

        int numRows = (childCount + mNumEmptyTiles) / numColumns;
        int totalHeight = getPaddingTop() + getPaddingBottom() + numRows * childHeight
                + (numRows - 1) * mVerticalSpacing;

        int gridWidth = numColumns * childWidthWithSpacing - mHorizontalSpacing;
        setMeasuredDimension(gridWidth, resolveSize(totalHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mNumEmptyTiles == 0 || getChildCount() == 0) return;

        // Draw empty tiles.
        if (mEmptyTileDrawable == null) {
            mEmptyTileDrawable = ApiCompatibilityUtils.getDrawable(
                    getResources(), R.drawable.most_visited_item_empty);
        }
        int tileLeft = mFirstEmptyTileLeft;
        int tileWidth = getChildAt(0).getMeasuredWidth();
        int tileHeight = getChildAt(0).getMeasuredHeight();
        for (int i = 0; i < mNumEmptyTiles; i++) {
            mEmptyTileDrawable.setBounds(
                    tileLeft,
                    mEmptyTileTop,
                    tileLeft + tileWidth,
                    mEmptyTileTop + tileHeight);
            mEmptyTileDrawable.draw(canvas);
            tileLeft += tileWidth + mHorizontalSpacing;
        }
    }
}
