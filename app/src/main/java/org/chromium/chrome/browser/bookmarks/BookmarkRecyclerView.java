// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

import org.chromium.base.VisibleForTesting;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.List;

/**
 * Container for all bookmark items shown in bookmark manager.
 */
public class BookmarkRecyclerView extends RecyclerView implements BookmarkUIObserver {

    private BookmarkDelegate mDelegate;
    private View mEmptyView;

    /**
     * Constructs a new instance of bookmark recycler view.
     */
    public BookmarkRecyclerView(Context context, AttributeSet attrs) {
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
    public BookmarkItemsAdapter getAdapter() {
        return (BookmarkItemsAdapter) super.getAdapter();
    }

    /**
     * Unlike ListView or GridView, RecyclerView does not provide default empty
     * view implementation. We need to check it ourselves.
     */
    private void updateEmptyViewVisibility(Adapter adapter) {
        mEmptyView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // BookmarkUIObserver implementations

    @Override
    public void onBookmarkDelegateInitialized(BookmarkDelegate delegate) {
        mDelegate = delegate;
        mDelegate.addUIObserver(this);

        BookmarkItemsAdapter adapter = new BookmarkItemsAdapter(getContext());
        adapter.onBookmarkDelegateInitialized(mDelegate);
        setAdapter(adapter);
    }

    @Override
    public void onDestroy() {
        mDelegate.removeUIObserver(this);
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        scrollToPosition(0);
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
        if (!mDelegate.getSelectionDelegate().isSelectionEnabled()) {
            for (int i = 0; i < getLayoutManager().getChildCount(); ++i) {
                View child = getLayoutManager().getChildAt(i);
                if (child instanceof Checkable) ((Checkable) child).setChecked(false);
            }
        }
    }

    @VisibleForTesting
    public BookmarkDelegate getDelegateForTesting() {
        return mDelegate;
    }
}
