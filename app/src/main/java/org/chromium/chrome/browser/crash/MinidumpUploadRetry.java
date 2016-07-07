// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.content.Context;
import android.os.Handler;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.util.NonThreadSafe;
import org.chromium.net.ConnectionType;
import org.chromium.net.NetworkChangeNotifier;

/**
 * This class listens to network changes and determine when it would good to
 * retry uploading minidumps.
 */
class MinidumpUploadRetry implements NetworkChangeNotifier.ConnectionTypeObserver {
    private final Context mContext;
    private static MinidumpUploadRetry sSingleton = null;

    private static class Scheduler implements Runnable {
        private static NonThreadSafe sThreadCheck;
        private final Context mContext;

        private Scheduler(Context context) {
            this.mContext = context;
        }

        @Override
        public void run() {
            if (sThreadCheck == null) {
                sThreadCheck = new NonThreadSafe();
            }
            // Make sure this is called on the same thread all the time.
            assert sThreadCheck.calledOnValidThread();
            if (!NetworkChangeNotifier.isInitialized()) {
                return;
            }
            if (sSingleton == null) {
                sSingleton = new MinidumpUploadRetry(mContext);
            }
        }
    }

    /**
     * Schedule a retry. If there is already one schedule, this is NO-OP.
     */
    static void scheduleRetry(Context context) {
        // NetworkChangeNotifier is not thread safe. We will post to UI thread
        // instead since that's where it fires off notification changes.
        new Handler(context.getMainLooper()).post(new Scheduler(context));
    }

    private MinidumpUploadRetry(Context context) {
        this.mContext = context;
        NetworkChangeNotifier.addConnectionTypeObserver(this);
    }

    @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    @Override
    public void onConnectionTypeChanged(int connectionType) {
        // Look for "favorable" connections. Note that we never
        // know what the user's crash upload preference is until
        // the time when we are actually uploading.
        if (connectionType == ConnectionType.CONNECTION_NONE) {
            return;
        }
        MinidumpUploadService.tryUploadAllCrashDumps(mContext);
        NetworkChangeNotifier.removeConnectionTypeObserver(this);
        sSingleton = null;
    }
}
