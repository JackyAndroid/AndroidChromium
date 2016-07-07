// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * This class sends requests to the Physical Web Service.
 */
class PwsClient {
    private static final String TAG = "PhysicalWeb";
    private static final String ENDPOINT_URL = "https://url-caster.appspot.com/resolve-scan";

    /**
     * Callback that is run after the PWS sends a response to a resolve-scan request.
     */
    public interface ResolveScanCallback {
        /**
         * Handle newly returned PwsResults.
         * @param pwsResults The results returned by the PWS.
         */
        public void onPwsResults(Collection<PwsResult> pwsResults);
    }

    /**
     * Callback that is run after receiving the response to an icon fetch request.
     */
    public interface FetchIconCallback {
        /**
         * Handle newly returned favicon Bitmaps.
         * @param iconUrl The favicon URL.
         * @param iconBitmap The icon image data.
         */
        public void onIconReceived(String iconUrl, Bitmap iconBitmap);
    }

    private static JSONObject createResolveScanPayload(Collection<String> urls)
            throws JSONException {
        // Encode the urls.
        JSONArray objects = new JSONArray();
        for (String url : urls) {
            JSONObject obj = new JSONObject();
            obj.put("url", url);
            objects.put(obj);
        }

        // Organize the data into a single object.
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("objects", objects);
        return jsonObject;
    }

    private static Collection<PwsResult> parseResolveScanResponse(JSONObject result) {
        // Get the metadata array.
        Collection<PwsResult> pwsResults = new ArrayList<>();
        JSONArray metadata;
        try {
            metadata = result.getJSONArray("metadata");
        } catch (JSONException e) {
            Log.e(TAG, "PWS returned invalid metadata", e);
            return pwsResults;
        }

        // Loop through the metadata for each url.
        for (int i = 0; i < metadata.length(); i++) {
            try {
                JSONObject obj = metadata.getJSONObject(i);
                String requestUrl = obj.getString("id");
                String siteUrl = obj.getString("url");
                String iconUrl = obj.optString("icon", null);
                String title = obj.optString("title", "");
                String description = obj.optString("description", "");
                pwsResults.add(new PwsResult(requestUrl, siteUrl, iconUrl, title, description));
            } catch (JSONException e) {
                Log.e(TAG, "PWS returned invalid metadata", e);
                continue;
            }
        }
        return pwsResults;
    }

    /**
     * Send an HTTP request to the PWS to resolve a set of URLs.
     * @param broadcastUrls The URLs to resolve.
     * @param resolveScanCallback The callback to be run when the response is received.
     */
    public void resolve(final Collection<String> broadcastUrls,
            final ResolveScanCallback resolveScanCallback) {
        // Create the response callback.
        JsonObjectHttpRequest.RequestCallback requestCallback =
                new JsonObjectHttpRequest.RequestCallback() {
            @Override
            public void onResponse(JSONObject result) {
                ThreadUtils.assertOnUiThread();
                Collection<PwsResult> pwsResults = parseResolveScanResponse(result);
                resolveScanCallback.onPwsResults(pwsResults);
            }

            @Override
            public void onError(int responseCode, Exception e) {
                ThreadUtils.assertOnUiThread();
                String httpErr = "";
                if (responseCode > 0) {
                    httpErr = ", HTTP " + responseCode;
                }
                Log.e(TAG, "Error making request to PWS%s", httpErr, e);
                resolveScanCallback.onPwsResults(new ArrayList<PwsResult>());
            }
        };

        // Create the request.
        HttpRequest request = null;
        try {
            JSONObject payload = createResolveScanPayload(broadcastUrls);
            request = new JsonObjectHttpRequest(ENDPOINT_URL, payload, requestCallback);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Error creating PWS HTTP request", e);
            return;
        } catch (JSONException e) {
            Log.e(TAG, "Error creating PWS JSON payload", e);
            return;
        }
        // The callback will be called on the main thread.
        AsyncTask.THREAD_POOL_EXECUTOR.execute(request);
    }

    /**
     * Send an HTTP request to fetch a favicon.
     * @param iconUrl The URL of the favicon.
     * @param fetchIconCallback The callback to be run when the icon is received.
     */
    public void fetchIcon(final String iconUrl,
            final FetchIconCallback fetchIconCallback) {
        // Create the response callback.
        BitmapHttpRequest.RequestCallback requestCallback =
                new BitmapHttpRequest.RequestCallback() {
            @Override
            public void onResponse(Bitmap iconBitmap) {
                fetchIconCallback.onIconReceived(iconUrl, iconBitmap);
            }

            @Override
            public void onError(int responseCode, Exception e) {
                ThreadUtils.assertOnUiThread();
                String httpErr = "";
                if (responseCode > 0) {
                    httpErr = ", HTTP " + responseCode;
                }
                Log.e(TAG, "Error requesting icon%s", httpErr, e);
            }
        };

        // Create the request.
        BitmapHttpRequest request = null;
        try {
            request = new BitmapHttpRequest(iconUrl, requestCallback);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Error creating icon request", e);
            return;
        }
        // The callback will be called on the main thread.
        AsyncTask.THREAD_POOL_EXECUTOR.execute(request);
    }
}
