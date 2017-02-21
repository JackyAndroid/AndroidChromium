// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkModelObserver;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.List;

/**
 * Main drawer list view of bookmark UI. It is responsible for presenting different viewing
 * modes and let users to choose.
 */
@SuppressLint("Instantiatable")
class BookmarkDrawerListView extends ListView implements BookmarkUIObserver {
    private BookmarkDelegate mDelegate;

    private BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {
        @Override
        public void bookmarkModelChanged() {
            mDelegate.notifyStateChange(BookmarkDrawerListView.this);
        }
    };

    private final BookmarkDrawerListViewAdapter mAdapter =
            new BookmarkDrawerListViewAdapter();

    public BookmarkDrawerListView(final Context context, AttributeSet attrs) {
        super(context, attrs);

        setAdapter(mAdapter);
        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mDelegate.closeDrawer();

                BookmarkDrawerListViewAdapter.Item item =
                        (BookmarkDrawerListViewAdapter.Item) mAdapter.getItem(position);

                switch (item.mType) {
                    case BookmarkDrawerListViewAdapter.TYPE_FOLDER:
                        mDelegate.openFolder(item.mFolderId);
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

    // BookmarkUIObserver implementations.

    @Override
    public void onBookmarkDelegateInitialized(BookmarkDelegate delegate) {
        mDelegate = delegate;
        delegate.getModel().addObserver(mBookmarkModelObserver);
        mAdapter.setBookmarkUIDelegate(delegate);
        delegate.addUIObserver(this);
    }

    @Override
    public void onDestroy() {
        mDelegate.getModel().removeObserver(mBookmarkModelObserver);
        mDelegate.removeUIObserver(this);
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        mAdapter.updateList();
        setItemChecked(mAdapter.getItemPosition(BookmarkUIState.STATE_FOLDER, folder),
                true);
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
    }
}
