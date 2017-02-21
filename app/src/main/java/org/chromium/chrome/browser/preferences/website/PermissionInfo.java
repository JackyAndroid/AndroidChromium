// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import java.io.Serializable;

/**
 * Permission information for a given origin.
 */
public abstract class PermissionInfo implements Serializable {
    private final String mOrigin;
    private final String mEmbedder;
    private final boolean mIsIncognito;

    public PermissionInfo(String origin, String embedder, boolean isIncognito) {
        mOrigin = origin;
        mEmbedder = embedder;
        mIsIncognito = isIncognito;
    }

    public String getOrigin() {
        return mOrigin;
    }

    public String getEmbedder() {
        return mEmbedder;
    }

    public boolean isIncognito() {
        return mIsIncognito;
    }

    public String getEmbedderSafe() {
        return mEmbedder != null ? mEmbedder : mOrigin;
    }

    /**
     * Returns the ContentSetting value for this origin.
     */
    public ContentSetting getContentSetting() {
        return ContentSetting.fromInt(
                getNativePreferenceValue(mOrigin, getEmbedderSafe(), mIsIncognito));
    }

    /**
     * Sets the native ContentSetting value for this origin.
     */
    public void setContentSetting(ContentSetting value) {
        setNativePreferenceValue(mOrigin, getEmbedderSafe(), value, mIsIncognito);
    }

    protected abstract int getNativePreferenceValue(
            String origin, String embedder, boolean isIncognito);

    protected abstract void setNativePreferenceValue(
            String origin, String embedder, ContentSetting value, boolean isIncognito);
}
