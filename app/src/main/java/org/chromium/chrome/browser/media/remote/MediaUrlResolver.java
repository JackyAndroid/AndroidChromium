// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import org.chromium.base.CommandLine;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeSwitches;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.List;
import java.util.Map;

/**
 * Resolves the final URL if it's a redirect. Works asynchronously, uses HTTP
 * HEAD request to determine if the URL is redirected.
 */
public class MediaUrlResolver extends AsyncTask<Void, Void, MediaUrlResolver.Result> {

    // Cast.Sender.UrlResolveResult UMA histogram values; must match values of
    // RemotePlaybackUrlResolveResult in histograms.xml. Do not change these values, as they are
    // being used in UMA.
    private static final int RESOLVE_SUCCESSFUL = 0;
    private static final int MALFORMED_URL = 1;
    private static final int NO_CORS = 2;
    private static final int INCOMPATIBLE_CORS = 3;
    // Range of histogram.
    private static final int HISTOGRAM_RESULT_COUNT = 4;

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
        void setUri(Uri uri, boolean palyable);
    }


    protected static final class Result {
        private final String mUri;
        private final boolean mPlayable;

        public Result(String uri, boolean playable) {
            mUri = uri;
            mPlayable = playable;
        }

        public String getUri() {
            return mUri;
        }

        public  boolean isPlayable() {
            return mPlayable;
        }
    }

    private static final String TAG = "MediaUrlResolver";

    private static final String COOKIES_HEADER_NAME = "Cookies";
    private static final String USER_AGENT_HEADER_NAME = "User-Agent";
    private static final String ORIGIN_HEADER_NAME = "Origin";
    private static final String RANGE_HEADER_NAME = "Range";
    private static final String CORS_HEADER_NAME = "Access-Control-Allow-Origin";

    private static final String CHROMECAST_ORIGIN = "https://www.gstatic.com";

    // Media types supported for cast, see
    // media/base/container_names.h for the actual enum where these are defined
    private static final int UNKNOWN_MEDIA = 0;
    private static final int SMOOTHSTREAM_MEDIA = 39;
    private static final int DASH_MEDIA = 38;
    private static final int HLS_MEDIA = 22;
    private static final int MPEG4_MEDIA = 29;

    // We don't want to necessarily fetch the whole video but we don't want to miss the CORS header.
    // Assume that 64k should be more than enough to keep all the headers.
    private static final String RANGE_HEADER_VALUE = "bytes: 0-65536";

    private final Delegate mDelegate;
    private final boolean mDebug;

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
        mDebug = CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CAST_DEBUG_LOGS);
        mDelegate = delegate;
        mUserAgent = userAgent;
        mStreamHandler = streamHandler;
    }

    @Override
    protected MediaUrlResolver.Result doInBackground(Void... params) {
        Uri uri = mDelegate.getUri();
        if (uri == null) {
            return new MediaUrlResolver.Result("", false);
        }
        String url = uri.toString();
        String cookies = mDelegate.getCookies();
        // URL may already be partially percent encoded; double percent encoding will break
        // things, so decode it before sanitizing it.
        String sanitizedUrl = sanitizeUrl(Uri.decode(url));
        Map<String, List<String>> headers = null;

        // If we failed to sanitize the URL (e.g. because the host name contains underscores) then
        // HttpURLConnection won't work,so we can't follow redirections. Just try to use it as is.
        // TODO (aberent): Find out if there is a way of following redirections that is not so
        // strict on the URL format.
        if (!sanitizedUrl.equals("")) {
            HttpURLConnection urlConnection = null;
            try {
                URL requestUrl = new URL(null, sanitizedUrl, mStreamHandler);
                urlConnection = (HttpURLConnection) requestUrl.openConnection();
                if (!TextUtils.isEmpty(cookies)) {
                    urlConnection.setRequestProperty(COOKIES_HEADER_NAME, cookies);
                }
                // Pretend that this is coming from the Chromecast.
                urlConnection.setRequestProperty(ORIGIN_HEADER_NAME, CHROMECAST_ORIGIN);
                urlConnection.setRequestProperty(USER_AGENT_HEADER_NAME, mUserAgent);
                urlConnection.setRequestProperty(RANGE_HEADER_NAME, RANGE_HEADER_VALUE);

                // This triggers resolving the URL and receiving the headers.
                headers = urlConnection.getHeaderFields();

                url = urlConnection.getURL().toString();
            } catch (IOException e) {
                Log.e(TAG, "Failed to fetch the final URI", e);
                url = "";
            }
            if (urlConnection != null) urlConnection.disconnect();
        }
        return new MediaUrlResolver.Result(url, canPlayMedia(url, headers));
    }

    @Override
    protected void onPostExecute(MediaUrlResolver.Result result) {
        String url = result.getUri();
        Uri uri = "".equals(url) ? Uri.EMPTY : Uri.parse(url);
        mDelegate.setUri(uri, result.isPlayable());
    }

    private String sanitizeUrl(String unsafeUrl) {
        URL url;
        URI uri;
        try {
            url = new URL(unsafeUrl);
            uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(),
                    url.getPath(), url.getQuery(), url.getRef());
            return uri.toURL().toString();
        } catch (URISyntaxException syntaxException) {
            Log.w(TAG, "URISyntaxException " + syntaxException);
        } catch (MalformedURLException malformedUrlException) {
            Log.w(TAG, "MalformedURLException " + malformedUrlException);
        }
        return "";
    }

    private boolean canPlayMedia(String url, Map<String, List<String>> headers) {
        if (url.isEmpty()) {
            recordResultHistogram(MALFORMED_URL);
            return false;
        }

        if (headers != null && headers.containsKey(CORS_HEADER_NAME)) {
            // Check that the CORS data is valid for Chromecast
            List<String> corsData = headers.get(CORS_HEADER_NAME);
            if (corsData.isEmpty() || (!corsData.get(0).equals("*")
                    && !corsData.get(0).equals(CHROMECAST_ORIGIN))) {
                recordResultHistogram(INCOMPATIBLE_CORS);
                return false;
            }
        } else if (isEnhancedMedia(url)) {
            // HLS media requires Cors headers.
            if (mDebug) Log.d(TAG, "HLS stream without CORS header: " + url);
            recordResultHistogram(NO_CORS);
            return false;
        }
        // TODO(aberent) Return false for media types that are not playable on Chromecast
        // (getMediaType would need to know about more types to implement this).
        recordResultHistogram(RESOLVE_SUCCESSFUL);
        return true;
    }

    private boolean isEnhancedMedia(String url) {
        int mediaType = getMediaType(url);
        return mediaType == HLS_MEDIA || mediaType == DASH_MEDIA || mediaType == SMOOTHSTREAM_MEDIA;
    }

    @VisibleForTesting
    void recordResultHistogram(int result) {
        RecordHistogram.recordEnumeratedHistogram("Cast.Sender.UrlResolveResult", result,
                HISTOGRAM_RESULT_COUNT);
    }

    static int getMediaType(String url) {
        if (url.contains(".m3u8")) return HLS_MEDIA;
        if (url.contains(".mp4")) return MPEG4_MEDIA;
        if (url.contains(".mpd")) return DASH_MEDIA;
        if (url.contains(".ism")) return SMOOTHSTREAM_MEDIA;
        return UNKNOWN_MEDIA;
    }
}