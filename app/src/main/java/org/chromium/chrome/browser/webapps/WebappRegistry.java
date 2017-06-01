// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;

import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.browsing_data.UrlFilter;
import org.chromium.chrome.browser.browsing_data.UrlFilterBridge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Singleton class which tracks web apps backed by a SharedPreferences file (abstracted by the
 * WebappDataStorage class). This class must be used on the main thread, except when warming
 * SharedPreferences.
 *
 * Aside from web app registration, which is asynchronous as a new SharedPreferences file must be
 * opened, all methods in this class are synchronous. All web app SharedPreferences known to
 * WebappRegistry are pre-warmed on browser startup when creating the singleton WebappRegistry
 * instance, whilst registering a new web app will automatically cache the new SharedPreferences
 * after it is created.
 *
 * This class is not a comprehensive list of installed web apps because it is impossible to know
 * when the user removes a web app from the home screen. The WebappDataStorage.wasLaunchedRecently()
 * heuristic attempts to compensate for this.
 */
public class WebappRegistry {

    static final String REGISTRY_FILE_NAME = "webapp_registry";
    static final String KEY_WEBAPP_SET = "webapp_set";
    static final String KEY_LAST_CLEANUP = "last_cleanup";

    /** Represents a period of 4 weeks in milliseconds */
    static final long FULL_CLEANUP_DURATION = TimeUnit.DAYS.toMillis(4L * 7L);

    /** Represents a period of 13 weeks in milliseconds */
    static final long WEBAPP_UNOPENED_CLEANUP_DURATION = TimeUnit.DAYS.toMillis(13L * 7L);

    /** Initialization-on-demand holder. This exists for thread-safe lazy initialization. */
    private static class Holder {
        // Not final for testing.
        private static WebappRegistry sInstance = new WebappRegistry();
    }

    private HashMap<String, WebappDataStorage> mStorages;
    private SharedPreferences mPreferences;

    /**
     * Callback run when a WebappDataStorage object is registered for the first time. The storage
     * parameter will never be null.
     */
    public interface FetchWebappDataStorageCallback {
        void onWebappDataStorageRetrieved(WebappDataStorage storage);
    }

    private WebappRegistry() {
        mPreferences = openSharedPreferences();
        mStorages = new HashMap<>();
    }

    /**
     * Returns the singleton WebappRegistry instance. Creates the instance on first call.
     */
    public static WebappRegistry getInstance() {
        return Holder.sInstance;
    }

    /**
     * Warm up the WebappRegistry and a specific WebappDataStorage SharedPreferences.
     * @param id The web app id to warm up in addition to the WebappRegistry.
     */
    public static void warmUpSharedPrefsForId(String id) {
        getInstance().initStorages(id, false);
    }

    /**
     * Warm up the WebappRegistry and all WebappDataStorage SharedPreferences.
     */
    public static void warmUpSharedPrefs() {
        getInstance().initStorages(null, false);
    }

    @VisibleForTesting
    public static void refreshSharedPrefsForTesting() {
        Holder.sInstance = new WebappRegistry();
        getInstance().initStorages(null, true);
    }

    /**
     * Registers the existence of a web app, creates a SharedPreference entry for it, and runs the
     * supplied callback (if not null) on the UI thread with the resulting WebappDataStorage object.
     * @param webappId The id of the web app to register.
     * @param callback The callback to run with the WebappDataStorage argument.
     * @return The storage object for the web app.
     */
    public void register(final String webappId, final FetchWebappDataStorageCallback callback) {
        new AsyncTask<Void, Void, WebappDataStorage>() {
            @Override
            protected final WebappDataStorage doInBackground(Void... nothing) {
                // Create the WebappDataStorage on the background thread, as this must create and
                // open a new SharedPreferences.
                return WebappDataStorage.open(webappId);
            }

            @Override
            protected final void onPostExecute(WebappDataStorage storage) {
                // Guarantee that last used time != WebappDataStorage.LAST_USED_INVALID. Must be
                // run on the main thread as SharedPreferences.Editor.apply() is called.
                mStorages.put(webappId, storage);
                mPreferences.edit().putStringSet(KEY_WEBAPP_SET, mStorages.keySet()).apply();
                storage.updateLastUsedTime();
                if (callback != null) callback.onWebappDataStorageRetrieved(storage);
            }
        }.execute();
    }

    /**
     * Returns the WebappDataStorage object for webappId, or null if one cannot be found.
     * @param webappId The id of the web app.
     * @return The storage object for the web app, or null if webappId is not registered.
     */
    public WebappDataStorage getWebappDataStorage(String webappId) {
        return mStorages.get(webappId);
    }

    /**
     * Returns the WebappDataStorage object whose scope most closely matches the provided URL, or
     * null if a matching web app cannot be found. The most closely matching scope is the longest
     * scope which has the same prefix as the URL to open.
     * @param url The URL to search for.
     * @return The storage object for the web app, or null if one cannot be found.
     */
    public WebappDataStorage getWebappDataStorageForUrl(final String url) {
        WebappDataStorage bestMatch = null;
        int largestOverlap = 0;
        for (HashMap.Entry<String, WebappDataStorage> entry : mStorages.entrySet()) {
            WebappDataStorage storage = entry.getValue();
            String scope = storage.getScope();
            if (url.startsWith(scope) && scope.length() > largestOverlap) {
                bestMatch = storage;
                largestOverlap = scope.length();
            }
        }
        return bestMatch;
    }

