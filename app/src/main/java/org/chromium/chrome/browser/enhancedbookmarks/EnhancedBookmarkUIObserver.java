// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import org.chromium.components.bookmarks.BookmarkId;

import java.util.List;

/**
 * Observer interface to get notification for UI mode changes, bookmark changes, and other related
 * event that affects UI. All enhanced bookmark UI components are expected to implement this and
 * update themselves correctly on each event.
 */
interface EnhancedBookmarkUIObserver {
    void onEnhancedBookmarkDelegateInitialized(EnhancedBookmarkDelegate delegate);

    /**
     * Called when the entire UI is being destroyed and will be no longer in use.
     */
    void onDestroy();

    /**
     * @see EnhancedBookmarkDelegate#openAllBookmarks()
     */
    void onAllBookmarksStateSet();

    /**
     * @see EnhancedBookmarkDelegate#openFolder(BookmarkId)
     */
    void onFolderStateSet(BookmarkId folder);

    /**
     * @see EnhancedBookmarkDelegate#openFilter(EnhancedBookmarkFilter)
     */
    void onFilterStateSet(EnhancedBookmarkFilter filter);

    /**
     * Please refer to
     * {@link EnhancedBookmarkDelegate#toggleSelectionForBookmark(BookmarkId)},
     * {@link EnhancedBookmarkDelegate#clearSelection()} and
     * {@link EnhancedBookmarkDelegate#getSelectedBookmarks()}
     */
    void onSelectionStateChange(List<BookmarkId> selectedBookmarks);
}
