// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

/**
 * This class represents protected media identifier permission information for given requesting
 * frame origin and embedding frame origin.
 */
public class ProtectedMediaIdentifierInfo extends PermissionInfo {
    public ProtectedMediaIdentifierInfo(String origin, String embedder, boolean isIncognito) {
        super(origin, embedder, isIncognito);
    }

    @Override
    protected int getNativePreferenceValue(String origin, String embedder, boolean isIncognito) {
        return WebsitePreferenceBridge.nativeGetProtectedMediaIdentifierSettingForOrigin(
                origin, embedder, isIncognito);
    }

    @Override
    protected void setNativePreferenceValue(
            String origin, String embedder, ContentSetting value, boolean isIncognito) {
        WebsitePreferenceBridge.nativeSetProtectedMediaIdentifierSettingForOrigin(
                origin, embedder, value.toInt(), isIncognito);
    }
}
