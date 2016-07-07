// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BookmarksBridge;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkItem;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.BookmarksBridge.BookmarksCallback;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.bookmark.EditBookmarkHelper;
import org.chromium.chrome.browser.compositor.layouts.content.InvalidationAwareThumbnailProvider;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.ntp.BookmarksPageView.BookmarksPageManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.content_public.browser.LoadUrlParams;

import java.util.List;

/**
 * Provides functionality when the user interacts with the bookmarks page. This class supports
 * the regular bookmarks page as well as "select bookmark mode", in which context menus are
 * disabled and clicking a bookmark just notifies a listener instead of loading the bookmark URL.
 */
public class BookmarksPage implements NativePage, InvalidationAwareThumbnailProvider {

    private static final String LAST_USED_BOOKMARK_FOLDER_ID = "last_used_folder_id";

    private static final int PAGE_MODE_NORMAL = 0;
    private static final int PAGE_MODE_DOCUMENT = 1;

    private final Profile mProfile;
    private BookmarksBridge mBookmarksBridge;
    private FaviconHelper mFaviconHelper;

    private final BookmarksPageView mPageView;
    private final String mTitle;
    private final int mBackgroundColor;
    private final int mThemeColor;

    // Whether destroy() has been called.
    private boolean mIsDestroyed;

    private BookmarkId mCurrentFolderId;

    private final SharedPreferences mSharedPreferences;

    /**
     * Interface to be notified when the user clicks on a bookmark. To be used with
     * buildPageForShortcutActivity().
     */
    public interface BookmarkSelectedListener {
        /**
         * Called when a bookmark is selected.
         * @param url The url of the selected bookmark.
         * @param title The title of the selected bookmark.
         * @param favicon The favicon of the selected bookmark.
         */
        void onBookmarkSelected(String url, String title, Bitmap favicon);

        /**
         * Called when a new tab has been opened in a new tab.
         */
        void onNewTabOpened();
    }

    /**
     * Creates a BookmarksPage to be shown in a tab.
     * @param context The view context for showing the page.
     * @param tab The tab in which the page will be shown.
     * @param tabModelSelector The TabModelSelector to use when opening new tabs from the bookmarks
     *         page.
     * @return The new BookmarksPage object.
     */
    public static BookmarksPage buildPage(Context context, Tab tab,
            TabModelSelector tabModelSelector) {
        return new BookmarksPage(context, tab.getProfile(), tab, tabModelSelector, null,
                PAGE_MODE_NORMAL);
    }

    /**
     * Creates a BookmarksPage to be shown in document mode.
     * @param context The view context for showing the page.
     * @param tab The tab from which bookmarks page is loaded.
     * @param tabModelSelector The TabModelSelector to use when opening new tabs from the bookmarks
     *         page.
     * @param profile The profile from which to load bookmarks.
     * @param listener The BookmarkSelectedListener to notify when the user clicks a bookmark.
     * @return The new BookmarksPage object.
     */
    public static BookmarksPage buildPageInDocumentMode(Context context, Tab tab,
            TabModelSelector tabModelSelector, Profile profile, BookmarkSelectedListener listener) {
        return new BookmarksPage(
                context, profile, tab, tabModelSelector, listener, PAGE_MODE_DOCUMENT);
    }

    /**
     * Delegates user triggered actions for the bookmarks page.
     */
    private class BookmarksPageManagerImpl implements BookmarksPageManager {
        // This must remain in sync with Bookmarks.OpenAction in
        // tools/metrics/histograms/histograms.xml. Also, never remove or reorder histogram values.
        // It is safe to append new values to the end.
        private static final String ACTION_OPEN_BOOKMARK_HISTOGRAM = "Bookmarks.OpenAction";
        protected static final int ACTION_OPEN_BOOKMARK_CURRENT_TAB = 0;
        protected static final int ACTION_OPEN_BOOKMARK_NEW_TAB = 1;
        protected static final int ACTION_OPEN_BOOKMARK_NEW_INCOGNITO_TAB = 2;
        protected static final int ACTION_OPEN_BOOKMARK_BOUNDARY = 3;

        protected Tab mTab;
        protected TabModelSelector mTabModelSelector;

        public BookmarksPageManagerImpl(Tab tab, TabModelSelector tabModelSelector) {
            mTab = tab;
            mTabModelSelector = tabModelSelector;
        }

