// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeBackgroundService;

/**
 * Class for scheduing download resumption tasks.
 */
public class DownloadResumptionScheduler {
    public static final String TASK_TAG = "DownloadResumption";
    private static final String TAG = "DownloadScheduler";
    private static final int ONE_DAY_IN_SECONDS = 24 * 60 * 60;
    private final Context mContext;
    private static DownloadResumptionScheduler sDownloadResumptionScheduler;

    @SuppressFBWarnings("LI_LAZY_INIT")
    public static DownloadResumptionScheduler getDownloadResumptionScheduler(Context context) {
        assert context == context.getApplicationContext();
        if (sDownloadResumptionScheduler == null) {
            sDownloadResumptionScheduler = new DownloadResumptionScheduler(context);
        }
        return sDownloadResumptionScheduler;
    }

    protected DownloadResumptionScheduler(Context context) {
        mContext = context;
    }

    /**
     * For tests only: sets the DownloadResumptionScheduler.
     * @param service An instance of DownloadResumptionScheduler.
     */
    @VisibleForTesting
    public static void setDownloadResumptionScheduler(DownloadResumptionScheduler scheduler) {
        sDownloadResumptionScheduler = scheduler;
    }

    /**
     * Schedules a future task to start download resumption.
     * @param allowMeteredConnection Whether download resumption can start if connection is metered.
     */
    public void schedule(boolean allowMeteredConnection) {
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(mContext);
        int networkType = allowMeteredConnection
                ? Task.NETWORK_STATE_CONNECTED : Task.NETWORK_STATE_UNMETERED;
        OneoffTask task = new OneoffTask.Builder()
                .setService(ChromeBackgroundService.class)
                .setExecutionWindow(0, ONE_DAY_IN_SECONDS)
                .setTag(TASK_TAG)
                .setUpdateCurrent(true)
                .setRequiredNetwork(networkType)
                .setRequiresCharging(false)
                .build();
        try {
            gcmNetworkManager.schedule(task);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "unable to schedule resumption task.", e);
        }
    }

    /**
     * Cancels a download resumption task if it is scheduled.
     */
    public void cancelTask() {
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(mContext);
        gcmNetworkManager.cancelTask(TASK_TAG, ChromeBackgroundService.class);
    }

    /**
     * Start browser process and resumes all interrupted downloads.
     */
    public void handleDownloadResumption() {
        // Fire an intent to the DownloadNotificationService so that it will handle download
        // resumption.
        Intent launchIntent = new Intent(
                DownloadNotificationService.ACTION_DOWNLOAD_RESUME_ALL);
        launchIntent.setComponent(new ComponentName(mContext.getPackageName(),
                DownloadNotificationService.class.getName()));
        mContext.startService(launchIntent);
    }
}