// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.physicalweb;

import org.chromium.base.ThreadUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * This class represents an HTTP request.
 * This is to be used as a base class for more specific request classes.
 * @param <T> The type representing the request payload.
 */
abstract class HttpRequest<T> implements Runnable {
    private final URL mUrl;
    private final HttpRequestCallback<T> mCallback;

    /**
     * Construct a Request object.
     * @param url The URL to make an HTTP request to.
     * @param callback The callback run when the HTTP response is received.
     *     The callback will be run on the main thread.
     * @throws MalformedURLException on invalid url
     */
    public HttpRequest(String url, HttpRequestCallback<T> callback) throws MalformedURLException {
        mUrl = new URL(url);
        if (!mUrl.getProtocol().equals("http") && !mUrl.getProtocol().equals("https")) {
            throw new MalformedURLException("This is not a http or https URL: " + url);
        }
        mCallback = callback;
    }

    /**
     * The callback that gets run after the request is made.
     */
    public interface HttpRequestCallback<T> {
        /**
         * The callback run on a valid response.
         * @param result The result object.
         */
        void onResponse(T result);

        /**
         * The callback run on an Exception.
         * @param httpResponseCode The HTTP response code.  This will be 0 if no
         *        response was received.
         * @param e The encountered Exception.
         */
        void onError(int httpResponseCode, Exception e);
    }

    /**
     * Make the HTTP request and parse the HTTP response.
     */
    @Override
    public void run() {
        // Setup some values
        HttpURLConnection urlConnection = null;
        T result = null;
        InputStream inputStream = null;
        int responseCode = 0;
        IOException ioException = null;

        // Make the request
        try {
            urlConnection = (HttpURLConnection) mUrl.openConnection();
            writeToUrlConnection(urlConnection);
            responseCode = urlConnection.getResponseCode();
            inputStream = new BufferedInputStream(urlConnection.getInputStream());
            result = readInputStream(inputStream);
        } catch (IOException e) {
            ioException = e;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        // Invoke the callback on the main thread.
        final Exception finalException = ioException;
        final T finalResult = result;
        final int finalResponseCode = responseCode;
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (finalException == null) {
                    mCallback.onResponse(finalResult);
                } else {
                    mCallback.onError(finalResponseCode, finalException);
                }
            }
        });
    }

    /**
     * Helper method to make an HTTP request.
     * @param urlConnection The HTTP connection.
     * @throws IOException on error
     */
    protected abstract void writeToUrlConnection(HttpURLConnection urlConnection)
            throws IOException;

    /**
     * Helper method to read an HTTP response.
     * @param is The InputStream.
     * @return An object representing the HTTP response.
     * @throws IOException on error
     */
    protected abstract T readInputStream(InputStream is) throws IOException;
}
