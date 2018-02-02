// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the final URL if it's a redirect. Works asynchronously, uses HTTP
 * HEAD request to determine if the URL is redirected.
 */
public class MediaUrlResolver extends AsyncTask<Void, Void, MediaUrlResolver.Result> {

    // Cast.Sender.UrlResolveResult UMA histogram values; must match values of
    // RemotePlaybackUrlResolveResult in histograms.xml. Do not change these values, as they are
    // being used in UMA.
    private static final int RESOLVE_RESULT_SUCCESS = 0;
    private static final int RESOLVE_RESULT_MALFORMED_URL = 1;
    private static final int RESOLVE_RESULT_NO_CORS = 2;
    private static final int RESOLVE_RESULT_INCOMPATIBLE_CORS = 3;
    private static final int RESOLVE_RESULT_SERVER_ERROR = 4;
    private static final int RESOLVE_RESULT_NETWORK_ERROR = 5;
    private static final int RESOLVE_RESULT_UNSUPPORTED_MEDIA = 6;

    // Range of histogram.
    private static final int HISTOGRAM_RESULT_COUNT = 7;

    // Acceptal response codes for URL resolving request.
    private static final Integer[] SUCCESS_RESPONSE_CODES = {
        // Request succeeded.
        HttpURLConnection.HTTP_OK,
        HttpURLConnection.HTTP_PARTIAL,

        // HttpURLConnection only follows up to 5 redirects, this response is unlikely but possible.
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
    };

    /**
     * The interface to get the initial URI with cookies from and pass the final
     * URI to.
     */
    public interface Delegate {
        /**
         * @return the original URL to resolve.
         */
        Uri getUri();

        /**
         * @return the cookies to fetch the URL with.
         */
        String getCookies();

        /**
         * Passes the resolved URL to the delegate.
         *
         * @param uri the resolved URL.
         */
        void deliverResult(Uri uri, boolean palyable);
    }


    protected static final class Result {
        private final Uri mUri;
        private final boolean mPlayable;

        public Result(Uri uri, boolean playable) {
            mUri = uri;
            mPlayable = playable;
        }

        public Uri getUri() {
            return mUri;
        }

        public boolean isPlayable() {
            return mPlayable;
        }
    }

    private static final String TAG = "MediaFling";

    private static final String COOKIES_HEADER_NAME = "Cookies";
    private static final String USER_AGENT_HEADER_NAME = "User-Agent";
    private static final String ORIGIN_HEADER_NAME = "Origin";
    private static final String RANGE_HEADER_NAME = "Range";
    private static final String CORS_HEADER_NAME = "Access-Control-Allow-Origin";

    private static final String CHROMECAST_ORIGIN = "https://www.gstatic.com";

    // Media types supported for cast, see
    // media/base/container_names.h for the actual enum where these are defined.
    // See https://developers.google.com/cast/docs/media#media-container-formats for the formats
    // supported by Cast devices.
    private static final int MEDIA_TYPE_UNKNOWN = 0;
    private static final int MEDIA_TYPE_AAC = 1;
    private static final int MEDIA_TYPE_HLS = 22;
    private static final int MEDIA_TYPE_MP3 = 26;
    private static final int MEDIA_TYPE_MPEG4 = 29;
    private static final int MEDIA_TYPE_OGG = 30;
    private static final int MEDIA_TYPE_WAV = 35;
    private static final int MEDIA_TYPE_WEBM = 36;
    private static final int MEDIA_TYPE_DASH = 38;
    private static final int MEDIA_TYPE_SMOOTHSTREAM = 39;

    // We don't want to necessarily fetch the whole video but we don't want to miss the CORS header.
    // Assume that 64k should be more than enough to keep all the headers.
    private static final String RANGE_HEADER_VALUE = "bytes=0-65536";

    private final Delegate mDelegate;

    private final String mUserAgent;
    private final URLStreamHandler mStreamHandler;

    /**
     * The constructor
     * @param delegate The customer for this URL resolver.
     * @param userAgent The browser user agent
     */
    public MediaUrlResolver(Delegate delegate, String userAgent) {
        this(delegate, userAgent, null);
    }

    @VisibleForTesting
    MediaUrlResolver(Delegate delegate, String userAgent, URLStreamHandler streamHandler) {
        mDelegate = delegate;
        mUserAgent = userAgent;
        mStreamHandler = streamHandler;
    }

