// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ssl;

import org.chromium.components.security_state.ConnectionSecurityLevel;
import org.chromium.content_public.browser.WebContents;

/**
 * Provides a way of accessing helpers for page security state.
 */
public class SecurityStateModel {
    /**
     * Fetch the security level for a given web contents.
     *
     * @param webContents The web contents to get the security level for.
     * @return The ConnectionSecurityLevel for the specified web contents.
     *
     * @see ConnectionSecurityLevel
     */
    public static int getSecurityLevelForWebContents(WebContents webContents) {
        if (webContents == null) return ConnectionSecurityLevel.NONE;
        return nativeGetSecurityLevelForWebContents(webContents);
    }

    /**
     * @param webContents The web contents to query for deprecated SHA-1 presence.
     * @return Whether the security level of the page was deprecated due to SHA-1.
     */
    public static boolean isDeprecatedSHA1Present(WebContents webContents) {
        if (webContents == null) return false;
        return nativeIsDeprecatedSHA1Present(webContents);
    }

    /**
     * @param webContents The web contents to query for passive mixed content presence.
     * @return Whether the page contains passive mixed content.
     */
    public static boolean isPassiveMixedContentPresent(WebContents webContents) {
        if (webContents == null) return false;
        return nativeIsPassiveMixedContentPresent(webContents);
    }

    private SecurityStateModel() {}

    private static native int nativeGetSecurityLevelForWebContents(WebContents webContents);
    private static native boolean nativeIsDeprecatedSHA1Present(WebContents webContents);
    private static native boolean nativeIsPassiveMixedContentPresent(WebContents webContents);
}
