// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.profiles;

import android.graphics.Bitmap;

import org.chromium.base.annotations.CalledByNative;

/**
 * Methods to bridge into native history to provide most recent urls, titles and thumbnails.
 */
public class MostVisitedSites {

    private long mNativeMostVisitedSites;

    /**
     * Interface for receiving the list of most visited urls.
     */
    public interface MostVisitedURLsObserver {
        /**
         * This is called when the list of most visited URLs is initially available or updated.
         * Parameters guaranteed to be non-null.
         *
         * @param titles Array of most visited url page titles.
         * @param urls Array of most visited URLs, including popular URLs if
         *             available and necessary (i.e. there aren't enough most
         *             visited URLs).
         */
        @CalledByNative("MostVisitedURLsObserver")
        public void onMostVisitedURLsAvailable(String[] titles, String[] urls);

        /**
         * This is called when the list of popular URLs is initially available or updated.
         * Parameters guaranteed to be non-null.
         *
         * @param urls Array of popular URLs.
         * @param faviconUrls Array of URLs for the corresponding favicons (if known).
         */
        @CalledByNative("MostVisitedURLsObserver")
        public void onPopularURLsAvailable(
                String[] urls, String[] faviconUrls, String[] largeIconUrls);
    }

    /**
     * Interface for receiving a thumbnail for a most visited site.
     */
    public interface ThumbnailCallback {
        /**
         * Callback method for fetching thumbnail of a most visited URL.
         * Parameter may be null.
         *
         * @param thumbnail The bitmap thumbnail for the requested URL.
         * @param isLocalThumbnail Whether the thumbnail was locally captured, as opposed to
         *                         server-provided.
         */
        @CalledByNative("ThumbnailCallback")
        public void onMostVisitedURLsThumbnailAvailable(Bitmap thumbnail, boolean isLocalThumbnail);
    }

    /**
     * MostVisitedSites constructor requires a valid user profile object.
     *
     * @param profile The profile for which to fetch most visited sites.
     */
    public MostVisitedSites(Profile profile) {
        mNativeMostVisitedSites = nativeInit(profile);
    }

    /**
     * Cleans up the C++ side of this class. This instance must not be used after calling destroy().
     */
    public void destroy() {
        assert mNativeMostVisitedSites != 0;
        nativeDestroy(mNativeMostVisitedSites);
        mNativeMostVisitedSites = 0;
    }

    /**
     * Sets the MostVisitedURLsObserver to receive the list of most visited sites now or soon, and
     * after any changes to the list. Note: the observer may be notified synchronously or
     * asynchronously.
     * @param observer The MostVisitedURLsObserver to be called once when the most visited sites
     *                 are initially available and again whenever the list of most visited sites
     *                 changes.
     * @param numSites The maximum number of most visited sites to return.
     */
    public void setMostVisitedURLsObserver(final MostVisitedURLsObserver observer, int numSites) {
        MostVisitedURLsObserver wrappedObserver = new MostVisitedURLsObserver() {
            @Override
            public void onMostVisitedURLsAvailable(String[] titles, String[] urls) {
                // Don't notify observer if we've already been destroyed.
                if (mNativeMostVisitedSites != 0) {
                    observer.onMostVisitedURLsAvailable(titles, urls);
                }
            }
            @Override
            public void onPopularURLsAvailable(
                    String[] urls, String[] faviconUrls, String[] largeIconUrls) {
                // Don't notify observer if we've already been destroyed.
                if (mNativeMostVisitedSites != 0) {
                    observer.onPopularURLsAvailable(urls, faviconUrls, largeIconUrls);
                }
            }
        };
        nativeSetMostVisitedURLsObserver(mNativeMostVisitedSites, wrappedObserver, numSites);
    }

    /**
     * Fetches thumbnail bitmap for a url returned by getMostVisitedURLs.
     *
     * @param url String representation of url.
     * @param callback Instance of a callback object.
     */
    public void getURLThumbnail(String url, final ThumbnailCallback callback) {
        ThumbnailCallback wrappedCallback = new ThumbnailCallback() {
            @Override
            public void onMostVisitedURLsThumbnailAvailable(Bitmap thumbnail,
                    boolean isLocalThumbnail) {
                // Don't notify callback if we've already been destroyed.
                if (mNativeMostVisitedSites != 0) {
                    callback.onMostVisitedURLsThumbnailAvailable(thumbnail, isLocalThumbnail);
                }
            }
        };
        nativeGetURLThumbnail(mNativeMostVisitedSites, url, wrappedCallback);
    }

    /**
     * Blacklists a URL from the most visited URLs list.
     * @param url The URL to be blacklisted.
     */
    public void blacklistUrl(String url) {
        nativeBlacklistUrl(mNativeMostVisitedSites, url);
    }

    /**
     * Records metrics about which types of tiles are displayed.
     * @param tileTypes An array of values from MostVisitedTileType indicating the type of each
     *                  tile that's currently showing.
     * @paral isIconMode Whether the icon-based version of the NTP is showing (as opposed to the
     *                   thumbnail-based version).
     */
    public void recordTileTypeMetrics(int[] tileTypes, boolean isIconMode) {
        nativeRecordTileTypeMetrics(mNativeMostVisitedSites, tileTypes, isIconMode);
    }

    /**
     * Records the opening of a Most Visited Item.
     * @param index The index of the item that was opened.
     * @param tileType The visual type of the item. Valid values are listed in MostVisitedTileType.
     */
    public void recordOpenedMostVisitedItem(int index, int tileType) {
        nativeRecordOpenedMostVisitedItem(mNativeMostVisitedSites, index, tileType);
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeMostVisitedSites);
    private native void nativeSetMostVisitedURLsObserver(long nativeMostVisitedSites,
            MostVisitedURLsObserver observer, int numSites);
    private native void nativeGetURLThumbnail(long nativeMostVisitedSites, String url,
            ThumbnailCallback callback);
    private native void nativeBlacklistUrl(long nativeMostVisitedSites, String url);
    private native void nativeRecordTileTypeMetrics(long nativeMostVisitedSites, int[] tileTypes,
            boolean isIconMode);
    private native void nativeRecordOpenedMostVisitedItem(long nativeMostVisitedSites, int index,
            int tileType);

}
