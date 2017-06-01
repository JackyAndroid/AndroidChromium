// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.partnerbookmarks;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Reads bookmarks from the partner content provider (if any).
*/
public class PartnerBookmarksReader {
    private static final String TAG = "PartnerBookmarksReader";

    private static boolean sInitialized = false;
    private static boolean sForceDisableEditing = false;

    /** Root bookmark id reserved for the implied root of the bookmarks */
    static final long ROOT_FOLDER_ID = 0;

    /** ID used to indicate an invalid bookmark node. */
    static final long INVALID_BOOKMARK_ID = -1;

    // JNI c++ pointer
    private long mNativePartnerBookmarksReader = 0;

    /** The context (used to get a ContentResolver) */
    protected Context mContext;

    // TODO(aruslan): Move it out to a separate class that defines
    // a partner bookmarks provider contract, see http://b/6399404
    /** Object defining a partner bookmark. For this package only. */
    static class Bookmark {
        // To be provided by the bookmark extractors.
        /** Local id of the read bookmark */
        long mId;
        /** Read id of the parent node */
        long mParentId;
        /** True if it's folder */
        boolean mIsFolder;
        /** URL of the bookmark. Required for non-folders. */
        String mUrl;
        /** Title of the bookmark. */
        String mTitle;
        /** .PNG Favicon of the bookmark. Optional. Not used for folders. */
        byte[] mFavicon;
        /** .PNG TouchIcon of the bookmark. Optional. Not used for folders. */
        byte[] mTouchicon;

        // For auxiliary use while reading.
        /** Native id of the C++-processed bookmark */
        long mNativeId = INVALID_BOOKMARK_ID;
        /** The parent node if any */
        Bookmark mParent;
        /** Children nodes for the perfect garbage collection disaster */
        ArrayList<Bookmark> mEntries = new ArrayList<Bookmark>();
    }

    /** Closable iterator for available bookmarks. */
    protected interface BookmarkIterator extends Iterator<Bookmark> {
        public void close();
    }

    /** Returns an iterator to the available bookmarks. Called by async task. */
    protected BookmarkIterator getAvailableBookmarks() {
        return PartnerBookmarksProviderIterator.createIfAvailable(
                mContext.getContentResolver());
    }

    /**
     * Creates the instance of the reader.
     * @param context A Context object.
     */
    public PartnerBookmarksReader(Context context) {
        mContext = context;
        mNativePartnerBookmarksReader = nativeInit();
        initializeAndDisableEditingIfNecessary();
    }

    /**
     * Asynchronously read bookmarks from the partner content provider
     */
    public void readBookmarks() {
        if (mNativePartnerBookmarksReader == 0) {
            assert false : "readBookmarks called after nativeDestroy.";
            return;
        }
        new ReadBookmarksTask().execute();
    }

    /**
     * Called when the partner bookmark needs to be pushed.
     * @param url       The URL.
     * @param title     The title.
     * @param isFolder  True if it's a folder.
     * @param parentId  NATIVE parent folder id.
     * @param favicon   .PNG blob for icon; used if no touchicon is set.
     * @param touchicon .PNG blob for icon.
     * @return          NATIVE id of a bookmark
     */
    private long onBookmarkPush(String url, String title, boolean isFolder, long parentId,
            byte[] favicon, byte[] touchicon) {
        return nativeAddPartnerBookmark(mNativePartnerBookmarksReader, url, title,
                isFolder, parentId, favicon, touchicon);
    }

    /** Notifies the reader is complete and partner bookmarks should be submitted to the shim. */
    protected void onBookmarksRead() {
        nativePartnerBookmarksCreationComplete(mNativePartnerBookmarksReader);
        nativeDestroy(mNativePartnerBookmarksReader);
        mNativePartnerBookmarksReader = 0;
    }

    /** Handles fetching partner bookmarks in a background thread. */
    private class ReadBookmarksTask extends AsyncTask<Void, Void, Void> {
        private final Object mRootSync = new Object();

