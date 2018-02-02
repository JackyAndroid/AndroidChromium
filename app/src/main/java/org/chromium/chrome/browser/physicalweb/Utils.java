// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.support.v4.content.PermissionChecker;

import org.chromium.base.ContextUtils;


/**
 * This class provides basic static utilities for the Physical Web.
 */
class Utils {
    public static final int RESULT_FAILURE = 0;
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_INDETERMINATE = 2;

    public static boolean isDataConnectionActive() {
        ConnectivityManager cm = (ConnectivityManager) ContextUtils.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        return (cm.getActiveNetworkInfo() != null
                && cm.getActiveNetworkInfo().isConnectedOrConnecting());
    }

    public static boolean isBluetoothPermissionGranted() {
        return PermissionChecker.checkSelfPermission(
                ContextUtils.getApplicationContext(), Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static int getBluetoothEnabledStatus() {
        int statusResult = RESULT_INDETERMINATE;
        if (isBluetoothPermissionGranted()) {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            statusResult = (bt != null && bt.isEnabled()) ? RESULT_SUCCESS : RESULT_FAILURE;
        }
        return statusResult;
    }
}
