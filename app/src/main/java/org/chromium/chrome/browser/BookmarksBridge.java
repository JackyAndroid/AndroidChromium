// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.util.Pair;

import org.chromium.base.ObserverList;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.enhancedbookmarks.BookmarkMatch;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides the communication channel for Android to fetch and manipulate the
 * bookmark model stored in native.
 */
public class BookmarksBridge {
    private final Profile mProfile;
    private boolean mIsDoingExtensiveChanges;
    private long mNativeBookmarksBridge;
    private boolean mIsNativeBookmarkModelLoaded;
    private final List<DelayedBookmarkCallback> mDelayedBookmarkCallbacks =
            new ArrayList<DelayedBookmarkCallback>();
    private final ObserverList<BookmarkModelObserver> mObservers =
            new ObserverList<BookmarkModelObserver>();

    /**
     * Interface for callback object for fetching bookmarks and folder hierarchy.
     */
    public interface BookmarksCallback {
        /**
         * Callback method for fetching bookmarks for a folder and the folder hierarchy.
         * @param folderId The folder id to which the bookmarks belong.
         * @param bookmarksList List holding the fetched bookmarks and details.
         */
        @CalledByNative("BookmarksCallback")
        void onBookmarksAvailable(BookmarkId folderId, List<BookmarkItem> bookmarksList);

        /**
         * Callback method for fetching the folder hierarchy.
         * @param folderId The folder id to which the bookmarks belong.
         * @param bookmarksList List holding the fetched folder details.
         */
        @CalledByNative("BookmarksCallback")
        void onBookmarksFolderHierarchyAvailable(BookmarkId folderId,
                List<BookmarkItem> bookmarksList);
    }

    /**
     * Base empty implementation observer class that provides listeners to be notified of changes
     * to the bookmark model. It's mandatory to implement one method, bookmarkModelChanged. Other
     * methods are optional and if they aren't overridden, the default implementation of them will
     * eventually call bookmarkModelChanged. Unless noted otherwise, all the functions won't be
     * called during extensive change.
     */
    public abstract static class BookmarkModelObserver {
        /**
         * Invoked when a node has moved.
         * @param oldParent The parent before the move.
         * @param oldIndex The index of the node in the old parent.
         * @param newParent The parent after the move.
         * @param newIndex The index of the node in the new parent.
         */
        public void bookmarkNodeMoved(
                BookmarkItem oldParent, int oldIndex, BookmarkItem newParent, int newIndex) {
            bookmarkModelChanged();
        }

        /**
         * Invoked when a node has been added.
         * @param parent The parent of the node being added.
         * @param index The index of the added node.
         */
        public void bookmarkNodeAdded(BookmarkItem parent, int index) {
            bookmarkModelChanged();
        }

        /**
         * Invoked when a node has been removed, the item may still be starred though. This can
         * be called during extensive change, and have the flag argument indicating it.
         * @param parent The parent of the node that was removed.
         * @param oldIndex The index of the removed node in the parent before it was removed.
         * @param node The node that was removed.
         * @param isDoingExtensiveChanges whether extensive changes are happening.
         */
        public void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node,
                boolean isDoingExtensiveChanges) {
            if (isDoingExtensiveChanges) return;

            bookmarkNodeRemoved(parent, oldIndex, node);
        }

