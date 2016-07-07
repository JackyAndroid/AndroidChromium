// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.BoundedLinearLayout;

/**
 * Layout for the new tab page. This positions the page elements in the correct vertical positions.
 * There are no separate phone and tablet UIs; this layout adapts based on the available space.
 */
public class NewTabPageLayout extends BoundedLinearLayout {

    // Space permitting, the spacers will grow from 0dp to the heights given below. If there is
    // additional space, it will be distributed evenly between the top and bottom spacers.
    private static final float TOP_SPACER_HEIGHT_DP = 44f;
    private static final float MIDDLE_SPACER_HEIGHT_DP = 24f;
    private static final float BOTTOM_SPACER_HEIGHT_DP = 44f;
    private static final float TOTAL_SPACER_HEIGHT_DP = TOP_SPACER_HEIGHT_DP
            + MIDDLE_SPACER_HEIGHT_DP + BOTTOM_SPACER_HEIGHT_DP;

    private final int mTopSpacerHeight;
    private final int mMiddleSpacerHeight;
    private final int mBottomSpacerHeight;
    private final int mTotalSpacerHeight;

    private int mParentScrollViewportHeight;

    private View mTopSpacer;
    private View mMiddleSpacer;
    private View mBottomSpacer;
    private View mScrollCompensationSpacer;

    /**
     * Constructor for inflating from XML.
     */
    public NewTabPageLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        mTopSpacerHeight = Math.round(density * TOP_SPACER_HEIGHT_DP);
        mMiddleSpacerHeight = Math.round(density * MIDDLE_SPACER_HEIGHT_DP);
        mBottomSpacerHeight = Math.round(density * BOTTOM_SPACER_HEIGHT_DP);
        mTotalSpacerHeight = Math.round(density * TOTAL_SPACER_HEIGHT_DP);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTopSpacer = findViewById(R.id.ntp_top_spacer);
        mMiddleSpacer = findViewById(R.id.ntp_middle_spacer);
        mBottomSpacer = findViewById(R.id.ntp_bottom_spacer);
        mScrollCompensationSpacer = findViewById(R.id.ntp_scroll_spacer);
    }

    /**
     * Specifies the height of the scroll viewport for the container view of this View.
     *
     * <p>
     * As this is required in onMeasure, we can not rely on the parent having the proper
     * size set yet and thus must be told explicitly of this size.
     *
     * @param height The height of the scroll viewport containing this view.
     */
    public void setParentScrollViewportHeight(int height) {
        mParentScrollViewportHeight = height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mScrollCompensationSpacer.getLayoutParams().height = 0;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        distributeExtraSpace(mTopSpacer.getMeasuredHeight());

        int minScrollAmountRequired = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);

            MarginLayoutParams layoutParams = (MarginLayoutParams) child.getLayoutParams();
            if (child.getVisibility() != View.GONE) {
                minScrollAmountRequired += layoutParams.topMargin;
            }

            if (child.getId() == R.id.most_visited_layout) break;
            if (child.getId() == R.id.opt_out_promo) break;

            if (child.getVisibility() != View.GONE) {
                minScrollAmountRequired += child.getMeasuredHeight();
                minScrollAmountRequired += layoutParams.bottomMargin;
            }
        }

        int scrollVsHeightDiff = getMeasuredHeight() - mParentScrollViewportHeight;
        if (getMeasuredHeight() > mParentScrollViewportHeight
                && scrollVsHeightDiff < minScrollAmountRequired) {
            mScrollCompensationSpacer.getLayoutParams().height =
                    minScrollAmountRequired - scrollVsHeightDiff;
            mScrollCompensationSpacer.setVisibility(View.INVISIBLE);

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            distributeExtraSpace(mTopSpacer.getMeasuredHeight());
        } else {
            mScrollCompensationSpacer.setVisibility(View.GONE);
        }
    }

    /**
     * Distribute extra vertical space between the three spacer views.
     * @param extraHeight The amount of extra space, in pixels.
     */
    private void distributeExtraSpace(int extraHeight) {
        int topSpacerHeight;
        int middleSpacerHeight;
        int bottomSpacerHeight;

        if (extraHeight < mTotalSpacerHeight) {
            topSpacerHeight = Math.round(extraHeight
                    * (TOP_SPACER_HEIGHT_DP / TOTAL_SPACER_HEIGHT_DP));
            extraHeight -= topSpacerHeight;
            middleSpacerHeight = Math.round(extraHeight * (MIDDLE_SPACER_HEIGHT_DP
                    / (MIDDLE_SPACER_HEIGHT_DP + BOTTOM_SPACER_HEIGHT_DP)));
            extraHeight -= middleSpacerHeight;
            bottomSpacerHeight = extraHeight;
        } else {
            topSpacerHeight = mTopSpacerHeight;
            middleSpacerHeight = mMiddleSpacerHeight;
            bottomSpacerHeight = mBottomSpacerHeight;
            extraHeight -= mTotalSpacerHeight;

            // Distribute remaining space evenly between the top and bottom spacers.
            topSpacerHeight += (extraHeight + 1) / 2;
            bottomSpacerHeight += extraHeight / 2;
        }

        int widthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY);
        mTopSpacer.measure(widthSpec,
                MeasureSpec.makeMeasureSpec(topSpacerHeight, MeasureSpec.EXACTLY));
        mMiddleSpacer.measure(widthSpec,
                MeasureSpec.makeMeasureSpec(middleSpacerHeight, MeasureSpec.EXACTLY));
        mBottomSpacer.measure(widthSpec,
                MeasureSpec.makeMeasureSpec(bottomSpacerHeight, MeasureSpec.EXACTLY));
    }
}
