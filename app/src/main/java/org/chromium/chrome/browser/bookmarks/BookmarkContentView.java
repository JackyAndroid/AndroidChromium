// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.FadingShadow;
import org.chromium.chrome.browser.widget.FadingShadowView;
import org.chromium.chrome.browser.widget.LoadingView;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.List;

/**
 * A ViewGroup that holds an {@link BookmarkActionBar}, a {@link FadingShadowView}, a
 * {@link BookmarkRecyclerView} and a {@link LoadingView}.
 */
public class BookmarkContentView extends RelativeLayout implements
        BookmarkUIObserver {
    private BookmarkDelegate mDelegate;
    private BookmarkRecyclerView mItemsContainer;
    private BookmarkActionBar mActionBar;
    private LoadingView mLoadingView;

    /**
     * Creates an instance of {@link BookmarkContentView}. This constructor should be used
     * by the framework when inflating from XML.
     */
    public BookmarkContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mItemsContainer = (BookmarkRecyclerView) findViewById(R.id.bookmark_items_container);
        TextView emptyView = (TextView) findViewById(R.id.bookmark_empty_view);
        emptyView.setText(R.string.bookmarks_folder_empty);
        mItemsContainer.setEmptyView(emptyView);
        mActionBar = (BookmarkActionBar) findViewById(R.id.bookmark_action_bar);
        mLoadingView = (LoadingView) findViewById(R.id.bookmark_initial_loading_view);
        FadingShadowView shadow = (FadingShadowView) findViewById(R.id.shadow);
        if (DeviceFormFactor.isLargeTablet(getContext())) {
            shadow.setVisibility(View.GONE);
        } else {
            shadow.init(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.toolbar_shadow_color), FadingShadow.POSITION_TOP);
        }
    }

    /**
     * Handles the event when user clicks back button and the UI is in selection mode.
     * @return True if there are selected bookmarks, and the back button is processed by this
     *         method. False otherwise.
     */
    boolean onBackPressed() {
        if (mDelegate != null && mDelegate.getSelectionDelegate().isSelectionEnabled()) {
            mDelegate.getSelectionDelegate().clearSelection();
            return true;
        }
        return false;
    }

    void showLoadingUi() {
        mActionBar.showLoadingUi();
        mLoadingView.showLoadingUI();
    }

    // BookmarkUIObserver implementations.

    @Override
    public void onBookmarkDelegateInitialized(BookmarkDelegate delegate) {
        mDelegate = delegate;
        mDelegate.addUIObserver(this);
        mItemsContainer.onBookmarkDelegateInitialized(mDelegate);
        mActionBar.onBookmarkDelegateInitialized(mDelegate);
    }

    @Override
    public void onDestroy() {
        mDelegate.removeUIObserver(this);
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        mLoadingView.hideLoadingUI();
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
    }
}
