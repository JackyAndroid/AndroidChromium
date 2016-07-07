// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkItem;
import org.chromium.chrome.browser.BookmarksBridge.BookmarksCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.ui.base.LocalizationUtils;

import java.util.Collections;
import java.util.List;

/**
 * The native bookmarks page, represented by some basic data such as folder contents,
 * folder hierarchy, and an Android View that displays the page.
 */
public class BookmarksPageView extends LinearLayout implements BookmarksCallback {

    private static final int MAX_NUM_FAVICONS_TO_CACHE = 256;

    private BookmarksPageManager mManager;
    private HorizontalScrollView mHierarchyContainer;
    private LinearLayout mHierarchyLayout;
    private Bitmap mDefaultFavicon;
    private BookmarkItemView.DrawingData mDrawingData;
    private final int mDesiredFaviconSize;
    private final LruCache<String, Bitmap> mFaviconCache;

    private final BookmarkListAdapter mAdapter;
    private BookmarksListView mBookmarksList;
    private TextView mEmptyView;
    private int mSavedListPosition = 0;
    private int mSavedListTop = 0;

    private boolean mSnapshotBookmarksChanged;
    private int mSnapshotWidth;
    private int mSnapshotHeight;
    private int mSnapshotBookmarksListPosition;
    private int mSnapshotBookmarksListTop;
    private int mSnapshotBookmarksHierarchyScrollX;

    /**
     * Manages the view interaction with the rest of the system.
     */
    public interface BookmarksPageManager {
        /**
         * @return True, if destroy() has been called.
         */
        boolean isDestroyed();

        /**
         * @return Whether this bookmarks page should use incognito mode.
         */
        boolean isIncognito();

        /**
         * @return Whether "Open in new tab" should be shown in the context menu
         *         when long pressing a bookmark.
         */
        boolean shouldShowOpenInNewTab();

        /**
         * @return Whether "Open in new incognito tab" should be shown in the context menu
         *         when long pressing a bookmark.
         */
        boolean shouldShowOpenInNewIncognitoTab();

        /**
         * @return Whether to show a context menu on long press.
         */
        boolean isContextMenuEnabled();

        /**
         * Opens the bookmark item. If item is a bookmark then it opens the page else,
         * if folder, it displays the folder contents.
         * @param item BookmarkItem to be opened.
         */
        void open(BookmarkItemView item);

        /**
         * Opens a bookmark item in a new tab.
         * @param item The bookmark item to open.
         */
        void openInNewTab(BookmarkItemView item);

        /**
         * Opens a bookmark item in a new incognito tab.
         * @param item The bookmark item to open.
         */
        void openInNewIncognitoTab(BookmarkItemView item);

        /**
         * Opens the bookmark folder to display the folder contents.
         * @param item BookmarkFolderHierarchyItem to be opened.
         */
        void openFolder(BookmarkFolderHierarchyItem item);

        /**
         * Deletes a bookmark entry.
         * @param item The bookmark item to remove.
         */
        void delete(BookmarkItemView item);

        /**
         * Edits a bookmark entry.
         * @param item The bookmark item to be edited.
         */
        void edit(BookmarkItemView item);

        /**
         * Gets the favicon image for a given URL.
         * @param url The URL of the site whose favicon is being requested.
         * @param size The desired size of the favicon in pixels.
         * @param faviconCallback The callback to be notified when the favicon is available.
         */
        void getFaviconImageForUrl(String url, int size, FaviconImageCallback faviconCallback);
    }

    /**
     * Constructor for inflating from XML.
     */
    public BookmarksPageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDesiredFaviconSize = getResources().getDimensionPixelSize(
                R.dimen.default_favicon_size);
        mFaviconCache = new LruCache<String, Bitmap>(MAX_NUM_FAVICONS_TO_CACHE);
        mAdapter = new BookmarkListAdapter();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHierarchyContainer =
                (HorizontalScrollView) findViewById(R.id.folder_structure_scroll_view);
        mHierarchyContainer.setSmoothScrollingEnabled(true);
        mHierarchyLayout = (LinearLayout) findViewById(R.id.bookmark_folder_structure);

