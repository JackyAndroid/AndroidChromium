// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.geo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Process;
import android.util.Base64;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.GeolocationInfo;

import java.util.Locale;

/**
 * Provides methods for building the X-Geo HTTP header, which provides device location to a server
 * when making an HTTP request.
 *
 * X-Geo header spec: https://goto.google.com/xgeospec.
 */
public class GeolocationHeader {

    // Values for the histogram Geolocation.HeaderSentOrNot. Values 1, 5, 6, and 7 are defined in
    // histograms.xml and should not be used in other ways.
    public static final int UMA_LOCATION_DISABLED_FOR_GOOGLE_DOMAIN = 0;
    public static final int UMA_LOCATION_NOT_AVAILABLE = 2;
    public static final int UMA_LOCATION_STALE = 3;
    public static final int UMA_HEADER_SENT = 4;
    public static final int UMA_LOCATION_DISABLED_FOR_CHROME_APP = 5;
    public static final int UMA_MAX = 8;

    /** The maximum age in milliseconds of a location that we'll send in an X-Geo header. */
    private static final int MAX_LOCATION_AGE = 24 * 60 * 60 * 1000;  // 24 hours

    /** The maximum age in milliseconds of a location before we'll request a refresh. */
    private static final int REFRESH_LOCATION_AGE = 5 * 60 * 1000;  // 5 minutes

    private static final String HTTPS_SCHEME = "https";

    /**
     * Requests a location refresh so that a valid location will be available for constructing
     * an X-Geo header in the near future (i.e. within 5 minutes).
     *
     * @param context The Context used to get the device location.
     */
    public static void primeLocationForGeoHeader(Context context) {
        if (!hasGeolocationPermission(context)) return;

        GeolocationTracker.refreshLastKnownLocation(context, REFRESH_LOCATION_AGE);
    }

    /**
     * Returns whether the X-Geo header is allowed to be sent for the current URL.
     *
     * @param context The Context used to get the device location.
     * @param url The URL of the request with which this header will be sent.
     * @param isIncognito Whether the request will happen in an incognito tab.
     */
    public static boolean isGeoHeaderEnabledForUrl(Context context, String url,
            boolean isIncognito) {
        return isGeoHeaderEnabledForUrl(context, url, isIncognito, false);
    }

    private static boolean isGeoHeaderEnabledForUrl(Context context, String url,
            boolean isIncognito, boolean recordUma) {
        // Only send X-Geo in normal mode.
        if (isIncognito) return false;

        // Only send X-Geo header to Google domains.
        if (!UrlUtilities.nativeIsGoogleSearchUrl(url)) return false;

        Uri uri = Uri.parse(url);
        if (!HTTPS_SCHEME.equals(uri.getScheme())) return false;

        if (!hasGeolocationPermission(context)) {
            if (recordUma) recordHistogram(UMA_LOCATION_DISABLED_FOR_CHROME_APP);
            return false;
        }

        // Only send X-Geo header if the user hasn't disabled geolocation for url.
        if (isLocationDisabledForUrl(uri, isIncognito)) {
            if (recordUma) recordHistogram(UMA_LOCATION_DISABLED_FOR_GOOGLE_DOMAIN);
            return false;
        }

        return true;
    }

    /**
     * Returns an X-Geo HTTP header string if:
     *  1. The current mode is not incognito.
     *  2. The url is a google search URL (e.g. www.google.co.uk/search?q=cars), and
     *  3. The user has not disabled sharing location with this url, and
     *  4. There is a valid and recent location available.
     *
     * Returns null otherwise.
     *
     * @param context The Context used to get the device location.
     * @param url The URL of the request with which this header will be sent.
     * @param isIncognito Whether the request will happen in an incognito tab.
     * @return The X-Geo header string or null.
     */
    public static String getGeoHeader(Context context, String url, boolean isIncognito) {
        if (!isGeoHeaderEnabledForUrl(context, url, isIncognito, true)) {
            return null;
        }

        // Only send X-Geo header if there's a fresh location available.
        Location location = GeolocationTracker.getLastKnownLocation(context);
        if (location == null) {
            recordHistogram(UMA_LOCATION_NOT_AVAILABLE);
            return null;
        }
        if (GeolocationTracker.getLocationAge(location) > MAX_LOCATION_AGE) {
            recordHistogram(UMA_LOCATION_STALE);
            return null;
        }

        recordHistogram(UMA_HEADER_SENT);

        // Timestamp in microseconds since the UNIX epoch.
        long timestamp = location.getTime() * 1000;
        // Latitude times 1e7.
        int latitude = (int) (location.getLatitude() * 10000000);
        // Longitude times 1e7.
        int longitude = (int) (location.getLongitude() * 10000000);
        // Radius of 68% accuracy in mm.
        int radius = (int) (location.getAccuracy() * 1000);

        // Encode location using ascii protobuf format followed by base64 encoding.
        // https://goto.google.com/partner_location_proto
        String locationAscii = String.format(Locale.US,
                "role:1 producer:12 timestamp:%d latlng{latitude_e7:%d longitude_e7:%d} radius:%d",
                timestamp, latitude, longitude, radius);
        String locationBase64 = new String(Base64.encode(locationAscii.getBytes(), Base64.NO_WRAP));

        return "X-Geo: a " + locationBase64;
    }

    static boolean hasGeolocationPermission(Context context) {
        return context.checkPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION, Process.myPid(), Process.myUid())
                        == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns true if the user has disabled sharing their location with url (e.g. via the
     * geolocation infobar). If the user has not chosen a preference for url and url uses the https
     * scheme, this considers the user's preference for url with the http scheme instead.
     */
    static boolean isLocationDisabledForUrl(Uri uri, boolean isIncognito) {
        GeolocationInfo locationSettings = new GeolocationInfo(uri.toString(), null, isIncognito);
        ContentSetting locationPermission = locationSettings.getContentSetting();

        // If no preference has been chosen and the scheme is https, fall back to the preference for
        // this same host over http with no explicit port number.
        if (locationPermission == null || locationPermission == ContentSetting.ASK) {
            String scheme = uri.getScheme();
            if (scheme != null && scheme.toLowerCase(Locale.US).equals("https")
                    && uri.getAuthority() != null && uri.getUserInfo() == null) {
                String urlWithHttp = "http://" + uri.getHost();
                locationSettings = new GeolocationInfo(urlWithHttp, null, isIncognito);
                locationPermission = locationSettings.getContentSetting();
            }
        }

        return locationPermission == ContentSetting.BLOCK;
    }

    /** Records a data point for the Geolocation.HeaderSentOrNot histogram. */
    private static void recordHistogram(int result) {
        RecordHistogram.recordEnumeratedHistogram("Geolocation.HeaderSentOrNot", result, UMA_MAX);
    }
}
