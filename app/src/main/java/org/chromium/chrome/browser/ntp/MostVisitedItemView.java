// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * Displays the title, thumbnail, and favicon of a most visited page. The item can be clicked, or
 * long-pressed to trigger a context menu with options to "open in new tab", "open in incognito
 * tab", or "remove".
 */
public class MostVisitedItemView extends FrameLayout {

    private static final int MISSING_FAVICON_COLOR = 0xffe6e6e8;

    private TextView mTitleView;
    private MostVisitedThumbnail mThumbnailView;
    private int mFaviconSize;
    private int mTitlePaddingStart;

    /**
     * Constructor for inflating from XML.
     */
    public MostVisitedItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Initializes the item. This must be called immediately after construction.
     *
     * @param title The title of the page.
     */
    public void init(String title) {
        mTitleView = (TextView) findViewById(R.id.most_visited_title);
        mThumbnailView = (MostVisitedThumbnail) findViewById(R.id.most_visited_thumbnail);

        mTitleView.setText(title);

        // Add padding to fill the space where the favicon will be shown. Once the favicon is
        // available (in setFavicon()), this extra padding will be removed and the favicon will be
        // added as a compound drawable. This prevents the text from jumping around when the favicon
        // becomes available.
        mTitlePaddingStart = ApiCompatibilityUtils.getPaddingStart(mTitleView);
        mFaviconSize = getResources().getDimensionPixelSize(R.dimen.default_favicon_size);
        int extraPaddingStart = mFaviconSize + mTitleView.getCompoundDrawablePadding();
        ApiCompatibilityUtils.setPaddingRelative(mTitleView, mTitlePaddingStart + extraPaddingStart,
                0, 0, 0);
    }

    /**
     * Update the thumbnail and trigger a redraw with the new thumbnail.
     */
    public void setThumbnail(Bitmap thumbnail) {
        mThumbnailView.setThumbnail(thumbnail);
    }

    /**
     * Update the favicon and trigger a redraw with the new favicon.
     */
    public void setFavicon(Bitmap favicon) {
        Resources res = getResources();
        Drawable d;
        if (favicon != null) {
            d = new BitmapDrawable(res, favicon);
        } else {
            d = new ColorDrawable(MISSING_FAVICON_COLOR);
        }
        d.setBounds(0, 0, mFaviconSize, mFaviconSize);
        ApiCompatibilityUtils.setCompoundDrawablesRelative(mTitleView, d, null, null, null);
        ApiCompatibilityUtils.setPaddingRelative(mTitleView, mTitlePaddingStart, 0, 0, 0);
    }
}
