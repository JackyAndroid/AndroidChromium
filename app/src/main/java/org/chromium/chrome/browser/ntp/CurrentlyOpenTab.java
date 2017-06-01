// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

/**
 * A class that represents tabs open in this device that can be switched to.  The Runnable is
 * expected to bring the Tab back when run.
 */
public class CurrentlyOpenTab {
    private final int mTabId;
    private final String mUrl;
    private final String mTitle;
    private final Runnable mRunnable;

    /**
     * Basic constructor for {@link CurrentlyOpenTab}.
     * @param tabId The id of the tab.
     * @param url The url that the tab is currently at.
     * @param title The title of the page that the tab is showing.
     * @param runnable Run when the item is selected.
     */
    public CurrentlyOpenTab(int tabId, String url, String title, Runnable runnable) {
        mTabId = tabId;
        mUrl = url;
        mTitle = title;
        mRunnable = runnable;
    }

    public int getTabId() {
        return mTabId;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getTitle() {
        return mTitle;
    }

    public Runnable getRunnable() {
        return mRunnable;
    }
}
