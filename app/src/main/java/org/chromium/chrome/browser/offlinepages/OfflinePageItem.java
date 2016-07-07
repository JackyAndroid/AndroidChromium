// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import org.chromium.base.VisibleForTesting;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;

/**
 * Simple object representing an offline page.
 */
public class OfflinePageItem {
    private final String mUrl;
    private final BookmarkId mBookmarId;
    private final String mOfflineUrl;
    private final long mFileSize;
    private final int mAccessCount;
    private final long mLastAccessTimeMs;

    public OfflinePageItem(String url, long bookmarkId, String offlineUrl, long fileSize,
            int accessCount, long lastAccessTimeMs) {
        mUrl = url;
        mBookmarId = new BookmarkId(bookmarkId, BookmarkType.NORMAL);
        mOfflineUrl = offlineUrl;
        mFileSize = fileSize;
        mAccessCount = accessCount;
        mLastAccessTimeMs = lastAccessTimeMs;
    }

    /** @return URL of the offline page. */
    @VisibleForTesting
    public String getUrl() {
        return mUrl;
    }

    /** @return Bookmark Id related to the offline page. */
    @VisibleForTesting
    public BookmarkId getBookmarkId() {
        return mBookmarId;
    }

    /** @return Path to the offline copy of the page. */
    @VisibleForTesting
    public String getOfflineUrl() {
        return mOfflineUrl;
    }

    /** @return Size of the offline copy of the page. */
    @VisibleForTesting
    public long getFileSize() {
        return mFileSize;
    }

    /** @return Number of times that the offline page has been accessed. */
    @VisibleForTesting
    public int getAccessCount() {
        return mAccessCount;
    }

    /** @return Last time in milliseconds the offline page has been accessed. */
    public long getLastAccessTimeMs() {
        return mLastAccessTimeMs;
    }
}
