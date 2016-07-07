// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ListView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.FadingShadow;

/**
 * The ListView that holds the bookmark items.
 */
public class BookmarksListView extends ListView {

    private static final int SHADOW_COLOR = 0x11000000;

    private FadingShadow mFadingShadow;

    public BookmarksListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFadingShadow = new FadingShadow(SHADOW_COLOR);
        setFadingEdgeLength(getResources().getDimensionPixelSize(R.dimen.ntp_shadow_height));
        setDivider(null);
        setDividerHeight(0);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        setVerticalFadingEdgeEnabled(true);
        float shadowStrength = getTopFadingEdgeStrength();
        float shadowHeight = getVerticalFadingEdgeLength();
        setVerticalFadingEdgeEnabled(false);
        mFadingShadow.drawShadow(this, canvas, FadingShadow.POSITION_TOP, shadowHeight,
                shadowStrength);
    }

    // Change the visibility of this method to public, so BookmarksPageView can call it.
    @Override
    public boolean awakenScrollBars() {
        return super.awakenScrollBars();
    }
}
