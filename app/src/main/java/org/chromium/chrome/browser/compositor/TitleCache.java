// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor;

import android.graphics.Bitmap;

/**
 * The interface that defines a {@link TitleCache}. The {@link TitleCache} is supposed to
 * store one bitmap per tab. The retrieval type of the title image is up to the renderer's
 * implementation.
 */
public interface TitleCache {
    /**
     * Put a {@link Tab} title bitmap in the cache.
     * @param tabId         The id of the {@link Tab}.
     * @param titleBitmap   The {@link Bitmap} representing the title of the {@link ChromeTab}.
     * @param faviconBitmap The {@link Bitmap} representing the favicon of the {@link ChromeTab}.
     * @param isIncognito   True if the title is for an icognito tab.
     * @param isRtl         True if the title should be RTL.
     */
    void put(int tabId, Bitmap titleBitmap, Bitmap faviconBitmap, boolean isIncognito,
            boolean isRtl);

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
