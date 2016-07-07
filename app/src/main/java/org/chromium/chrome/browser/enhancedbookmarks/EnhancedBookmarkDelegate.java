// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.support.v4.widget.DrawerLayout;

import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.List;

/**
 * Interface used among EnhancedBookmark UI components to broadcast UI change notifications and get
 * bookmark data model.
 */
interface EnhancedBookmarkDelegate {

    /**
     * Delegate used to open urls for main fragment on tablet.
     */
    interface EnhancedBookmarkStateChangeListener {
        /**
         * Let the tab containing bookmark manager load the url and later handle UI updates.
         * @param url The url to open in tab.
         */
        public void onBookmarkUIStateChange(String url);
    }

    /**
     * Corresponds to "All Items" list item in the side drawer. Shows all bookmarks.
     */
    void openAllBookmarks();

    /**
     * Corresponds to any folder named list item in the side drawer. Shows bookmarks under the
     * folder.
     * @param folder Parent folder that contains bookmarks to show as its children.
     */
    void openFolder(BookmarkId folder);

    /**
     * Corresponds to any filter named list item in the side drawer. Shows bookmarks that match
     * that filter.
     * @param filter A filter that will narrow down a list of bookmarks to show.
     */
    void openFilter(EnhancedBookmarkFilter filter);

    /**
     * Clear all selected items. After this call, {@link #isSelectionEnabled()} will return false.
     */
    void clearSelection();

    /**
     * Toggle the selection state of a bookmark. If the given bookmark is not
     * editable, it will take no effect.
     * @return True if the bookmark is selected after toggling. False otherwise.
     */
    boolean toggleSelectionForBookmark(BookmarkId bookmark);

    /**
     * @return True if the bookmark is selected. False otherwise.
     */
    boolean isBookmarkSelected(BookmarkId bookmark);

    /**
     * @return Whether selection is happening.
     */
    boolean isSelectionEnabled();

    /**
     * @return The list of bookmarks that are currently selected by the user.
     */
    List<BookmarkId> getSelectedBookmarks();

    /**
     * Notifies the current mode set event to the given observer. For example, if the current mode
     * is MODE_ALL_BOOKMARKS, it calls onAllBookmarksModeSet.
     */
    void notifyStateChange(EnhancedBookmarkUIObserver observer);

    /**
     * @return Whether there is a drawer.
     */
    boolean doesDrawerExist();

    /**
     * Close drawer if it's visible.
     */
    void closeDrawer();

    /**
     * @return The current drawer layout instance, if it exists.
     */
    DrawerLayout getDrawerLayout();

    /**
     * Closes the EnhancedBookmark UI (if on phone) and opens the given bookmark.
     * @param bookmark       bookmark to open.
     * @param launchLocation The UI location where user tried to open bookmark. It is one of
     *                       {@link LaunchLocation} values
     */
    void openBookmark(BookmarkId bookmark, int launchLocation);

    /**
     * Shows the search UI.
     */
    void openSearchUI();

    /**
     * Dismisses the search UI.
     */
    void closeSearchUI();

    /**
     * Add an observer to enhanced bookmark UI changes.
     */
    void addUIObserver(EnhancedBookmarkUIObserver observer);

    /**
     * Remove an observer of enhanced bookmark UI changes.
     */
    void removeUIObserver(EnhancedBookmarkUIObserver observer);

    /**
     * @return Enhanced bookmark data model associated with this UI.
     */
    EnhancedBookmarksModel getModel();

    /**
     * @return Current UIState of Enhanced Bookmark main UI. If no mode is stored,
     *         {@link UIState#STATE_LOADING} is returned.
     */
    int getCurrentState();

    /**
     * @return LargeIconBridge instance. By sharing the instance, we can also share the cache.
     */
    LargeIconBridge getLargeIconBridge();

    /**
     * @return SnackbarManager instance.
     */
    SnackbarManager getSnackbarManager();
}
