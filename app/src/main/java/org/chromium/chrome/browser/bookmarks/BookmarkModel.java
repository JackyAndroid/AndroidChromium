// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import org.chromium.base.ObserverList;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;

import java.util.List;

/**
 * A class that encapsulates {@link BookmarkBridge} and provides extra features such as undo, large
 * icon fetching, reader mode url redirecting, etc. This class should serve as the single class for
 * the UI to acquire data from the backend.
 */
public class BookmarkModel extends BookmarkBridge {
    private static final int FAVICON_MAX_CACHE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Observer that listens to delete event. This interface is used by undo controllers to know
     * which bookmarks were deleted. Note this observer only listens to events that go through
     * bookmark model.
     */
    public interface BookmarkDeleteObserver {

        /**
         * Callback being triggered immediately before bookmarks are deleted.
         * @param titles All titles of the bookmarks to be deleted.
         * @param isUndoable Whether the deletion is undoable.
         */
        void onDeleteBookmarks(String[] titles, boolean isUndoable);
    }

    private ObserverList<BookmarkDeleteObserver> mDeleteObservers = new ObserverList<>();

    /**
     * Initialize bookmark model for last used non-incognito profile.
     */
    public BookmarkModel() {
        this(Profile.getLastUsedProfile().getOriginalProfile());
    }

    @VisibleForTesting
    public BookmarkModel(Profile profile) {
        super(profile);
    }

    /**
     * Clean up all the bridges. This must be called after done using this class.
     */
    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public boolean isBookmarkModelLoaded() {
        return super.isBookmarkModelLoaded();
    }

    /**
     * Add an observer that listens to delete events that go through the bookmark model.
     * @param observer The observer to add.
     */
    public void addDeleteObserver(BookmarkDeleteObserver observer) {
        mDeleteObservers.addObserver(observer);
    }

    /**
     * Remove the observer from listening to bookmark deleting events.
     * @param observer The observer to remove.
     */
    public void removeDeleteObserver(BookmarkDeleteObserver observer) {
        mDeleteObservers.removeObserver(observer);
    }

    /**
     * Delete one or multiple bookmarks from model. If more than one bookmarks are passed here, this
     * method will group these delete operations into one undo bundle so that later if the user
     * clicks undo, all bookmarks deleted here will be restored.
     * @param bookmarks Bookmarks to delete. Note this array should not contain a folder and its
     *                  children, because deleting folder will also remove all its children, and
     *                  deleting children once more will cause errors.
     */
    public void deleteBookmarks(BookmarkId... bookmarks) {
        assert bookmarks != null && bookmarks.length > 0;
        // Store all titles of bookmarks.
        String[] titles = new String[bookmarks.length];
        boolean isUndoable = true;
        for (int i = 0; i < bookmarks.length; i++) {
            titles[i] = getBookmarkTitle(bookmarks[i]);
            isUndoable &= (bookmarks[i].getType() == BookmarkType.NORMAL);
        }

        if (bookmarks.length == 1) {
            deleteBookmark(bookmarks[0]);
        } else {
            startGroupingUndos();
            for (BookmarkId bookmark : bookmarks) {
                deleteBookmark(bookmark);
            }
            endGroupingUndos();
        }

        for (BookmarkDeleteObserver observer : mDeleteObservers) {
            observer.onDeleteBookmarks(titles, isUndoable);
        }
    }

    /**
     * Calls {@link BookmarkBridge#moveBookmark(BookmarkId, BookmarkId, int)} for the given
     * bookmark list. The bookmarks are appended at the end.
     */
    public void moveBookmarks(List<BookmarkId> bookmarkIds, BookmarkId newParentId) {
        int appenedIndex = getChildCount(newParentId);
        for (int i = 0; i < bookmarkIds.size(); ++i) {
            moveBookmark(bookmarkIds.get(i), newParentId, appenedIndex + i);
        }
    }

    /**
     * @see org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkItem#getTitle()
     */
    public String getBookmarkTitle(BookmarkId bookmarkId) {
        return getBookmarkById(bookmarkId).getTitle();
    }

    /**
     * @return The id of the default folder to add bookmarks/folders to.
     */
    public BookmarkId getDefaultFolder() {
        return getMobileFolderId();
    }
}
