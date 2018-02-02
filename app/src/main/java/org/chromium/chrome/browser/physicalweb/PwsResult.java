// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.physicalweb;

import org.chromium.base.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * A result from the Physical Web Server.
 *
 * This represents metadata about a URL retrieved from from a PWS response.  It does not
 * necessarily represent one response as a single PWS response may include metadata about multiple
 * URLs.
 */
class PwsResult {
    private static final String TAG = "PhysicalWeb";
    private static final String PAGE_INFO_KEY = "pageInfo";
    private static final String REQUEST_URL_KEY = "scannedUrl";
    private static final String SITE_URL_KEY = "resolvedUrl";
    private static final String ICON_KEY = "icon";
    private static final String TITLE_KEY = "title";
    private static final String DESCRIPTION_KEY = "description";
    private static final String GROUP_ID_KEY = "groupId";

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
     * The group id as determined by the PWS.
     * This value is useful for associating multiple URLs that refer to similar content in the same
     * bucket.
     */
    public final String groupId;

    /**
     * Construct a PwsResult.
     */
    PwsResult(String requestUrl, String siteUrl, String iconUrl, String title, String description,
                String groupId) {
        this.requestUrl = requestUrl;
        this.siteUrl = siteUrl;
        this.iconUrl = iconUrl;
        this.title = title;
        this.description = description;

        String groupIdToSet = groupId;
        if (groupId == null) {
            try {
                groupIdToSet = new URL(siteUrl).getHost() + title;
            } catch (MalformedURLException e) {
                Log.e(TAG, "PwsResult created with a malformed URL", e);
                groupIdToSet = siteUrl + title;
            }
        }
        this.groupId = groupIdToSet;
    }

    /**
     * Creates a JSON object that represents this data structure.
     * @return a JSON serialization of this data structure.
     * @throws JSONException if the values cannot be deserialized.
     */
    public JSONObject jsonSerialize() throws JSONException {
        return new JSONObject()
                .put(REQUEST_URL_KEY, requestUrl)
                .put(SITE_URL_KEY, siteUrl)
                .put(PAGE_INFO_KEY, new JSONObject()
                    .put(ICON_KEY, iconUrl)
                    .put(TITLE_KEY, title)
                    .put(DESCRIPTION_KEY, description)
                    .put(GROUP_ID_KEY, groupId));
    }

    /**
     * Populates a PwsResult with data from a given JSON object.
     * @param jsonObject a serialized PwsResult.
     * @return The PwsResult represented by the serialized object.
     * @throws JSONException if the values cannot be serialized.
     */
    public static PwsResult jsonDeserialize(JSONObject jsonObject) throws JSONException {
        JSONObject pageInfo = jsonObject.getJSONObject(PAGE_INFO_KEY);
        return new PwsResult(
                jsonObject.getString(REQUEST_URL_KEY),
                jsonObject.getString(SITE_URL_KEY),
                pageInfo.optString(ICON_KEY, null),
                pageInfo.optString(TITLE_KEY, ""),
                pageInfo.optString(DESCRIPTION_KEY, null),
                pageInfo.optString(GROUP_ID_KEY, null));
    }
}
