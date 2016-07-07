// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.content.Context;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkItem;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.enhancedbookmarks.EnhancedBookmarksModel.EnhancedBookmarkDeleteObserver;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;

import java.util.Locale;

/**
 * Shows an undo bar when the user modifies enhanced bookmarks,
 * allowing them to undo their changes.
 * TODO(danduong): Add move undo
 */
public class EnhancedBookmarkUndoController extends BookmarkModelObserver implements
        SnackbarManager.SnackbarController, EnhancedBookmarkDeleteObserver {

    private final EnhancedBookmarksModel mBookmarksModel;
    private final SnackbarManager mSnackbarManager;
    private final Context mContext;

    /**
     * Creates an instance of {@link EnhancedBookmarkUndoController}.
     * @param context The {@link Context} in which snackbar is shown.
     * @param model The enhanced bookmark model.
     * @param snackbarManager SnackManager passed from activity.
     */
    public EnhancedBookmarkUndoController(Context context, EnhancedBookmarksModel model,
            SnackbarManager snackbarManager) {
        mBookmarksModel = model;
        mBookmarksModel.addDeleteObserver(this);
        mSnackbarManager = snackbarManager;
        mContext = context;
    }

    /**
     * Cleans up this class, unregistering for application notifications from bookmark model.
     */
    public void destroy() {
        mBookmarksModel.removeDeleteObserver(this);
        mSnackbarManager.dismissSnackbars(this);
    }

    @Override
    public void onAction(Object actionData) {
        mBookmarksModel.undo();
        mSnackbarManager.dismissSnackbars(this);
    }

    @Override
    public void onDismissNoAction(Object actionData) {
    }

    @Override
    public void onDismissForEachType(boolean isTimeout) {
    }

    // Overriding BookmarkModelObserver
    @Override
    public void bookmarkModelChanged() {
        mSnackbarManager.dismissSnackbars(this);
    }

    @Override
    public void bookmarkNodeChanged(BookmarkItem node) {
        // Title/url change of a bookmark should not affect undo.
    }

    @Override
    public void bookmarkNodeAdded(BookmarkItem parent, int index) {
        // Adding a new bookmark should not affect undo.
    }

    // Implement EnhancedBookmarkDeleteObserver
    @Override
    public void onDeleteBookmarks(String[] titles, boolean isUndoable) {
        assert titles != null && titles.length >= 1;

        if (!isUndoable) return;

        if (titles.length == 1) {
            mSnackbarManager.showSnackbar(Snackbar.make(titles[0], this)
                    .setTemplateText(mContext.getString(R.string.undo_bar_delete_message))
                    .setAction(mContext.getString(R.string.undo_bar_button_text), null));
        } else {
            mSnackbarManager.showSnackbar(
                    Snackbar.make(String.format(Locale.getDefault(), "%d", titles.length), this)
                    .setTemplateText(mContext.getString(R.string.undo_bar_multiple_delete_message))
                    .setAction(mContext.getString(R.string.undo_bar_button_text), null));
        }
    }
}
