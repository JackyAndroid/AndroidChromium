// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * Interface to the resource prefetch predictor.
 *
 * This allows to initiate and abort prefetches of likely subresources, based on
 * the local browsing history.
 */
@JNINamespace("predictors")
class ResourcePrefetchPredictor {
    private static boolean sInitializationStarted;

    private final Profile mProfile;

    /**
     * @param profile The profile used to get the prefetch predictor.
     */
    public ResourcePrefetchPredictor(Profile profile) {
        mProfile = profile;
    }

    /**
     * Starts the asynchronous initialization of the prefetch predictor.
     */
    public boolean startInitialization() {
        ThreadUtils.assertOnUiThread();
        sInitializationStarted = true;
        return nativeStartInitialization(mProfile);
    }

    /**
     * Starts a prefetch for a URL.
     *
     * @param url The URL to start the prefetch for.
     * @return false in case the ResourcePrefetchPredictor is not usable.
     */
    public boolean startPrefetching(String url) {
        ThreadUtils.assertOnUiThread();
        if (!sInitializationStarted) {
            throw new RuntimeException("startInitialization() not called.");
        }
        return nativeStartPrefetching(mProfile, url);
    }

    /**
     * Stops a prefetch for a URL, if one is in progress.
     *
     * @param url The URL to stop the prefetch of.
     * @return false in case the ResourcePrefetchPredictor is not usable.
     */
    public boolean stopPrefetching(String url) {
        ThreadUtils.assertOnUiThread();
        return nativeStopPrefetching(mProfile, url);
    }

    private static native boolean nativeStartInitialization(Profile profile);
    private static native boolean nativeStartPrefetching(Profile profile, String url);
    private static native boolean nativeStopPrefetching(Profile profile, String url);
}
