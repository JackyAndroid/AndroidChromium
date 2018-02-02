// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.partnerbookmarks;

import android.content.Context;
import android.content.pm.ApplicationInfo;

/**
 * The Java counterpart for the C++ partner bookmarks shim.
 * Responsible for:
 * - checking if we need to fetch the partner bookmarks,
 * - kicking off the async fetching of the partner bookmarks,
 * - pushing the partner bookmarks to the C++ side,
 * - reporting that all partner bookmarks were read to the C++ side.
 */
public class PartnerBookmarksShim {
    private static boolean sIsReadingAttempted;

    /**
     * Checks if we need to fetch the Partner bookmarks and kicks the reading off. If reading was
     * attempted before, it won't do anything.
     */
    public static void kickOffReading(Context context) {
        if (sIsReadingAttempted) return;
        sIsReadingAttempted = true;

        PartnerBookmarksReader reader = new PartnerBookmarksReader(context);
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            reader.onBookmarksRead();
            return;
        }
        reader.readBookmarks();
    }
}
