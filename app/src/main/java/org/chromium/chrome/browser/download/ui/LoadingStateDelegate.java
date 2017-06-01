// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

/**
 * Determines when the data from all of the backends has been loaded.
 * <p>
 * TODO(ianwen): add a timeout mechanism to either the DownloadLoadingDelegate or to the
 * backend so that if it takes forever to load one of the backend, users are still able to see
 * the other two.
 */
public class LoadingStateDelegate {
    public static final int REGULAR_DOWNLOADS = 0b001;
    public static final int INCOGNITO_DOWNLOADS = 0b010;
    public static final int OFFLINE_PAGES = 0b100;

    private static final int ALL_LOADED = 0b111;

    private int mLoadingState;
    private int mPendingFilter = DownloadFilter.FILTER_ALL;

    /** @param offTheRecord Whether this delegate needs to consider incognito. */
    public LoadingStateDelegate(boolean offTheRecord) {
        // If we don't care about incognito, mark it as loaded.
        mLoadingState = offTheRecord ? 0 : INCOGNITO_DOWNLOADS;
    }

    /**
     * Tells this delegate one of the three backends has been loaded.
     * @param backendFlag Which backend was loaded.
     * @return Whether or not the backends are all loaded.
     */
    public boolean updateLoadingState(int backendFlag) {
        mLoadingState |= backendFlag;
        return isLoaded();
    }

    /** @return Whether all backends are loaded. */
    public boolean isLoaded() {
        return mLoadingState == ALL_LOADED;
    }

    /** Caches a filter for when the backends have loaded. */
    public void setPendingFilter(int filter) {
        mPendingFilter = filter;
    }

    /** @return The cached filter, or {@link DownloadFilter#FILTER_ALL} if none was set. */
    public int getPendingFilter() {
        return mPendingFilter;
    }
}
