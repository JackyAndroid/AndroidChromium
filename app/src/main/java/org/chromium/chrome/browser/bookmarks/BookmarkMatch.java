// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.util.Pair;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.List;

/**
 * Object that associates a BookmarkId with search term matches found in the bookmark's title and
 * url.
 */
public class BookmarkMatch {

    private final BookmarkId mBookmarkId;
    private final List<Pair<Integer, Integer>> mTitleMatchPositions;
    private final List<Pair<Integer, Integer>> mUrlMatchPositions;

    /**
     * @param bookmarkId The BookmarkId fassociated with this match.
     * @param titleMatchPositions A list of [begin, end) positions for matches in the title;
     *                            may be null.
     * @param urlMatchPositions A list of [begin, end) positions for matches in the url;
     *                          may be null.
     */
    public BookmarkMatch(BookmarkId bookmarkId, List<Pair<Integer, Integer>> titleMatchPositions,
            List<Pair<Integer, Integer>> urlMatchPositions) {
        mBookmarkId = bookmarkId;
        mTitleMatchPositions = titleMatchPositions;
        mUrlMatchPositions = urlMatchPositions;
    }

    /**
     * @return The BookmarkId associated with this match.
     */
    public BookmarkId getBookmarkId() {
        return mBookmarkId;
    }

    /**
     * @return A list of [begin, end) positions for matches in the title; may return null.
     */
    public List<Pair<Integer, Integer>> getTitleMatchPositions() {
        return mTitleMatchPositions;
    }

    /**
     * @return A list of [begin, end) positions for matches in the url; may return null.
     */
    public List<Pair<Integer, Integer>> getUrlMatchPositions() {
        return mUrlMatchPositions;
    }

    @CalledByNative
    private static BookmarkMatch createBookmarkMatch(BookmarkId bookmarkId,
            List<Pair<Integer, Integer>> titleMatchPositions,
            List<Pair<Integer, Integer>> urlMatchPositions) {
        return new BookmarkMatch(bookmarkId, titleMatchPositions, urlMatchPositions);
    }
}
