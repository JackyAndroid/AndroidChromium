// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

/**
 * Geolocation information for a given origin.
 */
public class GeolocationInfo extends PermissionInfo {
    public GeolocationInfo(String origin, String embedder, boolean isIncognito) {
        super(origin, embedder, isIncognito);
    }

    @Override
    protected int getNativePreferenceValue(String origin, String embedder, boolean isIncognito) {
        return WebsitePreferenceBridge.nativeGetGeolocationSettingForOrigin(
                origin, embedder, isIncognito);
    }

    @Override
    protected void setNativePreferenceValue(
            String origin, String embedder, ContentSetting value, boolean isIncognito) {
        WebsitePreferenceBridge.nativeSetGeolocationSettingForOrigin(
                origin, embedder, value.toInt(), isIncognito);
    }
}
