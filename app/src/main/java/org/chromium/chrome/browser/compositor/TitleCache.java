// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor;

import org.chromium.chrome.browser.tab.Tab;

/**
 * The interface that defines a {@link TitleCache}. The {@link TitleCache} is supposed to
 * store one bitmap per tab. The retrieval type of the title image is up to the renderer's
 * implementation.
 */
public interface TitleCache {

    /**
     * Update the title (favicon and text), and return the title string.
     * @param tab The tab to draw title cache.
     * @param defaultTitle The default title to use when title and even URL are both empty.
     */
    String getUpdatedTitle(Tab tab, String defaultTitle);

    /**
     * Removes a title image from the cache.
     * @param tabId The id of the {@link Tab} to remove from the cache.
     */
    void remove(int tabId);

    /**
     * Clears everything in the cache except for the provided tab id.
     * @param tabId The id of the {@link Tab} to keep in the cache.
     *     Use {@link Tab.INVALID_TAB_ID} to clear everything in the cache.
     */
    void clearExcept(int tabId);
}
