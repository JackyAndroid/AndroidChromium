// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.precache;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.sync.ProfileSyncService;

import java.util.Set;
import java.util.concurrent.FutureTask;

/**
 * Provides a timeoutable interface to wait for ProfileSyncService backend initialization and
 * configuration of a set of sync datatypes. If the sync service backend state successfully
 * initializes and configuration is complete for all the given sync datatypes, onDataTypesActive()
 * will be called. Otherwise onFailureOrTimedOut() will be invoked after a specified timeout.
 *
 * Objects of this class should be created and used only in the UI thread.
 */
public class SyncServiceInitializedNotifier implements ProfileSyncService.SyncStateChangedListener {
    /**
     * Listener for the sync service backend initialization or timeout.
     */
    public interface Listener {
        // Invoked when the backend is initialized, and configuration done for the datatypes.
        public void onDataTypesActive();

        // Invoked when timed-out.
        public void onFailureOrTimedOut();
    }

    private ProfileSyncService mSyncService;
    private Set<Integer> mActiveDataTypes;
    private Listener mListener;
    private FutureTask<?> mTimeoutTask;

    public SyncServiceInitializedNotifier(
            Set<Integer> activeDataTypes, Listener listener, long timeoutMillis) {
        assert listener != null;
        ThreadUtils.assertOnUiThread();
        mListener = listener;
        mActiveDataTypes = activeDataTypes;

        mSyncService = ProfileSyncService.get();
        if (mSyncService == null) {
            onFailureOrTimedOut();
            return;
        }
        mSyncService.addSyncStateChangedListener(this);
        mTimeoutTask = new FutureTask<Void>(new Runnable() {
            @Override
            public void run() {
                onFailureOrTimedOut();
            }
        }, null);
        ThreadUtils.postOnUiThreadDelayed(mTimeoutTask, timeoutMillis);
        // Call the listener once, in case the sync service configuration is already done.
        syncStateChanged();
    }

    @Override
    public void syncStateChanged() {
        ThreadUtils.assertOnUiThread();
        assert mSyncService != null;
        if (mSyncService.isSyncActive()
                && mSyncService.getActiveDataTypes().containsAll(mActiveDataTypes)) {
            onDataTypesActive();
        }
    }

    private void onDataTypesActive() {
        mSyncService.removeSyncStateChangedListener(this);
        if (!mTimeoutTask.isDone()) {
            mTimeoutTask.cancel(false);
        }
        mListener.onDataTypesActive();
    }

    private void onFailureOrTimedOut() {
        if (mSyncService != null) {
            mSyncService.removeSyncStateChangedListener(this);
        }
        mListener.onFailureOrTimedOut();
    }
}
