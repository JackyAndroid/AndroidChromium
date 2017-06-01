// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.incognito;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;

/**
 * Manages the notification indicating that there are incognito tabs opened in Document mode.
 */
public class IncognitoNotificationManager {
    private static final String INCOGNITO_TABS_OPEN_TAG = "incognito_tabs_open";
    private static final int INCOGNITO_TABS_OPEN_ID = 100;

    /**
     * Shows the close all incognito notification.
     */
    public static void showIncognitoNotification() {
        Context context = ContextUtils.getApplicationContext();
        String actionMessage =
                context.getResources().getString(R.string.close_all_incognito_notification);
        String title = context.getResources().getString(R.string.app_name);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setContentTitle(title)
                .setContentIntent(
                        IncognitoNotificationService.getRemoveAllIncognitoTabsIntent(context))
                .setContentText(actionMessage)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setSmallIcon(R.drawable.incognito_statusbar)
                .setShowWhen(false)
                .setLocalOnly(true);
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(INCOGNITO_TABS_OPEN_TAG, INCOGNITO_TABS_OPEN_ID, builder.build());
    }

    /**
     * Dismisses the incognito notification.
     */
    public static void dismissIncognitoNotification() {
        Context context = ContextUtils.getApplicationContext();
        NotificationManager nm =
                  (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(INCOGNITO_TABS_OPEN_TAG, INCOGNITO_TABS_OPEN_ID);
    }
}