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
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.browsing_data.UrlFilter;
import org.chromium.chrome.browser.browsing_data.UrlFilterBridge;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Keeps track of web apps which have created a SharedPreference file (through the used of the
 * WebappDataStorage class) which may need to be cleaned up in the future.
 *
 * It is NOT intended to be 100% accurate nor a comprehensive list of all installed web apps
 * because it is impossible to track when the user removes a web app from the Home screen and it
 * is similarily impossible to track pre-registry era web apps (this case is not a problem anyway
 * as these web apps have no external data to cleanup).
 */
public class WebappRegistry {

    static final String REGISTRY_FILE_NAME = "webapp_registry";
    static final String KEY_WEBAPP_SET = "webapp_set";
    static final String KEY_LAST_CLEANUP = "last_cleanup";

    /** Represents a period of 4 weeks in milliseconds */
    static final long FULL_CLEANUP_DURATION = TimeUnit.DAYS.toMillis(4L * 7L);

    /** Represents a period of 13 weeks in milliseconds */
    static final long WEBAPP_UNOPENED_CLEANUP_DURATION = TimeUnit.DAYS.toMillis(13L * 7L);

    /**
     * Called when a retrieval of the set of stored web app IDs occurs.
     */
    public interface FetchCallback {
        void onWebappIdsRetrieved(Set<String> readObject);
    }

    /**
     * Called when a retrieval of the stored WebappDataStorage occurs. The storage parameter will
     * be null if the web app queried for was not in the registry.
     */
    public interface FetchWebappDataStorageCallback {
        void onWebappDataStorageRetrieved(WebappDataStorage storage);
    }

    /**
     * Registers the existence of a web app, creates a SharedPreference entry for it, and runs the
     * supplied callback (if not null) on the UI thread with the resulting WebappDataStorage object.
     * @param webappId The id of the web app to register.
     * @param callback The callback to run with the WebappDataStorage argument.
     * @return The storage object for the web app.
     */
    public static void registerWebapp(final String webappId,
            final FetchWebappDataStorageCallback callback) {
        new AsyncTask<Void, Void, WebappDataStorage>() {
            @Override
            protected final WebappDataStorage doInBackground(Void... nothing) {
                SharedPreferences preferences = openSharedPreferences();
                // The set returned by getRegisteredWebappIds must be treated as immutable, so we
                // make a copy to edit and save.
                Set<String> webapps = new HashSet<>(getRegisteredWebappIds(preferences));
                boolean added = webapps.add(webappId);
                assert added;

                preferences.edit().putStringSet(KEY_WEBAPP_SET, webapps).apply();

                // Create the WebappDataStorage and update the last used time, so we can guarantee
                // that a web app which appears in the registry will have a
                // last used time != WebappDataStorage.LAST_USED_INVALID.
                WebappDataStorage storage = new WebappDataStorage(webappId);
                storage.updateLastUsedTime();
                return storage;
            }

            @Override
            protected final void onPostExecute(WebappDataStorage storage) {
                if (callback != null) callback.onWebappDataStorageRetrieved(storage);
            }
        }.execute();
    }

    /**
     * Runs the callback, supplying the WebappDataStorage object for webappId, or null if the web
     * app has not been registered.
     * @param webappId The id of the web app to register.
     * @return The storage object for the web app, or null if webappId is not registered.
     */
    public static void getWebappDataStorage(final String webappId,
            final FetchWebappDataStorageCallback callback) {
        new AsyncTask<Void, Void, WebappDataStorage>() {
            @Override
            protected final WebappDataStorage doInBackground(Void... nothing) {
                SharedPreferences preferences = openSharedPreferences();
                if (getRegisteredWebappIds(preferences).contains(webappId)) {
                    WebappDataStorage storage = WebappDataStorage.open(webappId);
                    return storage;
                }
                return null;
            }

            @Override
            protected final void onPostExecute(WebappDataStorage storage) {
                assert callback != null;
                callback.onWebappDataStorageRetrieved(storage);
            }
        }.execute();
    }

