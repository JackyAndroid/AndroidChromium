// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.chromium.chrome.R;

import org.chromium.content.browser.DownloadInfo;
import org.chromium.ui.base.LocalizationUtils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * DownloadNotifier implementation that uses Android Notification service and DownloadManager
 * service to create download notifications.
 */
public class SystemDownloadNotifier implements DownloadNotifier {
    private static final String NOTIFICATION_NAMESPACE = "SystemDownloadNotifier";

    private final NotificationManager mNotificationManager;

    private final Context mApplicationContext;

    /**
     * Constructor.
     * @param context Application context.
     */
    public SystemDownloadNotifier(Context context) {
        mApplicationContext = context.getApplicationContext();
        mNotificationManager = (NotificationManager) mApplicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Update the notification with id.
     * @param id Id of the notification that has to be updated.
     * @param notification the notification object that needs to be updated.
     */
    private void updateNotification(int id, Notification notification) {
        mNotificationManager.notify(NOTIFICATION_NAMESPACE, id, notification);
    }

    @Override
    public void cancelNotification(int downloadId) {
        mNotificationManager.cancel(NOTIFICATION_NAMESPACE, downloadId);
    }

    @Override
    public void notifyDownloadSuccessful(DownloadInfo downloadInfo, Intent intent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplicationContext)
                .setContentTitle(downloadInfo.getFileName())
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setContentText(mApplicationContext.getResources().getString(
                        R.string.download_notification_completed))
                .setContentIntent(PendingIntent.getActivity(
                        mApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
        updateNotification(downloadInfo.getDownloadId(), builder.build());
    }

    @Override
    public void notifyDownloadFailed(DownloadInfo downloadInfo) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplicationContext)
                .setContentTitle(downloadInfo.getFileName())
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setContentText(mApplicationContext.getResources().getString(
                        R.string.download_notification_failed));
        updateNotification(downloadInfo.getDownloadId(), builder.build());
    }

    @Override
    public void notifyDownloadProgress(DownloadInfo downloadInfo, long startTime) {
        // getPercentCompleted returns -1 if download time is indeterminate.
        boolean indeterminate = downloadInfo.getPercentCompleted() == -1;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplicationContext)
                .setContentTitle(downloadInfo.getFileName())
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setProgress(100, downloadInfo.getPercentCompleted(), indeterminate);

        if (!indeterminate) {
            NumberFormat formatter = NumberFormat.getPercentInstance(Locale.getDefault());
            String percentText = formatter.format(downloadInfo.getPercentCompleted() / 100.0);
            String duration = LocalizationUtils.getDurationString(
                    downloadInfo.getTimeRemainingInMillis());
            builder.setContentText(duration).setContentInfo(percentText);
        }
        if (startTime > 0) builder.setWhen(startTime);

        updateNotification(downloadInfo.getDownloadId(), builder.build());
    }
}
