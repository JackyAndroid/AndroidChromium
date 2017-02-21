// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

/**
 * Possible locations from which a bookmark can be opened.
 *
 * Please sync with the corresponding histograms.xml.
 */
class BookmarkLaunchLocation {
    public static final int ALL_ITEMS = 0; // Deprecated.
    public static final int UNCATEGORIZED = 1;  // Deprecated.
    public static final int FOLDER = 2;
    public static final int FILTER = 3;
    public static final int SEARCH = 4;
    public static final int BOOKMARK_EDITOR = 5;
    public static final int COUNT = 6;
}
