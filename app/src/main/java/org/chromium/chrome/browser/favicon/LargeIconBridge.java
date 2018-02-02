// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.favicon;

import android.graphics.Bitmap;
import android.util.LruCache;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * A Java API for using the C++ LargeIconService.
 *
 * An instance of this class must be created, used, and destroyed on the same thread.
 */
public class LargeIconBridge {

    private static final int CACHE_ENTRY_MIN_SIZE_BYTES = 1024;
    private long mNativeLargeIconBridge;
    private Profile mProfile;
    private LruCache<String, CachedFavicon> mFaviconCache;

    private static class CachedFavicon {
        public Bitmap icon;
        public int fallbackColor;
        public boolean isFallbackColorDefault;

        CachedFavicon(Bitmap newIcon, int newFallbackColor, boolean newIsFallbackColorDefault) {
            icon = newIcon;
            fallbackColor = newFallbackColor;
            isFallbackColorDefault = newIsFallbackColorDefault;
        }
    }

    /**
     * Callback for use with GetLargeIconForUrl().
     */
    public interface LargeIconCallback {
        /**
         * Called when the icon or fallback color is available.
         *
         * @param icon The icon, or null if none is available.
         * @param fallbackColor The fallback color to use if icon is null.
         */
        @CalledByNative("LargeIconCallback")
        void onLargeIconAvailable(Bitmap icon, int fallbackColor, boolean isFallbackColorDefault);
    }

    /**
     * Initializes the C++ side of this class.
     * @param profile Profile to use when fetching icons.
     */
    public LargeIconBridge(Profile profile) {
        mNativeLargeIconBridge = nativeInit();
        mProfile = profile;
    }

    /**
     * Create an internal cache.
     * @param cacheSizeBytes The maximum size of the cache in bytes. Must be greater than 0. Note
     *                       that this will be an approximate as there is no easy way to measure
     *                       the precise size in Java.
     */
    public void createCache(int cacheSizeBytes) {
        assert cacheSizeBytes > 0;

        mFaviconCache = new LruCache<String, CachedFavicon>(cacheSizeBytes) {
            @Override
            protected int sizeOf(String key, CachedFavicon favicon) {
                int iconBitmapSize = favicon.icon == null ? 0 : favicon.icon.getByteCount();
                return Math.max(CACHE_ENTRY_MIN_SIZE_BYTES, iconBitmapSize);
            }
        };
    }

    /**
     * Deletes the C++ side of this class. This must be called when this object is no longer needed.
     */
    public void destroy() {
        assert mNativeLargeIconBridge != 0;
        nativeDestroy(mNativeLargeIconBridge);
        mNativeLargeIconBridge = 0;
    }

    /**
     * Given a URL, returns a large icon for that URL if one is available (e.g. a favicon or
     * touch icon). If none is available, a fallback color is returned, based on the dominant color
     * of any small icons for the URL, or a default gray if no small icons are available. The icon
     * and fallback color are returned synchronously(when it's from cache) or asynchronously to the
     * given callback.
     *
     * @param pageUrl The URL of the page whose icon will be fetched.
     * @param desiredSizePx The desired size of the icon in pixels.
     * @param callback The method to call asynchronously when the result is available. This callback
     *                 will not be called if this method returns false.
     * @return True if a callback should be expected.
     */
    public boolean getLargeIconForUrl(final String pageUrl, int desiredSizePx,
            final LargeIconCallback callback) {
        assert mNativeLargeIconBridge != 0;
        assert callback != null;

        if (mFaviconCache == null) {
            return nativeGetLargeIconForURL(mNativeLargeIconBridge, mProfile, pageUrl,
                    desiredSizePx, callback);
        } else {
            CachedFavicon cached = mFaviconCache.get(pageUrl);
            if (cached != null) {
                callback.onLargeIconAvailable(
                        cached.icon, cached.fallbackColor, cached.isFallbackColorDefault);
                return true;
            }

            LargeIconCallback callbackWrapper = new LargeIconCallback() {
                @Override
                public void onLargeIconAvailable(
                        Bitmap icon, int fallbackColor, boolean isFallbackColorDefault) {
                    mFaviconCache.put(pageUrl,
                            new CachedFavicon(icon, fallbackColor, isFallbackColorDefault));
                    callback.onLargeIconAvailable(icon, fallbackColor, isFallbackColorDefault);
                }
            };
            return nativeGetLargeIconForURL(mNativeLargeIconBridge, mProfile, pageUrl,
                    desiredSizePx, callbackWrapper);
        }
    }

    private static native long nativeInit();
    private static native void nativeDestroy(long nativeLargeIconBridge);
    private static native boolean nativeGetLargeIconForURL(long nativeLargeIconBridge,
            Profile profile, String pageUrl, int desiredSizePx, LargeIconCallback callback);
}
