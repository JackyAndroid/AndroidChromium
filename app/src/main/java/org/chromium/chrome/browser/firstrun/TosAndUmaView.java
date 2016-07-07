// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * View that handles orientation changes for Terms of Service and UMA first run page.
 */
public class TosAndUmaView extends FrameLayout {

    public TosAndUmaView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // This assumes that view's layout_width is set to match_parent.
        assert MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        LinearLayout content = (LinearLayout) findViewById(R.id.fre_content);
        LinearLayout wrapper = (LinearLayout) findViewById(R.id.text_wrapper);
        MarginLayoutParams params = (MarginLayoutParams) wrapper.getLayoutParams();
        int paddingStart = 0;
        if (width >= 2 * getResources().getDimension(R.dimen.fre_image_carousel_width)
                && width > height) {
            content.setOrientation(LinearLayout.HORIZONTAL);
            paddingStart = getResources().getDimensionPixelSize(R.dimen.fre_margin);
            params.width = 0;
            params.height = LayoutParams.WRAP_CONTENT;
        } else {
            content.setOrientation(LinearLayout.VERTICAL);
            params.width = LayoutParams.WRAP_CONTENT;
            params.height = 0;
        }
        ApiCompatibilityUtils.setPaddingRelative(content,
                paddingStart,
                content.getPaddingTop(),
                ApiCompatibilityUtils.getPaddingEnd(content),
                content.getPaddingBottom());
        wrapper.setLayoutParams(params);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
