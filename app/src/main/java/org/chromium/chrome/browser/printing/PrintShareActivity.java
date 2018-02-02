// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.printing;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.util.IntentUtils;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * A simple activity that allows Chrome to expose print as an option in the share menu.
 */
public class PrintShareActivity extends AppCompatActivity {

    private static final String TAG = "cr_printing";

    private static Set<Activity> sPendingShareActivities =
            Collections.synchronizedSet(new HashSet<Activity>());
    private static ActivityStateListener sStateListener;
    private static AsyncTask<Void, Void, Void> sStateChangeTask;

    /**
     * Enable the print sharing option.
     *
     * @param activity The activity that will be triggering the share action.  The activitiy's
     *                 state will be tracked to disable the print option when the share operation
     *                 has been completed.
     * @param callback The callback to be triggered after the print option has been enabled.  This
     *                 may or may not be synchronous depending on whether this will require
     *                 interacting with the Android framework.
     */
    public static void enablePrintShareOption(final Activity activity, final Runnable callback) {
        ThreadUtils.assertOnUiThread();

        if (sStateListener == null) {
            sStateListener = new ActivityStateListener() {
                @Override
                public void onActivityStateChange(Activity activity, int newState) {
                    if (newState == ActivityState.PAUSED) return;
                    unregisterActivity(activity);
                }
            };
        }
        ApplicationStatus.registerStateListenerForAllActivities(sStateListener);
        boolean wasEmpty = sPendingShareActivities.isEmpty();
        sPendingShareActivities.add(activity);

        waitForPendingStateChangeTask();
        if (wasEmpty) {
            sStateChangeTask = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    if (sPendingShareActivities.isEmpty()) return null;

                    activity.getPackageManager().setComponentEnabledSetting(
                            new ComponentName(activity, PrintShareActivity.class),
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    if (sStateChangeTask == this) {
                        sStateChangeTask = null;
                    } else {
                        waitForPendingStateChangeTask();
                    }
                    callback.run();
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            callback.run();
        }
    }

    private static void unregisterActivity(final Activity activity) {
        ThreadUtils.assertOnUiThread();

        sPendingShareActivities.remove(activity);
        if (!sPendingShareActivities.isEmpty()) return;
        ApplicationStatus.unregisterActivityStateListener(sStateListener);

        waitForPendingStateChangeTask();
        sStateChangeTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (!sPendingShareActivities.isEmpty()) return null;

                activity.getPackageManager().setComponentEnabledSetting(
                        new ComponentName(activity, PrintShareActivity.class),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (sStateChangeTask == this) sStateChangeTask = null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Waits for any pending state change operations to be completed.
     *
     * This will avoid timing issues described here: crbug.com/649453.
     */
    private static void waitForPendingStateChangeTask() {
        ThreadUtils.assertOnUiThread();

        if (sStateChangeTask == null) return;
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            sStateChangeTask.get();
            sStateChangeTask = null;
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Print state change task did not complete as expected");
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Intent intent = getIntent();
            if (intent == null) return;
            if (!Intent.ACTION_SEND.equals(intent.getAction())) return;
            if (!IntentUtils.safeHasExtra(getIntent(), ShareHelper.EXTRA_TASK_ID)) return;
            handlePrintAction();
        } finally {
            finish();
        }
    }

    private void handlePrintAction() {
        int triggeringTaskId =
                IntentUtils.safeGetIntExtra(getIntent(), ShareHelper.EXTRA_TASK_ID, 0);
        List<WeakReference<Activity>> activities = ApplicationStatus.getRunningActivities();
        ChromeActivity triggeringActivity = null;
        for (int i = 0; i < activities.size(); i++) {
            Activity activity = activities.get(i).get();
            if (activity == null) continue;

            // Since the share intent is triggered without NEW_TASK or NEW_DOCUMENT, the task ID
            // of this activity will match that of the triggering activity.
            if (activity.getTaskId() == triggeringTaskId
                    && activity instanceof ChromeActivity) {
                triggeringActivity = (ChromeActivity) activity;
                break;
            }
        }
        if (triggeringActivity == null) return;
        unregisterActivity(triggeringActivity);
        triggeringActivity.onMenuOrKeyboardAction(R.id.print_id, true);
    }

}