        /**
         * Invoked when a node has been removed, the item may still be starred though.
         *
         * @param parent The parent of the node that was removed.
         * @param oldIndex The index of the removed node in the parent before it was removed.
         * @param node The node that was removed.
         */
        public void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node) {
            bookmarkModelChanged();
        }

        /**
         * Invoked when all user-editable nodes have been removed. The exception is partner and
         * managed bookmarks, which are not affected by this operation.
         */
        public void bookmarkAllUserNodesRemoved() {
            bookmarkModelChanged();
        }

        /**
         * Invoked when the title or url of a node changes.
         * @param node The node being changed.
         */
        public void bookmarkNodeChanged(BookmarkItem node) {
            bookmarkModelChanged();
        }

        /**
         * Invoked when the children (just direct children, not descendants) of a node have been
         * reordered in some way, such as sorted.
         * @param node The node whose children are being reordered.
         */
        public void bookmarkNodeChildrenReordered(BookmarkItem node) {
            bookmarkModelChanged();
        }

        /**
         * Called when the native side of bookmark is loaded and now in usable state.
         */
        public void bookmarkModelLoaded() {
            bookmarkModelChanged();
        }

        /**
         *  Called when there are changes to the bookmark model that don't trigger any of the other
         *  callback methods or it wasn't handled by other callback methods.
         *  Examples:
         *  - On partner bookmarks change.
         *  - On extensive change finished.
         *  - Falling back from other methods that are not overridden in this class.
         */
        public abstract void bookmarkModelChanged();
    }

    /**
     * Handler to fetch the bookmarks, titles, urls and folder hierarchy.
     * @param profile Profile instance corresponding to the active profile.
     */
    public BookmarksBridge(Profile profile) {
        mProfile = profile;
        mNativeBookmarksBridge = nativeInit(profile);
        mIsDoingExtensiveChanges = nativeIsDoingExtensiveChanges(mNativeBookmarksBridge);
    }

    /**
     * Destroys this instance so no further calls can be executed.
     */
    public void destroy() {
        if (mNativeBookmarksBridge != 0) {
            nativeDestroy(mNativeBookmarksBridge);
            mNativeBookmarksBridge = 0;
            mIsNativeBookmarkModelLoaded = false;
            mDelayedBookmarkCallbacks.clear();
        }
        mObservers.clear();
    }

    /**
     * Load an empty partner bookmark shim for testing. The root node for bookmark will be an
     * empty node.
     */
    @VisibleForTesting
    public void loadEmptyPartnerBookmarkShimForTesting() {
        nativeLoadEmptyPartnerBookmarkShimForTesting(mNativeBookmarksBridge);
    }

    /**
     * Add an observer to bookmark model changes.
     * @param observer The observer to be added.
     */
    public void addObserver(BookmarkModelObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Remove an observer of bookmark model changes.
     * @param observer The observer to be removed.
     */
    public void removeObserver(BookmarkModelObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * @return Whether or not the underlying bookmark model is loaded.
     */
    public boolean isBookmarkModelLoaded() {
        return mIsNativeBookmarkModelLoaded;
    }

    /**
     * @return A BookmarkItem instance for the given BookmarkId.
     *         <code>null</code> if it doesn't exist.
     */
    public BookmarkItem getBookmarkById(BookmarkId id) {
        assert mIsNativeBookmarkModelLoaded;
        return nativeGetBookmarkByID(mNativeBookmarksBridge, id.getId(), id.getType());
    }

    /**
     * @return All the permanent nodes.
     */
    public List<BookmarkId> getPermanentNodeIDs() {
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkId> result = new ArrayList<BookmarkId>();
        nativeGetPermanentNodeIDs(mNativeBookmarksBridge, result);
        return result;
    }

    /**
     * @return The top level folder's parents.
     */
    public List<BookmarkId> getTopLevelFolderParentIDs() {
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkId> result = new ArrayList<BookmarkId>();
        nativeGetTopLevelFolderParentIDs(mNativeBookmarksBridge, result);
        return result;
    }

    /**
     * @param getSpecial Whether special top folders should be returned.
     * @param getNormal  Whether normal top folders should be returned.
     * @return The top level folders. Note that special folders come first and normal top folders
     *         will be in the alphabetical order.
     */
    public List<BookmarkId> getTopLevelFolderIDs(boolean getSpecial, boolean getNormal) {
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkId> result = new ArrayList<BookmarkId>();
        nativeGetTopLevelFolderIDs(mNativeBookmarksBridge, getSpecial, getNormal, result);
        return result;
    }

    /**
     * Populates folderList with BookmarkIds of folders users can move bookmarks
     * to and all folders have corresponding depth value in depthList. Folders
     * having depths of 0 will be shown as top-layered folders. These include
     * "Desktop Folder" itself as well as all children of "mobile" and "other".
     * Children of 0-depth folders have depth of 1, and so on.
     *
     * The result list will be sorted alphabetically by title. "mobile", "other",
     * root node, managed folder, partner folder are NOT included as results.
     */
    @VisibleForTesting
    public void getAllFoldersWithDepths(List<BookmarkId> folderList,
            List<Integer> depthList) {
        assert mIsNativeBookmarkModelLoaded;
        nativeGetAllFoldersWithDepths(mNativeBookmarksBridge, folderList, depthList);
    }

    /**
     * Calls {@link #getAllFoldersWithDepths(List, List)} and remove all folders and children
     * in bookmarksToMove. This method is useful when finding a list of possible parent folers when
     * moving some folders (a folder cannot be moved to its own children).
     */
    public void getMoveDestinations(List<BookmarkId> folderList,
            List<Integer> depthList, List<BookmarkId> bookmarksToMove) {
        assert mIsNativeBookmarkModelLoaded;
        nativeGetAllFoldersWithDepths(mNativeBookmarksBridge, folderList, depthList);
        if (bookmarksToMove == null || bookmarksToMove.size() == 0) return;

        boolean shouldTrim = false;
        int trimThreshold = -1;
        for (int i = 0; i < folderList.size(); i++) {
            int depth = depthList.get(i);
            if (shouldTrim) {
                if (depth <= trimThreshold) {
                    shouldTrim = false;
                    trimThreshold = -1;
                } else {
                    folderList.remove(i);
                    depthList.remove(i);
                    i--;
                }
            }
            // Do not use else here because shouldTrim could be set true after if (shouldTrim)
            // statement.
            if (!shouldTrim) {
                BookmarkId folder = folderList.get(i);
                if (bookmarksToMove.contains(folder)) {
                    shouldTrim = true;
                    trimThreshold = depth;
                    folderList.remove(i);
                    depthList.remove(i);
                    i--;
                }
            }
        }
    }

    /**
     * @return The BookmarkId for root folder node
     */
    public BookmarkId getRootFolderId() {
        assert mIsNativeBookmarkModelLoaded;
        return nativeGetRootFolderId(mNativeBookmarksBridge);
    }

    /**
     * @return The BookmarkId for Mobile folder node
     */
    public BookmarkId getMobileFolderId() {
        assert mIsNativeBookmarkModelLoaded;
        return nativeGetMobileFolderId(mNativeBookmarksBridge);
    }

    /**
     * @return Id representing the special "other" folder from bookmark model.
     */
    public BookmarkId getOtherFolderId() {
        assert mIsNativeBookmarkModelLoaded;
        return nativeGetOtherFolderId(mNativeBookmarksBridge);
    }

    /**
     * @return BokmarkId representing special "desktop" folder, namely "bookmark bar".
     */
    public BookmarkId getDesktopFolderId() {
        assert mIsNativeBookmarkModelLoaded;
        return nativeGetDesktopFolderId(mNativeBookmarksBridge);
    }

    /**
     * @return The number of children that the given node has.
     */
    public int getChildCount(BookmarkId id) {
        assert mIsNativeBookmarkModelLoaded;
        return nativeGetChildCount(mNativeBookmarksBridge, id.getId(), id.getType());
    }

    /**
     * Reads sub-folder IDs, sub-bookmark IDs, or both of the given folder.
     *
     * @param getFolders   Whether sub-folders should be returned.
     * @param getBookmarks Whether sub-bookmarks should be returned.
     * @return Child IDs of the given folder, with the specified type.
     */
    public List<BookmarkId> getChildIDs(BookmarkId id, boolean getFolders, boolean getBookmarks) {
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkId> result = new ArrayList<BookmarkId>();
        nativeGetChildIDs(mNativeBookmarksBridge,
                id.getId(),
                id.getType(),
                getFolders,
                getBookmarks,
                result);
        return result;
    }

    /**
     * Gets the child of a folder at the specific position.
     * @param folderId Id of the parent folder
     * @param index Posision of child among all children in folder
     * @return BookmarkId of the child, which will be null if folderId does not point to a folder or
     *         index is invalid.
     */
    public BookmarkId getChildAt(BookmarkId folderId, int index) {
        assert mIsNativeBookmarkModelLoaded;
        return nativeGetChildAt(mNativeBookmarksBridge, folderId.getId(), folderId.getType(),
                index);
    }

    /**
     * @return All bookmark IDs ordered by descending creation date. Partner/managed bookmarks are
     *         not included.
     */
    public List<BookmarkId> getAllBookmarkIDsOrderedByCreationDate() {
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkId> result = new ArrayList<BookmarkId>();
        nativeGetAllBookmarkIDsOrderedByCreationDate(mNativeBookmarksBridge, result);
        return result;
    }

    /**
     * Synchronously gets a list of bookmarks that match the specified search query.
     * @param query Keyword used for searching bookmarks.
     * @param maxNumberOfResult Maximum number of result to fetch.
     * @return List of bookmarks that are related to the given query.
     */
    public List<BookmarkMatch> searchBookmarks(String query, int maxNumberOfResult) {
        List<BookmarkMatch> bookmarkMatches = new ArrayList<BookmarkMatch>();
        nativeSearchBookmarks(mNativeBookmarksBridge, bookmarkMatches, query,
                maxNumberOfResult);
        return bookmarkMatches;
    }


    /**
     * Set title of the given bookmark.
     */
    public void setBookmarkTitle(BookmarkId id, String title) {
        assert mIsNativeBookmarkModelLoaded;
        nativeSetBookmarkTitle(mNativeBookmarksBridge, id.getId(), id.getType(), title);
    }

    /**
     * Set URL of the given bookmark.
     */
    public void setBookmarkUrl(BookmarkId id, String url) {
        assert mIsNativeBookmarkModelLoaded;
        assert id.getType() == BookmarkType.NORMAL;
        nativeSetBookmarkUrl(mNativeBookmarksBridge, id.getId(), id.getType(), url);
    }

    /**
     * @return Whether the given bookmark exist in the current bookmark model, e.g., not deleted.
     */
    public boolean doesBookmarkExist(BookmarkId id) {
        assert mIsNativeBookmarkModelLoaded;
        return nativeDoesBookmarkExist(mNativeBookmarksBridge, id.getId(), id.getType());
    }

    /**
     * Fetches the bookmarks of the given folder. This is an always-synchronous version of another
     * getBookmarksForForder function.
     *
     * @param folderId The parent folder id.
     * @return Bookmarks of the given folder.
     */
    public List<BookmarkItem> getBookmarksForFolder(BookmarkId folderId) {
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkItem> result = new ArrayList<BookmarkItem>();
        nativeGetBookmarksForFolder(mNativeBookmarksBridge, folderId, null, result);
        return result;
    }

    /**
     * Fetches the bookmarks of the current folder. Callback will be
     * synchronous if the bookmark model is already loaded and async if it is loaded in the
     * background.
     * @param folderId The current folder id.
     * @param callback Instance of a callback object.
     */
    public void getBookmarksForFolder(BookmarkId folderId, BookmarksCallback callback) {
        if (mIsNativeBookmarkModelLoaded) {
            nativeGetBookmarksForFolder(mNativeBookmarksBridge, folderId, callback,
                    new ArrayList<BookmarkItem>());
        } else {
            mDelayedBookmarkCallbacks.add(new DelayedBookmarkCallback(folderId, callback,
                    DelayedBookmarkCallback.GET_BOOKMARKS_FOR_FOLDER, this));
        }
    }

    /**
     * Check whether the given folder should be visible. This is for top permanent folders that we
     * want to hide when there is no child.
     * @return Whether the given folder should be visible.
     */
    public boolean isFolderVisible(BookmarkId id) {
        assert mIsNativeBookmarkModelLoaded;
        return nativeIsFolderVisible(mNativeBookmarksBridge, id.getId(), id.getType());
    }

    /**
     * Fetches the folder hierarchy of the given folder. Callback will be
     * synchronous if the bookmark model is already loaded and async if it is loaded in the
     * background.
     * @param folderId The current folder id.
     * @param callback Instance of a callback object.
     */
    public void getCurrentFolderHierarchy(BookmarkId folderId, BookmarksCallback callback) {
        if (mIsNativeBookmarkModelLoaded) {
            nativeGetCurrentFolderHierarchy(mNativeBookmarksBridge, folderId, callback,
                    new ArrayList<BookmarkItem>());
        } else {
            mDelayedBookmarkCallbacks.add(new DelayedBookmarkCallback(folderId, callback,
                    DelayedBookmarkCallback.GET_CURRENT_FOLDER_HIERARCHY, this));
        }
    }

    /**
     * Deletes a specified bookmark node.
     * @param bookmarkId The ID of the bookmark to be deleted.
     */
    public void deleteBookmark(BookmarkId bookmarkId) {
        nativeDeleteBookmark(mNativeBookmarksBridge, bookmarkId);
    }

    /**
     * Move the bookmark to the new index within same folder or to a different folder.
     * @param bookmarkId The id of the bookmark that is being moved.
     * @param newParentId The parent folder id.
     * @param index The new index for the bookmark.
     */
    public void moveBookmark(BookmarkId bookmarkId, BookmarkId newParentId, int index) {
        nativeMoveBookmark(mNativeBookmarksBridge, bookmarkId, newParentId, index);
    }

    /**
     * Add a new folder to the given parent folder
     *
     * @param parent Folder where to add. Must be a normal editable folder, instead of a partner
     *               bookmark folder or a managed bookomark folder or root node of the entire
     *               bookmark model.
     * @param index The position to locate the new folder
     * @param title The title text of the new folder
     * @return Id of the added node. If adding failed (index is invalid, string is null, parent is
     *         not editable), returns null.
     */
    public BookmarkId addFolder(BookmarkId parent, int index, String title) {
        assert parent.getType() == BookmarkType.NORMAL;
        assert index >= 0;
        assert title != null;

        return nativeAddFolder(mNativeBookmarksBridge, parent, index, title);
    }

    /**
     * Add a new bookmark to a specific position below parent
     *
     * @param parent Folder where to add. Must be a normal editable folder, instead of a partner
     *               bookmark folder or a managed bookomark folder or root node of the entire
     *               bookmark model.
     * @param index The position where the bookmark will be placed in parent folder
     * @param title Title of the new bookmark
     * @param url Url of the new bookmark
     * @return Id of the added node. If adding failed (index is invalid, string is null, parent is
     *         not editable), returns null.
     */
    public BookmarkId addBookmark(BookmarkId parent, int index, String title, String url) {
        assert parent.getType() == BookmarkType.NORMAL;
        assert index >= 0;
        assert title != null;
        assert url != null;

        return nativeAddBookmark(mNativeBookmarksBridge, parent, index, title, url);
    }

    /**
     * Undo the last undoable action on the top of the bookmark undo stack
     */
    public void undo() {
        nativeUndo(mNativeBookmarksBridge);
    }

    /**
     * Start grouping actions for a single undo operation
     * Note: This only works with BookmarkModel, not partner bookmarks.
     */
    public void startGroupingUndos() {
        nativeStartGroupingUndos(mNativeBookmarksBridge);
    }

    /**
     * End grouping actions for a single undo operation
     * Note: This only works with BookmarkModel, not partner bookmarks.
     */
    public void endGroupingUndos() {
        nativeEndGroupingUndos(mNativeBookmarksBridge);
    }

    public boolean isEditBookmarksEnabled() {
        return nativeIsEditBookmarksEnabled(mNativeBookmarksBridge);
    }

    public static boolean isEnhancedBookmarksEnabled() {
        return nativeIsEnhancedBookmarksFeatureEnabled();
    }

    /**
     * Notifies the observer that bookmark model has been loaded.
     */
    protected void notifyBookmarkModelLoaded() {
        // Call isBookmarkModelLoaded() to do the check since it could be overridden by the child
        // class to add the addition logic.
        if (isBookmarkModelLoaded()) {
            for (BookmarkModelObserver observer : mObservers) {
                observer.bookmarkModelLoaded();
            }
        }
    }

    @CalledByNative
    private void bookmarkModelLoaded() {
        mIsNativeBookmarkModelLoaded = true;

        notifyBookmarkModelLoaded();

        if (!mDelayedBookmarkCallbacks.isEmpty()) {
            for (int i = 0; i < mDelayedBookmarkCallbacks.size(); i++) {
                mDelayedBookmarkCallbacks.get(i).callCallbackMethod();
            }
            mDelayedBookmarkCallbacks.clear();
        }
    }

    @CalledByNative
    private void bookmarkModelDeleted() {
        destroy();
    }

    @CalledByNative
    private void bookmarkNodeMoved(
            BookmarkItem oldParent, int oldIndex, BookmarkItem newParent, int newIndex) {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeMoved(oldParent, oldIndex, newParent, newIndex);
        }
    }

    @CalledByNative
    private void bookmarkNodeAdded(BookmarkItem parent, int index) {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeAdded(parent, index);
        }
    }

    @CalledByNative
    private void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node) {
        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeRemoved(parent, oldIndex, node,
                    mIsDoingExtensiveChanges);
        }
    }

    @CalledByNative
    private void bookmarkAllUserNodesRemoved() {
        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkAllUserNodesRemoved();
        }
    }

    @CalledByNative
    private void bookmarkNodeChanged(BookmarkItem node) {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeChanged(node);
        }
    }

    @CalledByNative
    private void bookmarkNodeChildrenReordered(BookmarkItem node) {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeChildrenReordered(node);
        }
    }

    @CalledByNative
    private void extensiveBookmarkChangesBeginning() {
        mIsDoingExtensiveChanges = true;
    }

    @CalledByNative
    private void extensiveBookmarkChangesEnded() {
        mIsDoingExtensiveChanges = false;
        bookmarkModelChanged();
    }

    @CalledByNative
    private void bookmarkModelChanged() {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkModelChanged();
        }
    }

    @CalledByNative
    private static BookmarkItem createBookmarkItem(long id, int type, String title, String url,
            boolean isFolder, long parentId, int parentIdType, boolean isEditable,
            boolean isManaged) {
        return new BookmarkItem(new BookmarkId(id, type), title, url, isFolder,
                new BookmarkId(parentId, parentIdType), isEditable, isManaged);
    }

    @CalledByNative
    private static void addToList(List<BookmarkItem> bookmarksList, BookmarkItem bookmark) {
        bookmarksList.add(bookmark);
    }

    @CalledByNative
    private static void addToBookmarkIdList(List<BookmarkId> bookmarkIdList, long id, int type) {
        bookmarkIdList.add(new BookmarkId(id, type));
    }

    @CalledByNative
    private static void addToBookmarkIdListWithDepth(List<BookmarkId> folderList, long id,
            int type, List<Integer> depthList, int depth) {
        folderList.add(new BookmarkId(id, type));
        depthList.add(depth);
    }

    @CalledByNative
    private static void addToBookmarkMatchList(List<BookmarkMatch> bookmarkMatchList,
            long id, int type, int[] titleMatchStartPositions,
            int[] titleMatchEndPositions, int[] urlMatchStartPositions,
            int[] urlMatchEndPositions) {
        bookmarkMatchList.add(new BookmarkMatch(new BookmarkId(id, type),
                createPairsList(titleMatchStartPositions, titleMatchEndPositions),
                createPairsList(urlMatchStartPositions, urlMatchEndPositions)));
    }

    private static List<Pair<Integer, Integer>> createPairsList(int[] left, int[] right) {
        List<Pair<Integer, Integer>> pairList = new ArrayList<Pair<Integer, Integer>>();
        for (int i = 0; i < left.length; i++) {
            pairList.add(new Pair<Integer, Integer>(left[i], right[i]));
        }
        return pairList;
    }


    private native BookmarkItem nativeGetBookmarkByID(long nativeBookmarksBridge, long id,
            int type);
    private native void nativeGetPermanentNodeIDs(long nativeBookmarksBridge,
            List<BookmarkId> bookmarksList);
    private native void nativeGetTopLevelFolderParentIDs(long nativeBookmarksBridge,
            List<BookmarkId> bookmarksList);
    private native void nativeGetTopLevelFolderIDs(long nativeBookmarksBridge, boolean getSpecial,
            boolean getNormal, List<BookmarkId> bookmarksList);
    private native void nativeGetAllFoldersWithDepths(long nativeBookmarksBridge,
            List<BookmarkId> folderList, List<Integer> depthList);
    private native BookmarkId nativeGetRootFolderId(long nativeBookmarksBridge);
    private native BookmarkId nativeGetMobileFolderId(long nativeBookmarksBridge);
    private native BookmarkId nativeGetOtherFolderId(long nativeBookmarksBridge);
    private native BookmarkId nativeGetDesktopFolderId(long nativeBookmarksBridge);
    private native int nativeGetChildCount(long nativeBookmarksBridge, long id, int type);
    private native void nativeGetChildIDs(long nativeBookmarksBridge, long id, int type,
            boolean getFolders, boolean getBookmarks, List<BookmarkId> bookmarksList);
    private native BookmarkId nativeGetChildAt(long nativeBookmarksBridge, long id, int type,
            int index);
    private native void nativeGetAllBookmarkIDsOrderedByCreationDate(long nativeBookmarksBridge,
            List<BookmarkId> result);
    private native void nativeSetBookmarkTitle(long nativeBookmarksBridge, long id, int type,
            String title);
    private native void nativeSetBookmarkUrl(long nativeBookmarksBridge, long id, int type,
            String url);
    private native boolean nativeDoesBookmarkExist(long nativeBookmarksBridge, long id, int type);
    private native void nativeGetBookmarksForFolder(long nativeBookmarksBridge,
            BookmarkId folderId, BookmarksCallback callback,
            List<BookmarkItem> bookmarksList);
    private native boolean nativeIsFolderVisible(long nativeBookmarksBridge, long id, int type);
    private native void nativeGetCurrentFolderHierarchy(long nativeBookmarksBridge,
            BookmarkId folderId, BookmarksCallback callback,
            List<BookmarkItem> bookmarksList);
    private native BookmarkId nativeAddFolder(long nativeBookmarksBridge, BookmarkId parent,
            int index, String title);
    private native void nativeDeleteBookmark(long nativeBookmarksBridge, BookmarkId bookmarkId);
    private native void nativeMoveBookmark(long nativeBookmarksBridge, BookmarkId bookmarkId,
            BookmarkId newParentId, int index);
    private native BookmarkId nativeAddBookmark(long nativeBookmarksBridge, BookmarkId parent,
            int index, String title, String url);
    private native void nativeUndo(long nativeBookmarksBridge);
    private native void nativeStartGroupingUndos(long nativeBookmarksBridge);
    private native void nativeEndGroupingUndos(long nativeBookmarksBridge);
    private static native boolean nativeIsEnhancedBookmarksFeatureEnabled();
    private native void nativeLoadEmptyPartnerBookmarkShimForTesting(long nativeBookmarksBridge);
    private native void nativeSearchBookmarks(long nativeBookmarksBridge,
            List<BookmarkMatch> bookmarkMatches, String query, int maxNumber);
    private native long nativeInit(Profile profile);
    private native boolean nativeIsDoingExtensiveChanges(long nativeBookmarksBridge);
    private native void nativeDestroy(long nativeBookmarksBridge);
    private static native boolean nativeIsEditBookmarksEnabled(long nativeBookmarksBridge);

    /**
     * Simple object representing the bookmark item.
     */
    public static class BookmarkItem {

        private final String mTitle;
        private final String mUrl;
        private final BookmarkId mId;
        private final boolean mIsFolder;
        private final BookmarkId mParentId;
        private final boolean mIsEditable;
        private final boolean mIsManaged;

        private BookmarkItem(BookmarkId id, String title, String url, boolean isFolder,
                BookmarkId parentId, boolean isEditable, boolean isManaged) {
            mId = id;
            mTitle = title;
            mUrl = url;
            mIsFolder = isFolder;
            mParentId = parentId;
            mIsEditable = isEditable;
            mIsManaged = isManaged;
        }

        /** @return Title of the bookmark item. */
        public String getTitle() {
            return mTitle;
        }

        /** @return Url of the bookmark item. */
        public String getUrl() {
            return mUrl;
        }

        /** @return Id of the bookmark item. */
        public BookmarkId getId() {
            return mId;
        }

        /** @return Whether item is a folder or a bookmark. */
        public boolean isFolder() {
            return mIsFolder;
        }

        /** @return Parent id of the bookmark item. */
        public BookmarkId getParentId() {
            return mParentId;
        }

        /** @return Whether this bookmark can be edited. */
        public boolean isEditable() {
            return mIsEditable;
        }

        /**@return Whether this bookmark's URL can be edited */
        public boolean isUrlEditable() {
            return isEditable() && mId.getType() == BookmarkType.NORMAL;
        }

        /**@return Whether this bookmark can be moved */
        public boolean isMovable() {
            return isEditable() && mId.getType() == BookmarkType.NORMAL;
        }

        /** @return Whether this is a managed bookmark. */
        public boolean isManaged() {
            return mIsManaged;
        }
    }

    /**
     * Details about callbacks that need to be called once the bookmark model has loaded.
     */
    private static class DelayedBookmarkCallback {

        private static final int GET_BOOKMARKS_FOR_FOLDER = 0;
        private static final int GET_CURRENT_FOLDER_HIERARCHY = 1;

        private final BookmarksCallback mCallback;
        private final BookmarkId mFolderId;
        private final int mCallbackMethod;
        private final BookmarksBridge mHandler;

        private DelayedBookmarkCallback(BookmarkId folderId, BookmarksCallback callback,
                int method, BookmarksBridge handler) {
            mFolderId = folderId;
            mCallback = callback;
            mCallbackMethod = method;
            mHandler = handler;
        }

        /**
         * Invoke the callback method.
         */
        private void callCallbackMethod() {
            switch (mCallbackMethod) {
                case GET_BOOKMARKS_FOR_FOLDER:
                    mHandler.getBookmarksForFolder(mFolderId, mCallback);
                    break;
                case GET_CURRENT_FOLDER_HIERARCHY:
                    mHandler.getCurrentFolderHierarchy(mFolderId, mCallback);
                    break;
                default:
                    assert false;
                    break;
            }
        }
    }
}
