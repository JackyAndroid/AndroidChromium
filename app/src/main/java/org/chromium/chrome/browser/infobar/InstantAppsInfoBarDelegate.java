// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.instantapps.InstantAppsBannerData;
import org.chromium.chrome.browser.instantapps.InstantAppsHandler;
import org.chromium.content_public.browser.WebContents;

/**
 * Delegate for {@link InstantAppsInfoBar}. Use launch() method to display the infobar.
 */
public class InstantAppsInfoBarDelegate {
    private static final String TAG = "IAInfoBarDelegate";

    private InstantAppsBannerData mData;

    public static void launch(InstantAppsBannerData data) {
        nativeLaunch(data.getWebContents(), data, data.getUrl());
    }

    @CalledByNative
    private static InstantAppsInfoBarDelegate create() {
        return new InstantAppsInfoBarDelegate();
    }

    private InstantAppsInfoBarDelegate() {}

    @CalledByNative
    private void openInstantApp(InstantAppsBannerData data) {
        InstantAppsHandler.getInstance().launchFromBanner(data);
    }

    private static native void nativeLaunch(WebContents webContents, InstantAppsBannerData data,
            String url);
}
