// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.Manifest;

import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.components.location.LocationUtils;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

/**
 * Provides methods for querying Chrome's internal location setting and
 * combining that with the system-wide setting and permissions.
 *
 * This class should be used only on the UI thread.
 */
public class LocationSettings {

    private static LocationSettings sInstance;

    /**
     * Don't use this; use getInstance() instead. This should be used only by the Application inside
     * of createLocationSettings().
     */
    protected LocationSettings() {
    }

    /**
     * Returns the singleton instance of LocationSettings, creating it if needed.
     */
    @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
    public static LocationSettings getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            ChromeApplication application =
                    (ChromeApplication) ContextUtils.getApplicationContext();
            sInstance = application.createLocationSettings();
        }
        return sInstance;
    }

    @CalledByNative
    private static boolean canSitesRequestLocationPermission(WebContents webContents) {
        ContentViewCore cvc = ContentViewCore.fromWebContents(webContents);
        if (cvc == null) return false;
        WindowAndroid windowAndroid = cvc.getWindowAndroid();
        if (windowAndroid == null) return false;

        LocationUtils locationUtils = LocationUtils.getInstance();
        if (!locationUtils.isSystemLocationSettingEnabled()) return false;

        return locationUtils.hasAndroidLocationPermission()
                || windowAndroid.canRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /**
     * Returns true if location is enabled system-wide and the Chrome location setting is enabled.
     */
    public boolean areAllLocationSettingsEnabled() {
        return isChromeLocationSettingEnabled()
                && LocationUtils.getInstance().isSystemLocationSettingEnabled();
    }

    /**
     * Returns whether Chrome's user-configurable location setting is enabled.
     */
    public boolean isChromeLocationSettingEnabled() {
        return PrefServiceBridge.getInstance().isAllowLocationEnabled();
    }

    @VisibleForTesting
    public static void setInstanceForTesting(LocationSettings instance) {
        sInstance = instance;
    }
}
