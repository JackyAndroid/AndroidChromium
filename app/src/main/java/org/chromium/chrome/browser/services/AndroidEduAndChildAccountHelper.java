// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.services;

import android.content.Context;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.childaccounts.ChildAccountService;

/**
 * A helper for Android EDU and child account checks.
 * Usage:
 * new AndroidEduAndChildAccountHelper() { override onParametersReady() }.start(appContext).
 */
public abstract class AndroidEduAndChildAccountHelper
        implements Callback<Boolean>, AndroidEduOwnerCheckCallback {
    private Boolean mIsAndroidEduDevice;
    private Boolean mHasChildAccount;
    // Abbreviated to < 20 chars.
    private static final String TAG = "EduChildHelper";

    /** The callback called when Android EDU and child account parameters are known. */
    public abstract void onParametersReady();

    /** @return Whether the device is Android EDU device. */
    public boolean isAndroidEduDevice() {
        return mIsAndroidEduDevice;
    }

    /** @return Whether the device has a child account. */
    public boolean hasChildAccount() {
        return mHasChildAccount;
    }

    /**
     * Starts fetching the Android EDU and child accounts information.
     * Calls onParametersReady() once the information is fetched.
     * @param appContext The application context.
     */
    public void start(Context appContext) {
        Log.d(TAG, "before checking child and EDU");
        ChildAccountService.checkHasChildAccount(appContext, this);
        ((ChromeApplication) appContext).checkIsAndroidEduDevice(this);
        // TODO(aruslan): Should we start a watchdog to kill if Child/Edu stuff takes too long?
        Log.d(TAG, "returning from start");
    }

    private void checkDone() {
        if (mIsAndroidEduDevice == null || mHasChildAccount == null) return;
        Log.d(TAG, "parameters are ready");
        onParametersReady();
    }

    // AndroidEdu.OwnerCheckCallback:
    @Override
    public void onSchoolCheckDone(boolean isAndroidEduDevice) {
        Log.d(TAG, "onSchoolCheckDone");
        mIsAndroidEduDevice = isAndroidEduDevice;
        checkDone();
    }

    // Callback<Boolean>:
    @Override
    public void onResult(Boolean hasChildAccount) {
        Log.d(TAG, "onChildAccountChecked");
        mHasChildAccount = hasChildAccount;
        checkDone();
    }
}
