// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.partnercustomizations;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksReader;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and caches partner browser customizations information if it exists.
 */
public class PartnerBrowserCustomizations {
    private static final String TAG = "PartnerBrowserProvider";
    private static final String PROVIDER_AUTHORITY = "com.android.partnerbrowsercustomizations";

    // Private homepage structure.
    static final String PARTNER_HOMEPAGE_PATH = "homepage";
    static final String PARTNER_DISABLE_BOOKMARKS_EDITING_PATH = "disablebookmarksediting";
    @VisibleForTesting
    public static final String PARTNER_DISABLE_INCOGNITO_MODE_PATH = "disableincognitomode";

    private static String sProviderAuthority = PROVIDER_AUTHORITY;
    private static boolean sIgnoreBrowserProviderSystemPackageCheck = false;
    private static volatile String sHomepage;
    private static volatile boolean sIncognitoModeDisabled;
    private static volatile boolean sBookmarksEditingDisabled;
    private static boolean sIsInitialized;
    private static List<Runnable> sInitializeAsyncCallbacks = new ArrayList<Runnable>();

    /**
     * @return True if the partner homepage content provider exists and enabled. Note that The data
     *         this method reads is not initialized until the asynchronous initialization of this
     *         class has been completed.
     */
    public static boolean isHomepageProviderAvailableAndEnabled() {
        return !TextUtils.isEmpty(getHomePageUrl());
    }

    /**
     * @return Whether incognito mode is disabled by the partner.
     */
    public static boolean isIncognitoDisabled() {
        return sIncognitoModeDisabled;
    }

    /**
     * @return Whether partner bookmarks editing is disabled by the partner.
     */
    @VisibleForTesting
    static boolean isBookmarksEditingDisabled() {
        return sBookmarksEditingDisabled;
    }

    /**
     * @return True, if initialization is finished. Checking that there is no provider, or failing
     *         to read provider is also considered initialization.
     */
    @VisibleForTesting
    static boolean isInitialized() {
        return sIsInitialized;
    }

    @VisibleForTesting
    public static void setProviderAuthorityForTests(String providerAuthority) {
        sProviderAuthority = providerAuthority;
    }

    /**
     * For security, we only allow system package to be a browser customizations provider. However,
     * requiring root and installing system apk makes testing harder, so we decided to have this
     * hack for testing. This must not be called other than tests.
     *
     * @param ignore whether we should ignore browser provider system package checking.
     */
    @VisibleForTesting
    public static void ignoreBrowserProviderSystemPackageCheckForTests(boolean ignore) {
        sIgnoreBrowserProviderSystemPackageCheck = ignore;
    }

    @VisibleForTesting
    public static Uri buildQueryUri(String path) {
        return new Uri.Builder()
                .scheme("content")
                .authority(sProviderAuthority)
                .appendPath(path)
                .build();
    }

