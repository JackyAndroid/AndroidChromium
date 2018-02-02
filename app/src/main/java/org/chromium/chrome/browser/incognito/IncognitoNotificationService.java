// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.incognito;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.document.DocumentUtils;
import org.chromium.chrome.browser.tabmodel.TabWindowManager;
import org.chromium.chrome.browser.tabmodel.TabbedModeTabPersistencePolicy;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service that handles the action of clicking on the incognito notification.
 */
public class IncognitoNotificationService extends IntentService {

    private static final String TAG = "incognito_notification";

    private static final String ACTION_CLOSE_ALL_INCOGNITO =
            "com.google.android.apps.chrome.incognito.CLOSE_ALL_INCOGNITO";

    @VisibleForTesting
    public static PendingIntent getRemoveAllIncognitoTabsIntent(Context context) {
        Intent intent = new Intent(context, IncognitoNotificationService.class);
        intent.setAction(ACTION_CLOSE_ALL_INCOGNITO);
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Empty public constructor needed by Android. */
    public IncognitoNotificationService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        closeIncognitoTabsInRunningTabbedActivities();

        boolean clearedIncognito = deleteIncognitoStateFilesInDirectory(
                TabbedModeTabPersistencePolicy.getOrCreateTabbedModeStateDirectory());

        // If we failed clearing all of the incognito tabs, then do not dismiss the notification.
        if (!clearedIncognito) return;

        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                int incognitoCount = TabWindowManager.getInstance().getIncognitoTabCount();
                assert incognitoCount == 0;

                if (incognitoCount == 0) {
                    IncognitoNotificationManager.dismissIncognitoNotification();
                }
            }
        });

        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                // Now ensure that the snapshots in recents are all cleared for Tabbed activities
                // to remove any trace of incognito mode.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    focusChromeIfNecessary();
                } else {
                    removeNonVisibleChromeTabbedRecentEntries();
                }
            }
        });
    }

    private void focusChromeIfNecessary() {
        Set<Integer> visibleTaskIds = getTaskIdsForVisibleActivities();
        int tabbedTaskId = -1;

        List<WeakReference<Activity>> runningActivities =
                ApplicationStatus.getRunningActivities();
        for (int i = 0; i < runningActivities.size(); i++) {
            Activity activity = runningActivities.get(i).get();
            if (activity == null) continue;

            if (activity instanceof ChromeTabbedActivity) {
                tabbedTaskId = activity.getTaskId();
                break;
            }
        }

        // If the task containing the tabbed activity is visible, then do nothing as there is no
        // snapshot that would need to be regenerated.
        if (visibleTaskIds.contains(tabbedTaskId)) return;

        Context context = ContextUtils.getApplicationContext();
        Intent startIntent = new Intent(Intent.ACTION_MAIN);
        startIntent.setPackage(context.getPackageName());
        startIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(startIntent);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void removeNonVisibleChromeTabbedRecentEntries() {
        Set<Integer> visibleTaskIds = getTaskIdsForVisibleActivities();

        Context context = ContextUtils.getApplicationContext();
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = getPackageManager();

        for (AppTask task : manager.getAppTasks()) {
            RecentTaskInfo info = DocumentUtils.getTaskInfoFromTask(task);
            if (info == null) continue;
            String className = DocumentUtils.getTaskClassName(task, pm);

            // It is not easily possible to distinguish between tasks sitting on top of
            // ChromeLauncherActivity, so we treat them all as likely ChromeTabbedActivities and
            // close them to be on the cautious side of things.
            if ((TextUtils.equals(className, ChromeTabbedActivity.class.getName())
                    || TextUtils.equals(className, ChromeLauncherActivity.class.getName()))
                    && !visibleTaskIds.contains(info.id)) {
                task.finishAndRemoveTask();
            }
        }
    }

    private Set<Integer> getTaskIdsForVisibleActivities() {
        List<WeakReference<Activity>> runningActivities =
                ApplicationStatus.getRunningActivities();
        Set<Integer> visibleTaskIds = new HashSet<>();
        for (int i = 0; i < runningActivities.size(); i++) {
            Activity activity = runningActivities.get(i).get();
            if (activity == null) continue;

            int activityState = ApplicationStatus.getStateForActivity(activity);
            if (activityState != ActivityState.STOPPED
                    && activityState != ActivityState.DESTROYED) {
                visibleTaskIds.add(activity.getTaskId());
            }
        }
        return visibleTaskIds;
    }

    /**
     * Iterate across the running activities and for any running tabbed mode activities close their
     * incognito tabs.
     *
     * @see TabWindowManager#getIndexForWindow(Activity)
     */
    private void closeIncognitoTabsInRunningTabbedActivities() {
        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                List<WeakReference<Activity>> runningActivities =
                        ApplicationStatus.getRunningActivities();
                for (int i = 0; i < runningActivities.size(); i++) {
                    Activity activity = runningActivities.get(i).get();
                    if (activity == null) continue;
                    if (!(activity instanceof ChromeTabbedActivity)) continue;

                    ChromeTabbedActivity tabbedActivity = (ChromeTabbedActivity) activity;
                    if (tabbedActivity.isActivityDestroyed()) continue;

                    tabbedActivity.getTabModelSelector().getModel(true).closeAllTabs(
                            false, false);
                }
            }
        });
    }

    /**
     * @return Whether deleting all the incognito files was successful.
     */
    private boolean deleteIncognitoStateFilesInDirectory(File directory) {
        File[] allTabStates = directory.listFiles();
        if (allTabStates == null) return true;

        boolean deletionSuccessful = true;
        for (int i = 0; i < allTabStates.length; i++) {
            String fileName = allTabStates[i].getName();
            Pair<Integer, Boolean> tabInfo = TabState.parseInfoFromFilename(fileName);
            if (tabInfo == null || !tabInfo.second) continue;
            deletionSuccessful &= allTabStates[i].delete();
        }
        return deletionSuccessful;
    }

}
