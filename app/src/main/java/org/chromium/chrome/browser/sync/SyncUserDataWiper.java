// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync;

import org.chromium.base.Promise;
import org.chromium.chrome.browser.bookmarks.BookmarkModel;
import org.chromium.chrome.browser.browsing_data.BrowsingDataType;
import org.chromium.chrome.browser.browsing_data.TimePeriod;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PrefServiceBridge.OnClearBrowsingDataListener;

/**
 * A class to wipe the user's bookmarks and all types of sync data.
 */
public class SyncUserDataWiper {
    private static final int[] SYNC_DATA_TYPES = {
            BrowsingDataType.HISTORY,
            BrowsingDataType.CACHE,
            BrowsingDataType.COOKIES,
            BrowsingDataType.PASSWORDS,
            BrowsingDataType.FORM_DATA
    };

    /**
     * Wipes the user's bookmarks and sync data.
     * @return A promise which will be fulfilled once the data is wiped.
     */
    public static Promise<Void> wipeSyncUserData() {
        final Promise<Void> promise = new Promise<>();

        final BookmarkModel model = new BookmarkModel();
        model.runAfterBookmarkModelLoaded(new Runnable() {
            @Override
            public void run() {
                model.removeAllUserBookmarks();
                model.destroy();
                PrefServiceBridge.getInstance().clearBrowsingData(
                        new OnClearBrowsingDataListener(){
                            @Override
                            public void onBrowsingDataCleared() {
                                promise.fulfill(null);
                            }
                        },
                        SYNC_DATA_TYPES, TimePeriod.ALL_TIME);
            }
        });

        return promise;
    }
}

