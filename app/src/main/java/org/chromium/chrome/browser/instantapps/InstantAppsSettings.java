// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.instantapps;

import org.chromium.content_public.browser.WebContents;

/**
 * A bridge class for retrieving Instant Apps-related settings.
 */
public class InstantAppsSettings {

    /**
     * Check whether the instant app at the given url should be opened by default.
     */
    public static boolean isInstantAppDefault(WebContents webContents, String url) {
        return nativeGetInstantAppDefault(webContents, url);
    }

    /**
     * Remember that the instant app at the given url should be opened by default.
     */
    public static void setInstantAppDefault(WebContents webContents, String url) {
        nativeSetInstantAppDefault(webContents, url);
    }

    /**
     * Check whether the banner promoting an instant app should be shown.
     */
    public static boolean shouldShowBanner(WebContents webContents, String url) {
        return nativeShouldShowBanner(webContents, url);
    }

    private static native boolean nativeGetInstantAppDefault(WebContents webContents, String url);
    private static native void nativeSetInstantAppDefault(WebContents webContents, String url);
    private static native boolean nativeShouldShowBanner(WebContents webContents, String url);
}
