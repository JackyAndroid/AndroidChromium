// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.content.Context;
import android.util.AttributeSet;

import org.chromium.chrome.browser.BookmarksBridge.BookmarkItem;
import org.chromium.components.bookmarks.BookmarkId;

/**
 * A view representing each row shown in {@link EnhancedBookmarkSearchRow}. Note this type of row is
 * not selectable for now.
 */
public class EnhancedBookmarkSearchRow extends EnhancedBookmarkBookmarkRow {

    /**
     * A listener that is triggered when a search result is selected.
     */
    interface SearchHistoryDelegate {
        /**
         * Save the current search term to search history. This is called when a search result has
         * been clicked.
         */
        public void saveSearchHistory();
    }

    private SearchHistoryDelegate mHistoryDelegate;

    /**
     * Constructor for xml inflation.
     */
    public EnhancedBookmarkSearchRow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    @Override
    BookmarkItem setBookmarkId(BookmarkId bookmarkId) {
        BookmarkItem item = super.setBookmarkId(bookmarkId);
        return item;
    }

    @Override
    protected boolean isSelectable() {
        return false;
    }

    @Override
    public void onClick() {
        mDelegate.openBookmark(mBookmarkId, LaunchLocation.SEARCH);
        mDelegate.closeSearchUI();
        mHistoryDelegate.saveSearchHistory();
    }

    /**
     * Sets the delegate that handles saving search history.
     */
    void setSearchHistoryDelegate(SearchHistoryDelegate delegate) {
        mHistoryDelegate = delegate;
    }
}
