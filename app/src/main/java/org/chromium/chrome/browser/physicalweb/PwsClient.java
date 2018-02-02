// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.graphics.Bitmap;

import java.util.Collection;

/**
 * This class sends requests to the Physical Web Service.
 */
interface PwsClient {
    /**
     * Callback that is run after the PWS sends a response to a resolve-scan request.
     */
    interface ResolveScanCallback {
        /**
         * Handle newly returned PwsResults.
         * @param pwsResults The results returned by the PWS.
         */
        public void onPwsResults(Collection<PwsResult> pwsResults);
    }

    /**
     * Callback that is run after receiving the response to an icon fetch request.
     */
    interface FetchIconCallback {
        /**
         * Handle newly returned favicon Bitmaps.
         * @param iconUrl The favicon URL.
         * @param iconBitmap The icon image data.
         */
        public void onIconReceived(String iconUrl, Bitmap iconBitmap);
    }

    /**
     * Send an HTTP request to the PWS to resolve a set of URLs.
     * @param broadcastUrls The URLs to resolve.
     * @param resolveScanCallback The callback to be run when the response is received.
     */
    void resolve(Collection<UrlInfo> broadcastUrls, ResolveScanCallback resolveScanCallback);

    /**
     * Send an HTTP request to fetch a favicon.
     * @param iconUrl The URL of the favicon.
     * @param fetchIconCallback The callback to be run when the icon is received.
     */
    void fetchIcon(String iconUrl, FetchIconCallback fetchIconCallback);
}
