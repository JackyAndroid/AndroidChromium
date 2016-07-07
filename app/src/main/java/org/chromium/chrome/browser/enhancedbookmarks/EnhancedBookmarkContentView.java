// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.widget.FadingShadow;
import org.chromium.chrome.browser.widget.FadingShadowView;
import org.chromium.chrome.browser.widget.LoadingView;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.List;

/**
 * A ViewGroup that holds an {@link EnhancedBookmarkActionBar}, a {@link FadingShadowView}, a
 * {@link EnhancedBookmarkRecyclerView} and a {@link LoadingView}.
 */
public class EnhancedBookmarkContentView extends RelativeLayout implements
        EnhancedBookmarkUIObserver {
    private EnhancedBookmarkDelegate mDelegate;
    private EnhancedBookmarkRecyclerView mItemsContainer;
    private EnhancedBookmarkActionBar mActionBar;
    private LoadingView mLoadingView;

    /**
     * Creates an instance of {@link EnhancedBookmarkContentView}. This constructor should be used
     * by the framework when inflating from XML.
     */
    public EnhancedBookmarkContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mItemsContainer = (EnhancedBookmarkRecyclerView) findViewById(R.id.eb_items_container);
        TextView emptyView = (TextView) findViewById(R.id.eb_empty_view);
        emptyView.setText(OfflinePageBridge.isEnabled()
                ? R.string.offline_pages_folder_empty : R.string.bookmarks_folder_empty);
        mItemsContainer.setEmptyView(emptyView);
        mActionBar = (EnhancedBookmarkActionBar) findViewById(R.id.eb_action_bar);
        mLoadingView = (LoadingView) findViewById(R.id.eb_initial_loading_view);
        FadingShadowView shadow = (FadingShadowView) findViewById(R.id.shadow);
        if (DeviceFormFactor.isLargeTablet(getContext())) {
            shadow.setVisibility(View.GONE);
        } else {
            shadow.init(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.enhanced_bookmark_app_bar_shadow_color),
                    FadingShadow.POSITION_TOP);
            shadow.setStrength(1.0f);
        }
    }

    /**
     * Handles the event when user clicks back button and the UI is in selection mode.
     * @return True if there are selected bookmarks, and the back button is processed by this
     *         method. False otherwise.
     */
    boolean onBackPressed() {
        if (mDelegate != null && mDelegate.isSelectionEnabled()) {
            mDelegate.clearSelection();
            return true;
        }
        return false;
    }

    void showLoadingUi() {
        mActionBar.showLoadingUi();
        mLoadingView.showLoadingUI();
    }

    // EnhancedBookmarkUIObserver implementations.

    @Override
    public void onEnhancedBookmarkDelegateInitialized(EnhancedBookmarkDelegate delegate) {
        mDelegate = delegate;
        mDelegate.addUIObserver(this);
        mItemsContainer.onEnhancedBookmarkDelegateInitialized(mDelegate);
        mActionBar.onEnhancedBookmarkDelegateInitialized(mDelegate);
    }

    @Override
    public void onDestroy() {
        mDelegate.removeUIObserver(this);
    }

    @Override
    public void onAllBookmarksStateSet() {
        mLoadingView.hideLoadingUI();
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        mLoadingView.hideLoadingUI();
    }

    @Override
    public void onFilterStateSet(EnhancedBookmarkFilter filter) {
        mLoadingView.hideLoadingUI();
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
    }
}
