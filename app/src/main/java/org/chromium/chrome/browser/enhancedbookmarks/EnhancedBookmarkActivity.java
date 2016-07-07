// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarManageable;
import org.chromium.components.bookmarks.BookmarkId;

/**
 * The activity that wraps all enhanced bookmark UI on the phone. It keeps a
 * {@link EnhancedBookmarkManager} inside of it and creates a snackbar manager. This activity
 * should only be shown on phones; on tablet the enhanced bookmark UI is shown inside of a tab (see
 * {@link EnhancedBookmarkPage}).
 */
public class EnhancedBookmarkActivity extends EnhancedBookmarkActivityBase implements
        SnackbarManageable {

    private EnhancedBookmarkManager mBookmarkManager;
    private SnackbarManager mSnackbarManager;
    static final int EDIT_BOOKMARK_REQUEST_CODE = 14;
    public static final String INTENT_VISIT_BOOKMARK_ID =
            "EnhancedBookmarkEditActivity.VisitBookmarkId";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        mSnackbarManager = new SnackbarManager(getWindow());
        mBookmarkManager = new EnhancedBookmarkManager(this);
        setContentView(mBookmarkManager.getView());
        EnhancedBookmarkUtils.setTaskDescriptionInDocumentMode(this, getString(
                OfflinePageBridge.isEnabled() ? R.string.offline_pages_saved_pages
                        : R.string.bookmarks));

        // Hack to work around inferred theme false lint error: http://crbug.com/445633
        assert (R.layout.eb_main_content != 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBookmarkManager.destroy();
    }

    @Override
    public SnackbarManager getSnackbarManager() {
        return mSnackbarManager;
    }

    @Override
    public void onBackPressed() {
        if (!mBookmarkManager.onBackPressed()) super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_BOOKMARK_REQUEST_CODE && resultCode == RESULT_OK) {
            BookmarkId bookmarkId = BookmarkId.getBookmarkIdFromString(data.getStringExtra(
                    INTENT_VISIT_BOOKMARK_ID));
            mBookmarkManager.openBookmark(bookmarkId, LaunchLocation.BOOKMARK_EDITOR);
        }
    }
}
