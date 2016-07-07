// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

/**
 * Handles requesting the android runtime permissions for the permission update infobar.
 */
class PermissionUpdateInfoBarDelegate implements WindowAndroid.PermissionCallback {

    private final WindowAndroid mWindowAndroid;
    private final String[] mAndroidPermisisons;
    private long mNativePtr;
    private ActivityStateListener mActivityStateListener;

    @CalledByNative
    private static PermissionUpdateInfoBarDelegate create(
            long nativePtr, WebContents webContents, String[] permissions) {
        return new PermissionUpdateInfoBarDelegate(nativePtr, webContents, permissions);
    }

    private PermissionUpdateInfoBarDelegate(
            long nativePtr, WebContents webContents, String[] permissions) {
        mNativePtr = nativePtr;
        mAndroidPermisisons = permissions;
        mWindowAndroid = ContentViewCore.fromWebContents(webContents).getWindowAndroid();
    }

    @CalledByNative
    private void onNativeDestroyed() {
        mNativePtr = 0;
        if (mActivityStateListener != null) {
            ApplicationStatus.unregisterActivityStateListener(mActivityStateListener);
            mActivityStateListener = null;
        }
    }

    @CalledByNative
    private void requestPermissions() {
        boolean canRequestAllPermissions = true;
        for (int i = 0; i < mAndroidPermisisons.length; i++) {
            canRequestAllPermissions &=
                    (mWindowAndroid.hasPermission(mAndroidPermisisons[i])
                            || mWindowAndroid.canRequestPermission(mAndroidPermisisons[i]));
        }

        if (canRequestAllPermissions) {
            mWindowAndroid.requestPermissions(mAndroidPermisisons, this);
        } else {
            Activity activity = mWindowAndroid.getActivity().get();
            if (activity == null) {
                nativeOnPermissionResult(mNativePtr, false);
                return;
            }

            mActivityStateListener = new ActivityStateListener() {
                @Override
                public void onActivityStateChange(Activity activity, int newState) {
                    if (newState == ActivityState.DESTROYED) {
                        ApplicationStatus.unregisterActivityStateListener(this);
                        mActivityStateListener = null;

                        nativeOnPermissionResult(mNativePtr, false);
                    } else if (newState == ActivityState.RESUMED) {
                        ApplicationStatus.unregisterActivityStateListener(this);
                        mActivityStateListener = null;

                        notifyPermissionResult();
                    }
                }
            };
            ApplicationStatus.registerStateListenerForActivity(mActivityStateListener, activity);

            Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            settingsIntent.setData(Uri.parse(
                    "package:" + mWindowAndroid.getApplicationContext().getPackageName()));
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(settingsIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
        notifyPermissionResult();
    }

    private void notifyPermissionResult() {
        boolean hasAllPermissions = true;
        for (int i = 0; i < mAndroidPermisisons.length; i++) {
            hasAllPermissions &=
                    mWindowAndroid.hasPermission(mAndroidPermisisons[i]);
        }
        if (mNativePtr != 0) nativeOnPermissionResult(mNativePtr, hasAllPermissions);
    }

    private native void nativeOnPermissionResult(
            long nativePermissionUpdateInfoBarDelegate, boolean allPermissionsGranted);
}