    /**
     * Runs the callback, supplying the WebappDataStorage object whose scope most closely matches
     * the provided URL, or null if a matching web app cannot be found. The most closely matching
     * scope is the longest scope which has the same prefix as the URL to open.
     * @param url      The URL to search for.
     * @return The storage object for the web app, or null if webappId is not registered.
     */
    public static void getWebappDataStorageForUrl(final String url,
            final FetchWebappDataStorageCallback callback) {
        new AsyncTask<Void, Void, WebappDataStorage>() {
            @Override
            protected final WebappDataStorage doInBackground(Void... nothing) {
                SharedPreferences preferences = openSharedPreferences();
                WebappDataStorage bestMatch = null;
                int largestOverlap = 0;
                for (String id : getRegisteredWebappIds(preferences)) {
                    WebappDataStorage storage = WebappDataStorage.open(id);
                    String scope = storage.getScope();
                    if (url.startsWith(scope) && scope.length() > largestOverlap) {
                        bestMatch = storage;
                        largestOverlap = scope.length();
                    }
                }
                return bestMatch;
            }

            protected final void onPostExecute(WebappDataStorage storage) {
                assert callback != null;
                callback.onWebappDataStorageRetrieved(storage);
            }
        }.execute();
    }

    /**
     * Asynchronously retrieves the list of web app IDs which this registry is aware of.
     * @param callback Called when the set has been retrieved. The set may be empty.
     */
    @VisibleForTesting
    public static void getRegisteredWebappIds(final FetchCallback callback) {
        new AsyncTask<Void, Void, Set<String>>() {
            @Override
            protected final Set<String> doInBackground(Void... nothing) {
                return getRegisteredWebappIds(openSharedPreferences());
            }

            @Override
            protected final void onPostExecute(Set<String> result) {
                assert callback != null;
                callback.onWebappIdsRetrieved(result);
            }
        }.execute();
    }

