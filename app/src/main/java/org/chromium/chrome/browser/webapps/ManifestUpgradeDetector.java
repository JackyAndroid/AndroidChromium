// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.graphics.Bitmap;
import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.UrlUtilities;

/**
 * This class checks whether the WebAPK needs to be re-installed and sends a request to re-install
 * the WebAPK if it needs to be re-installed.
 */
public class ManifestUpgradeDetector implements ManifestUpgradeDetectorFetcher.Callback {
    /** ManifestUpgradeDetector callback. */
    public interface Callback {
        /**
         * Called when the Web Manifest for the initial URL load has been fetched (successfully or
         * unsuccessfully).
         * TODO(pkotwicz): Add calls to {@link #onFinishedFetchingWebManifestForInitialUrl()}.
         * @param needsUpgrade Whether the WebAPK should be updated because the Web Manifest has
         *                     changed. False if the Web Manifest could not be fetched.
         * @param data         The fetched Web Manifest data. Null if the initial URL does not point
         *                     to a Web Manifest.
         */
        void onFinishedFetchingWebManifestForInitialUrl(
                boolean needsUpgrade, FetchedManifestData data);

        /**
         * Called when the Web Manifest has been successfully fetched (including on the initial URL
         * load).
         * @param needsUpgrade Whether the WebAPK should be updated because the Web Manifest has
         *        changed.
         * @param data The fetched Web Manifest data.
         */
        void onGotManifestData(boolean needsUpgrade, FetchedManifestData data);
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
    private WebApkMetaData mMetaData;

    /**
     * Fetches the WebAPK's Web Manifest from the web.
     */
    private ManifestUpgradeDetectorFetcher mFetcher;
    private Callback mCallback;

    /**
     * Creates an instance of {@link ManifestUpgradeDetector}.
     *
     * @param tab WebAPK's tab.
     * @param webappInfo Parameters used for generating the WebAPK. Extracted from WebAPK's Android
     *                   manifest.
     * @param metadata Metadata from WebAPK's Android Manifest.
     * @param callback Called once it has been determined whether the WebAPK needs to be upgraded.
     */
    public ManifestUpgradeDetector(Tab tab, WebApkMetaData metaData, Callback callback) {
        mTab = tab;
        mMetaData = metaData;
        mCallback = callback;
    }

    /**
     * Starts fetching the web manifest resources.
     */
    public boolean start() {
        if (mFetcher != null) return false;

        if (TextUtils.isEmpty(mMetaData.manifestUrl)) {
            return false;
        }

        mFetcher = createFetcher(mTab, mMetaData.scope, mMetaData.manifestUrl);
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
        mCallback.onGotManifestData(upgrade, fetchedData);
    }

    /**
     * Checks whether the WebAPK needs to be upgraded provided the fetched manifest data.
     */
    private boolean needsUpgrade(FetchedManifestData fetchedData) {
        if (!urlsMatchIgnoringFragments(mMetaData.iconUrl, fetchedData.iconUrl)
                || !mMetaData.iconMurmur2Hash.equals(fetchedData.iconMurmur2Hash)) {
            return true;
        }

        if (!urlsMatchIgnoringFragments(mMetaData.scope, fetchedData.scopeUrl)) {
            // Sometimes the scope doesn't match due to a missing "/" at the end of the scope URL.
            // Print log to find such cases.
            Log.d(TAG, "Needs to request update since the scope from WebappInfo (%s) doesn't match"
                    + "the one fetched from Web Manifest(%s).", mMetaData.scope,
                    fetchedData.scopeUrl);
            return true;
        }

        if (!urlsMatchIgnoringFragments(mMetaData.startUrl, fetchedData.startUrl)
                || !TextUtils.equals(mMetaData.shortName, fetchedData.shortName)
                || !TextUtils.equals(mMetaData.name, fetchedData.name)
                || mMetaData.backgroundColor != fetchedData.backgroundColor
                || mMetaData.themeColor != fetchedData.themeColor
                || mMetaData.orientation != fetchedData.orientation
                || mMetaData.displayMode != fetchedData.displayMode) {
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
