// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import org.chromium.chrome.browser.init.ProcessInitializationHandler;

/**
 * A Service that provides access to {@link ChromeBrowserSyncAdapter}.
 */
public class ChromeBrowserSyncAdapterService extends Service {
    private static ChromeBrowserSyncAdapter sSyncAdapter = null;
    private static final Object LOCK = new Object();

    /**
     * Get the sync adapter reference, creating an instance if necessary.
     */
    private ChromeBrowserSyncAdapter getOrCreateSyncAdapter(Context applicationContext) {
        synchronized (LOCK) {
            if (sSyncAdapter == null) {
                ProcessInitializationHandler.getInstance().initializePreNative();
                sSyncAdapter = new ChromeBrowserSyncAdapter(applicationContext, getApplication());
            }
        }
        return sSyncAdapter;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return getOrCreateSyncAdapter(getApplicationContext()).getSyncAdapterBinder();
    }
}
