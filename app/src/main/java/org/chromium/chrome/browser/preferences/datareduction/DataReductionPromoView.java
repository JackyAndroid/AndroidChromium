// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.datareduction;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.LinearLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.PaddedFrameLayout;

/**
 * View that handles orientation changes for the Data Reduction Proxy promo. When the width is
 * greater than the height, switches the promo content view from vertical to horizontal and moves
 * the illustration from the top of the text to the side of the text.
 */
public class DataReductionPromoView extends PaddedFrameLayout {

    private static final int ILLUSTRATION_HORIZONTAL_PADDING_DP = 24;
    private static final int FRAME_HEIGHT_MARGIN_DP = 30;

    private View mIllustration;
    private LinearLayout mPromoContent;
    private int mMaxChildWidth;
    private int mMaxChildWidthHorizontal;
    private int mIllustrationPaddingBottom;
    private int mIllustrationPaddingSide;
    private int mFrameHeightMargin;

    public DataReductionPromoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaxChildWidth = getResources()
                    .getDimensionPixelSize(R.dimen.data_reduction_promo_screen_width);
        mMaxChildWidthHorizontal = getResources()
                    .getDimensionPixelSize(R.dimen.data_reduction_promo_screen_width_horizontal);
        mIllustrationPaddingBottom = getResources()
                    .getDimensionPixelSize(R.dimen.data_reduction_promo_illustration_margin_bottom);
        float density = getResources().getDisplayMetrics().density;
        mIllustrationPaddingSide = (int) (ILLUSTRATION_HORIZONTAL_PADDING_DP * density + 0.5f);
        mFrameHeightMargin = (int) (FRAME_HEIGHT_MARGIN_DP * density + 0.5f);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIllustration = findViewById(R.id.data_reduction_illustration);
        mPromoContent = (LinearLayout) findViewById(R.id.data_reduction_promo_content);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (width >= 2 * mIllustration.getWidth() && width > height) {
            mPromoContent.setOrientation(LinearLayout.HORIZONTAL);
            setMaxChildWidth(mMaxChildWidthHorizontal);
            ApiCompatibilityUtils.setPaddingRelative(
                    mIllustration, 0, 0, mIllustrationPaddingSide, 0);
        } else {
            mPromoContent.setOrientation(LinearLayout.VERTICAL);
            setMaxChildWidth(mMaxChildWidth);
            mIllustration.setPadding(0, 0, 0, mIllustrationPaddingBottom);
        }

        setMaxChildHeight(height - mFrameHeightMargin);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
