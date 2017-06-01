// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.ContentResolver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.provider.Settings;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.webapk.lib.client.WebApkVersion;

import java.util.concurrent.TimeUnit;

/**
 * WebApkUpdateManager manages when to check for updates to the WebAPK's Web Manifest, and sends
 * an update request to the WebAPK Server when an update is needed.
 */
public class WebApkUpdateManager implements ManifestUpgradeDetector.Callback {
    private static final String TAG = "WebApkUpdateManager";

    /** Number of milliseconds between checks for whether the WebAPK's Web Manifest has changed. */
    public static final long FULL_CHECK_UPDATE_INTERVAL = TimeUnit.DAYS.toMillis(3L);

    /**
     * Number of milliseconds to wait before re-requesting an updated WebAPK from the WebAPK
     * server if the previous update attempt failed.
     */
    public static final long RETRY_UPDATE_DURATION = TimeUnit.HOURS.toMillis(12L);

    /** Id of WebAPK data in WebappDataStorage */
    private String mId;

    /** WebAPK package name. */
    private String mWebApkPackageName;

    /** Meta data from the WebAPK's Android Manifest */
    private WebApkMetaData mMetaData;

    /** WebAPK's icon. */
    private Bitmap mIcon;

    /**
     * Whether the previous WebAPK update succeeded. True if there has not been any update attempts.
     */
    private boolean mPreviousUpdateSucceeded;

    private ManifestUpgradeDetector mUpgradeDetector;

    /**
     * Checks whether the WebAPK's Web Manifest has changed. Requests an updated WebAPK if the
     * Web Manifest has changed. Skips the check if the check was done recently.
     * @param tab  The tab of the WebAPK.
     * @param info The WebApkInfo of the WebAPK.
     */
    public void updateIfNeeded(Tab tab, WebApkInfo info) {
        mMetaData = WebApkMetaDataUtils.extractMetaDataFromWebApk(info.webApkPackageName());
        if (mMetaData == null) return;

        mWebApkPackageName = info.webApkPackageName();
        mId = info.id();
        mIcon = info.icon();

        WebappDataStorage storage = WebappRegistry.getInstance().getWebappDataStorage(mId);
        mPreviousUpdateSucceeded = didPreviousUpdateSucceed(storage);

        if (!shouldCheckIfWebManifestUpdated(storage, mMetaData, mPreviousUpdateSucceeded)) return;

        mUpgradeDetector = buildManifestUpgradeDetector(tab, mMetaData);
        mUpgradeDetector.start();
    }

    public void destroy() {
        destroyUpgradeDetector();
    }

    @Override
    public void onFinishedFetchingWebManifestForInitialUrl(
            boolean needsUpgrade, ManifestUpgradeDetector.FetchedManifestData data) {
        onGotManifestData(needsUpgrade, data);
    }

    @Override
    public void onGotManifestData(
            boolean needsUpgrade, ManifestUpgradeDetector.FetchedManifestData data) {
        WebappDataStorage storage = WebappRegistry.getInstance().getWebappDataStorage(mId);
        storage.updateTimeOfLastCheckForUpdatedWebManifest();

        boolean gotManifest = (data != null);
        needsUpgrade |= isShellApkVersionOutOfDate(mMetaData);
        Log.v(TAG, "Got Manifest: " + gotManifest);
        Log.v(TAG, "WebAPK upgrade needed: " + needsUpgrade);

        // If the Web Manifest was not found and an upgrade is requested, stop fetching Web
        // Manifests as the user navigates to avoid sending multiple WebAPK update requests. In
        // particular:
        // - A WebAPK update request on the initial load because the Shell APK version is out of
        //   date.
        // - A second WebAPK update request once the user navigates to a page which points to the
        //   correct Web Manifest URL because the Web Manifest has been updated by the Web
        //   developer.
        //
        // If the Web Manifest was not found and an upgrade is not requested, keep on fetching
        // Web Manifests as the user navigates. For instance, the WebAPK's start_url might not
        // point to a Web Manifest because start_url redirects to the WebAPK's main page.
        if (gotManifest || needsUpgrade) {
            destroyUpgradeDetector();
        }

        if (!needsUpgrade) {
            if (!mPreviousUpdateSucceeded) {
                recordUpdate(storage, true);
            }
            return;
        }

        // Set WebAPK update as having failed in case that Chrome is killed prior to
        // {@link onBuiltWebApk} being called.
        recordUpdate(storage, false);

        if (data != null) {
            updateAsync(data.startUrl, data.scopeUrl, data.name, data.shortName, data.iconUrl,
                    data.iconMurmur2Hash, data.icon, data.displayMode, data.orientation,
                    data.themeColor, data.backgroundColor);
            return;
        }

        updateAsyncUsingAndroidManifestMetaData();
    }

    /**
     * Builds {@link ManifestUpgradeDetector}. In a separate function for the sake of tests.
     */
    protected ManifestUpgradeDetector buildManifestUpgradeDetector(
            Tab tab, WebApkMetaData metaData) {
        return new ManifestUpgradeDetector(tab, metaData, this);
    }

    /**
     * Sends a request to WebAPK Server to update WebAPK using the meta data from the WebAPK's
     * Android Manifest.
     */
    private void updateAsyncUsingAndroidManifestMetaData() {
        updateAsync(mMetaData.startUrl, mMetaData.scope, mMetaData.name, mMetaData.shortName,
                mMetaData.iconUrl, mMetaData.iconMurmur2Hash, mIcon, mMetaData.displayMode,
                mMetaData.orientation, mMetaData.themeColor, mMetaData.backgroundColor);
    }