    /**
     * Returns the list of web app IDs which are written to SharedPreferences.
     */
    @VisibleForTesting
    public static Set<String> getRegisteredWebappIdsForTesting() {
        // Wrap with unmodifiableSet to ensure it's never modified. See crbug.com/568369.
        return Collections.unmodifiableSet(openSharedPreferences().getStringSet(
                KEY_WEBAPP_SET, Collections.<String>emptySet()));
    }

    /**
     * Deletes the data for all "old" web apps, as well as all WebAPKs that have been uninstalled in
     * the last month. "Old" web apps have not been opened by the user in the last 3 months, or have
     * had their last used time set to 0 by the user clearing their history. Cleanup is run, at
     * most, once a month.
     * @param currentTime The current time which will be checked to decide if the task should be run
     *                    and if a web app should be cleaned up.
     */
    public void unregisterOldWebapps(long currentTime) {
        if ((currentTime - mPreferences.getLong(KEY_LAST_CLEANUP, 0)) < FULL_CLEANUP_DURATION) {
            return;
        }

        Iterator<HashMap.Entry<String, WebappDataStorage>> it = mStorages.entrySet().iterator();
        while (it.hasNext()) {
            HashMap.Entry<String, WebappDataStorage> entry = it.next();
            WebappDataStorage storage = entry.getValue();
            String webApkPackage = storage.getWebApkPackageName();
            if (webApkPackage != null) {
                if (isWebApkInstalled(webApkPackage)) {
                    continue;
                }
            } else if ((currentTime - storage.getLastUsedTime())
                    < WEBAPP_UNOPENED_CLEANUP_DURATION) {
                continue;
            }
            storage.delete();
            it.remove();
        }

        mPreferences.edit()
                .putLong(KEY_LAST_CLEANUP, currentTime)
                .putStringSet(KEY_WEBAPP_SET, mStorages.keySet())
                .apply();
    }

    /**
     * Deletes the data of all web apps whose url matches |urlFilter|.
     * @param urlFilter The filter object to check URLs.
     */
    @VisibleForTesting
    void unregisterWebappsForUrlsImpl(UrlFilter urlFilter) {
        Iterator<HashMap.Entry<String, WebappDataStorage>> it = mStorages.entrySet().iterator();
        while (it.hasNext()) {
            HashMap.Entry<String, WebappDataStorage> entry = it.next();
            WebappDataStorage storage = entry.getValue();
            if (urlFilter.matchesUrl(storage.getUrl())) {
                storage.delete();
                it.remove();
            }
        }

        if (mStorages.isEmpty()) {
            mPreferences.edit().clear().apply();
        } else {
            mPreferences.edit().putStringSet(KEY_WEBAPP_SET, mStorages.keySet()).apply();
        }
    }

    @CalledByNative
    static void unregisterWebappsForUrls(UrlFilterBridge urlFilter) {
        WebappRegistry.getInstance().unregisterWebappsForUrlsImpl(urlFilter);
        urlFilter.destroy();
    }

    /**
     * Deletes the URL and scope, and sets the last used time to 0 for all web apps whose url
     * matches |urlFilter|.
     * @param urlFilter The filter object to check URLs.
     */
    @VisibleForTesting
    void clearWebappHistoryForUrlsImpl(UrlFilter urlFilter) {
        for (HashMap.Entry<String, WebappDataStorage> entry : mStorages.entrySet()) {
            WebappDataStorage storage = entry.getValue();
            if (urlFilter.matchesUrl(storage.getUrl())) {
                storage.clearHistory();
            }
        }
    }

    @CalledByNative
    static void clearWebappHistoryForUrls(UrlFilterBridge urlFilter) {
        WebappRegistry.getInstance().clearWebappHistoryForUrlsImpl(urlFilter);
        urlFilter.destroy();
    }

    /**
     * Returns true if the given WebAPK is installed.
     */
    private boolean isWebApkInstalled(String webApkPackage) {
        try {
            ContextUtils.getApplicationContext().getPackageManager().getPackageInfo(
                    webApkPackage, PackageManager.GET_ACTIVITIES);
        } catch (NameNotFoundException e) {
            return false;
        }
        return true;
    }

    private static SharedPreferences openSharedPreferences() {
        return ContextUtils.getApplicationContext().getSharedPreferences(
                REGISTRY_FILE_NAME, Context.MODE_PRIVATE);
    }

    private void initStorages(String idToInitialize, boolean replaceExisting) {
        Set<String> webapps =
                mPreferences.getStringSet(KEY_WEBAPP_SET, Collections.<String>emptySet());
        boolean initAll = (idToInitialize == null || idToInitialize.isEmpty());

        // Don't overwrite any entry in mStorages unless replaceExisting is set to true.
        if (initAll) {
            for (String id : webapps) {
                if (replaceExisting || !mStorages.containsKey(id)) {
                    mStorages.put(id, WebappDataStorage.open(id));
                }
            }
        } else {
            if (webapps.contains(idToInitialize)
                    && (replaceExisting || !mStorages.containsKey(idToInitialize))) {
                mStorages.put(idToInitialize, WebappDataStorage.open(idToInitialize));
            }
        }
    }
}
