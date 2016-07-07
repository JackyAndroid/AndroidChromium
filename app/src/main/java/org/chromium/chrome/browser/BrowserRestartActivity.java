// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.util.IntentUtils;

/**
 * Kills and (optionally) restarts the main Chrome process, then immediately kills itself.
 *
 * Starting this Activity should only be done by the {@link ChromeLifetimeController}.
 *
 * This Activity runs on a separate process from the main Chrome browser and cannot see the main
 * process' Activities.  It works around an Android framework issue for alarms set via the
 * AlarmManager, which requires a minimum alarm duration of 5 seconds: https://crbug.com/515919.
 */
public class BrowserRestartActivity extends Activity {
    static final String ACTION_START_WATCHDOG =
            "org.chromium.chrome.browser.BrowserRestartActivity.start_watchdog";
    static final String ACTION_KILL_PROCESS =
            "org.chromium.chrome.browser.BrowserRestartActivity.kill_process";

    static final String EXTRA_MAIN_PID =
            "org.chromium.chrome.browser.BrowserRestartActivity.main_pid";
    static final String EXTRA_RESTART =
            "org.chromium.chrome.browser.BrowserRestartActivity.restart";

    private static final String TAG = "BrowserRestartActivity";

    // The amount of time to wait for Chrome to destroy all the activities of the main process
    // before this Activity forcefully kills it.
    private static final long WATCHDOG_DELAY_MS = 1000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        if (TextUtils.equals(ACTION_START_WATCHDOG, intent.getAction())) {
            // Kill the main process if Android fails to finish our Activities in a timely manner.
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    destroyProcess(intent);
                }
            }, WATCHDOG_DELAY_MS);
        } else if (TextUtils.equals(ACTION_KILL_PROCESS, intent.getAction())) {
            destroyProcess(intent);
        } else {
            assert false;
        }
    }

    private void destroyProcess(Intent intent) {
        // Kill the main Chrome process.
        int mainBrowserPid = IntentUtils.safeGetIntExtra(
                intent, BrowserRestartActivity.EXTRA_MAIN_PID, -1);
        assert mainBrowserPid != -1;
        Process.killProcess(mainBrowserPid);

        // Fire an Intent to restart Chrome.
        boolean restart = IntentUtils.safeGetBooleanExtra(
                intent, BrowserRestartActivity.EXTRA_RESTART, false);
        if (restart) {
            Context context = ApplicationStatus.getApplicationContext();
            Intent restartIntent = new Intent(Intent.ACTION_MAIN);
            restartIntent.setPackage(context.getPackageName());
            restartIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(restartIntent);
        }

        // Kill this process.
        finish();
        Process.killProcess(Process.myPid());
    }
}
