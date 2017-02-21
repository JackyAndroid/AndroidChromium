// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ScrollView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.FadingShadow;

/**
 * An extension of the ScrollView that supports edge shadows with alpha components.
 */
public class FadingEdgeScrollView extends ScrollView {
    private static final int SHADOW_COLOR = 0x11000000;

    private final FadingShadow mFadingShadow;
    private boolean mDrawTopShadow = true;
    private boolean mDrawBottomShadow = true;

    public FadingEdgeScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mFadingShadow = new FadingShadow(SHADOW_COLOR);
        setFadingEdgeLength(getResources().getDimensionPixelSize(R.dimen.ntp_shadow_height));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        setVerticalFadingEdgeEnabled(true);
        float topShadowStrength = getTopFadingEdgeStrength();
        float bottomShadowStrength = getBottomFadingEdgeStrength();
        float shadowHeight = getVerticalFadingEdgeLength();
        setVerticalFadingEdgeEnabled(false);

        if (mDrawTopShadow) {
            mFadingShadow.drawShadow(this, canvas, FadingShadow.POSITION_TOP,
                    shadowHeight, topShadowStrength);
        }

        if (mDrawBottomShadow) {
            mFadingShadow.drawShadow(this, canvas, FadingShadow.POSITION_BOTTOM,
                    shadowHeight, bottomShadowStrength);
        }
    }

    /**
     * Sets which shadows should be drawn.
     * @param drawTopShadow    Whether to draw the shadow on the top part of the view.
     * @param drawBottomShadow Whether to draw the shadow on the bottom part of the view.
     */
    public void setShadowVisibility(boolean drawTopShadow, boolean drawBottomShadow) {
        mDrawTopShadow = drawTopShadow;
        mDrawBottomShadow = drawBottomShadow;
    }
}
