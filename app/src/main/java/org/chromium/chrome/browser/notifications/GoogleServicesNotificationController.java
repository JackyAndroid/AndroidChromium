// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;

/**
 * This class controls Android notifications for Google Services such as signin and sync.
 *
 * Users of these classes must ensure that their notification IDs are unique, and preferably
 * listed in {@link NotificationConstants}.
 * TODO(beverloo): Merge the display of notifications with NotificationUIManager.
 */
public class GoogleServicesNotificationController {
    private static final String TAG = "GoogleServicesNotificationController";
    private static final Object LOCK = new Object();

    private static GoogleServicesNotificationController sInstance;

    private final Context mApplicationContext;
    private NotificationManagerProxy mNotificationManager;

    /**
     * Retrieve the singleton instance of this class.
     *
     * @param context the current context.
     * @return the singleton instance.
     */
    public static GoogleServicesNotificationController get(Context context) {
        synchronized (LOCK) {
            if (sInstance == null) {
                sInstance = new GoogleServicesNotificationController(context);
            }
            return sInstance;
        }
    }

    public static String formatMessageParts(
            Context context, Integer featureNameResource, Integer messageResource) {
        return context.getString(featureNameResource) + ": " + context.getString(messageResource);
    }

    private GoogleServicesNotificationController(Context context) {
        mApplicationContext = context.getApplicationContext();
        mNotificationManager = new NotificationManagerProxyImpl(
                (NotificationManager) mApplicationContext.getSystemService(
                        Context.NOTIFICATION_SERVICE));
    }

    public void updateSingleNotification(int id, String message, Intent intent) {
        if (message == null) {
            cancelNotification(id);
        } else {
            showNotification(id, message, message, intent);
        }
    }

    /**
     * Shows a message in the Android status bar.
     * @param id       Must be a unique notification ID.
     * @param tickerText  Message to display in the ticker when the notification first appears.
     * @param contentText Message to display in the main body of the notification.
     * @param intent         Intent to fire when the notification is clicked.
     */
    public void showNotification(int id, String tickerText, String contentText, Intent intent) {
        String title = mApplicationContext.getString(R.string.app_name);
        PendingIntent contentIntent = PendingIntent.getActivity(mApplicationContext, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplicationContext)
                                                     .setAutoCancel(true)
                                                     .setContentIntent(contentIntent)
                                                     .setContentTitle(title)
                                                     .setContentText(contentText)
                                                     .setSmallIcon(R.drawable.ic_chrome)
                                                     .setTicker(tickerText)
                                                     .setLocalOnly(true);

        Notification notification =
                new NotificationCompat.BigTextStyle(builder).bigText(contentText).build();
        mNotificationManager.notify(id, notification);
    }

    public void cancelNotification(int id) {
        mNotificationManager.cancel(id);
    }

    @VisibleForTesting
    public void overrideNotificationManagerForTests(NotificationManagerProxy managerProxy) {
        mNotificationManager = managerProxy;
    }
}