        mBookmarksList = (BookmarksListView) findViewById(R.id.bookmarks_list_view);
        mBookmarksList.setAdapter(mAdapter);

        mEmptyView = (TextView) findViewById(R.id.bookmarks_empty_view);
        mBookmarksList.setEmptyView(mEmptyView);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // Fixes lanscape transitions when unfocusing the URL bar: crbug.com/288546
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return super.onCreateInputConnection(outAttrs);
    }

    /**
     * Handles initializing the view.
     * @param manager The manager handling the external dependencies of this view.
     */
    void initialize(BookmarksPageManager manager) {
        mManager = manager;
    }

    @Override
    protected void onDetachedFromWindow() {
        mSavedListPosition = mBookmarksList.getFirstVisiblePosition();
        View v = mBookmarksList.getChildAt(0);
        mSavedListTop = (v == null) ? 0 : v.getTop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mBookmarksList.setSelectionFromTop(mSavedListPosition, mSavedListTop);
    }

    /**
     * @see org.chromium.chrome.browser.compositor.layouts.content
     *         .InvalidationAwareThumbnailProvider#shouldCaptureThumbnail()
     */
    boolean shouldCaptureThumbnail() {
        if (getWidth() == 0 || getHeight() == 0) return false;

        View topItem = mBookmarksList.getChildAt(0);
        return mSnapshotBookmarksChanged
                || getWidth() != mSnapshotWidth
                || getHeight() != mSnapshotHeight
                || mSnapshotBookmarksListPosition != mBookmarksList.getFirstVisiblePosition()
                || mSnapshotBookmarksListTop != (topItem == null ? 0 : topItem.getTop())
                || mHierarchyContainer.getScrollX() != mSnapshotBookmarksHierarchyScrollX;
    }

    /**
     * Triggered after a thumbnail has been captured to update the thumbnail visual state used to
     * determine dirtiness.
     */
    void updateThumbnailState() {
        mSnapshotWidth = getWidth();
        mSnapshotHeight = getHeight();
        mSnapshotBookmarksListPosition = mBookmarksList.getFirstVisiblePosition();
        View topItem = mBookmarksList.getChildAt(0);
        mSnapshotBookmarksListTop = topItem == null ? 0 : topItem.getTop();
        mSnapshotBookmarksHierarchyScrollX = mHierarchyContainer.getScrollX();
        mSnapshotBookmarksChanged = false;
    }

    // BookmarksCallback overrides

    @Override
    public void onBookmarksAvailable(BookmarkId folderId, List<BookmarkItem> bookmarksList) {
        if (mEmptyView.length() == 0) {
            // Set the empty view's text now that the first bookmarks callback has happened. If we
            // set the text earlier, the user will see the "No bookmarks here" message while we're
            // waiting for the callback.
            mEmptyView.setText(R.string.bookmarks_folder_empty);
        }

        mAdapter.setBookmarksList(bookmarksList);
        mAdapter.notifyDataSetChanged();

        // In theory, the adapter should trigger a re-layout when the bookmarks list changes. In
        // practice, if the bookmarks page is in the background and hence not attached to the view
        // hierarchy, this doesn't happen. So, trigger the re-layout explicitly.
        mBookmarksList.requestLayout();
        mBookmarksList.invalidate();

        // Cause the scroll bar to appear then fade out again if this folder is scrollable.
        mBookmarksList.awakenScrollBars();

        mSnapshotBookmarksChanged = true;
    }

    @Override
    public void onBookmarksFolderHierarchyAvailable(BookmarkId folderId,
            List<BookmarkItem> bookmarksList) {
        if (mManager.isDestroyed()) return;
        mHierarchyLayout.removeAllViews();
        for (int i = bookmarksList.size() - 1; i >= 0; i--) {
            BookmarkItem bookmark = bookmarksList.get(i);
            addItemToHierarchyView(bookmark.getTitle(), bookmark.getId(), (i == 0) ? true : false);
        }
        mHierarchyLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                mHierarchyLayout.removeOnLayoutChangeListener(this);
                mHierarchyContainer.fullScroll(LocalizationUtils.isLayoutRtl()
                        ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
            }
        });

        mSnapshotBookmarksChanged = true;
    }

    /**
     * Add a BookmarkFolderHierarchyItem item to the hierarchy layout.
     * @param title Title of the folder
     * @param id Id of the folder
     * @param isCurrentFolder Whether the folder is the current folder.
     */
    private void addItemToHierarchyView(String title, final BookmarkId id,
            boolean isCurrentFolder) {
        if (TextUtils.isEmpty(title)) {
            // TODO(cramya): Need to check why we cannot get "Bookmarks" folder information.
            title = getResources().getString(R.string.ntp_bookmarks);
        } else {
            ImageView separator = new ImageView(getContext());
            separator.setImageResource(R.drawable.breadcrumb_arrow);
            mHierarchyLayout.addView(separator);
        }
        final BookmarkFolderHierarchyItem item = new BookmarkFolderHierarchyItem(
                getContext(), mManager, id, title, isCurrentFolder);
        mHierarchyLayout.addView(item);
    }

    /**
     * List Adapter for Bookmarks List View.
     */
    private class BookmarkListAdapter extends BaseAdapter {

        public List<BookmarkItem> mBookmarks = Collections.emptyList();

        /**
         * Sets the bookmarks list for adapter.
         * @param bookmarks BookmarkItem list.
         */
        public void setBookmarksList(List<BookmarkItem> bookmarks) {
            mBookmarks = bookmarks;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (mDrawingData == null) mDrawingData = new BookmarkItemView.DrawingData(getContext());
            final BookmarkItem bookmark = getItem(position);
            final BookmarkItemView item;
            if (convertView instanceof BookmarkItemView) {
                item = (BookmarkItemView) convertView;
                if (!item.reset(bookmark.getId(), bookmark.getTitle(), bookmark.getUrl(),
                        bookmark.isEditable(), bookmark.isManaged())) {
                    return item;
                }
            } else {
                item = new BookmarkItemView(getContext(), mManager,
                        bookmark.getId(), bookmark.getTitle(), bookmark.getUrl(),
                        bookmark.isEditable(), bookmark.isManaged(), mDrawingData);
            }
            if (!bookmark.isFolder() && !TextUtils.isEmpty(bookmark.getUrl())) {
                Bitmap favicon = mFaviconCache.get(bookmark.getUrl());
                if (favicon != null) {
                    item.setFavicon(favicon);
                } else if (!mManager.isDestroyed()) {
                    FaviconImageCallback faviconCallback = new FaviconImageCallback() {
                        @Override
                        public void onFaviconAvailable(Bitmap image, String iconUrl) {
                            if (image == null) {
                                if (mDefaultFavicon == null) {
                                    mDefaultFavicon = BitmapFactory.decodeResource(
                                            getResources(), R.drawable.default_favicon);
                                }
                                image = mDefaultFavicon;
                            }
                            mFaviconCache.put(bookmark.getUrl(), image);
                            // It's possible the BookmarkItemView has been recycled and is now
                            // displaying a different bookmark. Don't update the favicon in this
                            // case.
                            if (bookmark.getUrl().equals(item.getUrl())) {
                                item.setFavicon(image);
                                mSnapshotBookmarksChanged = true;
                            }
                        }
                    };
                    mManager.getFaviconImageForUrl(bookmark.getUrl(), mDesiredFaviconSize,
                            faviconCallback);
                }
            }
            return item;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getCount() {
            return mBookmarks.size();
        }

        @Override
        public BookmarkItem getItem(int position) {
            return mBookmarks.get(position);
        }

        @Override
        public boolean isEmpty() {
            return mBookmarks.isEmpty();
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
