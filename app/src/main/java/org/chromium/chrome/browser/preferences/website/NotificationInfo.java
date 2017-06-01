// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

/**
 * Notification information for a given origin.
 */
public class NotificationInfo extends PermissionInfo {
    public NotificationInfo(String origin, String embedder, boolean isIncognito) {
        super(origin, embedder, isIncognito);
    }

    @Override
    protected int getNativePreferenceValue(String origin, String embedder, boolean isIncognito) {
        return WebsitePreferenceBridge.nativeGetNotificationSettingForOrigin(
                origin, isIncognito);
    }

    @Override
    protected void setNativePreferenceValue(
            String origin, String embedder, ContentSetting value, boolean isIncognito) {
        WebsitePreferenceBridge.nativeSetNotificationSettingForOrigin(
                origin, value.toInt(), isIncognito);
    }
}
