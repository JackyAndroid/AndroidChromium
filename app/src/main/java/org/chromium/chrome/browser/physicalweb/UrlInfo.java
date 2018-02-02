// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * This class represents a scanned URL and information associated with that URL.
 */
class UrlInfo {
    private static final String URL_KEY = "url";
    private static final String DISTANCE_KEY = "distance";
    private static final String SCAN_TIMESTAMP_KEY = "scan_timestamp";
    private static final String HAS_BEEN_DISPLAYED_KEY = "has_been_displayed";
    private final String mUrl;
    private double mDistance;
    private long mScanTimestamp;
    private boolean mHasBeenDisplayed;

    public UrlInfo(String url, double distance, long scanTimestamp) {
        mUrl = url;
        mDistance = distance;
        mScanTimestamp = scanTimestamp;
        mHasBeenDisplayed = false;
    }

    /**
     * Constructs a simple UrlInfo with only a URL.
     */
    public UrlInfo(String url) {
        this(url, -1.0, System.currentTimeMillis());
    }

    /**
     * Gets the URL represented by this object.
     * @param The URL.
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Sets the distance of the URL from the scanner in meters.
     * @param distance The estimated distance of the URL from the scanner in meters.
     */
    public void setDistance(double distance) {
        mDistance = distance;
    }

    /**
     * Gets the distance of the URL from the scanner in meters.
     * @return The estimated distance of the URL from the scanner in meters.
     */
    public double getDistance() {
        return mDistance;
    }

    /**
     * Sets the timestamp of when the URL was last scanned.
     * This timestamp should be recorded using System.currentTimeMillis().
     * @param scanTimestamp the new timestamp.
     */
    public void setScanTimestamp(long scanTimestamp) {
        mScanTimestamp = scanTimestamp;
    }

    /**
     * Gets the timestamp of when the URL was last scanned.
     * This timestamp is recorded using System.currentTimeMillis().
     * @return The scan timestamp.
     */
    public long getScanTimestamp() {
        return mScanTimestamp;
    }

    /**
     * Marks this URL as having been displayed to the user.
     */
    public void setHasBeenDisplayed() {
        mHasBeenDisplayed = true;
    }

    /**
     * Tells if we've displayed this URL.
     * @return Whether we've displayed this URL.
     */
    public boolean hasBeenDisplayed() {
        return mHasBeenDisplayed;
    }

    /**
     * Creates a JSON object that represents this data structure.
     * @return a JSON serialization of this data structure.
     * @throws JSONException if the values cannot be deserialized.
     */
    public JSONObject jsonSerialize() throws JSONException {
        return new JSONObject()
                .put(URL_KEY, mUrl)
                .put(DISTANCE_KEY, mDistance)
                .put(SCAN_TIMESTAMP_KEY, mScanTimestamp)
                .put(HAS_BEEN_DISPLAYED_KEY, mHasBeenDisplayed);
    }

    /**
     * Populates a UrlInfo with data from a given JSON object.
     * @param jsonObject a serialized UrlInfo.
     * @return The UrlInfo represented by the serialized object.
     * @throws JSONException if the values cannot be serialized.
     */
    public static UrlInfo jsonDeserialize(JSONObject jsonObject) throws JSONException {
        UrlInfo urlInfo = new UrlInfo(
                jsonObject.getString(URL_KEY),
                jsonObject.getDouble(DISTANCE_KEY),
                jsonObject.getLong(SCAN_TIMESTAMP_KEY));
        if (jsonObject.optBoolean(HAS_BEEN_DISPLAYED_KEY, false)) {
            urlInfo.setHasBeenDisplayed();
        }
        return urlInfo;
    }

    /**
     * Represents the UrlInfo as a String.
     */
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%s %f %d %b",
                mUrl, mDistance, mScanTimestamp, mHasBeenDisplayed);
    }
}
