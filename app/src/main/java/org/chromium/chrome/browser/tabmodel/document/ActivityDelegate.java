// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.document.DocumentUtils;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.AsyncTabParamsManager;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModel.Entry;
import org.chromium.chrome.browser.util.IntentUtils;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Interfaces with the ActivityManager to identify Tabs/Tasks that are being tracked by
 * Android's Recents list.
 */
public abstract class ActivityDelegate {
    private final Class<?> mRegularClass;
    private final Class<?> mIncognitoClass;

    /**
     * Creates a ActivityDelegate.
     * @param regularClass Class of the regular DocumentActivity.
     * @param incognitoClass Class of the Incognito DocumentActivity.
     */
    public ActivityDelegate(Class<?> regularClass, Class<?> incognitoClass) {
        mRegularClass = regularClass;
        mIncognitoClass = incognitoClass;
    }

    /**
     * Returns whether an Activity is a DocumentActivity.  Assumes the Incognito Activity inherits
     * from the regular Activity.
     * @param activity Activity to check.
     * @return Whether the Activity is a DocumentActivity.
     */
    public boolean isDocumentActivity(Activity activity) {
        return mRegularClass.isInstance(activity);
    }

    /**
     * Checks whether or not the Intent corresponds to an Activity that should be tracked.
     * @param isIncognito Whether or not the TabModel is managing incognito tabs.
     * @param intent Intent used to launch the Activity.
     * @return Whether or not to track the Activity.
     */
    public boolean isValidActivity(boolean isIncognito, Intent intent) {
        if (intent == null) return false;
        String desiredClassName = isIncognito ? mIncognitoClass.getName() : mRegularClass.getName();
        String desiredLegacyClassName = isIncognito
                ? DocumentActivity.LEGACY_INCOGNITO_CLASS_NAME
                : DocumentActivity.LEGACY_CLASS_NAME;
        String className = null;
        if (intent.getComponent() == null) {
            ResolveInfo resolveInfo = ExternalNavigationDelegateImpl.resolveActivity(intent);
            if (resolveInfo != null) className = resolveInfo.activityInfo.name;
        } else {
            className = intent.getComponent().getClassName();
        }

        return TextUtils.equals(className, desiredClassName)
                || TextUtils.equals(className, desiredLegacyClassName);
    }

    /**
     * Finishes all DocumentActivities that appear in Android's Recents.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void finishAllDocumentActivities() {
        Context context = ContextUtils.getApplicationContext();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
            Intent intent = DocumentUtils.getBaseIntentFromTask(task);
            if (isValidActivity(false, intent) || isValidActivity(true, intent)) {
                task.finishAndRemoveTask();
            }
        }
    }

    /**
     * Get a map of the Chrome tasks displayed by Android's Recents.
     * @param isIncognito Whether or not the TabList is managing incognito tabs.
     */
    public abstract List<Entry> getTasksFromRecents(boolean isIncognito);

    /**
     * Moves the given task to the front, if it exists.
     * @param isIncognito Whether or not the TabList is managing incognito tabs.
     * @param tabId ID of the tab to move to front.
     */
    public abstract void moveTaskToFront(boolean isIncognito, int tabId);

    /**
     * Finishes and removes the task.
     * @param isIncognito Whether or not the TabList is managing incognito tabs.
     * @param tabId ID of the tab to move to front.
     */
    public abstract void finishAndRemoveTask(boolean isIncognito, int tabId);

    /**
     * Check if the Tab is associated with an Activity that hasn't been destroyed.
     * This catches situations where a DocumentActivity is no longer listed in Android's Recents
     * list, but is not dead yet.
     * @param tabId ID of the Tab to find.
     * @return Whether the tab is still alive.
     */
    public boolean isTabAssociatedWithNonDestroyedActivity(boolean isIncognito, int tabId) {
        List<WeakReference<Activity>> activities = ApplicationStatus.getRunningActivities();
        for (WeakReference<Activity> ref : activities) {
            Activity activity = ref.get();
            if (activity != null
                    && isValidActivity(isIncognito, activity.getIntent())
                    && getTabIdFromIntent(activity.getIntent()) == tabId
                    && !isActivityDestroyed(activity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether or not the Intent contains an ID for document mode.
     * @param intent Intent to check.
     * @return ID for the document that has the given intent as base intent, or
     *         {@link Tab.INVALID_TAB_ID} if it couldn't be retrieved.
     */
    public static int getTabIdFromIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return Tab.INVALID_TAB_ID;

        // Avoid AsyncTabCreationParams related flows early returning here.
        if (AsyncTabParamsManager.hasParamsWithTabToReparent()) {
            return IntentUtils.safeGetIntExtra(
                    intent, IntentHandler.EXTRA_TAB_ID, Tab.INVALID_TAB_ID);
        }

        Uri data = intent.getData();
        if (!TextUtils.equals(data.getScheme(), UrlConstants.DOCUMENT_SCHEME)) {
            return Tab.INVALID_TAB_ID;
        }

        try {
            return Integer.parseInt(data.getHost());
        } catch (NumberFormatException e) {
            return Tab.INVALID_TAB_ID;
        }
    }

    /**
     * Parse out the URL for a document Intent.
     * @param intent Intent to check.
     * @return The URL that the Intent was fired to display, or null if it couldn't be retrieved.
     */
    public static String getInitialUrlForDocument(Intent intent) {
        if (intent == null || intent.getData() == null) return null;
        Uri data = intent.getData();
        return TextUtils.equals(data.getScheme(), UrlConstants.DOCUMENT_SCHEME)
                ? data.getQuery() : null;
    }

    /**
     * @return Whether any incognito tabs are visible to the user in Android's Overview list.
     */
    public abstract boolean isIncognitoDocumentAccessibleToUser();

    /**
     * @return Running Activity that owns the given Tab, null if the Activity couldn't be found.
     */
    public static Activity getActivityForTabId(int id) {
        if (id == Tab.INVALID_TAB_ID) return null;

        for (WeakReference<Activity> ref : ApplicationStatus.getRunningActivities()) {
            if (!(ref.get() instanceof ChromeActivity)) continue;

            ChromeActivity activity = (ChromeActivity) ref.get();
            if (activity == null) continue;

            if (activity.getTabModelSelector().getTabById(id) != null) return activity;
        }
        return null;
    }

    /**
     * @return Whether or not the given Activity is destroyed.
     */
    protected abstract boolean isActivityDestroyed(Activity activity);
}