    /**
     * Constructs an async task that reads PartnerBrowserCustomization provider.
     *
     * @param context   The current application context.
     * @param timeoutMs If initializing takes more than this time, cancels it. The unit is ms.
     */
    public static void initializeAsync(final Context context, long timeoutMs) {
        sIsInitialized = false;
        // Setup an initializing async task.
        final AsyncTask<Void, Void, Void> initializeAsyncTask =
                new AsyncTask<Void, Void, Void>() {
            private boolean mDisablePartnerBookmarksShim;
            private boolean mHomepageUriChanged;

            private void refreshHomepage() {
                try {
                    ContentResolver contentResolver = context.getContentResolver();
                    Cursor cursor = contentResolver.query(
                            buildQueryUri(PARTNER_HOMEPAGE_PATH), null, null, null, null);
                    if (cursor != null && cursor.moveToFirst() && cursor.getColumnCount() == 1
                            && !isCancelled()) {
                        if (TextUtils.isEmpty(sHomepage)
                                || !sHomepage.equals(cursor.getString(0))) {
                            mHomepageUriChanged = true;
                        }
                        sHomepage = cursor.getString(0);
                    }
                    if (cursor != null) cursor.close();
                } catch (Exception e) {
                    Log.w(TAG, "Partner homepage provider URL read failed : ", e);
                }
            }

            private void refreshIncognitoModeDisabled() {
                try {
                    ContentResolver contentResolver = context.getContentResolver();
                    Cursor cursor = contentResolver.query(
                            buildQueryUri(PARTNER_DISABLE_INCOGNITO_MODE_PATH),
                                    null, null, null, null);
                    if (cursor != null && cursor.moveToFirst() && cursor.getColumnCount() == 1
                            && !isCancelled()) {
                        sIncognitoModeDisabled = cursor.getInt(0) == 1;
                    }
                    if (cursor != null) cursor.close();
                } catch (Exception e) {
                    Log.w(TAG, "Partner disable incognito mode read failed : ", e);
                }
            }

            private void refreshBookmarksEditingDisabled() {
                try {
                    ContentResolver contentResolver = context.getContentResolver();
                    Cursor cursor = contentResolver.query(
                            buildQueryUri(PARTNER_DISABLE_BOOKMARKS_EDITING_PATH),
                                    null, null, null, null);
                    if (cursor != null && cursor.moveToFirst() && cursor.getColumnCount() == 1
                            && !isCancelled()) {
                        boolean bookmarksEditingDisabled = cursor.getInt(0) == 1;
                        if (bookmarksEditingDisabled != sBookmarksEditingDisabled) {
                            mDisablePartnerBookmarksShim = true;
                        }
                        sBookmarksEditingDisabled = bookmarksEditingDisabled;
                    }
                    if (cursor != null) cursor.close();
                } catch (Exception e) {
                    Log.w(TAG, "Partner disable bookmarks editing read failed : ", e);
                }
            }

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    ProviderInfo providerInfo = context.getPackageManager()
                            .resolveContentProvider(sProviderAuthority, 0);
                    if (providerInfo == null) return null;

                    if ((providerInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                            && !sIgnoreBrowserProviderSystemPackageCheck) {
                        Log.w("TAG", "Browser Cutomizations content provider package, "
                                + providerInfo.packageName + ", is not a system package. "
                                + "This could be a malicious attepment from a third party app, "
                                + "so skip reading the browser content provider.");
                        return null;
                    }

                    if (isCancelled()) return null;
                    refreshIncognitoModeDisabled();

                    if (isCancelled()) return null;
                    refreshBookmarksEditingDisabled();

                    if (isCancelled()) return null;
                    refreshHomepage();
                } catch (Exception e) {
                    Log.w(TAG, "Fetching partner customizations failed", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                onFinalized();
            }

            @Override
            protected void onCancelled(Void result) {
                onFinalized();
            }

            private void onFinalized() {
                sIsInitialized = true;

                for (Runnable callback : sInitializeAsyncCallbacks) {
                    callback.run();
                }
                sInitializeAsyncCallbacks.clear();

                if (mHomepageUriChanged) {
                    HomepageManager.getInstance(context).notifyHomepageUpdated();
                }

                // Disable partner bookmarks editing if necessary.
                if (mDisablePartnerBookmarksShim) {
                    PartnerBookmarksReader.disablePartnerBookmarksEditing();
                }
            }
        };

        initializeAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        // Cancel the initialization if it reaches timeout.
        ThreadUtils.postOnUiThreadDelayed(new Runnable() {
            @Override
            public void run() {
                initializeAsyncTask.cancel(true);
            }
        }, timeoutMs);
    }

    /**
     * Sets a callback that will be executed when the initialization is done.
     *
     * @param callback  This is called when the initialization is done.
     */
    public static void setOnInitializeAsyncFinished(final Runnable callback) {
        if (sIsInitialized) {
            ThreadUtils.postOnUiThread(callback);
        } else {
            sInitializeAsyncCallbacks.add(callback);
        }
    }

    /**
     * Sets a callback that will be executed when the initialization is done.
     *
     * @param callback  This is called when the initialization is done.
     * @param timeoutMs If initializing takes more than this time since this function is called,
     *                  force run |callback| early. The unit is ms.
     */
    public static void setOnInitializeAsyncFinished(final Runnable callback, long timeoutMs) {
        sInitializeAsyncCallbacks.add(callback);

        ThreadUtils.postOnUiThreadDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        if (sInitializeAsyncCallbacks.remove(callback)) callback.run();
                    }
                },
                sIsInitialized ? 0 : timeoutMs);
    }

    public static void destroy() {
        sIsInitialized = false;
        sInitializeAsyncCallbacks.clear();
        sHomepage = null;
    }

    /**
     * @return Home page URL from Android provider. If null, that means either there is no homepage
     *         provider or provider set it to null to disable homepage.
     */
    public static String getHomePageUrl() {
        return sHomepage;
    }
}
