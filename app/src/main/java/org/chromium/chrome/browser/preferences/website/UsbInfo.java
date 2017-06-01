// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import java.io.Serializable;

/**
 * USB device information for a given origin.
 *
 * These objects are compared only by the identity of the device, not by which site has permission
 * to access it.
 */
public class UsbInfo implements Serializable {
    private final String mOrigin;
    private final String mEmbedder;
    private final String mName;
    private final String mObject;

    UsbInfo(String origin, String embedder, String name, String object) {
        mOrigin = origin;
        mEmbedder = embedder;
        mName = name;
        mObject = object;
    }

    /**
     * Returns the origin that requested the permission.
     */
    public String getOrigin() {
        return mOrigin;
    }

    /**
     * Returns the origin that the requester was embedded in.
     */
    public String getEmbedder() {
        return mEmbedder;
    }

    /**
     * Returns the name of the USB device for display in the UI.
     */
    public String getName() {
        return mName;
    }

    /**
     * Returns the opaque object string that represents the device.
     */
    public String getObject() {
        return mObject;
    }

    /**
     * Revokes permission for the origin to access the USB device.
     */
    public void revoke() {
        WebsitePreferenceBridge.nativeRevokeUsbPermission(mOrigin, mEmbedder, mObject);
    }
}
