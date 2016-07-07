// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.physicalweb;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

/**
 * A class that represents an HTTP request for an image.
 * The response is a Bitmap.
 */
class BitmapHttpRequest extends HttpRequest<Bitmap> {
    /**
     * Construct a bitmap HTTP request.
     * @param url The url to make this HTTP request to.
     * @param callback The callback run when the HTTP response is received.
     *     The callback can be called with a null bitmap if the image
     *     couldn't be decoded.
     * @throws MalformedURLException on invalid url
     */
    public BitmapHttpRequest(String url, RequestCallback callback)
            throws MalformedURLException {
        super(url, callback);
    }

    /**
     * The callback that gets run after the request is made.
     */
    public interface RequestCallback extends HttpRequest.HttpRequestCallback<Bitmap> {}

    /**
     * Helper method to make an HTTP request.
     * @param urlConnection The HTTP connection.
     */
    public void writeToUrlConnection(HttpURLConnection urlConnection) throws IOException {}

    /**
     * Helper method to read an HTTP response.
     * @param is The InputStream.
     * @return The decoded image.
     */
    protected Bitmap readInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        byte[] bitmapData = os.toByteArray();
        return BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length);
    }
}