    /**
     * 1. Deletes the data for all "old" web apps.
     * "Old" web apps have not been opened by the user in the last 3 months, or have had their last
     * used time set to 0 by the user clearing their history. Cleanup is run, at most, once a month.
     * 2. Deletes the data for all WebAPKs that have been uninstalled in the last month.
     *
     * @param currentTime The current time which will be checked to decide if the task should be run
     *                    and if a web app should be cleaned up.
     */
    static void unregisterOldWebapps(final long currentTime) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected final Void doInBackground(Void... nothing) {
                SharedPreferences preferences = openSharedPreferences();
                long lastCleanup = preferences.getLong(KEY_LAST_CLEANUP, 0);
                if ((currentTime - lastCleanup) < FULL_CLEANUP_DURATION) return null;

                Set<String> currentWebapps = getRegisteredWebappIds(preferences);
                Set<String> retainedWebapps = new HashSet<>(currentWebapps);
                PackageManager pm = ContextUtils.getApplicationContext().getPackageManager();
                for (String id : currentWebapps) {
                    WebappDataStorage storage = new WebappDataStorage(id);
                    String webApkPackage = storage.getWebApkPackageName();
                    if (webApkPackage != null) {
                        if (isWebApkInstalled(pm, webApkPackage)) continue;
                    } else {
                        long lastUsed = storage.getLastUsedTime();
                        if ((currentTime - lastUsed) < WEBAPP_UNOPENED_CLEANUP_DURATION) continue;
                    }
                    WebappDataStorage.deleteDataForWebapp(id);
                    retainedWebapps.remove(id);
                }

                preferences.edit()
                        .putLong(KEY_LAST_CLEANUP, currentTime)
                        .putStringSet(KEY_WEBAPP_SET, retainedWebapps)
                        .apply();
                return null;
            }
        }.execute();
    }

    /**
     * Returns whether the given WebAPK is still installed.
     */
    private static boolean isWebApkInstalled(PackageManager pm, String webApkPackage) {
        assert !ThreadUtils.runningOnUiThread();
        try {
            pm.getPackageInfo(webApkPackage, PackageManager.GET_ACTIVITIES);
        } catch (NameNotFoundException e) {
            return false;
        }
        return true;
    }

    /**
     * Deletes the data of all web apps whose url matches |urlFilter|, as well as the registry
     * tracking those web apps.
     */
    @VisibleForTesting
    static void unregisterWebappsForUrls(final UrlFilter urlFilter, final Runnable callback) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected final Void doInBackground(Void... nothing) {
                SharedPreferences preferences = openSharedPreferences();
                Set<String> registeredWebapps =
                        new HashSet<>(getRegisteredWebappIds(preferences));
                Set<String> webappsToUnregister = new HashSet<>();
                for (String id : registeredWebapps) {
                    if (urlFilter.matchesUrl(WebappDataStorage.open(id).getUrl())) {
                        WebappDataStorage.deleteDataForWebapp(id);
                        webappsToUnregister.add(id);
                    }
                }

                // TODO(dominickn): SharedPreferences should be accessed on the main thread, not
                // from an AsyncTask. Simultaneous access from two threads creates a race condition.
                // Update all callsites in this class.
                registeredWebapps.removeAll(webappsToUnregister);
                if (registeredWebapps.isEmpty()) {
                    preferences.edit().clear().apply();
                } else {
                    preferences.edit().putStringSet(KEY_WEBAPP_SET, registeredWebapps).apply();
                }

                return null;
            }

            @Override
            protected final void onPostExecute(Void nothing) {
                assert callback != null;
                callback.run();
            }
        }.execute();
    }

    @CalledByNative
    static void unregisterWebappsForUrls(
            final UrlFilterBridge urlFilter, final long callbackPointer) {
        unregisterWebappsForUrls(urlFilter, new Runnable() {
            @Override
            public void run() {
                urlFilter.destroy();
                nativeOnWebappsUnregistered(callbackPointer);
            }
        });
    }

    /**
     * Deletes the URL and scope, and sets the last used time to 0 for all web apps whose url
     * matches |urlFilter|.
     */
    @VisibleForTesting
    static void clearWebappHistoryForUrls(final UrlFilter urlFilter, final Runnable callback) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected final Void doInBackground(Void... nothing) {
                SharedPreferences preferences = openSharedPreferences();
                for (String id : getRegisteredWebappIds(preferences)) {
                    if (urlFilter.matchesUrl(WebappDataStorage.open(id).getUrl())) {
                        WebappDataStorage.clearHistory(id);
                    }
                }
                return null;
            }

            @Override
            protected final void onPostExecute(Void nothing) {
                assert callback != null;
                callback.run();
            }
        }.execute();
    }

    @CalledByNative
    static void clearWebappHistoryForUrls(
            final UrlFilterBridge urlFilter, final long callbackPointer) {
        clearWebappHistoryForUrls(urlFilter, new Runnable() {
            @Override
            public void run() {
                urlFilter.destroy();
                nativeOnClearedWebappHistory(callbackPointer);
            }
        });
    }

    private static SharedPreferences openSharedPreferences() {
        return ContextUtils.getApplicationContext().getSharedPreferences(
                REGISTRY_FILE_NAME, Context.MODE_PRIVATE);
    }

    private static Set<String> getRegisteredWebappIds(SharedPreferences preferences) {
        // Wrap with unmodifiableSet to ensure it's never modified. See crbug.com/568369.
        return Collections.unmodifiableSet(
                preferences.getStringSet(KEY_WEBAPP_SET, Collections.<String>emptySet()));
    }

    private WebappRegistry() {
    }

    private static native void nativeOnWebappsUnregistered(long callbackPointer);
    private static native void nativeOnClearedWebappHistory(long callbackPointer);
}
