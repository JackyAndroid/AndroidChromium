// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.content.app.ContentApplication;
import org.chromium.content.browser.BrowserStartupController;

/**
 * {@link BackgroundSyncLauncherService} is scheduled through the {@link GcmNetworkManager}
 * when the browser needs to be launched in response to changing network or power conditions.
 */
public class BackgroundSyncLauncherService extends GcmTaskService {
    private static final String TAG = "cr_BgSyncLauncher";

    @Override
    @VisibleForTesting
    public int onRunTask(TaskParams params) {
        // Start the browser. The browser's BackgroundSyncManager (for the active profile) will
        // start, check the network, and run any necessary sync events. This task runs with a wake
        // lock, but has a three minute timeout, so we need to start the browser in its own task.
        // TODO(jkarlin): Protect the browser sync event with a wake lock. See crbug.com/486020.
        Log.v(TAG, "Starting Browser after coming online");
        final Context context = this;
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!BackgroundSyncLauncher.hasInstance()) {
                    launchBrowser(context);
                }
            }
        });
        return GcmNetworkManager.RESULT_SUCCESS;
    }

    @VisibleForTesting
    @SuppressFBWarnings("DM_EXIT")
    protected void launchBrowser(Context context) {
        ContentApplication.initCommandLine(context);
        try {
            BrowserStartupController.get(context, LibraryProcessType.PROCESS_BROWSER)
                    .startBrowserProcessesSync(false);
        } catch (ProcessInitException e) {
            Log.e(TAG, "ProcessInitException while starting the browser process");
            // Since the library failed to initialize nothing in the application
            // can work, so kill the whole application not just the activity.
            System.exit(-1);
        }
    }

    @Override
    @VisibleForTesting
    public void onInitializeTasks() {
        BackgroundSyncLauncher.rescheduleTasksOnUpgrade(this);
    }
}
