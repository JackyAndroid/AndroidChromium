// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.physicalweb;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

/**
 * A class that represents an HTTP request for a JSON object.
 * Both the request payload and the response are JSON objects.
 */
class JsonObjectHttpRequest extends HttpRequest<JSONObject> {
    private final JSONObject mJsonObject;

    /**
     * Construct a JSON object request.
     * @param url The url to make this HTTP request to.
     * @param jsonObject The JSON payload.
     * @param callback The callback run when the HTTP response is received.
     * @throws MalformedURLException on invalid url
     */
    public JsonObjectHttpRequest(String url, JSONObject jsonObject, RequestCallback callback)
            throws MalformedURLException {
        super(url, callback);
        mJsonObject = jsonObject;
    }

    /**
     * The callback that gets run after the request is made.
     */
    public interface RequestCallback extends HttpRequest.HttpRequestCallback<JSONObject> {}

    /**
     * Helper method to make an HTTP request.
     * @param urlConnection The HTTP connection.
     */
    public void writeToUrlConnection(HttpURLConnection urlConnection) throws IOException {
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Content-Type", "application/json");
        urlConnection.setRequestProperty("Accept", "application/json");
        urlConnection.setRequestMethod("POST");
        OutputStream os = urlConnection.getOutputStream();
        os.write(mJsonObject.toString().getBytes("UTF-8"));
        os.close();
    }

    /**
     * Helper method to read an HTTP response.
     * @param is The InputStream.
     * @return An object representing the HTTP response.
     */
    protected JSONObject readInputStream(InputStream is) throws IOException {
        String jsonString = readStreamToString(is);
        JSONObject jsonObject;
        try {
            return new JSONObject(jsonString);
        } catch (JSONException error) {
            throw new IOException(error.toString());
        }
    }

    private static String readStreamToString(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        return os.toString("UTF-8");
    }
}
