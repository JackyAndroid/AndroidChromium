// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.blink_public.platform.WebDisplayMode;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.ShortcutSource;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.content_public.common.ScreenOrientationValues;

import java.util.concurrent.TimeUnit;

/**
 * Stores data about an installed web app. Uses SharedPreferences to persist the data to disk.
 * This class must only be accessed via {@link WebappRegistry}, which is used to register and keep
 * track of web app data known to Chrome.
 */
public class WebappDataStorage {

    static final String SHARED_PREFS_FILE_PREFIX = "webapp_";
    static final String KEY_SPLASH_ICON = "splash_icon";
    static final String KEY_LAST_USED = "last_used";
    static final String KEY_URL = "url";
    static final String KEY_SCOPE = "scope";
    static final String KEY_ICON = "icon";
    static final String KEY_NAME = "name";
    static final String KEY_SHORT_NAME = "short_name";
    static final String KEY_DISPLAY_MODE = "display_mode";
    static final String KEY_ORIENTATION = "orientation";
    static final String KEY_THEME_COLOR = "theme_color";
    static final String KEY_BACKGROUND_COLOR = "background_color";
    static final String KEY_SOURCE = "source";
    static final String KEY_ACTION = "action";
    static final String KEY_IS_ICON_GENERATED = "is_icon_generated";
    static final String KEY_VERSION = "version";
    static final String KEY_WEBAPK_PACKAGE_NAME = "webapk_package_name";

    // The completion time of the last check for whether the WebAPK's Web Manifest was updated.
    static final String KEY_LAST_CHECK_WEB_MANIFEST_UPDATE_TIME =
            "last_check_web_manifest_update_time";

    // The last time that the WebAPK update request completed (successfully or unsuccessfully).
    static final String KEY_LAST_WEBAPK_UPDATE_REQUEST_COMPLETE_TIME =
            "last_webapk_update_request_complete_time";

    // Whether the last WebAPK update request succeeded.
    static final String KEY_DID_LAST_WEBAPK_UPDATE_REQUEST_SUCCEED =
            "did_last_webapk_update_request_succeed";

    // Unset/invalid constants for last used times and URLs. 0 is used as the null last used time as
    // WebappRegistry assumes that this is always a valid timestamp.
    static final long LAST_USED_UNSET = 0;
    static final long LAST_USED_INVALID = -1;
    static final String URL_INVALID = "";
    static final int VERSION_INVALID = 0;

    // We use a heuristic to determine whether a web app is still installed on the home screen, as
    // there is no way to do so directly. Any web app which has been opened in the last ten days
    // is considered to be still on the home screen.
    static final long WEBAPP_LAST_OPEN_MAX_TIME = TimeUnit.DAYS.toMillis(10L);

    private static Clock sClock = new Clock();
    private static Factory sFactory = new Factory();

    private final String mId;
    private final SharedPreferences mPreferences;

    /**
     * Called after data has been retrieved from storage.
     */
    public interface FetchCallback<T> {
        public void onDataRetrieved(T readObject);
    }

    /**
     * Factory used to generate WebappDataStorage objects.
     * Overridden in tests to inject mocked objects.
     */
    public static class Factory {
        /**
         * Generates a WebappDataStorage instance for a specified web app.
         */
        public WebappDataStorage create(final String webappId) {
            return new WebappDataStorage(webappId);
        }
    }

    /**
     * Clock used to generate the current time in millseconds for setting last used time.
     */
    public static class Clock {
        /**
         * @return Current time in milliseconds.
         */
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }

    /**
     * Opens an instance of WebappDataStorage for the web app specified.
     * @param webappId The ID of the web app.
     */
    static WebappDataStorage open(final String webappId) {
        final WebappDataStorage storage = sFactory.create(webappId);
        if (storage.getLastUsedTime() == LAST_USED_INVALID) {
            // If the last used time is invalid then ensure that there is no data in the
            // WebappDataStorage which needs to be cleaned up.
            assert storage.isEmpty();
        }
        return storage;
    }

    /**
     * Sets the clock used to get the current time.
     */
    @VisibleForTesting
    public static void setClockForTests(Clock clock) {
        sClock = clock;
    }

