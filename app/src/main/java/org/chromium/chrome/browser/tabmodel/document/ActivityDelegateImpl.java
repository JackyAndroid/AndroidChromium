// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.document.DocumentUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModel.Entry;

import java.util.ArrayList;
import java.util.List;

/**
 * Interfaces with the ActivityManager to identify Tabs/Tasks that are being tracked by
 * Android's Recents list.
 * TODO(dfalcantara): Merge this class back into ActivityDelegate when upstream bots run Lollipop.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ActivityDelegateImpl extends ActivityDelegate {

    /**
     * Creates a ActivityDelegateImpl.
     * @param regularClass Class of the regular DocumentActivity.
     * @param incognitoClass Class of the Incognito DocumentActivity.
     */
    public ActivityDelegateImpl(Class<?> regularClass, Class<?> incognitoClass) {
        super(regularClass, incognitoClass);
    }

    @Override
    public List<Entry> getTasksFromRecents(boolean isIncognito) {
        Context context = ApplicationStatus.getApplicationContext();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        List<Entry> entries = new ArrayList<Entry>();
        for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
            Intent intent = DocumentUtils.getBaseIntentFromTask(task);
            if (!isValidActivity(isIncognito, intent)) continue;

            int tabId = getTabIdFromIntent(intent);
            if (tabId == Tab.INVALID_TAB_ID) continue;

            String initialUrl = getInitialUrlForDocument(intent);
            entries.add(new Entry(tabId, initialUrl));
        }
        return entries;
    }

    @Override
    public void moveTaskToFront(boolean isIncognito, int tabId) {
        ActivityManager.AppTask task = getTask(isIncognito, tabId);
        if (task != null) task.moveToFront();
    }

    @Override
    public void finishAndRemoveTask(boolean isIncognito, int tabId) {
        ActivityManager.AppTask task = getTask(isIncognito, tabId);
        if (task != null) task.finishAndRemoveTask();
    }

    private ActivityManager.AppTask getTask(boolean isIncognito, int tabId) {
        Context context = ApplicationStatus.getApplicationContext();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
            Intent intent = DocumentUtils.getBaseIntentFromTask(task);
            int taskId = getTabIdFromIntent(intent);
            if (taskId == tabId && isValidActivity(isIncognito, intent)) return task;
        }
        return null;
    }

    @Override
    public boolean isIncognitoDocumentAccessibleToUser() {
        Context context = ApplicationStatus.getApplicationContext();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
            Intent intent = DocumentUtils.getBaseIntentFromTask(task);
            if (isValidActivity(true, intent)) return true;
        }
        return false;
    }

    @Override
    protected boolean isActivityDestroyed(Activity activity) {
        return activity.isDestroyed();
    }
}
