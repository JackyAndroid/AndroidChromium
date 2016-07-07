// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkswidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.google.android.apps.chrome.appwidget.bookmarks.BookmarkThumbnailWidgetProvider;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeBrowserProvider.BookmarkNode;
import org.chromium.chrome.browser.ChromeBrowserProviderClient;
import org.chromium.chrome.browser.bookmark.BookmarkColumns;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.sync.AndroidSyncSettings;

/**
 * Service to support bookmarks on the Android home screen
 */
public class BookmarkThumbnailWidgetService extends RemoteViewsService {

    static final String TAG = "BookmarkThumbnailWidgetService";
    static final String ACTION_CHANGE_FOLDER_SUFFIX = ".CHANGE_FOLDER";
    static final String STATE_CURRENT_FOLDER = "current_folder";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int widgetId = IntentUtils.safeGetIntExtra(intent, AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (widgetId < 0) {
            Log.w(TAG, "Missing EXTRA_APPWIDGET_ID!");
            return null;
        }
        return new BookmarkFactory(this, widgetId);
    }

    static String getChangeFolderAction(Context context) {
        return context.getPackageName() + ACTION_CHANGE_FOLDER_SUFFIX;
    }

    private static SharedPreferences getWidgetState(Context context, int widgetId) {
        return context.getSharedPreferences(
                String.format("widgetState-%d", widgetId),
                Context.MODE_PRIVATE);
    }

    static void deleteWidgetState(Context context, int widgetId) {
        // Android Browser's widget used private API methods to access the shared prefs
        // files and deleted them. This is the best we can do with the public API.
        SharedPreferences preferences = getWidgetState(context, widgetId);
        if (preferences != null) preferences.edit().clear().commit();
    }

