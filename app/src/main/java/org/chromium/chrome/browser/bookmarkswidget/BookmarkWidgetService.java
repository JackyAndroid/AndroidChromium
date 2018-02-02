// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkswidget;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.StrictMode;
import android.support.annotation.BinderThread;
import android.support.annotation.UiThread;
import android.text.TextUtils;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.google.android.apps.chrome.appwidget.bookmarks.BookmarkThumbnailWidgetProvider;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkItem;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.bookmarks.BookmarkModel;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksShim;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.Nullable;

/**
 * Service to support the bookmarks widget.
 *
 * This provides the list of bookmarks to show in the widget via a RemoteViewsFactory (the
 * RemoteViews equivalent of an Adapter), and updates the widget when the bookmark model changes.
 *
 * Threading note: Be careful! Android calls some methods in this class on the UI thread and others
 * on (multiple) binder threads. Additionally, all interaction with the BookmarkModel must happen on
 * the UI thread. To keep the situation clear, every non-static method is annotated with either
 * {@link UiThread} or {@link BinderThread}.
 */
public class BookmarkWidgetService extends RemoteViewsService {

    private static final String TAG = "BookmarkWidget";
    private static final String ACTION_CHANGE_FOLDER_SUFFIX = ".CHANGE_FOLDER";
    private static final String PREF_CURRENT_FOLDER = "bookmarkswidget.current_folder";
    private static final String EXTRA_FOLDER_ID = "folderId";

    @UiThread
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int widgetId = IntentUtils.safeGetIntExtra(intent, AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (widgetId < 0) {
            Log.w(TAG, "Missing EXTRA_APPWIDGET_ID!");
            return null;
        }
        return new BookmarkAdapter(this, widgetId);
    }

