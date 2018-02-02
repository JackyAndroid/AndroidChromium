// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * DownloadNotifier implementation that creates and updates download notifications.
 * This class creates the {@link DownloadNotificationService} when needed, and binds
 * to the latter to issue calls to show and update notifications.
 */
public class SystemDownloadNotifier implements DownloadNotifier {
    private static final String TAG = "DownloadNotifier";
    private static final int DOWNLOAD_NOTIFICATION_TYPE_PROGRESS = 0;
    private static final int DOWNLOAD_NOTIFICATION_TYPE_SUCCESS = 1;
    private static final int DOWNLOAD_NOTIFICATION_TYPE_FAILURE = 2;
    private static final int DOWNLOAD_NOTIFICATION_TYPE_CANCEL = 3;
    private static final int DOWNLOAD_NOTIFICATION_TYPE_RESUME_ALL = 4;
    private static final int DOWNLOAD_NOTIFICATION_TYPE_PAUSE = 5;
    private static final int DOWNLOAD_NOTIFICATION_TYPE_INTERRUPT = 6;
    private final Context mApplicationContext;
    private final Object mLock = new Object();
    @Nullable private DownloadNotificationService mBoundService;
    private boolean mServiceStarted;
    private Set<String> mActiveDownloads = new HashSet<String>();
    private List<PendingNotificationInfo> mPendingNotifications =
            new ArrayList<PendingNotificationInfo>();

    /**
     * Pending download notifications to be posted.
     */
    static class PendingNotificationInfo {
        // Pending download notifications to be posted.
        public final int type;
        public final DownloadInfo downloadInfo;
        public long startTime;
        public boolean isAutoResumable;
        public boolean canDownloadWhileMetered;
        public boolean canResolve;
        public long systemDownloadId;
        public boolean isSupportedMimeType;

        public PendingNotificationInfo(int type, DownloadInfo downloadInfo) {
            this.type = type;
            this.downloadInfo = downloadInfo;
        }
    }

    /**
     * Constructor.
     * @param context Application context.
     */
    public SystemDownloadNotifier(Context context) {
        mApplicationContext = context.getApplicationContext();
    }

    /**
     * Object to receive information as the service is started and stopped.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            synchronized (mLock) {
                if (!(service instanceof DownloadNotificationService.LocalBinder)) {
                    Log.w(TAG, "Not from DownloadNotificationService, do not connect."
                            + " Component name: " + className);
                    assert false;
                    return;
                }
                mBoundService = ((DownloadNotificationService.LocalBinder) service).getService();
                // updateDownloadNotification() may leave some outstanding notifications
                // before the service is connected, handle them now.
                handlePendingNotifications();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            synchronized (mLock) {
                mBoundService = null;
                mServiceStarted = false;
            }
        }
    };

    /**
     * For tests only: sets the DownloadNotificationService.
     * @param service An instance of DownloadNotificationService.
     */
    @VisibleForTesting
    void setDownloadNotificationService(DownloadNotificationService service) {
        synchronized (mLock) {
            mBoundService = service;
        }
    }

    /**
     * Handles all the pending notifications that hasn't been processed.
     */
    @VisibleForTesting
    void handlePendingNotifications() {
        synchronized (mLock) {
            if (mPendingNotifications.isEmpty()) return;
            for (PendingNotificationInfo info : mPendingNotifications) {
                updateDownloadNotification(info);
            }
            mPendingNotifications.clear();
        }
    }

    /**
     * Starts and binds to the download notification service if needed.
     */
    private void startAndBindToServiceIfNeeded() {
        assert Thread.holdsLock(mLock);
        if (mServiceStarted) return;
        startService();
        mServiceStarted = true;
    }

    /**
     * Stops the download notification service if there are no download in progress.
     */
    private void stopServiceIfNeeded() {
        assert Thread.holdsLock(mLock);
        if (mActiveDownloads.isEmpty() && mServiceStarted) {
            stopService();
            mServiceStarted = false;
        }
    }

