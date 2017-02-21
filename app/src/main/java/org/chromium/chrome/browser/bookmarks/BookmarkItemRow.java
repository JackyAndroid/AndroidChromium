// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.AttributeSet;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkItem;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.components.bookmarks.BookmarkId;

/**
 * A row view that shows bookmark info in the bookmarks UI.
 */
public class BookmarkItemRow extends BookmarkRow implements LargeIconCallback {

    private String mUrl;
    private RoundedIconGenerator mIconGenerator;
    private final int mMinIconSize;
    private final int mDisplayedIconSize;
    private final int mCornerRadius;

    /**
     * Constructor for inflating from XML.
     */
    public BookmarkItemRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCornerRadius = getResources().getDimensionPixelSize(R.dimen.default_favicon_corner_radius);
        mMinIconSize = (int) getResources().getDimension(R.dimen.default_favicon_min_size);
        mDisplayedIconSize = getResources().getDimensionPixelSize(R.dimen.default_favicon_size);
        int textSize = getResources().getDimensionPixelSize(R.dimen.default_favicon_icon_text_size);
        int iconColor = ApiCompatibilityUtils.getColor(
                getResources(), R.color.default_favicon_background_color);
        mIconGenerator = new RoundedIconGenerator(mDisplayedIconSize , mDisplayedIconSize,
                mCornerRadius, iconColor, textSize);
    }

    // BookmarkRow implementation.

    @Override
    public void onClick() {
        int launchLocation = -1;
        switch (mDelegate.getCurrentState()) {
            case BookmarkUIState.STATE_FOLDER:
                launchLocation = BookmarkLaunchLocation.FOLDER;
                break;
            case BookmarkUIState.STATE_LOADING:
                assert false :
                        "The main content shouldn't be inflated if it's still loading";
                break;
            default:
                assert false : "State not valid";
                break;
        }
        mDelegate.openBookmark(mBookmarkId, launchLocation);
    }

    @Override
    BookmarkItem setBookmarkId(BookmarkId bookmarkId) {
        BookmarkItem item = super.setBookmarkId(bookmarkId);
        mUrl = item.getUrl();
        mIconImageView.setImageDrawable(null);
        mTitleView.setText(item.getTitle());
        mDelegate.getLargeIconBridge().getLargeIconForUrl(mUrl, mMinIconSize, this);
        return item;
    }

    // LargeIconCallback implementation.

    @Override
    public void onLargeIconAvailable(
            Bitmap icon, int fallbackColor, boolean isFallbackColorDefault) {
        if (icon == null) {
            mIconGenerator.setBackgroundColor(fallbackColor);
            icon = mIconGenerator.generateIconForUrl(mUrl);
            mIconImageView.setImageDrawable(new BitmapDrawable(getResources(), icon));
        } else {
            RoundedBitmapDrawable roundedIcon = RoundedBitmapDrawableFactory.create(
                    getResources(),
                    Bitmap.createScaledBitmap(icon, mDisplayedIconSize, mDisplayedIconSize, false));
            roundedIcon.setCornerRadius(mCornerRadius);
            mIconImageView.setImageDrawable(roundedIcon);
        }
    }
}
