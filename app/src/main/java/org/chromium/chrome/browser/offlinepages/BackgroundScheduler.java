// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;

import org.chromium.chrome.browser.ChromeBackgroundService;

/**
 * The background scheduler class is for setting GCM Network Manager tasks.
 */
public class BackgroundScheduler {
    private static final long ONE_WEEK_IN_SECONDS = 60 * 60 * 24 * 7;
    private static final long NO_DELAY = 0;
    private static final boolean OVERWRITE = true;

    /**
     * For the given Triggering conditions, start a new GCM Network Manager request.
     */
    public static void schedule(Context context, TriggerConditions triggerConditions) {
        schedule(context, triggerConditions, NO_DELAY, OVERWRITE);
    }

    /**
     * If there is no currently scheduled task, then start a GCM Network Manager request
     * for the given Triggering conditions but delayed to run after {@code delayStartSecs}.
     * Typically, the Request Coordinator will overwrite this task after task processing
     * and/or queue updates. This is a backup task in case processing is killed by the
     * system.
     */
    public static void backupSchedule(
            Context context, TriggerConditions triggerConditions, long delayStartSecs) {
        schedule(context, triggerConditions, delayStartSecs, !OVERWRITE);
    }

    /**
     * Cancel any outstanding GCM Network Manager requests.
     */
    public static void unschedule(Context context) {
        // Get the GCM Network Scheduler.
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);
        gcmNetworkManager.cancelTask(OfflinePageUtils.TASK_TAG, ChromeBackgroundService.class);
    }

    /**
     * For the given Triggering conditions, start a new GCM Network Manager request allowed
     * to run after {@code delayStartSecs} seconds.
     */
    private static void schedule(Context context, TriggerConditions triggerConditions,
            long delayStartSecs, boolean overwrite) {
        // Get the GCM Network Scheduler.
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);

        Bundle taskExtras = new Bundle();
        TaskExtrasPacker.packTimeInBundle(taskExtras);
        TaskExtrasPacker.packTriggerConditionsInBundle(taskExtras, triggerConditions);

        Task task = new OneoffTask.Builder()
                            .setService(ChromeBackgroundService.class)
                            .setExecutionWindow(delayStartSecs, ONE_WEEK_IN_SECONDS)
                            .setTag(OfflinePageUtils.TASK_TAG)
                            .setUpdateCurrent(overwrite)
                            .setRequiredNetwork(triggerConditions.requireUnmeteredNetwork()
                                            ? Task.NETWORK_STATE_UNMETERED
                                            : Task.NETWORK_STATE_CONNECTED)
                            .setRequiresCharging(triggerConditions.requirePowerConnected())
                            .setExtras(taskExtras)
                            .build();

        gcmNetworkManager.schedule(task);
    }
}