    @Override
    protected MediaUrlResolver.Result doInBackground(Void... params) {
        Uri uri = mDelegate.getUri();
        if (uri == null || uri.equals(Uri.EMPTY)) {
            return new MediaUrlResolver.Result(Uri.EMPTY, false);
        }
        String cookies = mDelegate.getCookies();

        Map<String, List<String>> headers = null;
        HttpURLConnection urlConnection = null;
        try {
            URL requestUrl = new URL(null, uri.toString(), mStreamHandler);
            urlConnection = (HttpURLConnection) requestUrl.openConnection();
            if (!TextUtils.isEmpty(cookies)) {
                urlConnection.setRequestProperty(COOKIES_HEADER_NAME, cookies);
            }

            // Pretend that this is coming from the Chromecast.
            urlConnection.setRequestProperty(ORIGIN_HEADER_NAME, CHROMECAST_ORIGIN);
            urlConnection.setRequestProperty(USER_AGENT_HEADER_NAME, mUserAgent);
            if (!isEnhancedMedia(uri)) {
                // Manifest files are typically smaller than 64K so range request can fail.
                urlConnection.setRequestProperty(RANGE_HEADER_NAME, RANGE_HEADER_VALUE);
            }

            // This triggers resolving the URL and receiving the headers.
            headers = urlConnection.getHeaderFields();

            uri = Uri.parse(urlConnection.getURL().toString());

            // If server's response is not valid, don't try to fling the video.
            int responseCode = urlConnection.getResponseCode();
            if (!Arrays.asList(SUCCESS_RESPONSE_CODES).contains(responseCode)) {
                recordResultHistogram(RESOLVE_RESULT_SERVER_ERROR);
                Log.e(TAG, "Server response is not valid: %d", responseCode);
                uri = Uri.EMPTY;
            }
        } catch (IOException e) {
            recordResultHistogram(RESOLVE_RESULT_NETWORK_ERROR);
            Log.e(TAG, "Failed to fetch the final url", e);
            uri = Uri.EMPTY;
        }
        if (urlConnection != null) urlConnection.disconnect();
        return new MediaUrlResolver.Result(uri, canPlayMedia(uri, headers));
    }

    @Override
    protected void onPostExecute(MediaUrlResolver.Result result) {
        mDelegate.deliverResult(result.getUri(), result.isPlayable());
    }

    private boolean canPlayMedia(Uri uri, Map<String, List<String>> headers) {
        if (uri == null || uri.equals(Uri.EMPTY)) {
            recordResultHistogram(RESOLVE_RESULT_MALFORMED_URL);
            return false;
        }

        if (headers != null && headers.containsKey(CORS_HEADER_NAME)) {
            // Check that the CORS data is valid for Chromecast
            List<String> corsData = headers.get(CORS_HEADER_NAME);
            if (corsData.isEmpty() || (!corsData.get(0).equals("*")
                    && !corsData.get(0).equals(CHROMECAST_ORIGIN))) {
                recordResultHistogram(RESOLVE_RESULT_INCOMPATIBLE_CORS);
                return false;
            }
        } else if (isEnhancedMedia(uri)) {
            // HLS media requires CORS headers.
            // TODO(avayvod): it actually requires CORS on the final video URLs vs the manifest.
            // Clank assumes that if CORS is set for the manifest it's set for everything but
            // it not necessary always true. See b/19138712
            Log.d(TAG, "HLS stream without CORS header: %s", uri);
            recordResultHistogram(RESOLVE_RESULT_NO_CORS);
            return false;
        }

        if (getMediaType(uri) == MEDIA_TYPE_UNKNOWN) {
            Log.d(TAG, "Unsupported media container format: %s", uri);
            recordResultHistogram(RESOLVE_RESULT_UNSUPPORTED_MEDIA);
            return false;
        }

        recordResultHistogram(RESOLVE_RESULT_SUCCESS);
        return true;
    }

    private boolean isEnhancedMedia(Uri uri) {
        int mediaType = getMediaType(uri);
        return mediaType == MEDIA_TYPE_HLS
                || mediaType == MEDIA_TYPE_DASH
                || mediaType == MEDIA_TYPE_SMOOTHSTREAM;
    }

    @VisibleForTesting
    void recordResultHistogram(int result) {
        RecordHistogram.recordEnumeratedHistogram("Cast.Sender.UrlResolveResult", result,
                HISTOGRAM_RESULT_COUNT);
    }

    static int getMediaType(Uri uri) {
        String path = uri.getPath().toLowerCase(Locale.US);
        if (path.endsWith(".m3u8")) return MEDIA_TYPE_HLS;
        if (path.endsWith(".mp4")) return MEDIA_TYPE_MPEG4;
        if (path.endsWith(".mpd")) return MEDIA_TYPE_DASH;
        if (path.endsWith(".ism")) return MEDIA_TYPE_SMOOTHSTREAM;
        if (path.endsWith(".m4a") || path.endsWith(".aac")) return MEDIA_TYPE_AAC;
        if (path.endsWith(".mp3")) return MEDIA_TYPE_MP3;
        if (path.endsWith(".wav")) return MEDIA_TYPE_WAV;
        if (path.endsWith(".webm")) return MEDIA_TYPE_WEBM;
        if (path.endsWith(".ogg")) return MEDIA_TYPE_OGG;
        return MEDIA_TYPE_UNKNOWN;
    }
}
