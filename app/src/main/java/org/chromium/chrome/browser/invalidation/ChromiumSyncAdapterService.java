// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public abstract class ChromiumSyncAdapterService extends Service {
    private static ChromiumSyncAdapter sSyncAdapter = null;
    private static final Object LOCK = new Object();

    /**
     * Get the sync adapter reference, creating an instance if necessary.
     */
    private ChromiumSyncAdapter getOrCreateSyncAdapter(Context applicationContext) {
        synchronized (LOCK) {
            if (sSyncAdapter == null) {
                sSyncAdapter = createChromiumSyncAdapter(applicationContext, getApplication());
            }
        }
        return sSyncAdapter;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return getOrCreateSyncAdapter(getApplicationContext()).getSyncAdapterBinder();
    }

    protected abstract ChromiumSyncAdapter createChromiumSyncAdapter(
            Context applicationContext, Application application);
}