    /**
     * Sets the factory used to generate WebappDataStorage objects.
     */
    @VisibleForTesting
    public static void setFactoryForTests(Factory factory) {
        sFactory = factory;
    }

    /**
     * Asynchronously retrieves the splash screen image associated with the web app. The work is
     * performed on a background thread as it requires a potentially expensive image decode.
     * @param callback Called when the splash screen image has been retrieved.
     *                 The bitmap result will be null if no image was found.
     */
    public void getSplashScreenImage(final FetchCallback<Bitmap> callback) {
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected final Bitmap doInBackground(Void... nothing) {
                return ShortcutHelper.decodeBitmapFromString(
                        mPreferences.getString(KEY_SPLASH_ICON, null));
            }

            @Override
            protected final void onPostExecute(Bitmap result) {
                assert callback != null;
                callback.onDataRetrieved(result);
            }
        }.execute();
    }

    /**
     * Update the splash screen image associated with the web app with the specified data. The image
     * must have been encoded using {@link ShortcutHelper#encodeBitmapAsString}.
     * @param splashScreenImage The image which should be shown on the splash screen of the web app.
     */
    public void updateSplashScreenImage(String splashScreenImage) {
        mPreferences.edit().putString(KEY_SPLASH_ICON, splashScreenImage).apply();
    }

    /**
     * Creates and returns a web app launch intent from the data stored in this object. Must not be
     * called on the main thread as it requires a potentially expensive image decode.
     * @return The web app launch intent.
     */
    public Intent createWebappLaunchIntent() {
        assert !ThreadUtils.runningOnUiThread();
        // Assume that all of the data is invalid if the version isn't set, so return a null intent.
        int version = mPreferences.getInt(KEY_VERSION, VERSION_INVALID);
        if (version == VERSION_INVALID) return null;

        // Use "standalone" as the default display mode as this was the original assumed default for
        // all web apps.
        return ShortcutHelper.createWebappShortcutIntent(mId,
                mPreferences.getString(KEY_ACTION, null),
                mPreferences.getString(KEY_URL, null),
                mPreferences.getString(KEY_SCOPE, null),
                mPreferences.getString(KEY_NAME, null),
                mPreferences.getString(KEY_SHORT_NAME, null),
                ShortcutHelper.decodeBitmapFromString(
                        mPreferences.getString(KEY_ICON, null)), version,
                mPreferences.getInt(KEY_DISPLAY_MODE, WebDisplayMode.Standalone),
                mPreferences.getInt(KEY_ORIENTATION, ScreenOrientationValues.DEFAULT),
                mPreferences.getLong(KEY_THEME_COLOR,
                        ShortcutHelper.MANIFEST_COLOR_INVALID_OR_MISSING),
                mPreferences.getLong(KEY_BACKGROUND_COLOR,
                        ShortcutHelper.MANIFEST_COLOR_INVALID_OR_MISSING),
                mPreferences.getBoolean(KEY_IS_ICON_GENERATED, false));
    }

    /**
     * Updates the data stored in this object to match that in the supplied intent.
     * @param shortcutIntent The intent to pull web app data from.
     */
    public void updateFromShortcutIntent(Intent shortcutIntent) {
        if (shortcutIntent == null) return;

        SharedPreferences.Editor editor = mPreferences.edit();
        boolean updated = false;

        // The URL and scope may have been deleted by the user clearing their history. Check whether
        // they are present, and update if necessary.
        String url = mPreferences.getString(KEY_URL, URL_INVALID);
        if (url.equals(URL_INVALID)) {
            url = IntentUtils.safeGetStringExtra(shortcutIntent, ShortcutHelper.EXTRA_URL);
            editor.putString(KEY_URL, url);
            updated = true;
        }

        if (mPreferences.getString(KEY_SCOPE, URL_INVALID).equals(URL_INVALID)) {
            String scope = IntentUtils.safeGetStringExtra(
                    shortcutIntent, ShortcutHelper.EXTRA_SCOPE);
            if (scope == null) {
                scope = ShortcutHelper.getScopeFromUrl(url);
            }
            editor.putString(KEY_SCOPE, scope);
            updated = true;
        }

        // For all other fields, assume that if the version key is present and equal to
        // ShortcutHelper.WEBAPP_SHORTCUT_VERSION, then all fields are present and do not need to be
        // updated. All fields except for the last used time, scope, and URL are either set or
        // cleared together.
        if (mPreferences.getInt(KEY_VERSION, VERSION_INVALID)
                != ShortcutHelper.WEBAPP_SHORTCUT_VERSION) {
            editor.putString(KEY_NAME, IntentUtils.safeGetStringExtra(
                        shortcutIntent, ShortcutHelper.EXTRA_NAME));
            editor.putString(KEY_SHORT_NAME, IntentUtils.safeGetStringExtra(
                        shortcutIntent, ShortcutHelper.EXTRA_SHORT_NAME));
            editor.putString(KEY_ICON, IntentUtils.safeGetStringExtra(
                        shortcutIntent, ShortcutHelper.EXTRA_ICON));
            editor.putInt(KEY_VERSION, ShortcutHelper.WEBAPP_SHORTCUT_VERSION);

            // "Standalone" was the original assumed default for all web apps.
            editor.putInt(KEY_DISPLAY_MODE, IntentUtils.safeGetIntExtra(
                        shortcutIntent, ShortcutHelper.EXTRA_DISPLAY_MODE,
                        WebDisplayMode.Standalone));
            editor.putInt(KEY_ORIENTATION, IntentUtils.safeGetIntExtra(
                        shortcutIntent, ShortcutHelper.EXTRA_ORIENTATION,
                        ScreenOrientationValues.DEFAULT));
            editor.putLong(KEY_THEME_COLOR, IntentUtils.safeGetLongExtra(
                        shortcutIntent, ShortcutHelper.EXTRA_THEME_COLOR,
                        ShortcutHelper.MANIFEST_COLOR_INVALID_OR_MISSING));
            editor.putLong(KEY_BACKGROUND_COLOR, IntentUtils.safeGetLongExtra(
                        shortcutIntent, ShortcutHelper.EXTRA_BACKGROUND_COLOR,
                        ShortcutHelper.MANIFEST_COLOR_INVALID_OR_MISSING));
            editor.putBoolean(KEY_IS_ICON_GENERATED, IntentUtils.safeGetBooleanExtra(
                        shortcutIntent, ShortcutHelper.EXTRA_IS_ICON_GENERATED, false));
            editor.putString(KEY_ACTION, shortcutIntent.getAction());
            editor.putInt(KEY_SOURCE, IntentUtils.safeGetIntExtra(
                        shortcutIntent, ShortcutHelper.EXTRA_SOURCE,
                        ShortcutSource.UNKNOWN));
            editor.putString(KEY_WEBAPK_PACKAGE_NAME, IntentUtils.safeGetStringExtra(
                    shortcutIntent, ShortcutHelper.EXTRA_WEBAPK_PACKAGE_NAME));
            updated = true;
        }
        if (updated) editor.apply();
    }

    /**
     * Returns true if this web app has been launched from home screen recently (within
     * WEBAPP_LAST_OPEN_MAX_TIME milliseconds).
     */
    public boolean wasLaunchedRecently() {
        // WebappRegistry.register sets the last used time, so that counts as a 'launch'.
        return (sClock.currentTimeMillis() - getLastUsedTime() < WEBAPP_LAST_OPEN_MAX_TIME);
    }

    /**
     * Deletes the data for a web app by clearing all the information inside the SharedPreferences
     * file. This does NOT delete the file itself but the file is left empty.
     */
    void delete() {
        mPreferences.edit().clear().apply();
    }

    /**
     * Deletes the URL and scope, and sets all timestamps to 0 in SharedPreferences.
     * This does not remove the stored splash screen image (if any) for the app.
     */
    void clearHistory() {
        SharedPreferences.Editor editor = mPreferences.edit();

        // The last used time is set to 0 to ensure that a valid value is always present.
        // If the web app is not launched prior to the next cleanup, then its remaining data will be
        // removed. Otherwise, the next launch from home screen will update the last used time.
        editor.putLong(KEY_LAST_USED, LAST_USED_UNSET);
        editor.remove(KEY_URL);
        editor.remove(KEY_SCOPE);
        editor.remove(KEY_LAST_CHECK_WEB_MANIFEST_UPDATE_TIME);
        editor.remove(KEY_LAST_WEBAPK_UPDATE_REQUEST_COMPLETE_TIME);
        editor.remove(KEY_DID_LAST_WEBAPK_UPDATE_REQUEST_SUCCEED);
        editor.apply();
    }

    /**
     * Returns the scope stored in this object, or URL_INVALID if it is not stored.
     */
    public String getScope() {
        return mPreferences.getString(KEY_SCOPE, URL_INVALID);
    }

    /**
     * Returns the URL stored in this object, or URL_INVALID if it is not stored.
     */
    public String getUrl() {
        return mPreferences.getString(KEY_URL, URL_INVALID);
    }

    /**
     * Returns the last used time of this object, or -1 if it is not stored.
     */
    public long getLastUsedTime() {
        return mPreferences.getLong(KEY_LAST_USED, LAST_USED_INVALID);
    }

    /**
     * Update the information associated with the web app with the specified data. Used for testing.
     * @param splashScreenImage The image encoded as a string which should be shown on the splash
     *                          screen of the web app.
     */
    @VisibleForTesting
    void updateSplashScreenImageForTests(String splashScreenImage) {
        mPreferences.edit().putString(KEY_SPLASH_ICON, splashScreenImage).apply();
    }

    /**
     * Updates the last used time of this object.
     */
    void updateLastUsedTime() {
        mPreferences.edit().putLong(KEY_LAST_USED, sClock.currentTimeMillis()).apply();
    }

    /**
     * Returns the package name if the data is for a WebAPK, null otherwise.
     */
    String getWebApkPackageName() {
        return mPreferences.getString(KEY_WEBAPK_PACKAGE_NAME, null);
    }

    /**
     * Updates the time of the completion of the last check for whether the WebAPK's Web Manifest
     * was updated.
     */
    void updateTimeOfLastCheckForUpdatedWebManifest() {
        mPreferences.edit()
                .putLong(KEY_LAST_CHECK_WEB_MANIFEST_UPDATE_TIME, sClock.currentTimeMillis())
                .apply();
    }

    /**
     * Returns the completion time of the last check for whether the WebAPK's Web Manifest was
     * updated. This time needs to be set when the WebAPK is registered.
     */
    long getLastCheckForWebManifestUpdateTime() {
        return mPreferences.getLong(KEY_LAST_CHECK_WEB_MANIFEST_UPDATE_TIME, LAST_USED_INVALID);
    }

    /**
     * Updates when the last WebAPK update request finished (successfully or unsuccessfully).
     */
    void updateTimeOfLastWebApkUpdateRequestCompletion() {
        mPreferences.edit()
                .putLong(KEY_LAST_WEBAPK_UPDATE_REQUEST_COMPLETE_TIME, sClock.currentTimeMillis())
                .apply();
    }

    /**
     * Returns when the last WebAPK update request completed (successfully or unsuccessfully).
     * This time needs to be set when the WebAPK is registered.
     */
    long getLastWebApkUpdateRequestCompletionTime() {
        return mPreferences.getLong(
                KEY_LAST_WEBAPK_UPDATE_REQUEST_COMPLETE_TIME, LAST_USED_INVALID);
    }

    /**
     * Updates the result of whether the last update request to WebAPK Server succeeded.
     */
    void updateDidLastWebApkUpdateRequestSucceed(boolean success) {
        mPreferences.edit()
                .putBoolean(KEY_DID_LAST_WEBAPK_UPDATE_REQUEST_SUCCEED, success)
                .apply();
    }

    /**
     * Returns whether the last update request to WebAPK Server succeeded.
     */
    boolean getDidLastWebApkUpdateRequestSucceed() {
        return mPreferences.getBoolean(KEY_DID_LAST_WEBAPK_UPDATE_REQUEST_SUCCEED, false);
    }

    protected WebappDataStorage(String webappId) {
        mId = webappId;
        mPreferences = ContextUtils.getApplicationContext().getSharedPreferences(
                SHARED_PREFS_FILE_PREFIX + webappId, Context.MODE_PRIVATE);
    }

    private boolean isEmpty() {
        return mPreferences.getAll().isEmpty();
    }
}
