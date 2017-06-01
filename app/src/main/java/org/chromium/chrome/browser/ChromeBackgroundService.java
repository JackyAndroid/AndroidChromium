// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.download.DownloadResumptionScheduler;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SnippetsLauncher;
import org.chromium.chrome.browser.offlinepages.BackgroundOfflinerTask;
import org.chromium.chrome.browser.offlinepages.BackgroundSchedulerProcessorImpl;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.precache.PrecacheController;
import org.chromium.chrome.browser.precache.PrecacheUMA;

/**
 * {@link ChromeBackgroundService} is scheduled through the {@link GcmNetworkManager} when the
 * browser needs to be launched for scheduled tasks, or in response to changing network or power
 * conditions.
 *
 * If HOLD_WAKELOCK is set to true in a bundle in the task params, then the ChromeBackgroundService
 * will wait until the task reports done before returning control to the {@link GcmNetworkManager}.
 * This both guarantees that the wakelock keeps chrome awake and that the GcmNetworkManager does not
 * start another task in place of the one just started.  The GcmNetworkManager can start more than
 * one task concurrently, thought, so it does not guarantee that a different task won't start.
 */
public class ChromeBackgroundService extends GcmTaskService {
    private static final String TAG = "BackgroundService";
    /** Bundle key to use to specify we should block the GcmNetworkManager thread until the task on
     * the UI thread is done before returning to the GcmNetworkManager.
     */
    public static final String HOLD_WAKELOCK = "HoldWakelock";
    private static final int WAKELOCK_TIMEOUT_SECONDS = 4 * 60;

    private BackgroundOfflinerTask mBackgroundOfflinerTask;

