// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ShortcutHelper;

import java.util.Map;

/**
 * Stores data about an installed web app. Uses SharedPreferences to persist the data to disk.
 * Before this class is used, the web app must be registered in {@link WebappRegistry}.
 *
 * EXAMPLE USAGE:
 *
 * (1) UPDATING/RETRIEVING THE ICON (web app MUST have been registered in WebappRegistry)
 * WebappDataStorage storage = WebappDataStorage.open(context, id);
 * storage.updateSplashScreenImage(bitmap);
 * storage.getSplashScreenImage(callback);
 */
public class WebappDataStorage {

    static final String SHARED_PREFS_FILE_PREFIX = "webapp_";
    static final String KEY_SPLASH_ICON = "splash_icon";
    static final String KEY_LAST_USED = "last_used";
    static final long INVALID_LAST_USED = -1;

    private static Factory sFactory = new Factory();

    private final SharedPreferences mPreferences;

    /**
     * Opens an instance of WebappDataStorage for the web app specified.
     * @param context  The context to open the SharedPreferences.
     * @param webappId The ID of the web app which is being opened.
     */
    public static WebappDataStorage open(final Context context, final String webappId) {
        final WebappDataStorage storage = sFactory.create(context, webappId);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected final Void doInBackground(Void... nothing) {
                if (storage.getLastUsedTime() == INVALID_LAST_USED) {
                    // If the last used time is invalid then assert that there is no data
                    // in the WebappDataStorage which needs to be cleaned up.
                    assert storage.getAllData().isEmpty();
                } else {
                    storage.updateLastUsedTime();
                }
                return null;
            }
        }.execute();
        return storage;
    }

    /**
     * Asynchronously retrieves the time which this WebappDataStorage was last
     * opened using {@link WebappDataStorage#open(Context, String)}.
     * @param context  The context to read the SharedPreferences file.
     * @param webappId The ID of the web app the used time is being read for.
     * @param callback Called when the last used time has been retrieved.
     */
    public static void getLastUsedTime(final Context context, final String webappId,
            final FetchCallback<Long> callback) {
        new AsyncTask<Void, Void, Long>() {
            @Override
            protected final Long doInBackground(Void... nothing) {
                long lastUsed = new WebappDataStorage(context.getApplicationContext(), webappId)
                        .getLastUsedTime();
                assert lastUsed != INVALID_LAST_USED;
                return lastUsed;
            }

            @Override
            protected final void onPostExecute(Long lastUsed) {
                callback.onDataRetrieved(lastUsed);
            }
        }.execute();
    }

    /**
     * Deletes the data for a web app by clearing all the information inside the SharedPreferences
     * file. This does NOT delete the file itself but the file is left empty.
     * @param context  The context to read the SharedPreferences file.
     * @param webappId The ID of the web app being deleted.
     */
    static void deleteDataForWebapp(final Context context, final String webappId) {
        assert !ThreadUtils.runningOnUiThread();
        openSharedPreferences(context, webappId).edit().clear().commit();
    }

    /**
     * Sets the factory used to generate WebappDataStorage objects.
     */
    @VisibleForTesting
    public static void setFactoryForTests(Factory factory) {
        sFactory = factory;
    }

    private static SharedPreferences openSharedPreferences(Context context, String webappId) {
        return context.getApplicationContext().getSharedPreferences(
                SHARED_PREFS_FILE_PREFIX + webappId, Context.MODE_PRIVATE);
    }

    protected WebappDataStorage(Context context, String webappId) {
        mPreferences = openSharedPreferences(context, webappId);
    }

    /*
     * Asynchronously retrieves the splash screen image associated with the
     * current web app.
     * @param callback Called when the splash screen image has been retrieved.
     *                 May be null if no image was found.
     */
    public void getSplashScreenImage(FetchCallback<Bitmap> callback) {
        new BitmapFetchTask(KEY_SPLASH_ICON, callback).execute();
    }

    /*
     * Update the information associated with the web app with the specified data.
     * @param splashScreenImage The image which should be shown on the splash screen of the web app.
     */
    public void updateSplashScreenImage(Bitmap splashScreenImage) {
        new UpdateTask(splashScreenImage).execute();
    }

    void updateLastUsedTime() {
        assert !ThreadUtils.runningOnUiThread();
        mPreferences.edit().putLong(KEY_LAST_USED, System.currentTimeMillis()).commit();
    }

    long getLastUsedTime() {
        assert !ThreadUtils.runningOnUiThread();
        return mPreferences.getLong(KEY_LAST_USED, INVALID_LAST_USED);
    }

    private Map<String, ?> getAllData() {
        return mPreferences.getAll();
    }

    /**
     * Called after data has been retrieved from storage.
     */
    public interface FetchCallback<T> {
        public void onDataRetrieved(T readObject);
    }

    /**
     * Factory used to generate WebappDataStorage objects.
     *
     * It is used in tests to override methods in WebappDataStorage and inject the mocked objects.
     */
    public static class Factory {

        /**
         * Generates a WebappDataStorage class for a specified web app.
         */
        public WebappDataStorage create(final Context context, final String webappId) {
            return new WebappDataStorage(context, webappId);
        }
    }

    private final class BitmapFetchTask extends AsyncTask<Void, Void, Bitmap> {

        private final String mKey;
        private final FetchCallback<Bitmap> mCallback;

        public BitmapFetchTask(String key, FetchCallback<Bitmap> callback) {
            mKey = key;
            mCallback = callback;
        }

        @Override
        protected final Bitmap doInBackground(Void... nothing) {
            return ShortcutHelper.decodeBitmapFromString(mPreferences.getString(mKey, null));
        }

        @Override
        protected final void onPostExecute(Bitmap result) {
            mCallback.onDataRetrieved(result);
        }
    }

    private final class UpdateTask extends AsyncTask<Void, Void, Void> {

        private final Bitmap mSplashImage;

        public UpdateTask(Bitmap splashImage) {
            mSplashImage = splashImage;
        }

        @Override
        protected Void doInBackground(Void... nothing) {
            mPreferences.edit()
                    .putString(KEY_SPLASH_ICON, ShortcutHelper.encodeBitmapAsString(mSplashImage))
                    .commit();
            return null;
        }
    }
}