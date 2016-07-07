// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.chromium.chrome.R;
import org.chromium.ui.widget.ButtonCompat;

/**
 * View that handles orientation changes for Sad Tab / Crashed Renderer page.
 */
public class SadTabView extends ScrollView {

    // Dimension (dp) at which reload button is dynamically sized and content centers
    private static final int MAX_BUTTON_WIDTH_DP = 620;
    private int mThresholdPx;
    private float mDensity;

    public SadTabView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;
        mThresholdPx = (int) (mDensity * MAX_BUTTON_WIDTH_DP);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // This assumes that view's layout_width is set to match_parent.
        assert MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        final ButtonCompat mReloadButton = (ButtonCompat) findViewById(R.id.sad_tab_reload_button);

        final LinearLayout.LayoutParams mReloadButtonParams =
                (LinearLayout.LayoutParams) mReloadButton.getLayoutParams();

        if ((width > height || width > mThresholdPx) && mReloadButton.getWidth() <= width) {
            // Orientation is landscape
            mReloadButtonParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            mReloadButtonParams.gravity = Gravity.END;
        } else {
            // Orientation is portrait
            mReloadButtonParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            mReloadButtonParams.gravity = Gravity.FILL_HORIZONTAL;
        }

        mReloadButton.setLayoutParams(mReloadButtonParams);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
