// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.webapk.lib.common.WebApkMetaDataKeys;

/**
 * This class checks whether the WebAPK needs to be re-installed and sends a request to re-install
 * the WebAPK if it needs to be re-installed.
 */
public class ManifestUpgradeDetector implements ManifestUpgradeDetectorFetcher.Callback {
    /**
     * Called when the process of checking Web Manifest update is complete.
     */
    public interface Callback {
        public void onUpgradeNeededCheckFinished(boolean needsUpgrade, FetchedManifestData data);
    }

    private static final String TAG = "cr_UpgradeDetector";

    /**
     * Fetched Web Manifest data.
     */
    public static class FetchedManifestData {
        public String startUrl;
        public String scopeUrl;
        public String name;
        public String shortName;
        public String iconUrl;

        // Hash of untransformed icon bytes. The hash should have been taken prior to any
        // encoding/decoding.
        public String iconMurmur2Hash;

        public Bitmap icon;
        public int displayMode;
        public int orientation;
        public long themeColor;
        public long backgroundColor;
    }

    /** The WebAPK's tab. */
    private final Tab mTab;

    /**
     * Web Manifest data at time that the WebAPK was generated.
     */
    private WebappInfo mWebappInfo;
    private String mManifestUrl;
    private String mStartUrl;
    private String mIconUrl;
    private String mIconMurmur2Hash;

    /**
     * Fetches the WebAPK's Web Manifest from the web.
     */
    private ManifestUpgradeDetectorFetcher mFetcher;
    private Callback mCallback;

    /**
     * Gets the Murmur2 hash from a Bundle. Returns an empty string if the value could not be
     * parsed.
     */
    private static String getMurmur2HashFromBundle(Bundle bundle) {
        String value = bundle.getString(WebApkMetaDataKeys.ICON_MURMUR2_HASH);

        // The Murmur2 hash should be terminated with 'L' to force the value to be a string.
        // According to https://developer.android.com/guide/topics/manifest/meta-data-element.html
        // numeric <meta-data> values can only be retrieved via {@link Bundle#getInt()} and
        // {@link Bundle#getFloat()}. We cannot use {@link Bundle#getFloat()} due to loss of
        // precision.
        if (value == null || !value.endsWith("L")) {
            return "";
        }
        return value.substring(0, value.length() - 1);
    }

    /**
     * Creates an instance of {@link ManifestUpgradeDetector}.
     *
     * @param tab WebAPK's tab.
     * @param webappInfo Parameters used for generating the WebAPK. Extracted from WebAPK's Android
     *                   manifest.
     * @param metadata Metadata from WebAPK's Android Manifest.
     * @param callback Called once it has been determined whether the WebAPK needs to be upgraded.
     */
    public ManifestUpgradeDetector(Tab tab, WebappInfo info, Bundle metadata, Callback callback) {
        mTab = tab;
        mWebappInfo = info;
        mCallback = callback;
        parseMetaData(metadata);
    }

    public String getManifestUrl() {
        return mManifestUrl;
    }

    public String getWebApkPackageName() {
        return mWebappInfo.webApkPackageName();
    }

    /**
     * Starts fetching the web manifest resources.
     */
    public boolean start() {
        if (mFetcher != null) return false;

        if (TextUtils.isEmpty(mManifestUrl)) {
            return false;
        }

        mFetcher = createFetcher(mTab, mWebappInfo.scopeUri().toString(), mManifestUrl);
        mFetcher.start(this);
        return true;
    }

    /**
     * Creates ManifestUpgradeDataFetcher.
     */
    protected ManifestUpgradeDetectorFetcher createFetcher(Tab tab, String scopeUrl,
            String manifestUrl) {
        return new ManifestUpgradeDetectorFetcher(tab, scopeUrl, manifestUrl);
    }

    private void parseMetaData(Bundle metadata) {
        mManifestUrl = IntentUtils.safeGetString(metadata, WebApkMetaDataKeys.WEB_MANIFEST_URL);
        mStartUrl = IntentUtils.safeGetString(metadata, WebApkMetaDataKeys.START_URL);
        mIconUrl = IntentUtils.safeGetString(metadata, WebApkMetaDataKeys.ICON_URL);
        mIconMurmur2Hash = getMurmur2HashFromBundle(metadata);
    }

    /**
     * Puts the object in a state where it is safe to be destroyed.
     */
    public void destroy() {
        if (mFetcher != null) {
            mFetcher.destroy();
        }
        mFetcher = null;
    }

    /**
     * Called when the updated Web Manifest has been fetched.
     */
    @Override
    public void onGotManifestData(String startUrl, String scopeUrl, String name, String shortName,
            String iconUrl, String iconMurmur2Hash, Bitmap iconBitmap, int displayMode,
            int orientation, long themeColor, long backgroundColor) {
        mFetcher.destroy();
        mFetcher = null;

        if (TextUtils.isEmpty(scopeUrl)) {
            scopeUrl = ShortcutHelper.getScopeFromUrl(startUrl);
        }

        FetchedManifestData fetchedData = new FetchedManifestData();
        fetchedData.startUrl = startUrl;
        fetchedData.scopeUrl = scopeUrl;
        fetchedData.name = name;
        fetchedData.shortName = shortName;
        fetchedData.iconUrl = iconUrl;
        fetchedData.iconMurmur2Hash = iconMurmur2Hash;
        fetchedData.icon = iconBitmap;
        fetchedData.displayMode = displayMode;
        fetchedData.orientation = orientation;
        fetchedData.themeColor = themeColor;
        fetchedData.backgroundColor = backgroundColor;

        // TODO(hanxi): crbug.com/627824. Validate whether the new fetched data is
        // WebAPK-compatible.
        boolean upgrade = needsUpgrade(fetchedData);
        mCallback.onUpgradeNeededCheckFinished(upgrade, fetchedData);
    }

    /**
     * Checks whether the WebAPK needs to be upgraded provided the fetched manifest data.
     */
    private boolean needsUpgrade(FetchedManifestData fetchedData) {
        if (!urlsMatchIgnoringFragments(mIconUrl, fetchedData.iconUrl)
                || !mIconMurmur2Hash.equals(fetchedData.iconMurmur2Hash)) {
            return true;
        }

        if (!urlsMatchIgnoringFragments(mWebappInfo.scopeUri().toString(), fetchedData.scopeUrl)) {
            // Sometimes the scope doesn't match due to a missing "/" at the end of the scope URL.
            // Print log to find such cases.
            Log.d(TAG, "Needs to request update since the scope from WebappInfo (%s) doesn't match"
                    + "the one fetched from Web Manifest(%s).", mWebappInfo.scopeUri().toString(),
                    fetchedData.scopeUrl);
            return true;
        }

        if (!urlsMatchIgnoringFragments(mStartUrl, fetchedData.startUrl)
                || !TextUtils.equals(mWebappInfo.shortName(), fetchedData.shortName)
                || !TextUtils.equals(mWebappInfo.name(), fetchedData.name)
                || mWebappInfo.backgroundColor() != fetchedData.backgroundColor
                || mWebappInfo.themeColor() != fetchedData.themeColor
                || mWebappInfo.orientation() != fetchedData.orientation
                || mWebappInfo.displayMode() != fetchedData.displayMode) {
            return true;
        }

        return false;
    }

    /**
     * Returns whether the urls match ignoring fragments. Canonicalizes the URLs prior to doing the
     * comparison.
     */
    protected boolean urlsMatchIgnoringFragments(String url1, String url2) {
        return UrlUtilities.urlsMatchIgnoringFragments(url1, url2);
    }
}
