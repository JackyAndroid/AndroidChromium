// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.password_manager;

import android.graphics.Bitmap;

import org.chromium.base.annotations.CalledByNative;

/**
 * Credential type which is used to represent credential which will be shown in account chooser
 * infobar.
 * */
public class Credential {
    private final String mUsername;
    private final String mDisplayName;
    private final String mFederation;
    private final int mType;
    private final int mIndex;
    private Bitmap mAvatar;

    /**
     * @param username username which is used to authenticate user.
     *                 The value is PasswordForm::username_value.
     * @param displayName user friendly name to show in the UI. It can be empty.
     *                    The value is PasswordForm::display_name.
     * @param federation Identity provider name for this credential (empty for local credentials).
     * @param type type which should be either local or federated. The value corresponds to a
     *             C++ enum CredentialType.
     * @param index position in array of credentials.
     */
    public Credential(String username, String displayName, String federation, int type, int index) {
        mUsername = username;
        mDisplayName = displayName;
        mFederation = federation;
        mType = type;
        mIndex = index;
        mAvatar = null;
    }

    public String getUsername() {
        return mUsername;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getFederation() {
        return mFederation;
    }

    public int getIndex() {
        return mIndex;
    }

    public int getType() {
        return mType;
    }

    public Bitmap getAvatar() {
        return mAvatar;
    }

    public void setBitmap(Bitmap avatar) {
        mAvatar = avatar;
    }

    @CalledByNative
    private static Credential createCredential(
            String username, String displayName, String federation, int type, int index) {
        return new Credential(username, displayName, federation, type, index);
    }

    @CalledByNative
    private static Credential[] createCredentialArray(int size) {
        return new Credential[size];
    }
}
