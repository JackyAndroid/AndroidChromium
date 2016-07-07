// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import org.chromium.chrome.browser.precache.PrecacheLauncher;

/**
 * When there is a change in the network connection,this will update the sharedpref value whether
 * to allow prefetch or not.
 */
public class ConnectionChangeReceiver extends BroadcastReceiver {

    private boolean mIsRegistered;

    public void registerReceiver(Context context) {
        mIsRegistered = true;
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(this, filter);
    }

    public void unregisterReceiver(Context context) {
        mIsRegistered = false;
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Only handle the action if we're currently registered. If we're not registered as a
        // listener, then we might be paused and native may not be loaded which would crash.
        if (mIsRegistered) {
            PrecacheLauncher.updatePrecachingEnabled(context.getApplicationContext());
        }
    }
}