    /**
     * Starts and binds to the download notification service.
     */
    @VisibleForTesting
    void startService() {
        assert Thread.holdsLock(mLock);
        mApplicationContext.startService(
                new Intent(mApplicationContext, DownloadNotificationService.class));
        mApplicationContext.bindService(new Intent(mApplicationContext,
                DownloadNotificationService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Stops the download notification service.
     */
    @VisibleForTesting
    void stopService() {
        assert Thread.holdsLock(mLock);
        mApplicationContext.stopService(
                new Intent(mApplicationContext, DownloadNotificationService.class));
    }

    @Override
    public void notifyDownloadCanceled(String downloadGuid) {
        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                .setDownloadGuid(downloadGuid)
                .build();
        updateDownloadNotification(
                new PendingNotificationInfo(DOWNLOAD_NOTIFICATION_TYPE_CANCEL, downloadInfo));
    }

    @Override
    public void notifyDownloadSuccessful(DownloadInfo downloadInfo, long systemDownloadId,
            boolean canResolve, boolean isSupportedMimeType) {
        PendingNotificationInfo info =
                new PendingNotificationInfo(DOWNLOAD_NOTIFICATION_TYPE_SUCCESS, downloadInfo);
        info.canResolve = canResolve;
        info.systemDownloadId = systemDownloadId;
        info.isSupportedMimeType = isSupportedMimeType;
        updateDownloadNotification(info);
    }

    @Override
    public void notifyDownloadFailed(DownloadInfo downloadInfo) {
        updateDownloadNotification(
                new PendingNotificationInfo(DOWNLOAD_NOTIFICATION_TYPE_FAILURE, downloadInfo));
    }

    @Override
    public void notifyDownloadProgress(
            DownloadInfo downloadInfo, long startTime, boolean canDownloadWhileMetered) {
        PendingNotificationInfo info =
                new PendingNotificationInfo(DOWNLOAD_NOTIFICATION_TYPE_PROGRESS, downloadInfo);
        info.startTime = startTime;
        info.canDownloadWhileMetered = canDownloadWhileMetered;
        updateDownloadNotification(info);
    }

    @Override
    public void notifyDownloadPaused(DownloadInfo downloadInfo) {
        PendingNotificationInfo info =
                new PendingNotificationInfo(DOWNLOAD_NOTIFICATION_TYPE_PAUSE, downloadInfo);
        updateDownloadNotification(info);
    }

    @Override
    public void notifyDownloadInterrupted(DownloadInfo downloadInfo, boolean isAutoResumable) {
        PendingNotificationInfo info =
                new PendingNotificationInfo(DOWNLOAD_NOTIFICATION_TYPE_INTERRUPT, downloadInfo);
        info.isAutoResumable = isAutoResumable;
        updateDownloadNotification(info);
    }

    @Override
    public void resumePendingDownloads() {
        updateDownloadNotification(
                new PendingNotificationInfo(DOWNLOAD_NOTIFICATION_TYPE_RESUME_ALL, null));
    }

    /**
     * Called when a successful notification is shown.
     * @param info Pending notification information to be handled.
     * @param notificationId ID of the notification.
     */
    @VisibleForTesting
    void onSuccessNotificationShown(
            final PendingNotificationInfo notificationInfo, final int notificationId) {
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                DownloadManagerService.getDownloadManagerService(
                        mApplicationContext).onSuccessNotificationShown(
                                notificationInfo.downloadInfo, notificationInfo.canResolve,
                                notificationId, notificationInfo.systemDownloadId);
            }
        });
    }

    /**
     * Updates the download notification if the notification service is started. Otherwise,
     * wait for the notification service to become ready.
     * @param info Pending notification information to be handled.
     */
    private void updateDownloadNotification(final PendingNotificationInfo notificationInfo) {
        synchronized (mLock) {
            startAndBindToServiceIfNeeded();
            final DownloadInfo info = notificationInfo.downloadInfo;
            if (notificationInfo.type == DOWNLOAD_NOTIFICATION_TYPE_PROGRESS) {
                mActiveDownloads.add(info.getDownloadGuid());
            } else if (notificationInfo.type != DOWNLOAD_NOTIFICATION_TYPE_RESUME_ALL) {
                mActiveDownloads.remove(info.getDownloadGuid());
            }
            if (mBoundService == null) {
                // We need to wait for the service to connect before we can handle
                // the notification. Put the notification in the pending notifications
                // list.
                mPendingNotifications.add(notificationInfo);
            } else {
                switch (notificationInfo.type) {
                    case DOWNLOAD_NOTIFICATION_TYPE_PROGRESS:
                        mBoundService.notifyDownloadProgress(info.getDownloadGuid(),
                                info.getFileName(), info.getPercentCompleted(),
                                info.getTimeRemainingInMillis(), notificationInfo.startTime,
                                info.isOffTheRecord(), notificationInfo.canDownloadWhileMetered,
                                info.isOfflinePage());
                        break;
                    case DOWNLOAD_NOTIFICATION_TYPE_PAUSE:
                        mBoundService.notifyDownloadPaused(info.getDownloadGuid(), true, false);
                        break;
                    case DOWNLOAD_NOTIFICATION_TYPE_INTERRUPT:
                        mBoundService.notifyDownloadPaused(
                                info.getDownloadGuid(), info.isResumable(),
                                notificationInfo.isAutoResumable);
                        break;
                    case DOWNLOAD_NOTIFICATION_TYPE_SUCCESS:
                        final int notificationId = mBoundService.notifyDownloadSuccessful(
                                info.getDownloadGuid(), info.getFilePath(), info.getFileName(),
                                notificationInfo.systemDownloadId, info.isOfflinePage(),
                                notificationInfo.isSupportedMimeType);
                        onSuccessNotificationShown(notificationInfo, notificationId);
                        stopServiceIfNeeded();
                        break;
                    case DOWNLOAD_NOTIFICATION_TYPE_FAILURE:
                        mBoundService.notifyDownloadFailed(
                                info.getDownloadGuid(), info.getFileName());
                        stopServiceIfNeeded();
                        break;
                    case DOWNLOAD_NOTIFICATION_TYPE_CANCEL:
                        mBoundService.notifyDownloadCanceled(info.getDownloadGuid());
                        stopServiceIfNeeded();
                        break;
                    case DOWNLOAD_NOTIFICATION_TYPE_RESUME_ALL:
                        mBoundService.resumeAllPendingDownloads();
                        stopServiceIfNeeded();
                        break;
                    default:
                        assert false;
                }
            }
        }
    }
}
