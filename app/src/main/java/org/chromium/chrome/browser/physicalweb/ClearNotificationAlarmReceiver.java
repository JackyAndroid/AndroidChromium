// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.chromium.base.Log;

/**
 * A broadcast receiver that clears the UrlManager's notification.
 * This class cannot make native calls because it may not be loaded when the
 * alarm fires.
 */
public class ClearNotificationAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "PhysicalWeb";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Running NotificationCleanupAlarmReceiver");
        UrlManager.getInstance().clearNotification();
    }
}