    /**
     * Sends request to WebAPK Server to update WebAPK.
     */
    protected void updateAsync(String startUrl, String scopeUrl, String name, String shortName,
            String iconUrl, String iconMurmur2Hash, Bitmap icon, int displayMode, int orientation,
            long themeColor, long backgroundColor) {
        int versionCode = readVersionCodeFromAndroidManifest(mWebApkPackageName);
        nativeUpdateAsync(mId, startUrl, scopeUrl, name, shortName, iconUrl, iconMurmur2Hash, icon,
                displayMode, orientation, themeColor, backgroundColor, mMetaData.manifestUrl,
                mWebApkPackageName, versionCode);
    }

    /**
     * Destroys {@link mUpgradeDetector}. In a separate function for the sake of tests.
     */
    protected void destroyUpgradeDetector() {
        if (mUpgradeDetector == null) return;

        mUpgradeDetector.destroy();
        mUpgradeDetector = null;
    }

    /** Returns the current time. In a separate function for the sake of testing. */
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Reads the WebAPK's version code. Returns 0 on failure.
     */
    private int readVersionCodeFromAndroidManifest(String webApkPackage) {
        try {
            PackageManager packageManager =
                    ContextUtils.getApplicationContext().getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(webApkPackage, 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Returns whether the previous WebAPK update attempt succeeded. Returns true if there has not
     * been any update attempts.
     */
    private static boolean didPreviousUpdateSucceed(WebappDataStorage storage) {
        long lastUpdateCompletionTime = storage.getLastWebApkUpdateRequestCompletionTime();
        if (lastUpdateCompletionTime == WebappDataStorage.LAST_USED_INVALID
                || lastUpdateCompletionTime == WebappDataStorage.LAST_USED_UNSET) {
            return true;
        }
        return storage.getDidLastWebApkUpdateRequestSucceed();
    }

    /**
     * Whether there is a new version of the //chrome/android/webapk/shell_apk code.
     */
    private static boolean isShellApkVersionOutOfDate(WebApkMetaData metaData) {
        return metaData.shellApkVersion < WebApkVersion.CURRENT_SHELL_APK_VERSION;
    }

    /**
     * Returns whether the Web Manifest should be refetched to check whether it has been updated.
     * TODO: Make this method static once there is a static global clock class.
     * @param storage WebappDataStorage with the WebAPK's cached data.
     * @param metaData Meta data from WebAPK's Android Manifest.
     * @param previousUpdateSucceeded Whether the previous update attempt succeeded.
     * True if there has not been any update attempts.
     */
    private boolean shouldCheckIfWebManifestUpdated(
            WebappDataStorage storage, WebApkMetaData metaData, boolean previousUpdateSucceeded) {
        // Updating WebAPKs requires "installation from unknown sources" to be enabled. It is
        // confusing for a user to see a dialog asking them to enable "installation from unknown
        // sources" when they are in the middle of using the WebAPK (as opposed to after requesting
        // to add a WebAPK to the homescreen).
        if (!installingFromUnknownSourcesAllowed()) {
            return false;
        }

        if (CommandLine.getInstance().hasSwitch(
                    ChromeSwitches.CHECK_FOR_WEB_MANIFEST_UPDATE_ON_STARTUP)) {
            return true;
        }

        if (isShellApkVersionOutOfDate(metaData)) return true;

        long now = currentTimeMillis();
        long sinceLastCheckDurationMs = now - storage.getLastCheckForWebManifestUpdateTime();
        if (sinceLastCheckDurationMs >= FULL_CHECK_UPDATE_INTERVAL) return true;

        long sinceLastUpdateRequestDurationMs =
                now - storage.getLastWebApkUpdateRequestCompletionTime();
        return sinceLastUpdateRequestDurationMs >= RETRY_UPDATE_DURATION
                && !previousUpdateSucceeded;
    }

    /**
     * Updates {@link WebappDataStorage} with the time of the latest WebAPK update and whether the
     * WebAPK update succeeded.
     */
    private static void recordUpdate(WebappDataStorage storage, boolean success) {
        // Update the request time and result together. It prevents getting a correct request time
        // but a result from the previous request.
        storage.updateTimeOfLastWebApkUpdateRequestCompletion();
        storage.updateDidLastWebApkUpdateRequestSucceed(success);
    }

    /**
     * Returns whether the user has enabled installing apps from sources other than the Google
     * Play Store.
     */
    private static boolean installingFromUnknownSourcesAllowed() {
        ContentResolver contentResolver = ContextUtils.getApplicationContext().getContentResolver();
        try {
            int setting = Settings.Secure.getInt(
                    contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS);
            return setting == 1;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    /**
     * Called after either a request to update the WebAPK has been sent or the update process
     * fails.
     */
    @CalledByNative
    private static void onBuiltWebApk(String id, boolean success) {
        WebappDataStorage storage = WebappRegistry.getInstance().getWebappDataStorage(id);
        recordUpdate(storage, success);
    }

    private static native void nativeUpdateAsync(String id, String startUrl, String scope,
            String name, String shortName, String iconUrl, String iconMurmur2Hash, Bitmap icon,
            int displayMode, int orientation, long themeColor, long backgroundColor,
            String manifestUrl, String webApkPackage, int webApkVersion);
}
