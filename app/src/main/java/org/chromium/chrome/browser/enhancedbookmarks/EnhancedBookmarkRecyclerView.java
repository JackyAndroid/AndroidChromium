// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.List;

/**
 * Container for all bookmark items shown in enhanced bookmark manager.
 */
public class EnhancedBookmarkRecyclerView extends RecyclerView implements
        EnhancedBookmarkUIObserver {

    private EnhancedBookmarkDelegate mDelegate;
    private View mEmptyView;
    private RecyclerView.ItemDecoration mVerticalSpaceItemDecoration;

    /**
     * Provides a way to override the default spacing between 2 items in RecyclerView.
     */
    private static class VerticalSpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int mSpacing;

        public VerticalSpaceItemDecoration(int spacing) {
            this.mSpacing = spacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            outRect.bottom = mSpacing;
        }
    }

    /**
     * Constructs a new instance of enhanced bookmark recycler view.
     */
    public EnhancedBookmarkRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setLayoutManager(new LinearLayoutManager(context));
        setHasFixedSize(true);
    }

    /**
     * Sets the view to be shown if there are no items in adapter.
     */
    void setEmptyView(View emptyView) {
        mEmptyView = emptyView;
    }

    // RecyclerView implementation

    @Override
    public void setAdapter(final Adapter adapter) {
        super.setAdapter(adapter);
        adapter.registerAdapterDataObserver(new AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                updateEmptyViewVisibility(adapter);
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                updateEmptyViewVisibility(adapter);
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);
                updateEmptyViewVisibility(adapter);
            }
        });
        updateEmptyViewVisibility(adapter);
    }

    @VisibleForTesting
    @Override
    public EnhancedBookmarkItemsAdapter getAdapter() {
        return (EnhancedBookmarkItemsAdapter) super.getAdapter();
    }

    /**
     * Unlike ListView or GridView, RecyclerView does not provide default empty
     * view implementation. We need to check it ourselves.
     */
    private void updateEmptyViewVisibility(Adapter adapter) {
        mEmptyView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // EnhancedBookmarkUIObserver implementations

    @Override
    public void onEnhancedBookmarkDelegateInitialized(EnhancedBookmarkDelegate delegate) {
        mDelegate = delegate;
        mDelegate.addUIObserver(this);

        EnhancedBookmarkItemsAdapter adapter = new EnhancedBookmarkItemsAdapter(getContext());
        adapter.onEnhancedBookmarkDelegateInitialized(mDelegate);
        setAdapter(adapter);
    }

    @Override
    public void onDestroy() {
        mDelegate.removeUIObserver(this);
    }

    @Override
    public void onAllBookmarksStateSet() {
        scrollToPosition(0);

        // Restores to the default vertical spacing.
        if (mVerticalSpaceItemDecoration != null) {
            removeItemDecoration(mVerticalSpaceItemDecoration);
            mVerticalSpaceItemDecoration = null;
        }
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        scrollToPosition(0);

        // Restores to the default vertical spacing.
        if (mVerticalSpaceItemDecoration != null) {
            removeItemDecoration(mVerticalSpaceItemDecoration);
            mVerticalSpaceItemDecoration = null;
        }
    }

    @Override
    public void onFilterStateSet(EnhancedBookmarkFilter filter) {
        assert filter == EnhancedBookmarkFilter.OFFLINE_PAGES;
        scrollToPosition(0);

        // For "Saved offline" filter view, more spacing is needed between 2 items since the added
        // line to show offline page size eats up the default spacing.
        if (mVerticalSpaceItemDecoration == null) {
            mVerticalSpaceItemDecoration =
                    new VerticalSpaceItemDecoration(getResources().getDimensionPixelSize(
                            R.dimen.offline_page_item_vertical_spacing));
            addItemDecoration(mVerticalSpaceItemDecoration);
        }
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
        if (!mDelegate.isSelectionEnabled()) {
            for (int i = 0; i < getLayoutManager().getChildCount(); ++i) {
                View child = getLayoutManager().getChildAt(i);
                if (child instanceof Checkable) ((Checkable) child).setChecked(false);
            }
        }
    }
}