        protected void recordOpenedBookmark(int action) {
            if (!isIncognito()) {
                NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_BOOKMARK);
                RecordHistogram.recordEnumeratedHistogram(ACTION_OPEN_BOOKMARK_HISTOGRAM, action,
                        ACTION_OPEN_BOOKMARK_BOUNDARY);
            }
        }

        @Override
        public boolean isDestroyed() {
            return mIsDestroyed;
        }

        @Override
        public boolean isIncognito() {
            return mTab.isIncognito();
        }

        @Override
        public boolean shouldShowOpenInNewTab() {
            return !isIncognito();
        }

        @Override
        public boolean shouldShowOpenInNewIncognitoTab() {
            return PrefServiceBridge.getInstance().isIncognitoModeEnabled();
        }

        @Override
        public boolean isContextMenuEnabled() {
            return true;
        }

        @Override
        public void open(BookmarkItemView item) {
            if (item.isFolder()) {
                mTab.loadUrl(new LoadUrlParams(
                        UrlConstants.BOOKMARKS_FOLDER_URL + item.getBookmarkId().toString()));
            } else {
                recordOpenedBookmark(ACTION_OPEN_BOOKMARK_CURRENT_TAB);
                mTab.loadUrl(new LoadUrlParams(item.getUrl()));
            }
        }

        @Override
        public void openInNewTab(BookmarkItemView item) {
            assert !item.isFolder();
            recordOpenedBookmark(ACTION_OPEN_BOOKMARK_NEW_TAB);
            mTabModelSelector.openNewTab(new LoadUrlParams(item.getUrl()),
                    TabLaunchType.FROM_LONGPRESS_BACKGROUND, mTab,
                    mTabModelSelector.isIncognitoSelected());
        }

        @Override
        public void openInNewIncognitoTab(BookmarkItemView item) {
            assert !item.isFolder();
            recordOpenedBookmark(ACTION_OPEN_BOOKMARK_NEW_INCOGNITO_TAB);
            mTabModelSelector.openNewTab(new LoadUrlParams(item.getUrl()),
                    TabLaunchType.FROM_LONGPRESS_FOREGROUND, mTab, true);
        }

        @Override
        public void openFolder(BookmarkFolderHierarchyItem item) {
            mTab.loadUrl(new LoadUrlParams(
                    UrlConstants.BOOKMARKS_FOLDER_URL + item.getFolderId()));
        }

        @Override
        public void delete(BookmarkItemView item) {
            if (mBookmarksBridge == null) return;
            mBookmarksBridge.deleteBookmark(item.getBookmarkId());
        }

        @Override
        public void edit(BookmarkItemView item) {
            BookmarksPage.this.edit(item);
        }

