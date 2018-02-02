// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.LocaleUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.GoogleAPIKeys;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.physicalweb.PwsClient.FetchIconCallback;
import org.chromium.chrome.browser.physicalweb.PwsClient.ResolveScanCallback;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Locale;

/**
 * This class sends requests to the Physical Web Service.
 */
class PwsClientImpl implements PwsClient {
    private static final String TAG = "PhysicalWeb";
    private static final String ENDPOINT_URL =
            "https://physicalweb.googleapis.com/v1alpha1/urls:resolve";

    // Format strings for creating the User-Agent string. It should somewhat resemble the Chrome for
    // Android User-Agent but doesn't need to match perfectly as this value will only be seen by the
    // Physical Web metadata service and favicon fetcher.
    // The WebKit version is not accessible from here so it is reported as 0.0.
    private static final String USER_AGENT_FORMAT =
            "Mozilla/5.0 (%s) AppleWebKit/0.0 (KHTML, like Gecko) %s Safari/0.0";
    private static final String OS_INFO_FORMAT = "Linux; Android %s; %s Build/%s";
    private static final String PRODUCT_FORMAT = "Chrome/%s Mobile";

    // HTTP request header strings, lazily initialized.
    private static String sUserAgent;
    private static String sAcceptLanguage;

    // Cached locale string. When the default locale changes, recreate the Accept-Language header.
    private static String sDefaultLocale;

    // The context must be valid for as long as this client is in use, since it is used to recreate
    // the Accept-Language header when the locale changes.
    private final Context mContext;

    public PwsClientImpl(Context context) {
        mContext = context;
    }

    private String getApiKey() {
        if (ChromeVersionInfo.isStableBuild()) {
            return GoogleAPIKeys.GOOGLE_API_KEY;
        } else {
            return GoogleAPIKeys.GOOGLE_API_KEY_PHYSICAL_WEB_TEST;
        }
    }

    private static JSONObject createResolveScanPayload(Collection<UrlInfo> urls)
            throws JSONException {
        // Encode the urls.
        JSONArray objects = new JSONArray();
        for (UrlInfo urlInfo : urls) {
            JSONObject obj = new JSONObject();
            obj.put("url", urlInfo.getUrl());
            objects.put(obj);
        }

        // Organize the data into a single object.
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("urls", objects);
        return jsonObject;
    }

    private static Collection<PwsResult> parseResolveScanResponse(JSONObject result) {
        // Get the metadata array.
        Collection<PwsResult> pwsResults = new ArrayList<>();
        JSONArray metadata = result.optJSONArray("results");
        if (metadata == null) {
            // There are no valid results.
            return pwsResults;
        }

        // Loop through the metadata for each url.
        for (int i = 0; i < metadata.length(); i++) {
            try {
                pwsResults.add(PwsResult.jsonDeserialize(metadata.getJSONObject(i)));
            } catch (JSONException e) {
                Log.e(TAG, "PWS returned invalid data", e);
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
    @Override
    public void resolve(final Collection<UrlInfo> broadcastUrls,
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
                Log.e(TAG, "Error making request to PWS%s", httpErr);
                resolveScanCallback.onPwsResults(new ArrayList<PwsResult>());
            }
        };

        // Create the request.
        HttpRequest request = null;
        try {
            JSONObject payload = createResolveScanPayload(broadcastUrls);
            String url = ENDPOINT_URL + "?key=" + getApiKey();
            request = new JsonObjectHttpRequest(
                    url, getUserAgent(), updateAcceptLanguage(), payload, requestCallback);
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
    @Override
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
                Log.e(TAG, "Error requesting icon%s", httpErr);
            }
        };

        // Create the request.
        BitmapHttpRequest request = null;
        try {
            request = new BitmapHttpRequest(
                    iconUrl, getUserAgent(), updateAcceptLanguage(), requestCallback);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Error creating icon request", e);
            return;
        }
        // The callback will be called on the main thread.
        AsyncTask.THREAD_POOL_EXECUTOR.execute(request);
    }

