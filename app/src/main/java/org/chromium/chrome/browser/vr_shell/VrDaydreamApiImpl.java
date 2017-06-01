// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;

import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.DaydreamApi;
import com.google.vr.ndk.base.GvrApi;

import org.chromium.base.annotations.UsedByReflection;

/**
 * A wrapper for DaydreamApi. Note that we have to recreate the DaydreamApi instance each time we
 * use it, or API calls begin to silently fail.
 */
@UsedByReflection("VrShellDelegate.java")
public class VrDaydreamApiImpl implements VrDaydreamApi {
    private final Activity mActivity;

    @UsedByReflection("VrShellDelegate.java")
    public VrDaydreamApiImpl(Activity activity) {
        mActivity = activity;
    }

    @Override
    public boolean isDaydreamReadyDevice() {
        return DaydreamApi.isDaydreamReadyPlatform(mActivity);
    }

    @Override
    public boolean registerDaydreamIntent(final PendingIntent pendingIntent) {
        DaydreamApi daydreamApi = DaydreamApi.create(mActivity);
        if (daydreamApi == null) return false;
        daydreamApi.registerDaydreamIntent(pendingIntent);
        daydreamApi.close();
        return true;
    }

    @Override
    public boolean unregisterDaydreamIntent() {
        DaydreamApi daydreamApi = DaydreamApi.create(mActivity);
        if (daydreamApi == null) return false;
        daydreamApi.unregisterDaydreamIntent();
        daydreamApi.close();
        return true;
    }

    @Override
    public Intent createVrIntent(final ComponentName componentName) {
        return DaydreamApi.createVrIntent(componentName);
    }

    @Override
    public boolean launchInVr(final PendingIntent pendingIntent) {
        DaydreamApi daydreamApi = DaydreamApi.create(mActivity);
        if (daydreamApi == null) return false;
        daydreamApi.launchInVr(pendingIntent);
        daydreamApi.close();
        return true;
    }

    @Override
    public boolean exitFromVr(int requestCode, final Intent intent) {
        DaydreamApi daydreamApi = DaydreamApi.create(mActivity);
        if (daydreamApi == null) return false;
        daydreamApi.exitFromVr(mActivity, requestCode, intent);
        daydreamApi.close();
        return true;
    }

    @Override
    public void setVrModeEnabled(boolean enabled) {
        AndroidCompat.setVrModeEnabled(mActivity, enabled);
    }

    @Override
    public Boolean isDaydreamCurrentViewer() {
        DaydreamApi daydreamApi = DaydreamApi.create(mActivity);
        if (daydreamApi == null) return false;
        int type = daydreamApi.getCurrentViewerType();
        daydreamApi.close();
        return type == GvrApi.ViewerType.DAYDREAM;
    }

    @Override
    public void launchVrHomescreen() {
        DaydreamApi daydreamApi = DaydreamApi.create(mActivity);
        if (daydreamApi == null) return;
        daydreamApi.launchVrHomescreen();
        daydreamApi.close();
    }
}
