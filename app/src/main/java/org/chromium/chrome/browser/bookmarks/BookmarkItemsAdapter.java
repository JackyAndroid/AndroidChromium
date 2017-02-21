// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkItem;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.bookmarks.BookmarkPromoHeader.PromoHeaderShowingChangeListener;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.ArrayList;
import java.util.List;

/**
 * BaseAdapter for {@link BookmarkRecyclerView}. It manages bookmarks to list there.
 */
class BookmarkItemsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements
        BookmarkUIObserver, PromoHeaderShowingChangeListener {
    private static final int PROMO_HEADER_VIEW = 0;
    private static final int FOLDER_VIEW = 1;
    private static final int DIVIDER_VIEW = 2;
    private static final int BOOKMARK_VIEW = 3;

    private BookmarkDelegate mDelegate;
    private Context mContext;
    private BookmarkPromoHeader mPromoHeaderManager;

    private List<List<? extends Object>> mSections;
    private List<Object> mPromoHeaderSection = new ArrayList<>();
    private List<Object> mFolderDividerSection = new ArrayList<>();
    private List<BookmarkId> mFolderSection = new ArrayList<>();
    private List<Object> mBookmarkDividerSection = new ArrayList<>();
    private List<BookmarkId> mBookmarkSection = new ArrayList<>();

    private BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {
        @Override
        public void bookmarkNodeChanged(BookmarkItem node) {
            assert mDelegate != null;
            int position = getPositionForBookmark(node.getId());
            if (position >= 0) notifyItemChanged(position);
        }

        @Override
        public void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node,
                boolean isDoingExtensiveChanges) {
            assert mDelegate != null;
            if (node.isFolder()) {
                mDelegate.notifyStateChange(BookmarkItemsAdapter.this);
            } else {
                int deletedPosition = getPositionForBookmark(node.getId());
                if (deletedPosition >= 0) {
                    removeItem(deletedPosition);
                }
            }
        }

        @Override
        public void bookmarkModelChanged() {
            assert mDelegate != null;
            mDelegate.notifyStateChange(BookmarkItemsAdapter.this);
        }
    };

    BookmarkItemsAdapter(Context context) {
        mContext = context;

        mSections = new ArrayList<>();
        mSections.add(mPromoHeaderSection);
        mSections.add(mFolderDividerSection);
        mSections.add(mFolderSection);
        mSections.add(mBookmarkDividerSection);
        mSections.add(mBookmarkSection);
    }

    BookmarkId getItem(int position) {
        return (BookmarkId) getSection(position).get(toSectionPosition(position));
    }

    private int toSectionPosition(int globalPosition) {
        int sectionPosition = globalPosition;
        for (List<?> section : mSections) {
            if (sectionPosition < section.size()) break;
            sectionPosition -= section.size();
        }
        return sectionPosition;
    }

    private List<? extends Object> getSection(int position) {
        int i = position;
        for (List<? extends Object> section : mSections) {
            if (i < section.size()) {
                return section;
            }
            i -= section.size();
        }
        return null;
    }

    /**
     * @return The position of the given bookmark in adapter. Will return -1 if not found.
     */
    private int getPositionForBookmark(BookmarkId bookmark) {
        assert bookmark != null;
        int position = -1;
        for (int i = 0; i < getItemCount(); i++) {
            if (bookmark.equals(getItem(i))) {
                position = i;
                break;
            }
        }
        return position;
    }

    /**
     * Set folders and bookmarks to show.
     * @param folders This can be null if there is no folders to show.
     */
    private void setBookmarks(List<BookmarkId> folders, List<BookmarkId> bookmarks) {
        if (folders == null) folders = new ArrayList<BookmarkId>();

        mFolderSection.clear();
        mFolderSection.addAll(folders);
        mBookmarkSection.clear();
        mBookmarkSection.addAll(bookmarks);

        updateHeader();
        updateDividerSections();

        // TODO(kkimlabs): Animation is disabled due to a performance issue on bookmark undo.
        //                 http://crbug.com/484174
        notifyDataSetChanged();
    }

    private void updateDividerSections() {
        mFolderDividerSection.clear();
        mBookmarkDividerSection.clear();

        boolean isHeaderPresent = !mPromoHeaderSection.isEmpty();

        if (isHeaderPresent && !mFolderSection.isEmpty()) {
            mFolderDividerSection.add(null);
        }
        if ((isHeaderPresent || !mFolderSection.isEmpty()) && !mBookmarkSection.isEmpty()) {
            mBookmarkDividerSection.add(null);
        }
    }

    private void removeItem(int position) {
        List<?> section = getSection(position);
        assert section == mFolderSection || section == mBookmarkSection;
        section.remove(toSectionPosition(position));
        notifyItemRemoved(position);
    }

    // RecyclerView.Adapter implementation.

    @Override
    public int getItemCount() {
        int count = 0;
        for (List<?> section : mSections) {
            count += section.size();
        }
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        List<?> section = getSection(position);

        if (section == mPromoHeaderSection) {
            return PROMO_HEADER_VIEW;
        } else if (section == mFolderDividerSection
                || section == mBookmarkDividerSection) {
            return DIVIDER_VIEW;
        } else if (section == mFolderSection) {
            return FOLDER_VIEW;
        } else if (section == mBookmarkSection) {
            return BOOKMARK_VIEW;
        }

        assert false : "Invalid position requested";
        return -1;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        assert mDelegate != null;

        switch (viewType) {
            case PROMO_HEADER_VIEW:
                return mPromoHeaderManager.createHolder(parent);
            case DIVIDER_VIEW:
                return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.bookmark_divider, parent, false)) {};
            case FOLDER_VIEW:
                BookmarkFolderRow folder = (BookmarkFolderRow) LayoutInflater.from(
                        parent.getContext()).inflate(R.layout.bookmark_folder_row, parent, false);
                folder.onBookmarkDelegateInitialized(mDelegate);
                return new ItemViewHolder(folder);
            case BOOKMARK_VIEW:
                BookmarkItemRow item = (BookmarkItemRow) LayoutInflater.from(
                        parent.getContext()).inflate(R.layout.bookmark_item_row, parent, false);
                item.onBookmarkDelegateInitialized(mDelegate);
                return new ItemViewHolder(item);
            default:
                assert false;
                return null;
        }
    }

    @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        BookmarkId id = getItem(position);

        switch (getItemViewType(position)) {
            case PROMO_HEADER_VIEW:
            case DIVIDER_VIEW:
                break;
            case FOLDER_VIEW:
                ((BookmarkRow) holder.itemView).setBookmarkId(id);
                break;
            case BOOKMARK_VIEW:
                ((BookmarkRow) holder.itemView).setBookmarkId(id);
                break;
            default:
                assert false : "View type not supported!";
        }
    }

    // PromoHeaderShowingChangeListener implementation.

    @Override
    public void onPromoHeaderShowingChanged(boolean isShowing) {
        assert mDelegate != null;
        if (mDelegate.getCurrentState() != BookmarkUIState.STATE_FOLDER) {
            return;
        }

        updateHeader();
        updateDividerSections();
        notifyDataSetChanged();
    }

    // BookmarkUIObserver implementations.

    @Override
    public void onBookmarkDelegateInitialized(BookmarkDelegate delegate) {
        mDelegate = delegate;
        mDelegate.addUIObserver(this);
        mDelegate.getModel().addObserver(mBookmarkModelObserver);
        mPromoHeaderManager = new BookmarkPromoHeader(mContext, this);
    }

    @Override
    public void onDestroy() {
        mDelegate.removeUIObserver(this);
        mDelegate.getModel().removeObserver(mBookmarkModelObserver);
        mDelegate = null;

        mPromoHeaderManager.destroy();
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        assert mDelegate != null;
        setBookmarks(mDelegate.getModel().getChildIDs(folder, true, false),
                mDelegate.getModel().getChildIDs(folder, false, true));
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {}

    private static class ItemViewHolder extends RecyclerView.ViewHolder {
        private ItemViewHolder(View view) {
            super(view);
        }
    }

    private void updateHeader() {
        if (mDelegate == null) return;

        int currentUIState = mDelegate.getCurrentState();
        if (currentUIState == BookmarkUIState.STATE_LOADING) return;

        mPromoHeaderSection.clear();
        assert currentUIState == BookmarkUIState.STATE_FOLDER : "Unexpected UI state";
        if (mPromoHeaderManager.shouldShow()) {
            mPromoHeaderSection.add(null);
        }
    }
}
