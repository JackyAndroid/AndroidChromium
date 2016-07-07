// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.partnerbookmarks;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.NoSuchElementException;

/**
 * Imports bookmarks from partner content provider using the private provider API.
 */
public class PartnerBookmarksProviderIterator implements PartnerBookmarksReader.BookmarkIterator {

    private static final String TAG = "PartnerBookmarksProviderIterator";
    private static final String PROVIDER_AUTHORITY = "com.android.partnerbookmarks";
    private static final Uri CONTENT_URI = new Uri.Builder().scheme("content")
            .authority(PROVIDER_AUTHORITY).build();

    // Private bookmarks structure.
    private static final String BOOKMARKS_PATH = "bookmarks";
    private static final Uri BOOKMARKS_CONTENT_URI =
            CONTENT_URI.buildUpon().appendPath(BOOKMARKS_PATH).build();
    private static final String BOOKMARKS_COLUMN_ID = "_id";
    private static final String BOOKMARKS_COLUMN_URL = "url";
    private static final String BOOKMARKS_COLUMN_TITLE = "title";
    private static final String BOOKMARKS_COLUMN_TYPE = "type";
    private static final String BOOKMARKS_COLUMN_PARENT = "parent";
    private static final String BOOKMARKS_COLUMN_FAVICON = "favicon";
    private static final String BOOKMARKS_COLUMN_TOUCHICON = "touchicon";

    private static final int BOOKMARK_TYPE_FOLDER = 2;

    // Bookmark id of the main container folder.
    private static final long BOOKMARK_CONTAINER_FOLDER_ID = 0;

    private static final String BOOKMARKS_SORT_ORDER = BOOKMARKS_COLUMN_TYPE
            + " DESC, " + BOOKMARKS_COLUMN_ID + " ASC";

    private static final String[] BOOKMARKS_PROJECTION = {
        BOOKMARKS_COLUMN_ID,
        BOOKMARKS_COLUMN_URL,
        BOOKMARKS_COLUMN_TITLE,
        BOOKMARKS_COLUMN_TYPE,
        BOOKMARKS_COLUMN_PARENT,
        BOOKMARKS_COLUMN_FAVICON,
        BOOKMARKS_COLUMN_TOUCHICON
    };

    private final Cursor mCursor;

    /**
     * Creates the bookmarks iterator if possible.
     * @param contentResolver The content resolver to use.
     * @return                Iterator over bookmarks or null.
     */
    public static PartnerBookmarksProviderIterator createIfAvailable(
            ContentResolver contentResolver) {
        Cursor cursor = contentResolver.query(BOOKMARKS_CONTENT_URI,
                BOOKMARKS_PROJECTION, null, null, BOOKMARKS_SORT_ORDER);
        if (cursor == null) return null;
        return new PartnerBookmarksProviderIterator(cursor);
    }

    private PartnerBookmarksProviderIterator(Cursor cursor) {
        mCursor = cursor;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        if (mCursor == null) throw new IllegalStateException();
        mCursor.close();
    }

    @Override
    public boolean hasNext() {
        if (mCursor == null) throw new IllegalStateException();
        // While the combination of !isLast() && !isAfterLast() should have been
        // sufficient, Android Cursor API doesn't specify isAfterLast's behavior
        // in the case of an empty cursor; hence, we fall back to
        // getCount() > 0 check.
        return mCursor.getCount() > 0 && !mCursor.isLast() && !mCursor.isAfterLast();
    }

    @Override
    public PartnerBookmarksReader.Bookmark next() {
        if (mCursor == null) throw new IllegalStateException();
        if (!mCursor.moveToNext()) throw new NoSuchElementException();

        PartnerBookmarksReader.Bookmark bookmark = new PartnerBookmarksReader.Bookmark();
        try {
            bookmark.mId = mCursor.getLong(mCursor.getColumnIndexOrThrow(BOOKMARKS_COLUMN_ID));
            // The container folder should not be among the results.
            if (bookmark.mId == BOOKMARK_CONTAINER_FOLDER_ID) {
                Log.i(TAG, "Dropping the bookmark: reserved _id was used");
                return null;
            }

            bookmark.mParentId =
                    mCursor.getLong(mCursor.getColumnIndexOrThrow(BOOKMARKS_COLUMN_PARENT));
            if (bookmark.mParentId == BOOKMARK_CONTAINER_FOLDER_ID) {
                bookmark.mParentId = PartnerBookmarksReader.ROOT_FOLDER_ID;
            }
            bookmark.mIsFolder =
                    mCursor.getInt(mCursor.getColumnIndexOrThrow(BOOKMARKS_COLUMN_TYPE))
                            == BOOKMARK_TYPE_FOLDER;
            bookmark.mUrl = mCursor.getString(mCursor.getColumnIndexOrThrow(BOOKMARKS_COLUMN_URL));
            bookmark.mTitle =
                    mCursor.getString(mCursor.getColumnIndexOrThrow(BOOKMARKS_COLUMN_TITLE));
            bookmark.mFavicon =
                    mCursor.getBlob(mCursor.getColumnIndexOrThrow(BOOKMARKS_COLUMN_FAVICON));
            bookmark.mTouchicon =
                    mCursor.getBlob(mCursor.getColumnIndexOrThrow(BOOKMARKS_COLUMN_TOUCHICON));
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "Dropping the bookmark: " + e.getMessage());
            return null;
        }

        if ((!bookmark.mIsFolder && bookmark.mUrl == null) || bookmark.mTitle == null) {
            Log.i(TAG, "Dropping the bookmark: no title, or no url on a non-foler");
            return null;
        }

        return bookmark;
    }
}
