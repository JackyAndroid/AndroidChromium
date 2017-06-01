// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.Context;
import android.os.Bundle;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeBackgroundServiceWaiter;
import org.chromium.chrome.browser.offlinepages.interfaces.BackgroundSchedulerProcessor;

/**
 * Handles servicing of background offlining requests coming via the GcmNetworkManager.
 */
public class BackgroundOfflinerTask {
    private static final String TAG = "BGOfflinerTask";
    private static final long DEFER_START_SECONDS = 5 * 60;

    public BackgroundOfflinerTask(BackgroundSchedulerProcessor bridge) {
        mBridge = bridge;
    }

    private final BackgroundSchedulerProcessor mBridge;

    /**
     * Triggers processing of background offlining requests.  This is called when
     * system conditions are appropriate for background offlining, typically from the
     * GcmTaskService onRunTask() method.  In response, we will start the
     * task processing by passing the call along to the C++ RequestCoordinator.
     * Also starts UMA collection.
     *
     * @returns true for success
     */
    public boolean startBackgroundRequests(Context context, Bundle bundle,
                                           ChromeBackgroundServiceWaiter waiter) {
        // Set up backup scheduled task in case processing is killed before RequestCoordinator
        // has a chance to reschedule base on remaining work.
        TriggerConditions previousTriggerConditions =
                TaskExtrasPacker.unpackTriggerConditionsFromBundle(bundle);
        BackgroundScheduler.backupSchedule(context, previousTriggerConditions, DEFER_START_SECONDS);

        DeviceConditions currentConditions = OfflinePageUtils.getDeviceConditions(context);
        if (!currentConditions.isPowerConnected()
                && currentConditions.getBatteryPercentage()
                        < previousTriggerConditions.getMinimumBatteryPercentage()) {
            Log.d(TAG, "Battery percentage is lower than minimum to start processing");
            return false;
        }

        if (SysUtils.isLowEndDevice() && ApplicationStatus.hasVisibleActivities()) {
            Log.d(TAG, "Application visible on low-end device so deferring background processing");
            return false;
        }

        // Now initiate processing.
        processBackgroundRequests(bundle, currentConditions, waiter);

        // Gather UMA data to measure how often the user's machine is amenable to background
        // loading when we wake to do a task.
        long taskScheduledTimeMillis = TaskExtrasPacker.unpackTimeFromBundle(bundle);
        OfflinePageUtils.recordWakeupUMA(context, taskScheduledTimeMillis);

        return true;
    }

    /**
     * Triggers processing of background offlining requests.
     */
    // TODO(petewil): Change back to private when UMA works in the test, and test
    // startBackgroundRequests instead of this method.
    @VisibleForTesting
    public void processBackgroundRequests(
            Bundle bundle, DeviceConditions deviceConditions,
            final ChromeBackgroundServiceWaiter waiter) {
        // TODO(petewil): Nothing is holding the Wake Lock.  We need some solution to
        // keep hold of it.  Options discussed so far are having a fresh set of functions
        // to grab and release a countdown latch, or holding onto the wake lock ourselves,
        // or grabbing the wake lock and then starting chrome and running startProcessing
        // on the UI thread.

        // TODO(petewil): Decode the TriggerConditions from the bundle.

        Callback<Boolean> callback = new Callback<Boolean>() {
            /**
             * Callback function which indicates completion of background work.
             * @param result - true if work was actually done (used for UMA).
             */
            @Override
            public void onResult(Boolean result) {
                // Release the wake lock.
                Log.d(TAG, "onProcessingDone");
                waiter.onWaitDone();
            }
        };

        // Pass the activation on to the bridge to the C++ RequestCoordinator.
        if (!mBridge.startProcessing(deviceConditions, callback)) {
            // Processing not started currently. Let callback know.
            callback.onResult(false);
        }
    }
}
