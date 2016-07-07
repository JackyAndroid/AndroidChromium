// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.app.Activity;
import android.content.Context;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.Toolbar.OnMenuItemClickListener;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkItem;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.widget.NumberRollView;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.List;

/**
 * Main action bar of Enhanced Bookmark UI. It is responsible for displaying title and buttons
 * associated with the current context.
 */
public class EnhancedBookmarkActionBar extends Toolbar implements EnhancedBookmarkUIObserver,
        OnMenuItemClickListener, OnClickListener {
    private static final int NAVIGATION_BUTTON_NONE = 0;
    private static final int NAVIGATION_BUTTON_MENU = 1;
    private static final int NAVIGATION_BUTTON_BACK = 2;
    private static final int NAVIGATION_BUTTON_SELECTION_BACK = 3;

    private int mNavigationButton;
    private BookmarkItem mCurrentFolder;
    private EnhancedBookmarkDelegate mDelegate;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private boolean mIsSelectionEnabled;

    private BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {
        @Override
        public void bookmarkModelChanged() {
            onSelectionStateChange(mDelegate.getSelectedBookmarks());
        }
    };

    public EnhancedBookmarkActionBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setNavigationOnClickListener(this);
        inflateMenu(R.menu.eb_action_bar_menu);
        if (DeviceFormFactor.isTablet(context)) getMenu().removeItem(R.id.close_menu_id);
        setOnMenuItemClickListener(this);

        if (OfflinePageBridge.isEnabled()) {
            getMenu().findItem(R.id.search_menu_id).setTitle(
                    R.string.offline_pages_action_bar_search);
            getMenu().findItem(R.id.selection_mode_edit_menu_id).setTitle(
                    R.string.offline_pages_edit_item);
            getMenu().findItem(R.id.selection_mode_move_menu_id).setTitle(
                    R.string.offline_pages_action_bar_move);
            getMenu().findItem(R.id.selection_mode_delete_menu_id).setTitle(
                    R.string.offline_pages_action_bar_delete);
        }
    }

    @Override
    public void onClick(View view) {
        switch (mNavigationButton) {
            case NAVIGATION_BUTTON_NONE:
                break;
            case NAVIGATION_BUTTON_MENU:
                // ActionBarDrawerToggle handles this.
                break;
            case NAVIGATION_BUTTON_BACK:
                mDelegate.openFolder(mCurrentFolder.getParentId());
                break;
            case NAVIGATION_BUTTON_SELECTION_BACK:
                mDelegate.clearSelection();
                break;
            default:
                assert false : "Incorrect navigation button state";
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.edit_menu_id) {
            EnhancedBookmarkAddEditFolderActivity.startEditFolderActivity(getContext(),
                    mCurrentFolder.getId());
        } else if (menuItem.getItemId() == R.id.close_menu_id) {
            EnhancedBookmarkUtils.finishActivityOnPhone(getContext());
            return true;
        } else if (menuItem.getItemId() == R.id.search_menu_id) {
            mDelegate.openSearchUI();
            return true;
        } else if (menuItem.getItemId() == R.id.selection_mode_edit_menu_id) {
            List<BookmarkId> list = mDelegate.getSelectedBookmarks();
            assert list.size() == 1;
            BookmarkItem item = mDelegate.getModel().getBookmarkById(list.get(0));
            if (item.isFolder()) {
                EnhancedBookmarkAddEditFolderActivity.startEditFolderActivity(getContext(),
                        item.getId());
            } else {
                EnhancedBookmarkUtils.startEditActivity(getContext(), item.getId(), null);
            }
            return true;
        } else if (menuItem.getItemId() == R.id.selection_mode_move_menu_id) {
            List<BookmarkId> list = mDelegate.getSelectedBookmarks();
            if (list.size() >= 1) {
                EnhancedBookmarkFolderSelectActivity.startFolderSelectActivity(getContext(),
                        list.toArray(new BookmarkId[list.size()]));
            }
            return true;
        } else if (menuItem.getItemId() == R.id.selection_mode_delete_menu_id) {
            mDelegate.getModel().deleteBookmarks(
                    mDelegate.getSelectedBookmarks().toArray(new BookmarkId[0]));
            return true;
        }

        assert false : "Unhandled menu click.";
        return false;
    }

    /**
     * Update the current navigation button (the top-left icon on LTR)
     * @param navigationButton one of NAVIGATION_BUTTON_* constants.
     */
    private void setNavigationButton(int navigationButton) {
        int iconResId = 0;
        int contentDescriptionId = 0;

        if (navigationButton == NAVIGATION_BUTTON_MENU && !mDelegate.doesDrawerExist()) {
            mNavigationButton = NAVIGATION_BUTTON_NONE;
        } else {
            mNavigationButton = navigationButton;
        }

        if (mNavigationButton == NAVIGATION_BUTTON_MENU) {
            initActionBarDrawerToggle();
            // ActionBarDrawerToggle will take care of icon and content description, so just return.
            return;
        }

        if (mActionBarDrawerToggle != null) {
            mActionBarDrawerToggle.setDrawerIndicatorEnabled(false);
            mDelegate.getDrawerLayout().setDrawerListener(null);
        }

        setNavigationOnClickListener(this);

        switch (mNavigationButton) {
            case NAVIGATION_BUTTON_NONE:
                break;
            case NAVIGATION_BUTTON_BACK:
                iconResId = R.drawable.eb_back_normal;
                contentDescriptionId = R.string.accessibility_toolbar_btn_back;
                break;
            case NAVIGATION_BUTTON_SELECTION_BACK:
                iconResId = R.drawable.eb_cancel_active;
                contentDescriptionId = R.string.accessibility_enhanced_bookmark_cancel_selection;
                break;
            default:
                assert false : "Incorrect navigationButton argument";
        }

        if (iconResId == 0) {
            setNavigationIcon(null);
        } else {
            setNavigationIcon(iconResId);
        }
        setNavigationContentDescription(contentDescriptionId);
    }

    /**
     * Set up ActionBarDrawerToggle, a.k.a. hamburger button.
     */
    private void initActionBarDrawerToggle() {
        // Sadly, the only way to set correct toolbar button listener for ActionBarDrawerToggle
        // is constructing, so we will need to construct every time we re-show this button.
        mActionBarDrawerToggle = new ActionBarDrawerToggle((Activity) getContext(),
                mDelegate.getDrawerLayout(), this,
                R.string.accessibility_enhanced_bookmark_drawer_toggle_btn_open,
                R.string.accessibility_enhanced_bookmark_drawer_toggle_btn_close);
        mDelegate.getDrawerLayout().setDrawerListener(mActionBarDrawerToggle);
        mActionBarDrawerToggle.syncState();
    }

    void showLoadingUi() {
        setTitle(null);
        setNavigationButton(NAVIGATION_BUTTON_NONE);
        getMenu().findItem(R.id.search_menu_id).setVisible(false);
        getMenu().findItem(R.id.edit_menu_id).setVisible(false);
    }

    // EnhancedBookmarkUIObserver implementations.

    @Override
    public void onEnhancedBookmarkDelegateInitialized(EnhancedBookmarkDelegate delegate) {
        mDelegate = delegate;
        mDelegate.addUIObserver(this);
        delegate.getModel().addObserver(mBookmarkModelObserver);
    }

    @Override
    public void onDestroy() {
        mDelegate.removeUIObserver(this);
        mDelegate.getModel().removeObserver(mBookmarkModelObserver);
    }

    @Override
    public void onAllBookmarksStateSet() {
        setTitle(getTitleForAllItems());
        setNavigationButton(NAVIGATION_BUTTON_MENU);
        getMenu().findItem(R.id.search_menu_id).setVisible(true);
        getMenu().findItem(R.id.edit_menu_id).setVisible(false);
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        mCurrentFolder = mDelegate.getModel().getBookmarkById(folder);

        getMenu().findItem(R.id.search_menu_id).setVisible(false);
        getMenu().findItem(R.id.edit_menu_id).setVisible(mCurrentFolder.isEditable());

        // If the parent folder is a top level node, we don't go up anymore.
        if (mDelegate.getModel().getTopLevelFolderParentIDs().contains(
                mCurrentFolder.getParentId())) {
            if (TextUtils.isEmpty(mCurrentFolder.getTitle())) {
                setTitle(getTitleForAllItems());
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
    public void onFilterStateSet(EnhancedBookmarkFilter filter) {
        assert filter == EnhancedBookmarkFilter.OFFLINE_PAGES;
        setTitle(R.string.enhanced_bookmark_title_bar_filter_offline_pages);
        setNavigationButton(NAVIGATION_BUTTON_MENU);
        getMenu().findItem(R.id.edit_menu_id).setVisible(false);
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
        boolean wasSelectionEnabled = mIsSelectionEnabled;
        mIsSelectionEnabled = mDelegate.isSelectionEnabled();
        NumberRollView numberRollView = (NumberRollView) findViewById(R.id.selection_mode_number);
        if (mIsSelectionEnabled) {
            setNavigationButton(NAVIGATION_BUTTON_SELECTION_BACK);
            setTitle(null);

            getMenu().setGroupVisible(R.id.normal_menu_group, false);
            getMenu().setGroupVisible(R.id.selection_mode_menu_group, true);
            // Editing a bookmark action on multiple selected items doesn't make sense. So disable.
            getMenu().findItem(R.id.selection_mode_edit_menu_id).setVisible(
                    selectedBookmarks.size() == 1);
            // Partner bookmarks can't move, so if the selection includes a partner bookmark,
            // disable the move button.
            for (BookmarkId bookmark : selectedBookmarks) {
                if (bookmark.getType() == BookmarkType.PARTNER) {
                    getMenu().findItem(R.id.selection_mode_move_menu_id).setVisible(false);
                    break;
                }
            }

            setBackgroundColor(
                    ApiCompatibilityUtils.getColor(getResources(), R.color.light_active_color));

            numberRollView.setVisibility(View.VISIBLE);
            if (!wasSelectionEnabled) numberRollView.setNumber(0, false);
            numberRollView.setNumber(selectedBookmarks.size(), true);
        } else {
            getMenu().setGroupVisible(R.id.normal_menu_group, true);
            getMenu().setGroupVisible(R.id.selection_mode_menu_group, false);
            setBackgroundColor(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.enhanced_bookmark_appbar_background));

            numberRollView.setVisibility(View.GONE);
            numberRollView.setNumber(0, false);

            mDelegate.notifyStateChange(this);
        }
    }

    private int getTitleForAllItems() {
        return OfflinePageBridge.isEnabled()
                ? R.string.offline_pages_all_items
                : R.string.enhanced_bookmark_title_bar_all_items;
    }
}
