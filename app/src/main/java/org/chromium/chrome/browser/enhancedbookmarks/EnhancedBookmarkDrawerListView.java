// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.chromium.chrome.browser.BookmarksBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.enhancedbookmarks.EnhancedBookmarkManager.UIState;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.List;

/**
 * Main drawer list view of Enhanced Bookmark UI. It is responsible for presenting different viewing
 * modes and let users to choose.
 */
@SuppressLint("Instantiatable")
class EnhancedBookmarkDrawerListView extends ListView implements EnhancedBookmarkUIObserver {
    private EnhancedBookmarkDelegate mDelegate;

    private BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {
        @Override
        public void bookmarkModelChanged() {
            mDelegate.notifyStateChange(EnhancedBookmarkDrawerListView.this);
        }
    };

    private final EnhancedBookmarkDrawerListViewAdapter mAdapter =
            new EnhancedBookmarkDrawerListViewAdapter();

    public EnhancedBookmarkDrawerListView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setAdapter(mAdapter);
        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mDelegate.closeDrawer();

                EnhancedBookmarkDrawerListViewAdapter.Item item =
                        (EnhancedBookmarkDrawerListViewAdapter.Item) mAdapter.getItem(position);

                switch (item.mType) {
                    case EnhancedBookmarkDrawerListViewAdapter.TYPE_FOLDER:
                        mDelegate.openFolder(item.mFolderId);
                        break;
                    case EnhancedBookmarkDrawerListViewAdapter.TYPE_ALL_ITEMS:
                        mDelegate.openAllBookmarks();
                        break;
                    case EnhancedBookmarkDrawerListViewAdapter.TYPE_FILTER:
                        mDelegate.openFilter(item.mFilter);
                        break;
                    default:
                        assert false;
                }
            }
        });
    }

    void showLoadingUi() {
        mAdapter.clear();
        mAdapter.notifyDataSetChanged();
        clearChoices();
    }

    // EnhancedBookmarkUIObserver implementations.

    @Override
    public void onEnhancedBookmarkDelegateInitialized(EnhancedBookmarkDelegate delegate) {
        mDelegate = delegate;
        delegate.getModel().addObserver(mBookmarkModelObserver);
        mAdapter.setEnhancedBookmarkUIDelegate(delegate);
        delegate.addUIObserver(this);
    }

    @Override
    public void onDestroy() {
        mDelegate.getModel().removeObserver(mBookmarkModelObserver);
        mDelegate.removeUIObserver(this);
    }

    @Override
    public void onAllBookmarksStateSet() {
        mAdapter.updateList();
        setItemChecked(mAdapter.getItemPosition(UIState.STATE_ALL_BOOKMARKS, null),
                true);
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        mAdapter.updateList();
        setItemChecked(mAdapter.getItemPosition(UIState.STATE_FOLDER, folder),
                true);
    }

    @Override
    public void onFilterStateSet(EnhancedBookmarkFilter filter) {
        mAdapter.updateList();
        setItemChecked(mAdapter.getItemPosition(UIState.STATE_FILTER, filter), true);
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
    }
}
