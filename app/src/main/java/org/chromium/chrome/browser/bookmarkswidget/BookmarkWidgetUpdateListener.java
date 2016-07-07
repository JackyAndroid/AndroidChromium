// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkswidget;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;

import org.chromium.chrome.browser.ChromeBrowserProvider;
import org.chromium.sync.AndroidSyncSettings;

/**
 * Encapsulates the different observers that can cause a widget update.
 */
public class BookmarkWidgetUpdateListener {
    private static final String TAG = "BookmarkWidgetUpdateListener";

    /**
     * Notifies about the different kinds of updates that affect the bookmarks widget.
     */
    public interface UpdateListener {
        /**
         * Called when the was a change in the bookmark model.
         */
        public void onBookmarkModelUpdated();

        /**
         * Called when the app sync enabled status has changed.
         *
         * @param enabled New state of the sync setting after the change.
         */
        public void onSyncEnabledStatusUpdated(boolean enabled);

        /**
         * Called when a page thumbnail has been updated or created.
         */
        public void onThumbnailUpdated(String url);
    }

    /**
     * Handles changes in the bookmarks.
     */
    private class BookmarkUpdateObserver extends ContentObserver {
        public BookmarkUpdateObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mListener != null) mListener.onBookmarkModelUpdated();
        }
    }

    /**
     * Handles changes in the sync settings.
     */
    private class SyncUpdateObserver implements AndroidSyncSettings.AndroidSyncSettingsObserver {
        private boolean mIsSyncEnabled;

        public SyncUpdateObserver() {
            AndroidSyncSettings.registerObserver(mContext, this);
            mIsSyncEnabled = AndroidSyncSettings.isSyncEnabled(mContext);
        }

        @Override
        public void androidSyncSettingsChanged() {
            boolean newSyncStatus = AndroidSyncSettings.isSyncEnabled(mContext);
            if (mIsSyncEnabled != newSyncStatus) {
                mIsSyncEnabled = newSyncStatus;
                if (mListener != null) mListener.onSyncEnabledStatusUpdated(newSyncStatus);
            }
        }
    }

    private final Context mContext;
    private UpdateListener mListener;
    private BookmarkUpdateObserver mBookmarkUpdateObserver;
    private AndroidSyncSettings.AndroidSyncSettingsObserver mSyncObserver;

    public BookmarkWidgetUpdateListener(Context context, UpdateListener listener) {
        mContext = context;

        if (listener == null) return;
        mListener = listener;

        // Register observers for bookmark and sync state updates.
        ContentResolver contentResolver = mContext.getContentResolver();
        mBookmarkUpdateObserver = new BookmarkUpdateObserver();
        contentResolver.registerContentObserver(
                ChromeBrowserProvider.getBookmarksApiUri(mContext), true,
                mBookmarkUpdateObserver);

        mSyncObserver = new SyncUpdateObserver();
    }

    public void destroy() {
        if (mListener == null) return;

        // Unregister observers.
        ContentResolver contentResolver = mContext.getContentResolver();
        contentResolver.unregisterContentObserver(mBookmarkUpdateObserver);
        AndroidSyncSettings.unregisterObserver(mContext, mSyncObserver);
        mListener = null;
    }
}
