// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;

import java.lang.ref.WeakReference;

/**
 * Answers requests to kill and (potentially) restart Chrome's main browser process.
 *
 * This class fires an Intent to start the {@link BrowserRestartActivity}, which will utltimately
 * kill the main browser process from its own process.
 *
 * https://crbug.com/515919 details why another Activity is used instead of using the AlarmManager.
 * https://crbug.com/545453 details why the BrowserRestartActivity handles the process killing.
 */
class ChromeLifetimeController implements ApplicationLifetime.Observer,
        ApplicationStatus.ActivityStateListener {
    private static final String TAG = "cr.LifetimeController";

    private boolean mRestartChromeOnDestroy;
    private int mRemainingActivitiesCount = 0;

    public ChromeLifetimeController() {
        ApplicationLifetime.addObserver(this);
    }

    @Override
    public void onTerminate(boolean restart) {
        mRestartChromeOnDestroy = restart;

        for (WeakReference<Activity> weakActivity : ApplicationStatus.getRunningActivities()) {
            Activity activity = weakActivity.get();
            if (activity != null) {
                ApplicationStatus.registerStateListenerForActivity(this, activity);
                mRemainingActivitiesCount++;
                activity.finish();
            }
        }

        // Start the Activity that will ultimately kill this process.
        fireBrowserRestartActivityIntent(BrowserRestartActivity.ACTION_START_WATCHDOG);
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        assert mRemainingActivitiesCount > 0;
        if (newState == ActivityState.DESTROYED) {
            mRemainingActivitiesCount--;
            if (mRemainingActivitiesCount == 0) {
                fireBrowserRestartActivityIntent(BrowserRestartActivity.ACTION_KILL_PROCESS);
            }
        }
    }

    private void fireBrowserRestartActivityIntent(String action) {
        Context context = ApplicationStatus.getApplicationContext();
        Intent intent = new Intent();
        intent.setAction(action);
        intent.setClassName(
                context.getPackageName(), BrowserRestartActivity.class.getName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(BrowserRestartActivity.EXTRA_MAIN_PID, Process.myPid());
        intent.putExtra(BrowserRestartActivity.EXTRA_RESTART, mRestartChromeOnDestroy);
        context.startActivity(intent);
    }
}
