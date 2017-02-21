// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.content.Context;
import android.support.v7.widget.Toolbar.OnMenuItemClickListener;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View.OnClickListener;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkItem;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.chrome.browser.widget.selection.SelectionToolbar;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.content_public.browser.LoadUrlParams;

import java.util.List;

/**
 * Main action bar of bookmark UI. It is responsible for displaying title and buttons
 * associated with the current context.
 */
public class BookmarkActionBar extends SelectionToolbar<BookmarkId> implements BookmarkUIObserver,
        OnMenuItemClickListener, OnClickListener {
    private BookmarkItem mCurrentFolder;
    private BookmarkDelegate mDelegate;

    private BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {
        @Override
        public void bookmarkModelChanged() {
            onSelectionStateChange(mDelegate.getSelectionDelegate().getSelectedItems());
        }
    };

    public BookmarkActionBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setNavigationOnClickListener(this);
        inflateMenu(R.menu.bookmark_action_bar_menu);
        setOnMenuItemClickListener(this);

        getMenu().findItem(R.id.search_menu_id).setTitle(R.string.bookmark_action_bar_search);
        getMenu().findItem(R.id.selection_mode_edit_menu_id).setTitle(R.string.edit_bookmark);
        getMenu().findItem(R.id.selection_mode_move_menu_id)
                .setTitle(R.string.bookmark_action_bar_move);
        getMenu().findItem(R.id.selection_mode_delete_menu_id)
                .setTitle(R.string.bookmark_action_bar_delete);
    }

    @Override
    protected void onNavigationBack() {
        mDelegate.openFolder(mCurrentFolder.getParentId());
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        SelectionDelegate<BookmarkId> selectionDelegate = mDelegate.getSelectionDelegate();
        if (menuItem.getItemId() == R.id.edit_menu_id) {
            BookmarkAddEditFolderActivity.startEditFolderActivity(getContext(),
                    mCurrentFolder.getId());
            return true;
        } else if (menuItem.getItemId() == R.id.close_menu_id) {
            BookmarkUtils.finishActivityOnPhone(getContext());
            return true;
        } else if (menuItem.getItemId() == R.id.search_menu_id) {
            mDelegate.openSearchUI();
            return true;
        } else if (menuItem.getItemId() == R.id.selection_mode_edit_menu_id) {
            List<BookmarkId> list = selectionDelegate.getSelectedItems();
            assert list.size() == 1;
            BookmarkItem item = mDelegate.getModel().getBookmarkById(list.get(0));
            if (item.isFolder()) {
                BookmarkAddEditFolderActivity.startEditFolderActivity(getContext(), item.getId());
            } else {
                BookmarkUtils.startEditActivity(getContext(), item.getId());
            }
            return true;
        } else if (menuItem.getItemId() == R.id.selection_mode_move_menu_id) {
            List<BookmarkId> list = selectionDelegate.getSelectedItems();
            if (list.size() >= 1) {
                BookmarkFolderSelectActivity.startFolderSelectActivity(getContext(),
                        list.toArray(new BookmarkId[list.size()]));
            }
            return true;
        } else if (menuItem.getItemId() == R.id.selection_mode_delete_menu_id) {
            mDelegate.getModel().deleteBookmarks(
                    selectionDelegate.getSelectedItems().toArray(new BookmarkId[0]));
            return true;
        } else if (menuItem.getItemId() == R.id.selection_open_in_new_tab_id) {
            openBookmarksInNewTabs(selectionDelegate.getSelectedItems(), new TabDelegate(false),
                    mDelegate.getModel());
            selectionDelegate.clearSelection();
            return true;
        } else if (menuItem.getItemId() == R.id.selection_open_in_incognito_tab_id) {
            openBookmarksInNewTabs(selectionDelegate.getSelectedItems(), new TabDelegate(true),
                    mDelegate.getModel());
            selectionDelegate.clearSelection();
            return true;
        }

        assert false : "Unhandled menu click.";
        return false;
    }

    void showLoadingUi() {
        setTitle(null);
        setNavigationButton(NAVIGATION_BUTTON_NONE);
        getMenu().findItem(R.id.search_menu_id).setVisible(false);
        getMenu().findItem(R.id.edit_menu_id).setVisible(false);
    }

    // BookmarkUIObserver implementations.

    @Override
    public void onBookmarkDelegateInitialized(BookmarkDelegate delegate) {
        mDelegate = delegate;
        mDelegate.addUIObserver(this);
        if (!delegate.isDialogUi()) getMenu().removeItem(R.id.close_menu_id);
        delegate.getModel().addObserver(mBookmarkModelObserver);

        // This class will handle setting the title. Pass 0 to the superclass so that it doesn't
        // try to set the title when a selection is cleared.
        int titleResId = 0;
        initialize(delegate.getSelectionDelegate(), titleResId, delegate.getDrawerLayout(),
                R.id.normal_menu_group, R.id.selection_mode_menu_group);
    }

    @Override
    public void onDestroy() {
        mDelegate.removeUIObserver(this);
        mDelegate.getModel().removeObserver(mBookmarkModelObserver);
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        mCurrentFolder = mDelegate.getModel().getBookmarkById(folder);

        getMenu().findItem(R.id.search_menu_id).setVisible(true);
        getMenu().findItem(R.id.edit_menu_id).setVisible(mCurrentFolder.isEditable());

        // If the parent folder is a top level node, we don't go up anymore.
        if (mDelegate.getModel().getTopLevelFolderParentIDs().contains(
                mCurrentFolder.getParentId())) {
            if (TextUtils.isEmpty(mCurrentFolder.getTitle())) {
                setTitle(R.string.bookmarks);
            } else {
                setTitle(mCurrentFolder.getTitle());
            }
            setNavigationButton(NAVIGATION_BUTTON_MENU);
        } else {
            setTitle(mCurrentFolder.getTitle());
            setNavigationButton(NAVIGATION_BUTTON_BACK);
        }
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
        super.onSelectionStateChange(selectedBookmarks);
        if (mIsSelectionEnabled) {
            // Editing a bookmark action on multiple selected items doesn't make sense. So disable.
            getMenu().findItem(R.id.selection_mode_edit_menu_id).setVisible(
                    selectedBookmarks.size() == 1);
            getMenu().findItem(R.id.selection_open_in_incognito_tab_id)
                    .setVisible(PrefServiceBridge.getInstance().isIncognitoModeEnabled());
            // It does not make sense to open a folder in new tab.
            for (BookmarkId bookmark : selectedBookmarks) {
                BookmarkItem item = mDelegate.getModel().getBookmarkById(bookmark);
                if (item != null && item.isFolder()) {
                    getMenu().findItem(R.id.selection_open_in_new_tab_id).setVisible(false);
                    getMenu().findItem(R.id.selection_open_in_incognito_tab_id).setVisible(false);
                    break;
                }
            }
            // Partner bookmarks can't move, so if the selection includes a partner bookmark,
            // disable the move button.
            for (BookmarkId bookmark : selectedBookmarks) {
                if (bookmark.getType() == BookmarkType.PARTNER) {
                    getMenu().findItem(R.id.selection_mode_move_menu_id).setVisible(false);
                    break;
                }
            }
        } else {
            mDelegate.notifyStateChange(this);
        }
    }

    private static void openBookmarksInNewTabs(
            List<BookmarkId> bookmarks, TabDelegate tabDelegate, BookmarkModel model) {
        for (BookmarkId id : bookmarks) {
            tabDelegate.createNewTab(new LoadUrlParams(model.getBookmarkById(id).getUrl()),
                    TabLaunchType.FROM_LONGPRESS_BACKGROUND, null);
        }
    }
}
