// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.physicalweb;

/**
 * A result from the Physical Web Server.
 *
 * This represents metadata about a URL retrieved from from a PWS response.  It does not
 * necessarily represent one response as a single PWS response may include metadata about multiple
 * URLs.
 */
class PwsResult {
    /**
     * The URL that was set in the request to the PWS.
     */
    public final String requestUrl;

    /**
     * The destination URL that the requestUrl redirects to.
     */
    public final String siteUrl;

    /**
     * The URL for the destination's favicon.
     */
    public final String iconUrl;

    /**
     * The title of the web page.
     */
    public final String title;

    /**
     * The description of the webpage.
     */
    public final String description;

    /**
     * Construct a PwsResult.
     * @param requestUrl The URL that was sent in the request to the PWS.
     */
    PwsResult(String requestUrl, String siteUrl, String iconUrl, String title, String description) {
        this.requestUrl = requestUrl;
        this.siteUrl = siteUrl;
        this.iconUrl = iconUrl;
        this.title = title;
        this.description = description;
    }
}
