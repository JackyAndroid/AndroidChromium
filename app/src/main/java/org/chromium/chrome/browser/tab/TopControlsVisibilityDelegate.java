// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.components.security_state.ConnectionSecurityLevel;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;

/**
 * A delegate to determine visibility of the top controls.
 */
public class TopControlsVisibilityDelegate {
    protected final Tab mTab;

    /**
     * Basic constructor.
     * @param tab The associated {@link Tab}.
     */
    public TopControlsVisibilityDelegate(Tab tab) {
        mTab = tab;
    }

    /**
     * @return Whether hiding top controls is enabled or not.
     */
    public boolean isHidingTopControlsEnabled() {
        WebContents webContents = mTab.getWebContents();
        if (webContents == null || webContents.isDestroyed()) return false;

        String url = mTab.getUrl();
        boolean enableHidingTopControls = url != null;
        enableHidingTopControls &= !url.startsWith(UrlConstants.CHROME_SCHEME);
        enableHidingTopControls &= !url.startsWith(UrlConstants.CHROME_NATIVE_SCHEME);

        int securityState = mTab.getSecurityLevel();
        enableHidingTopControls &= (securityState != ConnectionSecurityLevel.DANGEROUS
                && securityState != ConnectionSecurityLevel.SECURITY_WARNING);

        enableHidingTopControls &=
                !AccessibilityUtil.isAccessibilityEnabled(mTab.getApplicationContext());

        ContentViewCore cvc = mTab.getContentViewCore();
        enableHidingTopControls &= cvc == null || !cvc.isFocusedNodeEditable();
        enableHidingTopControls &= !mTab.isShowingErrorPage();
        enableHidingTopControls &= !webContents.isShowingInterstitialPage();
        enableHidingTopControls &= (mTab.getFullscreenManager() != null);
        enableHidingTopControls &= DeviceClassManager.enableFullscreen();
        enableHidingTopControls &= !mTab.isFullscreenWaitingForLoad();

        return enableHidingTopControls;
    }

    /**
     * @return Whether showing top controls is enabled or not.
     */
    public boolean isShowingTopControlsEnabled() {
        if (mTab.getFullscreenManager() == null) return true;
        return !mTab.getFullscreenManager().getPersistentFullscreenMode();
    }
}
