// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

/**
 * Provides methods for querying Android system-wide location settings as well as Chrome's internal
 * location setting.
 *
 * This class should be used only on the UI thread.
 */
public class LocationSettings {

    private static LocationSettings sInstance;

    protected final Context mContext;

    /**
     * Don't use this; use getInstance() instead. This should be used only by the Application inside
     * of createLocationSettings().
     */
    protected LocationSettings(Context context) {
        mContext = context;
    }

    /**
     * Returns the singleton instance of LocationSettings, creating it if needed.
     */
    @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
    public static LocationSettings getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            ChromeApplication application =
                    (ChromeApplication) ApplicationStatus.getApplicationContext();
            sInstance = application.createLocationSettings();
        }
        return sInstance;
    }

    @CalledByNative
    private static boolean canSitesRequestLocationPermission(WebContents webContents) {
        if (!LocationSettings.getInstance().isSystemLocationSettingEnabled()) return false;

        ContentViewCore cvc = ContentViewCore.fromWebContents(webContents);
        if (cvc == null) return false;
        WindowAndroid windowAndroid = cvc.getWindowAndroid();
        if (windowAndroid == null) return false;

        return windowAndroid.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                || windowAndroid.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                || windowAndroid.canRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /**
     * Returns true if location is enabled system-wide and the Chrome location setting is enabled.
     */
    public boolean areAllLocationSettingsEnabled() {
        return isChromeLocationSettingEnabled() && isSystemLocationSettingEnabled();
    }

    /**
     * Returns whether Chrome's user-configurable location setting is enabled.
     */
    public boolean isChromeLocationSettingEnabled() {
        return PrefServiceBridge.getInstance().isAllowLocationEnabled();
    }

    /**
     * Returns whether location is enabled system-wide, i.e. whether Chrome itself is able to access
     * location.
     */
    public boolean isSystemLocationSettingEnabled() {
        LocationManager locationManager =
                (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        return (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
    }

    /**
     * Returns an intent to launch Android Location Settings.
     */
    public Intent getSystemLocationSettingsIntent() {
        Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    @VisibleForTesting
    public static void setInstanceForTesting(LocationSettings instance) {
        sInstance = instance;
    }
}