    static void changeFolder(Context context, Intent intent) {
        int widgetId = IntentUtils.safeGetIntExtra(intent, AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        long folderId = IntentUtils.safeGetLongExtra(intent, BookmarkColumns.ID,
                ChromeBrowserProviderClient.INVALID_BOOKMARK_ID);
        if (widgetId >= 0 && folderId >= 0) {
            SharedPreferences prefs = getWidgetState(context, widgetId);
            prefs.edit().putLong(STATE_CURRENT_FOLDER, folderId).commit();
            AppWidgetManager.getInstance(context)
                    .notifyAppWidgetViewDataChanged(widgetId, R.id.bookmarks_list);
        }
    }

    static class BookmarkFactory implements RemoteViewsService.RemoteViewsFactory,
            BookmarkWidgetUpdateListener.UpdateListener {

        private final ChromeApplication mContext;
        private final int mWidgetId;
        private final SharedPreferences mPreferences;
        private BookmarkWidgetUpdateListener mUpdateListener;
        private BookmarkNode mCurrentFolder;
        private final Object mLock = new Object();

        public BookmarkFactory(Context context, int widgetId) {
            mContext = (ChromeApplication) context.getApplicationContext();
            mWidgetId = widgetId;
            mPreferences = getWidgetState(mContext, mWidgetId);
        }

        private static long getFolderId(BookmarkNode folder) {
            return folder != null ? folder.id() : ChromeBrowserProviderClient.INVALID_BOOKMARK_ID;
        }

        @SuppressFBWarnings("DM_EXIT")
        @Override
        public void onCreate() {
            // Required to be applied here redundantly to prevent crashes in the cases where the
            // package data is deleted or the Chrome application forced to stop.
            try {
                mContext.startBrowserProcessesAndLoadLibrariesSync(true);
            } catch (ProcessInitException e) {
                Log.e(TAG, "Failed to start browser process.", e);
                // Since the library failed to initialize nothing in the application
                // can work, so kill the whole application not just the activity
                System.exit(-1);
            }
            if (isWidgetNewlyCreated()) {
                RecordUserAction.record("BookmarkNavigatorWidgetAdded");
            }
            mUpdateListener = new BookmarkWidgetUpdateListener(mContext, this);
        }

        @Override
        public void onDestroy() {
            if (mUpdateListener != null) mUpdateListener.destroy();
            deleteWidgetState(mContext, mWidgetId);
        }

        @Override
        public void onBookmarkModelUpdated() {
            refreshWidget();
        }

        @Override
        public void onSyncEnabledStatusUpdated(boolean enabled) {
            synchronized (mLock) {
                // Need to operate in a separate thread as it involves queries to our provider.
                new SyncEnabledStatusUpdatedTask(enabled, getFolderId(mCurrentFolder)).execute();
            }
        }

        @Override
        public void onThumbnailUpdated(String url) {
            synchronized (mLock) {
                if (mCurrentFolder == null) return;

                for (BookmarkNode child : mCurrentFolder.children()) {
                    if (child.isUrl() && url.equals(child.url())) {
                        refreshWidget();
                        break;
                    }
                }
            }
        }

        void refreshWidget() {
            mContext.sendBroadcast(new Intent(
                    BookmarkThumbnailWidgetProviderBase.getBookmarkAppWidgetUpdateAction(mContext),
                    null, mContext, BookmarkThumbnailWidgetProvider.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId));
        }

        void requestFolderChange(long folderId) {
            mContext.sendBroadcast(new Intent(getChangeFolderAction(mContext))
                        .setClass(mContext, BookmarkWidgetProxy.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
                        .putExtra(BookmarkColumns.ID, folderId));
        }

        /**
         * This method relies on the fact that STATE_CURRENT_FOLDER pref is not yet
         * set when onCreate is called for a newly created widget.
         */
        private boolean isWidgetNewlyCreated() {
            long currentFolder = mPreferences.getLong(STATE_CURRENT_FOLDER,
                    ChromeBrowserProviderClient.INVALID_BOOKMARK_ID);
            return currentFolder == ChromeBrowserProviderClient.INVALID_BOOKMARK_ID;
        }

        // Performs the required checks to trigger an update of the widget after changing the sync
        // enable settings. The required provider methods cannot be accessed in the UI thread.
        private class SyncEnabledStatusUpdatedTask extends AsyncTask<Void, Void, Void> {
            private final boolean mEnabled;
            private final long mCurrentFolderId;

            public SyncEnabledStatusUpdatedTask(boolean enabled, long currentFolderId) {
                mEnabled = enabled;
                mCurrentFolderId = currentFolderId;
            }

            @Override
            protected Void doInBackground(Void... params) {
                // If we're in the Mobile Bookmarks folder the icon to go up the hierarchy
                // will either appear or disappear. Need to refresh.
                long mobileBookmarksFolderId =
                        ChromeBrowserProviderClient.getMobileBookmarksFolderId(mContext);
                if (mCurrentFolderId == mobileBookmarksFolderId) {
                    refreshWidget();
                    return null;
                }

                // If disabling sync, we need to move to the Mobile Bookmarks folder if we're
                // not inside that branch of the bookmark hierarchy (will become not accessible).
                if (!mEnabled && !ChromeBrowserProviderClient.isBookmarkInMobileBookmarksBranch(
                        mContext, mCurrentFolderId)) {
                    requestFolderChange(mobileBookmarksFolderId);
                }

                return null;
            }
        }

        // ---------------------------------------------------------------- //
        // ------- Methods below this line run in different thread -------- //
        // ---------------------------------------------------------------- //

        private void syncState() {
            long currentFolderId = mPreferences.getLong(STATE_CURRENT_FOLDER,
                    ChromeBrowserProviderClient.INVALID_BOOKMARK_ID);

            // Keep outside the synchronized block to avoid deadlocks in case loading the folder
            // triggers an update that locks when trying to read mCurrentFolder.
            BookmarkNode newFolder = loadBookmarkFolder(currentFolderId);

            synchronized (mLock) {
                mCurrentFolder =
                        getFolderId(newFolder) != ChromeBrowserProviderClient.INVALID_BOOKMARK_ID
                        ? newFolder : null;
            }

            mPreferences.edit()
                .putLong(STATE_CURRENT_FOLDER, getFolderId(mCurrentFolder))
                .apply();
        }

        private BookmarkNode loadBookmarkFolder(long folderId) {
            if (ThreadUtils.runningOnUiThread()) {
                Log.e(TAG, "Trying to load bookmark folder from the UI thread.");
                return null;
            }

            // If the current folder id doesn't exist (it was deleted) try the current parent.
            // If this fails too then fallback to Mobile Bookmarks.
            if (!ChromeBrowserProviderClient.bookmarkNodeExists(mContext, folderId)) {
                folderId = mCurrentFolder != null ? getFolderId(mCurrentFolder.parent())
                        : ChromeBrowserProviderClient.INVALID_BOOKMARK_ID;
                if (!ChromeBrowserProviderClient.bookmarkNodeExists(mContext, folderId)) {
                    folderId = ChromeBrowserProviderClient.INVALID_BOOKMARK_ID;
                }
            }

            // Need to verify this always because the package data might be cleared while the
            // widget is in the Mobile Bookmarks folder with sync enabled. In that case the
            // hierarchy up folder would still work (we can't update the widget) but the parent
            // folders should not be accessible because sync has been reset when clearing data.
            if (folderId != ChromeBrowserProviderClient.INVALID_BOOKMARK_ID
                    && !AndroidSyncSettings.isSyncEnabled(mContext)
                    && !ChromeBrowserProviderClient.isBookmarkInMobileBookmarksBranch(
                            mContext, folderId)) {
                folderId = ChromeBrowserProviderClient.INVALID_BOOKMARK_ID;
            }

            // Use the Mobile Bookmarks folder by default.
            if (folderId < 0) {
                folderId = ChromeBrowserProviderClient.getMobileBookmarksFolderId(mContext);
                if (folderId == ChromeBrowserProviderClient.INVALID_BOOKMARK_ID) return null;
            }

            return ChromeBrowserProviderClient.getBookmarkNode(mContext, folderId,
                    ChromeBrowserProviderClient.GET_PARENT
                    | ChromeBrowserProviderClient.GET_CHILDREN
                    | ChromeBrowserProviderClient.GET_FAVICONS
                    | ChromeBrowserProviderClient.GET_THUMBNAILS);
        }

        private BookmarkNode getBookmarkForPosition(int position) {
            if (mCurrentFolder == null) return null;

            // The position 0 is saved for an entry of the current folder used to go up.
            // This is not the case when the current node has no parent (it's the root node).
            return (mCurrentFolder.parent() == null)
                    ? mCurrentFolder.children().get(position)
                    : (position == 0
                            ? mCurrentFolder : mCurrentFolder.children().get(position - 1));
        }

        @Override
        public void onDataSetChanged() {
            long token = Binder.clearCallingIdentity();
            syncState();
            Binder.restoreCallingIdentity(token);
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public int getCount() {
            if (mCurrentFolder == null) return 0;
            return mCurrentFolder.children().size() + (mCurrentFolder.parent() != null ? 1 : 0);
        }

        @Override
        public long getItemId(int position) {
            return getFolderId(getBookmarkForPosition(position));
        }

        @Override
        public RemoteViews getLoadingView() {
            return new RemoteViews(mContext.getPackageName(),
                    R.layout.bookmark_thumbnail_widget_item);
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (mCurrentFolder == null) {
                Log.w(TAG, "No current folder data available.");
                return null;
            }

            BookmarkNode bookmark = getBookmarkForPosition(position);
            if (bookmark == null) {
                Log.w(TAG, "Couldn't get bookmark for position " + position);
                return null;
            }

            if (bookmark == mCurrentFolder && bookmark.parent() == null) {
                Log.w(TAG, "Invalid bookmark data: loop detected.");
                return null;
            }

            String title = bookmark.name();
            String url = bookmark.url();
            long id = (bookmark == mCurrentFolder) ? bookmark.parent().id() : bookmark.id();

            // Two layouts are needed because RemoteView does not supporting changing the scale type
            // of an ImageView: boomarks crop their thumbnails, while folders stretch their icon.
            RemoteViews views = !bookmark.isUrl()
                    ? new RemoteViews(mContext.getPackageName(),
                            R.layout.bookmark_thumbnail_widget_item_folder)
                    : new RemoteViews(mContext.getPackageName(),
                            R.layout.bookmark_thumbnail_widget_item);

            // Set the title of the bookmark. Use the url as a backup.
            views.setTextViewText(R.id.label, TextUtils.isEmpty(title) ? url : title);

            if (!bookmark.isUrl()) {
                int thumbId = (bookmark == mCurrentFolder)
                        ? R.drawable.thumb_bookmark_widget_folder_back_holo
                        : R.drawable.thumb_bookmark_widget_folder_holo;
                views.setImageViewResource(R.id.thumb, thumbId);
                views.setImageViewResource(R.id.favicon,
                        R.drawable.ic_bookmark_widget_bookmark_holo_dark);
            } else {
                // RemoteViews require a valid bitmap config.
                Options options = new Options();
                options.inPreferredConfig = Config.ARGB_8888;

                byte[] favicon = bookmark.favicon();
                if (favicon != null && favicon.length > 0) {
                    views.setImageViewBitmap(R.id.favicon,
                            BitmapFactory.decodeByteArray(favicon, 0, favicon.length, options));
                } else {
                    views.setImageViewResource(R.id.favicon,
                            org.chromium.chrome.R.drawable.globe_favicon);
                }

                byte[] thumbnail = bookmark.thumbnail();
                if (thumbnail != null && thumbnail.length > 0) {
                    views.setImageViewBitmap(R.id.thumb,
                            BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length, options));
                } else {
                    views.setImageViewResource(R.id.thumb, R.drawable.browser_thumbnail);
                }
            }

            Intent fillIn;
            if (!bookmark.isUrl()) {
                fillIn = new Intent(getChangeFolderAction(mContext))
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
                        .putExtra(BookmarkColumns.ID, id);
            } else {
                fillIn = new Intent(Intent.ACTION_VIEW);
                if (!TextUtils.isEmpty(url)) {
                    fillIn = fillIn.addCategory(Intent.CATEGORY_BROWSABLE)
                            .setData(Uri.parse(url));
                } else {
                    fillIn = fillIn.addCategory(Intent.CATEGORY_LAUNCHER);
                }
            }
            views.setOnClickFillInIntent(R.id.list_item, fillIn);
            return views;
        }
    }
}
