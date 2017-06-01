// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Class to wait for a task to complete before releasing control back to the caller.
 */
public class ChromeBackgroundServiceWaiter {
    private static final String TAG = "CBSWaiter";
    /** Synchronization object to control the wait. */
    private final CountDownLatch mLatch;
    /** How long to wait for before giving up */
    private int mWakelockTimeoutSeconds;

    public ChromeBackgroundServiceWaiter(int wakelockTimeoutSeconds) {
        mWakelockTimeoutSeconds = wakelockTimeoutSeconds;
        mLatch = new CountDownLatch(1);
    }

    /**
     * Wait, blocking the current thread until another thread calls onWaitDone.
     */
    public void startWaiting() {
        try {
            boolean waitSucceeded = mLatch.await(mWakelockTimeoutSeconds, TimeUnit.SECONDS);
            if (!waitSucceeded) {
                Log.d(TAG, "waiting for latch timed out");
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "ChromeBackgroundServiceWaiter interrupted while holding wake lock. " + e);
        }
    }


    /**
     * Called when the wait is complete.
     */
    public void onWaitDone() {
        // Release the waited thread to return to the caller, and thus release the wake lock held on
        // behalf of the Owner.
        mLatch.countDown();
    }
}