    static String getChangeFolderAction(Context context) {
        return context.getPackageName() + ACTION_CHANGE_FOLDER_SUFFIX;
    }

    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("DefaultLocale")
    static SharedPreferences getWidgetState(Context context, int widgetId) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();
        try {
            return context.getSharedPreferences(
                    String.format("widgetState-%d", widgetId),
                    Context.MODE_PRIVATE);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    static void deleteWidgetState(Context context, int widgetId) {
        SharedPreferences preferences = getWidgetState(context, widgetId);
        if (preferences != null) preferences.edit().clear().apply();
    }

    static void changeFolder(Context context, Intent intent) {
        int widgetId = IntentUtils.safeGetIntExtra(intent, AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        String serializedFolder = IntentUtils.safeGetStringExtra(intent, EXTRA_FOLDER_ID);
        if (widgetId >= 0 && serializedFolder != null) {
            SharedPreferences prefs = getWidgetState(context, widgetId);
            prefs.edit().putString(PREF_CURRENT_FOLDER, serializedFolder).apply();
            AppWidgetManager.getInstance(context)
                    .notifyAppWidgetViewDataChanged(widgetId, R.id.bookmarks_list);
        }
    }

    /**
     * Holds data describing a bookmark or bookmark folder.
     */
    private static class Bookmark {
        public String title;
        public String url;
        public BookmarkId id;
        public BookmarkId parentId;
        public boolean isFolder;
        public Bitmap favicon;

        public static Bookmark fromBookmarkItem(BookmarkItem item) {
            if (item == null) return null;

            Bookmark bookmark = new Bookmark();
            bookmark.title = item.getTitle();
            bookmark.url = item.getUrl();
            bookmark.id = item.getId();
            bookmark.parentId = item.getParentId();
            bookmark.isFolder = item.isFolder();
            return bookmark;
        }
    }

    /**
     * Holds the list of bookmarks in a folder, as well as information about the folder itself and
     * its parent folder, if any.
     */
    private static class BookmarkFolder {
        public Bookmark folder;
        @Nullable public Bookmark parent;
        public final List<Bookmark> children = new ArrayList<>();
    }

    /**
     * Called when the BookmarkLoader has finished loading the bookmark folder.
     */
    private interface BookmarkLoaderCallback {
        @UiThread
        void onBookmarksLoaded(BookmarkFolder folder);
    }

    /**
     * Loads a BookmarkFolder asynchronously, and returns the result via BookmarkLoaderCallback.
     *
     * This class must be used only on the UI thread.
     */
    private static class BookmarkLoader {
        private BookmarkLoaderCallback mCallback;
        private BookmarkFolder mFolder;
        private BookmarkModel mBookmarkModel;
        private LargeIconBridge mLargeIconBridge;
        private RoundedIconGenerator mIconGenerator;
        private int mMinIconSizeDp;
        private int mDisplayedIconSize;
        private int mCornerRadius;
        private int mRemainingTaskCount;

        @UiThread
        public void initialize(Context context, final BookmarkId folderId,
                BookmarkLoaderCallback callback) {
            mCallback = callback;

            Resources res = context.getResources();
            mLargeIconBridge = new LargeIconBridge(
                    Profile.getLastUsedProfile().getOriginalProfile());
            mMinIconSizeDp = (int) res.getDimension(R.dimen.default_favicon_min_size);
            mDisplayedIconSize = res.getDimensionPixelSize(R.dimen.default_favicon_size);
            mCornerRadius = res.getDimensionPixelSize(R.dimen.default_favicon_corner_radius);
            int textSize = res.getDimensionPixelSize(R.dimen.default_favicon_icon_text_size);
            int iconColor =
                    ApiCompatibilityUtils.getColor(res, R.color.default_favicon_background_color);
            mIconGenerator = new RoundedIconGenerator(mDisplayedIconSize, mDisplayedIconSize,
                    mCornerRadius, iconColor, textSize);

            mRemainingTaskCount = 1;
            mBookmarkModel = new BookmarkModel();
            mBookmarkModel.runAfterBookmarkModelLoaded(new Runnable() {
                @Override
                public void run() {
                    loadBookmarks(folderId);
                }
            });
        }

        @UiThread
        private void loadBookmarks(BookmarkId folderId) {
            mFolder = new BookmarkFolder();

            // Load the requested folder if it exists. Otherwise, fall back to the default folder.
            if (folderId != null) {
                mFolder.folder = Bookmark.fromBookmarkItem(mBookmarkModel.getBookmarkById(
                        folderId));
            }
            if (mFolder.folder == null) {
                folderId = mBookmarkModel.getDefaultFolder();
                mFolder.folder = Bookmark.fromBookmarkItem(mBookmarkModel.getBookmarkById(
                        folderId));
            }

            mFolder.parent = Bookmark.fromBookmarkItem(mBookmarkModel.getBookmarkById(
                    mFolder.folder.parentId));

            List<BookmarkItem> items = mBookmarkModel.getBookmarksForFolder(folderId);

            // Move folders to the beginning of the list.
            Collections.sort(items, new Comparator<BookmarkItem>() {
                @Override
                public int compare(BookmarkItem lhs, BookmarkItem rhs) {
                    return lhs.isFolder() == rhs.isFolder() ? 0 : lhs.isFolder() ? -1 : 1;
                }
            });

            for (BookmarkItem item : items) {
                Bookmark bookmark = Bookmark.fromBookmarkItem(item);
                loadFavicon(bookmark);
                mFolder.children.add(bookmark);
            }

            taskFinished();
        }

        @UiThread
        private void loadFavicon(final Bookmark bookmark) {
            if (bookmark.isFolder) return;

            mRemainingTaskCount++;
            LargeIconCallback callback = new LargeIconCallback() {
                @Override
                public void onLargeIconAvailable(
                        Bitmap icon, int fallbackColor, boolean isFallbackColorDefault) {
                    if (icon == null) {
                        mIconGenerator.setBackgroundColor(fallbackColor);
                        icon = mIconGenerator.generateIconForUrl(bookmark.url);
                    } else {
                        icon = Bitmap.createScaledBitmap(icon, mDisplayedIconSize,
                                mDisplayedIconSize, true);
                    }
                    bookmark.favicon = icon;
                    taskFinished();
                }
            };
            mLargeIconBridge.getLargeIconForUrl(bookmark.url, mMinIconSizeDp, callback);
        }

        @UiThread
        private void taskFinished() {
            mRemainingTaskCount--;
            if (mRemainingTaskCount == 0) {
                mCallback.onBookmarksLoaded(mFolder);
                destroy();
            }
        }

        @UiThread
        private void destroy() {
            mBookmarkModel.destroy();
            mLargeIconBridge.destroy();
        }
    }

    /**
     * Provides the RemoteViews, one per bookmark, to be shown in the widget.
     */
    private static class BookmarkAdapter implements RemoteViewsService.RemoteViewsFactory {

        // Can be accessed on any thread
        private final Context mContext;
        private final int mWidgetId;
        private final SharedPreferences mPreferences;

        // Accessed only on the UI thread
        private BookmarkModel mBookmarkModel;

        // Accessed only on binder threads.
        private BookmarkFolder mCurrentFolder;

        @UiThread
        public BookmarkAdapter(Context context, int widgetId) {
            mContext = context;
            mWidgetId = widgetId;
            mPreferences = getWidgetState(mContext, mWidgetId);
        }

        @UiThread
        @SuppressFBWarnings("DM_EXIT")
        @Override
        public void onCreate() {
            // Required to be applied here redundantly to prevent crashes in the cases where the
            // package data is deleted or the Chrome application forced to stop.
            try {
                ChromeBrowserInitializer.getInstance(mContext).handleSynchronousStartup();
            } catch (ProcessInitException e) {
                Log.e(TAG, "Failed to start browser process.", e);
                // Since the library failed to initialize nothing in the application
                // can work, so kill the whole application not just the activity
                System.exit(-1);
            }
            if (isWidgetNewlyCreated()) {
                RecordUserAction.record("BookmarkNavigatorWidgetAdded");
            }

            // Partner bookmarks need to be loaded explicitly.
            PartnerBookmarksShim.kickOffReading(mContext);

            mBookmarkModel = new BookmarkModel();
            mBookmarkModel.addObserver(new BookmarkModelObserver() {
                @Override
                public void bookmarkModelLoaded() {
                    // Do nothing. No need to refresh.
                }

                @Override
                public void bookmarkModelChanged() {
                    refreshWidget();
                }
            });
        }

        @UiThread
        private boolean isWidgetNewlyCreated() {
            // This method relies on the fact that PREF_CURRENT_FOLDER is not yet
            // set when onCreate is called for a newly created widget.
            String serializedFolder = mPreferences.getString(PREF_CURRENT_FOLDER, null);
            return serializedFolder == null;
        }

        @UiThread
        private void refreshWidget() {
            mContext.sendBroadcast(new Intent(
                    BookmarkWidgetProvider.getBookmarkAppWidgetUpdateAction(mContext),
                    null, mContext, BookmarkThumbnailWidgetProvider.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId));
        }

        // ---------------------------------------------------------------- //
        // Methods below this line are called on binder threads.            //
        // ---------------------------------------------------------------- //
        // Different methods may be called on *different* binder threads,   //
        // but the system ensures that the effects of each method call will //
        // be visible before the next method is called. Thus, additional    //
        // synchronization is not needed when accessing mCurrentFolder.     //
        // ---------------------------------------------------------------- //

        @BinderThread
        @Override
        public void onDestroy() {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mBookmarkModel != null) mBookmarkModel.destroy();
                }
            });
            deleteWidgetState(mContext, mWidgetId);
        }

        @BinderThread
        @Override
        public void onDataSetChanged() {
            updateBookmarkList();
        }

        @BinderThread
        private void updateBookmarkList() {
            BookmarkId folderId = BookmarkId
                    .getBookmarkIdFromString(mPreferences.getString(PREF_CURRENT_FOLDER, null));
            mCurrentFolder = loadBookmarks(folderId);
            mPreferences.edit().putString(PREF_CURRENT_FOLDER, mCurrentFolder.folder.id.toString())
                    .apply();
        }

        @BinderThread
        private BookmarkFolder loadBookmarks(final BookmarkId folderId) {
            final LinkedBlockingQueue<BookmarkFolder> resultQueue = new LinkedBlockingQueue<>(1);
            //A reference of BookmarkLoader is needed in binder thread to
            //prevent it from being garbage collected.
            final BookmarkLoader bookmarkLoader = new BookmarkLoader();
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bookmarkLoader.initialize(mContext, folderId, new BookmarkLoaderCallback() {
                        @Override
                        public void onBookmarksLoaded(BookmarkFolder folder) {
                            resultQueue.add(folder);
                        }
                    });
                }
            });
            try {
                return resultQueue.take();
            } catch (InterruptedException e) {
                return null;
            }
        }

        @BinderThread
        private Bookmark getBookmarkForPosition(int position) {
            if (mCurrentFolder == null) return null;

            // The position 0 is saved for an entry of the current folder used to go up.
            // This is not the case when the current node has no parent (it's the root node).
            if (mCurrentFolder.parent != null) {
                if (position == 0) return mCurrentFolder.folder;
                position--;
            }
            return mCurrentFolder.children.get(position);
        }

        @BinderThread
        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @BinderThread
        @Override
        public boolean hasStableIds() {
            return false;
        }

        @BinderThread
        @Override
        public int getCount() {
            //On some Sony devices, getCount() could be called before onDatasetChanged()
            //returns. If it happens, refresh widget until the bookmarks are all loaded.
            if (mCurrentFolder == null || !mPreferences.getString(PREF_CURRENT_FOLDER, "")
                    .equals(mCurrentFolder.folder.id.toString())) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshWidget();
                    }
                });
            }
            if (mCurrentFolder == null) {
                return 0;
            }
            return mCurrentFolder.children.size() + (mCurrentFolder.parent != null ? 1 : 0);
        }

        @BinderThread
        @Override
        public long getItemId(int position) {
            return getBookmarkForPosition(position).id.getId();
        }

        @BinderThread
        @Override
        public RemoteViews getLoadingView() {
            return new RemoteViews(mContext.getPackageName(), R.layout.bookmark_widget_item);
        }

        @BinderThread
        @Override
        public RemoteViews getViewAt(int position) {
            if (mCurrentFolder == null) {
                Log.w(TAG, "No current folder data available.");
                return null;
            }

            Bookmark bookmark = getBookmarkForPosition(position);
            if (bookmark == null) {
                Log.w(TAG, "Couldn't get bookmark for position %d", position);
                return null;
            }

            String title = bookmark.title;
            String url = bookmark.url;
            BookmarkId id = (bookmark == mCurrentFolder.folder)
                    ? mCurrentFolder.parent.id
                    : bookmark.id;

            RemoteViews views = new RemoteViews(mContext.getPackageName(),
                    R.layout.bookmark_widget_item);

            // Set the title of the bookmark. Use the url as a backup.
            views.setTextViewText(R.id.title, TextUtils.isEmpty(title) ? url : title);

            if (bookmark == mCurrentFolder.folder) {
                views.setImageViewResource(R.id.favicon, R.drawable.back_normal);
            } else if (bookmark.isFolder) {
                views.setImageViewResource(R.id.favicon, R.drawable.bookmark_folder);
            } else {
                views.setImageViewBitmap(R.id.favicon, bookmark.favicon);
            }

            Intent fillIn;
            if (bookmark.isFolder) {
                fillIn = new Intent(getChangeFolderAction(mContext))
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
                        .putExtra(EXTRA_FOLDER_ID, id.toString());
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
