// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.content.Context;
import android.util.AttributeSet;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkItem;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.components.bookmarks.BookmarkId;

/**
 * A row view that shows folder info in the enhanced bookmarks UI.
 */
public class EnhancedBookmarkFolderRow extends EnhancedBookmarkRow {

    /**
     * Constructor for inflating from XML.
     */
    public EnhancedBookmarkFolderRow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconImageView.setImageDrawable(
                TintedDrawable.constructTintedDrawable(getResources(), R.drawable.eb_folder));
    }

    // EnhancedBookmarkRow implementation.

    @Override
    public void onClick() {
        mDelegate.openFolder(mBookmarkId);
    }

    @Override
    BookmarkItem setBookmarkId(BookmarkId bookmarkId) {
        BookmarkItem item = super.setBookmarkId(bookmarkId);
        mTitleView.setText(item.getTitle());
        return item;
    }
}
