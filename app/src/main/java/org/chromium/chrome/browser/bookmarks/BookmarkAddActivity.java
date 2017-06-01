// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.init.AsyncInitializationActivity;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksShim;
import org.chromium.components.bookmarks.BookmarkId;

/**
 * This invisible activity adds a bookmark with the supplied title and url, then launches an
 * activity to show the new bookmark.
 *
 * This activity is used only on pre-M devices with Chrome pre-installed. When a third-party app
 * calls the now-obsolete method Browser.saveBookmark(), the call is forwarded through
 * AddBookmarkProxyActivity and launches this activity. See http://crbug.com/581961 for details.
 *
 * TODO(newt): remove this once Android L is no longer supported.
 */
public class BookmarkAddActivity extends AsyncInitializationActivity {

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_URL = "url";

    private BookmarkModel mModel;

    @Override
    protected void setContentView() {}

    @Override
    public void finishNativeInitialization() {
        RecordUserAction.record("MobileAddBookmarkViaIntent");

        final String title = getIntent().getStringExtra(EXTRA_TITLE);
        final String url = getIntent().getStringExtra(EXTRA_URL);

        // Partner bookmarks need to be loaded explicitly.
        PartnerBookmarksShim.kickOffReading(this);

        // Store mModel as a member variable so it can't be garbage collected. Otherwise the
        // Runnable might never be run.
        mModel = new BookmarkModel();
        mModel.runAfterBookmarkModelLoaded(new Runnable() {
            @Override
            public void run() {
                BookmarkId bookmarkId = BookmarkUtils.addBookmarkSilently(
                        BookmarkAddActivity.this, mModel, title, url);
                if (bookmarkId != null) {
                    BookmarkUtils.startEditActivity(BookmarkAddActivity.this, bookmarkId);
                }
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mModel != null) {
            mModel.destroy();
            mModel = null;
        }
    }
}
