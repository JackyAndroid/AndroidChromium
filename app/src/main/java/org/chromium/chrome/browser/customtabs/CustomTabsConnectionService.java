// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.chromium.chrome.browser.firstrun.FirstRunFlowSequencer;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;

/**
 * Custom tabs connection service, used by the embedded Chrome activities.
 */
public class CustomTabsConnectionService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        boolean firstRunNecessary = FirstRunFlowSequencer
                .checkIfFirstRunIsNecessary(getApplicationContext(), false) != null;
        if (firstRunNecessary) return null;
        if (!ChromePreferenceManager.getInstance(this).getCustomTabsEnabled()) return null;

        return (IBinder) CustomTabsConnection.getInstance(getApplication());
    }

    @Override
    public boolean onUnbind(Intent intent) {
        super.onUnbind(intent);
        return false; // No support for onRebind().
    }
}
