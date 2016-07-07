// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.fullscreen;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.FullscreenInfo;
import org.chromium.chrome.browser.tab.Tab;

/**
 * Class for managing the fullscreen infobar.
 */
public class FullscreenInfoBarDelegate {
    private final FullscreenHtmlApiHandler mHandler;
    private final boolean mIsForIncognitoTab;
    private long mNativeFullscreenInfoBarDelegate = 0;

    /**
     * Create a FullscreenInfoBarDelegate and show the infobar to user.
     *
     * @param handler Handler to exit the fullscreen when user click cancel on the info bar.
     * @param tab Tab that enters fullscreen.
     */
    public static FullscreenInfoBarDelegate create(FullscreenHtmlApiHandler handler, Tab tab) {
        return new FullscreenInfoBarDelegate(handler, tab);
    }

    private FullscreenInfoBarDelegate(
            FullscreenHtmlApiHandler handler, Tab tab) {
        assert tab != null;
        mHandler = handler;
        mIsForIncognitoTab = tab.isIncognito();
        mNativeFullscreenInfoBarDelegate = nativeLaunchFullscreenInfoBar(tab);
    }

    /**
     * Close the fullscreen infobar.
     */
    protected void closeFullscreenInfoBar() {
        if (mNativeFullscreenInfoBarDelegate != 0) {
            nativeCloseFullscreenInfoBar(mNativeFullscreenInfoBarDelegate);
        }
    }

    /**
     * Called when fullscreen is allowed for a particular origin.
     *
     * @param origin The site origin that fullscreen is allowed.
     */
    @CalledByNative
    private void onFullscreenAllowed(String origin) {
        FullscreenInfo fullscreenInfo = new FullscreenInfo(origin, null, mIsForIncognitoTab);
        fullscreenInfo.setContentSetting(ContentSetting.ALLOW);
    }

    /**
     * Called when fullscreen is cancelled on the infobar.
     */
    @CalledByNative
    private void onFullscreenCancelled() {
        mHandler.setPersistentFullscreenMode(false);
    }

    /**
     * Called when the infobar is dismissed.
     */
    @CalledByNative
    private void onInfoBarDismissed() {
        mNativeFullscreenInfoBarDelegate = 0;
    }

    private native long nativeLaunchFullscreenInfoBar(Tab tab);
    private native void nativeCloseFullscreenInfoBar(long nativeFullscreenInfoBarDelegate);
}
