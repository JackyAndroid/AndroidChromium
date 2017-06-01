// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import com.google.vr.ndk.base.Version;
import com.google.vr.vrcore.base.api.VrCoreNotAvailableException;
import com.google.vr.vrcore.base.api.VrCoreUtils;
import com.google.vr.vrcore.base.api.VrCoreUtils.ConnectionResult;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.UsedByReflection;

/**
 * Helper class to check if VrCore version is compatible with Chromium.
 */
@UsedByReflection("VrShellDelegate.java")
public class VrCoreVersionCheckerImpl implements VrCoreVersionChecker {
    private static final String TAG = "VrCoreVersionChecker";

    @UsedByReflection("VrShellDelegate.java")
    public VrCoreVersionCheckerImpl() {
    }

    @Override
    public boolean isVrCoreCompatible() {
        try {
            String vrCoreSdkLibraryVersionString = VrCoreUtils.getVrCoreSdkLibraryVersion(
                    ContextUtils.getApplicationContext());
            Version vrCoreSdkLibraryVersion = Version.parse(vrCoreSdkLibraryVersionString);
            Version targetSdkLibraryVersion =
                    Version.parse(com.google.vr.ndk.base.BuildConstants.VERSION);
            if (!vrCoreSdkLibraryVersion.isAtLeast(targetSdkLibraryVersion)) {
                throw new VrCoreNotAvailableException(ConnectionResult.SERVICE_OBSOLETE);
            }
            return true;
        } catch (VrCoreNotAvailableException e) {
            Log.i(TAG, "Unable to find a compatible VrCore.");
            return false;
        }
    }
}