    /**
     * Recreate the Chrome for Android User-Agent string as closely as possible without calling any
     * native code.
     * @return A User-Agent string
     */
    @VisibleForTesting
    String getUserAgent() {
        if (sUserAgent == null) {
            // Build the OS info string.
            // eg: Linux; Android 5.1.1; Nexus 4 Build/LMY48T
            String osInfo = String.format(OS_INFO_FORMAT, Build.VERSION.RELEASE, Build.MODEL,
                    Build.ID);

            // Build the product string.
            // eg: Chrome/50.0.2661.89 Mobile
            String product = String.format(PRODUCT_FORMAT, ChromeVersionInfo.getProductVersion());

            // Build the User-Agent string.
            // eg: Mozilla/5.0 (Linux; Android 5.1.1; Nexus 4 Build/LMY48T) AppleWebKit/0.0 (KHTML,
            //     like Gecko) Chrome/50.0.2661.89 Mobile Safari/0.0
            sUserAgent = String.format(USER_AGENT_FORMAT, osInfo, product);
        }
        return sUserAgent;
    }

    /**
     * Update an Accept-Language string based on the current default locales and make a string of
     * an Accept-Language header with q-values.
     * @return An Accept-Language string made of an Accept-Language header with q-values.
     */
    @VisibleForTesting
    String updateAcceptLanguage() {
        String localeString = LocaleUtils.getDefaultLocaleListString();
        if (sDefaultLocale == null || !sDefaultLocale.equals(localeString)) {
            String acceptLanguages = mContext.getResources().getString(R.string.accept_languages);
            acceptLanguages = prependToAcceptLanguagesIfNecessary(localeString, acceptLanguages);
            sAcceptLanguage = generateAcceptLanguageHeader(acceptLanguages);
            sDefaultLocale = localeString;
        }
        return sAcceptLanguage;
    }

    /**
     * Get the language code for the default locales and prepend it to the Accept-Language string
     * if it isn't already present. The logic should match PrependToAcceptLanguagesIfNecessary in
     * chrome/browser/android/preferences/pref_service_bridge.cc
     * @param locales A comma separated string that represents a list of default locales.
     * @param acceptLanguages The default language list for the language of the user's locales.
     * @return An updated language list.
     */
    @VisibleForTesting
    static String prependToAcceptLanguagesIfNecessary(String locales, String acceptLanguages) {
        String localeStrings = locales + "," + acceptLanguages;
        String[] localeList = localeStrings.split(",");

        ArrayList<Locale> uniqueList = new ArrayList<>();
        for (String localeString : localeList) {
            Locale locale = LocaleUtils.forLanguageTag(localeString);
            if (uniqueList.contains(locale) || locale.getLanguage().isEmpty()) {
                continue;
            }
            uniqueList.add(locale);
        }

        // If language is not in the accept languages list, also add language code.
        // A language code should only be inserted after the last languageTag that
        // contains that language.
        // This will work with the IDS_ACCEPT_LANGUAGE localized strings bundled
        // with Chrome but may fail on arbitrary lists of language tags due to
        // differences in case and whitespace.
        HashSet<String> seenLanguages = new HashSet<>();
        ArrayList<String> outputList = new ArrayList<>();
        for (int i = uniqueList.size() - 1; i >= 0; i--) {
            Locale localeAdd = uniqueList.get(i);
            String languageAdd = localeAdd.getLanguage();
            String countryAdd = localeAdd.getCountry();

            if (!seenLanguages.contains(languageAdd)) {
                seenLanguages.add(languageAdd);
                outputList.add(languageAdd);
            }
            if (!countryAdd.isEmpty()) {
                outputList.add(LocaleUtils.toLanguageTag(localeAdd));
            }
        }
        Collections.reverse(outputList);
        return TextUtils.join(",", outputList);
    }

    /**
     * Given a list of comma-delimited language codes in decreasing order of preference, insert
     * q-values to represent the relative quality/precedence of each language. The logic should
     * match GenerateAcceptLanguageHeader in net/http/http_util.cc.
     * @param languageList A comma-delimited list of language codes containing no whitespace.
     * @return An Accept-Language header with q-values.
     */
    @VisibleForTesting
    static String generateAcceptLanguageHeader(String languageList) {
        // We use integers for qvalue and qvalue decrement that are 10 times larger than actual
        // values to avoid a problem with comparing two floating point numbers.
        int kQvalueDecrement10 = 2;
        int qvalue10 = 10;
        String[] parts = languageList.split(",");
        Formatter langListWithQ = new Formatter();
        for (String language : parts) {
            if (qvalue10 == 10) {
                // q=1.0 is implicit
                langListWithQ.format("%s", language);
            } else {
                langListWithQ.format(",%s;q=0.%d", language, qvalue10);
            }
            // It does not make sense to have 'q=0'.
            if (qvalue10 > kQvalueDecrement10) {
                qvalue10 -= kQvalueDecrement10;
            }
        }
        return langListWithQ.toString();
    }
}
