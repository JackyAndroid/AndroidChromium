// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View.MeasureSpec;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * View that handles orientation changes for the Data Saver first run page.
 */
public class DataReductionProxyView extends FrameLayout {

    public DataReductionProxyView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // This assumes that view's layout_width is set to match_parent.
        assert MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        LinearLayout content = (LinearLayout) findViewById(R.id.fre_content);
        int paddingStart = 0;
        if (width >= 2 * getResources().getDimension(R.dimen.fre_image_carousel_width)
                && width > height) {
            content.setOrientation(LinearLayout.HORIZONTAL);
            paddingStart = getResources().getDimensionPixelSize(R.dimen.fre_margin);
        } else {
            content.setOrientation(LinearLayout.VERTICAL);
        }
        ApiCompatibilityUtils.setPaddingRelative(content,
                paddingStart,
                content.getPaddingTop(),
                ApiCompatibilityUtils.getPaddingEnd(content),
                content.getPaddingBottom());
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}