        @Override
        public void getFaviconImageForUrl(
                String url, int size, FaviconImageCallback faviconCallback) {
            BookmarksPage.this.getFaviconImageForUrl(url, size, faviconCallback);
        }
    }

    private class DocumentModeManager extends BookmarksPageManagerImpl {
        private final BookmarkSelectedListener mListener;

        public DocumentModeManager(Tab tab, TabModelSelector tabModelSelector,
                BookmarkSelectedListener listener) {
            super(tab, tabModelSelector);
            mListener = listener;
        }

        @Override
        public void openInNewTab(BookmarkItemView item) {
            super.openInNewTab(item);
            mListener.onNewTabOpened();
        }

        @Override
        public void openInNewIncognitoTab(BookmarkItemView item) {
            super.openInNewIncognitoTab(item);
            mListener.onNewTabOpened();
        }

        @Override
        public void open(BookmarkItemView item) {
            if (item.isFolder()) {
                updateBookmarksPageContents(item.getBookmarkId(), false);
            } else {
                recordOpenedBookmark(ACTION_OPEN_BOOKMARK_CURRENT_TAB);
                mListener.onBookmarkSelected(item.getUrl(), item.getTitle(), item.getFavicon());
            }
        }

        @Override
        public void openFolder(BookmarkFolderHierarchyItem item) {
            updateBookmarksPageContents(item.getFolderId(), false);
        }
    }

    private BookmarksPage(Context context, Profile profile, Tab tab,
            TabModelSelector tabModelSelector, BookmarkSelectedListener listener,
            int pageMode) {
        mProfile = profile;
        mFaviconHelper = new FaviconHelper();
        mTitle = context.getResources().getString(R.string.ntp_bookmarks);
        mBackgroundColor = ApiCompatibilityUtils.getColor(context.getResources(), R.color.ntp_bg);
        mThemeColor = ApiCompatibilityUtils.getColor(
                context.getResources(), R.color.default_primary_color);
        mCurrentFolderId = new BookmarkId(BookmarkId.INVALID_FOLDER_ID, BookmarkType.NORMAL);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        LayoutInflater inflater = LayoutInflater.from(context);
        mPageView = (BookmarksPageView) inflater.inflate(R.layout.bookmarks_page, null);

        if (pageMode == PAGE_MODE_NORMAL || pageMode == PAGE_MODE_DOCUMENT) {
            // mPageView (which has a transparent background) needs top padding of size
            // R.dimen.tab_strip_height so that the bookmarks page isn't drawn over the tab strip.
            // mPageView's first child (which has a white background) needs top padding of size
            // R.dimen.toolbar_height_no_shadow so that the bookmarks page contents start below
            // the URL bar.
            // TODO(newt): combine these padding values and apply them just to mPageView, once the
            // classic NTP is gone. Until then, we need to draw white behind the URL bar so the
            // animation shown when pressing the "+" button in the tab switcher to open a new tab
            // on the bookmarks page looks good.
            Resources res = context.getResources();
            int tabStripHeight = res.getDimensionPixelOffset(R.dimen.tab_strip_height);
            int toolbarHeightNoShadow = res.getDimensionPixelOffset(
                    R.dimen.toolbar_height_no_shadow);
            mPageView.setPadding(mPageView.getPaddingLeft(), tabStripHeight,
                    mPageView.getPaddingRight(), mPageView.getPaddingBottom());
            View v = ((ViewGroup) mPageView).getChildAt(0);
            v.setPadding(v.getPaddingLeft(), toolbarHeightNoShadow, v.getPaddingRight(),
                    v.getPaddingBottom());
        }

        BookmarksPageManager manager = null;
        switch (pageMode) {
            case PAGE_MODE_NORMAL:
                manager = buildManager(tab, tabModelSelector);
                break;
            case PAGE_MODE_DOCUMENT:
                manager = buildManagerForDocumentMode(tab, tabModelSelector, listener);
                break;
            default:
                assert false;
                break;
        }

        mPageView.initialize(manager);

        mBookmarksBridge = new BookmarksBridge(mProfile);
        mBookmarksBridge.addObserver(new BookmarkModelObserver() {
            private void updateIfNodeIsCurrentFolder(BookmarkId nodeId) {
                updateIfEitherNodeIsCurrentFolder(nodeId, nodeId);
            }

            private void updateIfEitherNodeIsCurrentFolder(BookmarkId firstNodeId,
                    BookmarkId secondNodeId) {
                if ((mCurrentFolderId.equals(firstNodeId)
                        || mCurrentFolderId.equals(secondNodeId))) {
                    updateBookmarksPageContents(mCurrentFolderId, true);
                }
            }

            @Override
            public void bookmarkNodeMoved(BookmarkItem oldParent,
                    int oldIndex, BookmarkItem newParent, int newIndex) {
                updateIfEitherNodeIsCurrentFolder(oldParent.getId(), newParent.getId());
            }

            @Override
            public void bookmarkNodeAdded(BookmarkItem parent, int index) {
                updateIfNodeIsCurrentFolder(parent.getId());
            }

            @Override
            public void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node,
                    boolean isExtensiveBookmarkChangesHappening) {
                // Since the node is already removed, it does not have a parent. Hence, we need
                // to pass the old parent information to check if it is the current folder.
                // node.getParentId() is INVALID_FOLDER_ID.
                updateIfEitherNodeIsCurrentFolder(node.getId(), parent.getId());
            }

            @Override
            public void bookmarkNodeChanged(BookmarkItem node) {
                updateIfEitherNodeIsCurrentFolder(node.getId(), node.getParentId());
            }

            @Override
            public void bookmarkNodeChildrenReordered(BookmarkItem node) {
                updateIfNodeIsCurrentFolder(node.getId());
            }

            @Override
            public void bookmarkModelLoaded() {
                // Purposefully don't do anything because we will be getting callbacks.
            }

            @Override
            public void bookmarkModelChanged() {
                updateBookmarksPageContents(mCurrentFolderId, true);
            }
        });
    }

    private BookmarksPageManager buildManager(Tab tab, TabModelSelector tabModelSelector) {
        return new BookmarksPageManagerImpl(tab, tabModelSelector);
    }

    private BookmarksPageManager buildManagerForDocumentMode(
            Tab tab, TabModelSelector tabModelSelector, BookmarkSelectedListener listener) {
        return new DocumentModeManager(tab, tabModelSelector, listener);
    }

    private void getFaviconImageForUrl(String url, int size, FaviconImageCallback faviconCallback) {
        if (mFaviconHelper == null) return;
        mFaviconHelper.getLocalFaviconImageForURL(mProfile, url, size, faviconCallback);
    }

    private void edit(BookmarkItemView item) {
        Context context = mPageView.getContext();
        if (item.getBookmarkId().getType() == BookmarkType.PARTNER) {
            EditBookmarkHelper.editPartnerBookmark(context, mProfile, item.getBookmarkId().getId(),
                    item.getTitle(), item.isFolder());
        } else {
            EditBookmarkHelper.editBookmark(context, item.getBookmarkId().getId(), item.isFolder());
        }
    }

    /**
     * Updates the contents of the bookmark page if the current folder has changed.
     * @param folderId The ID of the folder to load.
     * @param force Whether to force the update even if the folder has not changed.
     */
    private void updateBookmarksPageContents(BookmarkId folderId, boolean force) {
        if (mBookmarksBridge == null) return;
        if (!force && mCurrentFolderId.equals(folderId)) return;
        BookmarksCallback callbackWrapper = new BookmarksCallback() {
            @Override
            public void onBookmarksFolderHierarchyAvailable(BookmarkId folderId,
                    List<BookmarkItem> bookmarksList) {
                mPageView.onBookmarksFolderHierarchyAvailable(folderId, bookmarksList);
            }

            @Override
            public void onBookmarksAvailable(BookmarkId folderId,
                    List<BookmarkItem> bookmarksList) {
                // Update the folder ID based on the response, as sometimes it will be changed
                // from the passed in value (if the ID passed in was invalid).
                mCurrentFolderId = folderId;
                updateLastUsedFolderId();
                mPageView.onBookmarksAvailable(folderId, bookmarksList);
            }
        };
        mBookmarksBridge.getBookmarksForFolder(folderId, callbackWrapper);
        mBookmarksBridge.getCurrentFolderHierarchy(folderId, callbackWrapper);
    }

    private void updateLastUsedFolderId() {
        mSharedPreferences.edit().putString(
                LAST_USED_BOOKMARK_FOLDER_ID, mCurrentFolderId.toString()).apply();
    }

    /**
     * @return The last used bookmark folder id with fallback to the root folder id.
     */
    private BookmarkId getLastUsedFolderId() {
        // If null shared prefs returns null, getBookmarkIdFromString() returns root folder id.
        return BookmarkId.getBookmarkIdFromString(mSharedPreferences.getString(
                LAST_USED_BOOKMARK_FOLDER_ID, null));
    }

    // NativePage overrides

    @Override
    public String getUrl() {
        if (mCurrentFolderId.getId() < 0) {
            return UrlConstants.BOOKMARKS_URL;
        } else {
            return UrlConstants.BOOKMARKS_FOLDER_URL + mCurrentFolderId.toString();
        }
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public int getThemeColor() {
        return mThemeColor;
    }

    @Override
    public View getView() {
        return mPageView;
    }

    /**
     * Releases internal resources. This must be called eventually, and the object should not used
     * after calling this.
     */
    @Override
    public void destroy() {
        assert !mIsDestroyed;
        if (mFaviconHelper != null) {
            mFaviconHelper.destroy();
            mFaviconHelper = null;
        }
        if (mBookmarksBridge != null) {
            mBookmarksBridge.destroy();
            mBookmarksBridge = null;
        }
        mIsDestroyed = true;
    }

    @Override
    public String getHost() {
        return UrlConstants.BOOKMARKS_HOST;
    }

    @Override
    public void updateForUrl(String url) {
        BookmarkId folderId = null;
        if (url != null && url.startsWith(UrlConstants.BOOKMARKS_FOLDER_URL)) {
            String fragment = url.substring(UrlConstants.BOOKMARKS_FOLDER_URL.length());
            if (!fragment.isEmpty()) {
                folderId = BookmarkId.getBookmarkIdFromString(fragment);
            }
        }
        if (folderId == null) folderId = getLastUsedFolderId();
        updateBookmarksPageContents(folderId, false);
    }

    // InvalidationAwareThumbnailProvider

    @Override
    public boolean shouldCaptureThumbnail() {
        return mPageView.shouldCaptureThumbnail();
    }

    @Override
    public void captureThumbnail(Canvas canvas) {
        ViewUtils.captureBitmap(mPageView, canvas);
        mPageView.updateThumbnailState();
    }
}