        @Override
        protected Void doInBackground(Void... params) {
            BookmarkIterator bookmarkIterator = getAvailableBookmarks();
            if (bookmarkIterator == null) return null;

            // Get a snapshot of the bookmarks.
            LinkedHashMap<Long, Bookmark> idMap = new LinkedHashMap<Long, Bookmark>();
            HashSet<String> urlSet = new HashSet<String>();

            Bookmark rootBookmarksFolder = createRootBookmarksFolderBookmark();
            idMap.put(ROOT_FOLDER_ID, rootBookmarksFolder);

            while (bookmarkIterator.hasNext()) {
                Bookmark bookmark = bookmarkIterator.next();
                if (bookmark == null) continue;

                // Check for duplicate ids.
                if (idMap.containsKey(bookmark.mId)) {
                    Log.i(TAG, "Duplicate bookmark id: "
                            +  bookmark.mId + ". Dropping bookmark.");
                    continue;
                }

                // Check for duplicate URLs.
                if (!bookmark.mIsFolder && urlSet.contains(bookmark.mUrl)) {
                    Log.i(TAG, "More than one bookmark pointing to "
                            + bookmark.mUrl
                            + ". Keeping only the first one for consistency with Chromium.");
                    continue;
                }

                idMap.put(bookmark.mId, bookmark);
                urlSet.add(bookmark.mUrl);
            }
            bookmarkIterator.close();

            // Recreate the folder hierarchy and read it.
            recreateFolderHierarchy(idMap);
            if (rootBookmarksFolder.mEntries.size() == 0) {
                Log.e(TAG, "ATTENTION: not using partner bookmarks as none were provided");
                return null;
            }
            if (rootBookmarksFolder.mEntries.size() != 1) {
                Log.e(TAG, "ATTENTION: more than one top-level partner bookmarks, ignored");
                return null;
            }

            readBookmarkHierarchy(
                    rootBookmarksFolder,
                    new HashSet<PartnerBookmarksReader.Bookmark>());

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            synchronized (mRootSync) {
                onBookmarksRead();
            }
        }

        private void recreateFolderHierarchy(LinkedHashMap<Long, Bookmark> idMap) {
            for (Bookmark bookmark : idMap.values()) {
                if (bookmark.mId == ROOT_FOLDER_ID) continue;

                // Look for invalid parent ids and self-cycles.
                if (!idMap.containsKey(bookmark.mParentId) || bookmark.mParentId == bookmark.mId) {
                    bookmark.mParent = idMap.get(ROOT_FOLDER_ID);
                    bookmark.mParent.mEntries.add(bookmark);
                    continue;
                }

                bookmark.mParent = idMap.get(bookmark.mParentId);
                bookmark.mParent.mEntries.add(bookmark);
            }
        }

        private Bookmark createRootBookmarksFolderBookmark() {
            Bookmark root = new Bookmark();
            root.mId = ROOT_FOLDER_ID;
            root.mTitle = "[IMPLIED_ROOT]";
            root.mNativeId = INVALID_BOOKMARK_ID;
            root.mParentId = ROOT_FOLDER_ID;
            root.mIsFolder = true;
            return root;
        }

        private void readBookmarkHierarchy(
                Bookmark bookmark, HashSet<Bookmark> processedNodes) {
            // Avoid cycles in the hierarchy that could lead to infinite loops.
            if (processedNodes.contains(bookmark)) return;
            processedNodes.add(bookmark);

            if (bookmark.mId != ROOT_FOLDER_ID) {
                try {
                    synchronized (mRootSync) {
                        bookmark.mNativeId =
                                onBookmarkPush(
                                        bookmark.mUrl, bookmark.mTitle,
                                        bookmark.mIsFolder, bookmark.mParentId,
                                        bookmark.mFavicon, bookmark.mTouchicon);
                    }
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Error inserting bookmark " + bookmark.mTitle, e);
                }
                if (bookmark.mNativeId == INVALID_BOOKMARK_ID) {
                    Log.e(TAG, "Error creating bookmark '" + bookmark.mTitle + "'.");
                    return;
                }
            }

            if (bookmark.mIsFolder) {
                for (Bookmark entry : bookmark.mEntries) {
                    if (entry.mParent != bookmark) {
                        Log.w(TAG, "Hierarchy error in bookmark '"
                                + bookmark.mTitle + "'. Skipping.");
                        continue;
                    }
                    entry.mParentId = bookmark.mNativeId;
                    readBookmarkHierarchy(entry, processedNodes);
                }
            }
        }
    }

    /**
     * Disables partner bookmarks editing.
     */
    public static void disablePartnerBookmarksEditing() {
        sForceDisableEditing = true;
        if (sInitialized) nativeDisablePartnerBookmarksEditing();
    }

    private static void initializeAndDisableEditingIfNecessary() {
        sInitialized = true;
        if (sForceDisableEditing) disablePartnerBookmarksEditing();
    }

    // JNI
    private native long nativeInit();
    private native void nativeReset(long nativePartnerBookmarksReader);
    private native void nativeDestroy(long nativePartnerBookmarksReader);
    private native long nativeAddPartnerBookmark(long nativePartnerBookmarksReader,
            String url, String title, boolean isFolder, long parentId,
            byte[] favicon, byte[] touchicon);
    private native void nativePartnerBookmarksCreationComplete(long nativePartnerBookmarksReader);
    private static native void nativeDisablePartnerBookmarksEditing();
}
