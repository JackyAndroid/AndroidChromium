// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ResourceId;
import org.chromium.content_public.browser.WebContents;

/**
 * Provides JNI methods for DataReductionProxyInfoBars.
 */
public class DataReductionProxyInfoBarDelegate {
    /**
     * Launches the {@link InfoBar}.
     * @param webContents The {@link WebContents} in which to launch the {@link InfoBar}.
     */
    static void launch(WebContents webContents, String linkUrl) {
        nativeLaunch(webContents, linkUrl);
    }

    private DataReductionProxyInfoBarDelegate() {
    }

    @CalledByNative
    public static DataReductionProxyInfoBarDelegate create() {
        return new DataReductionProxyInfoBarDelegate();
    }

    /**
     * Creates and begins the process for showing a DataReductionProxyInfoBarDelegate.
     * @param enumeratedIconId ID corresponding to the icon that will be shown for the InfoBar.
     *                         The ID must have been mapped using the ResourceMapper class before
     *                         passing it to this function.
     */
    @CalledByNative
    InfoBar showDataReductionProxyInfoBar(int enumeratedIconId) {
        int drawableId = ResourceId.mapToDrawableId(enumeratedIconId);
        DataReductionProxyInfoBar infoBar = new DataReductionProxyInfoBar(drawableId);
        return infoBar;
    }

    private static native void nativeLaunch(WebContents webContents, String linkUrl);
}