    @Override
    @VisibleForTesting
    public int onRunTask(final TaskParams params) {
        final String taskTag = params.getTag();
        Log.i(TAG, "[" + taskTag + "] Woken up at " + new java.util.Date().toString());
        final ChromeBackgroundServiceWaiter waiter = getWaiterIfNeeded(params.getExtras());
        final Context context = this;
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (taskTag) {
                    case BackgroundSyncLauncher.TASK_TAG:
                        handleBackgroundSyncEvent(context, taskTag);
                        break;

                    case OfflinePageUtils.TASK_TAG:
                        handleOfflinePageBackgroundLoad(
                                context, params.getExtras(), waiter, taskTag);
                        break;

                    case SnippetsLauncher.TASK_TAG_WIFI:
                    case SnippetsLauncher.TASK_TAG_FALLBACK:
                        handleFetchSnippets(context, taskTag);
                        break;

                    case PrecacheController.PERIODIC_TASK_TAG:
                    case PrecacheController.CONTINUATION_TASK_TAG:
                        handlePrecache(context, taskTag);
                        break;

                    case DownloadResumptionScheduler.TASK_TAG:
                        DownloadResumptionScheduler.getDownloadResumptionScheduler(
                                context.getApplicationContext()).handleDownloadResumption();
                        break;

                    default:
                        Log.i(TAG, "Unknown task tag " + taskTag);
                        break;
                }
            }
        });
        // If needed, block the GcmNetworkManager thread until the UI thread has finished its work.
        waitForTaskIfNeeded(waiter);

        return GcmNetworkManager.RESULT_SUCCESS;
    }

    private void handleBackgroundSyncEvent(Context context, String tag) {
        if (!BackgroundSyncLauncher.hasInstance()) {
            // Start the browser. The browser's BackgroundSyncManager (for the active profile) will
            // start, check the network, and run any necessary sync events. This task runs with a
            // wake lock, but has a three minute timeout, so we need to start the browser in its
            // own task.
            // TODO(jkarlin): Protect the browser sync event with a wake lock.
            // See crbug.com/486020.
            launchBrowser(context, tag);
        }
    }

    private void handleFetchSnippets(Context context, String tag) {
        if (!SnippetsLauncher.hasInstance()) {
            launchBrowser(context, tag);
        }
        fetchSnippets();
    }

    @VisibleForTesting
    protected void fetchSnippets() {
        // Do not force regular background fetches.
        SnippetsBridge.fetchSnippets(/*forceRequest=*/false);
    }

    @VisibleForTesting
    protected void rescheduleFetching() {
        SnippetsBridge.rescheduleFetching();
    }

    private void handlePrecache(Context context, String tag) {
        if (!hasPrecacheInstance()) {
            launchBrowser(context, tag);
        }
        precache(context, tag);
    }

    @VisibleForTesting
    protected boolean hasPrecacheInstance() {
        return PrecacheController.hasInstance();
    }

    @VisibleForTesting
    protected void precache(Context context, String tag) {
        PrecacheController.get(context).precache(tag);
    }

    private void handleOfflinePageBackgroundLoad(
            Context context, Bundle bundle, ChromeBackgroundServiceWaiter waiter, String tag) {
        if (!LibraryLoader.isInitialized()) {
            launchBrowser(context, tag);
        }

        // Call BackgroundTask, provide context.
        if (mBackgroundOfflinerTask == null) {
            mBackgroundOfflinerTask =
                    new BackgroundOfflinerTask(new BackgroundSchedulerProcessorImpl());
        }
        mBackgroundOfflinerTask.startBackgroundRequests(context, bundle, waiter);
        // TODO(petewil) if processBackgroundRequest returns false, return RESTART_RESCHEDULE
        // to the GcmNetworkManager
    }

    /**
     * If the bundle contains the special HOLD_WAKELOCK key set to true, then we create a
     * CountDownLatch for use later in the wait step, and set its initial count to one.
     */
    @VisibleForTesting
    public ChromeBackgroundServiceWaiter getWaiterIfNeeded(Bundle bundle) {
        // If wait_needed is set to true, wait.
        if (bundle != null && bundle.getBoolean(HOLD_WAKELOCK, false)) {
            return new ChromeBackgroundServiceWaiter(WAKELOCK_TIMEOUT_SECONDS);
        }
        return null;
    }

    /**
     * Some tasks need to block the GcmNetworkManager thread (and thus hold the wake lock) until the
     * task is done.  If we have a waiter, then start waiting.
     */
    @VisibleForTesting
    public void waitForTaskIfNeeded(ChromeBackgroundServiceWaiter waiter) {
        if (waiter != null) {
            // Block current thread until the onWaitDone method is called, or a timeout occurs.
            waiter.startWaiting();
        }
    }

    @VisibleForTesting
    @SuppressFBWarnings("DM_EXIT")
    protected void launchBrowser(Context context, String tag) {
        Log.i(TAG, "Launching browser");
        try {
            ChromeBrowserInitializer.getInstance(this).handleSynchronousStartup();
        } catch (ProcessInitException e) {
            Log.e(TAG, "ProcessInitException while starting the browser process");
            switch (tag) {
                case PrecacheController.PERIODIC_TASK_TAG:
                case PrecacheController.CONTINUATION_TASK_TAG:
                    // Record the failure persistently, and upload to UMA when the library
                    // successfully loads next time.
                    PrecacheUMA.record(PrecacheUMA.Event.PRECACHE_TASK_LOAD_LIBRARY_FAIL);
                    break;
                default:
                    break;
            }
            // Since the library failed to initialize nothing in the application
            // can work, so kill the whole application not just the activity.
            System.exit(-1);
        }
    }

    @VisibleForTesting
    protected void rescheduleBackgroundSyncTasksOnUpgrade() {
        BackgroundSyncLauncher.rescheduleTasksOnUpgrade(this);
    }

    @VisibleForTesting
    protected void reschedulePrecacheTasksOnUpgrade() {
        PrecacheController.rescheduleTasksOnUpgrade(this);
    }

    private void rescheduleSnippetsTasksOnUpgrade() {
        if (SnippetsLauncher.shouldRescheduleTasksOnUpgrade()) {
            if (!SnippetsLauncher.hasInstance()) {
                launchBrowser(this, /*tag=*/""); // The |tag| doesn't matter here.
            }
            rescheduleFetching();
        }
    }

    @Override
    public void onInitializeTasks() {
        rescheduleBackgroundSyncTasksOnUpgrade();
        reschedulePrecacheTasksOnUpgrade();
        rescheduleSnippetsTasksOnUpgrade();
    }
}
