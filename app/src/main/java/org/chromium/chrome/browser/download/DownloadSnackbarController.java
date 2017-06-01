// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadBridge;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;

/**
 * Class for displaying a snackbar when a download completes.
 */
public class DownloadSnackbarController implements SnackbarManager.SnackbarController {
    public static final int INVALID_NOTIFICATION_ID = -1;
    private static final int SNACKBAR_DURATION_IN_MILLISECONDS = 5000;
    private final Context mContext;

    private static class ActionDataInfo {
        public final DownloadInfo downloadInfo;
        public final int notificationId;
        public final long systemDownloadId;

        ActionDataInfo(DownloadInfo downloadInfo, int notificationId, long systemDownloadId) {
            this.downloadInfo = downloadInfo;
            this.notificationId = notificationId;
            this.systemDownloadId = systemDownloadId;
        }
    }

    public DownloadSnackbarController(Context context) {
        mContext = context;
    }

    @Override
    public void onAction(Object actionData) {
        if (!(actionData instanceof ActionDataInfo)) {
            DownloadManagerService.openDownloadsPage(mContext);
            return;
        }
        final ActionDataInfo download = (ActionDataInfo) actionData;
        if (download.downloadInfo.isOfflinePage()) {
            OfflinePageDownloadBridge.openDownloadedPage(download.downloadInfo.getDownloadGuid());
            return;
        }
        DownloadManagerService manager = DownloadManagerService.getDownloadManagerService(mContext);
        manager.openDownloadedContent(download.downloadInfo, download.systemDownloadId);
        if (download.notificationId != INVALID_NOTIFICATION_ID) {
            NotificationManager notificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(
                    DownloadNotificationService.NOTIFICATION_NAMESPACE, download.notificationId);
        }
    }

    @Override
    public void onDismissNoAction(Object actionData) {
    }

    /**
     * Called to display the download succeeded snackbar.
     *
     * @param downloadInfo Info of the download.
     * @param notificationId Notification Id of the successful download.
     * @param downloadId Id of the download from Android DownloadManager.
     * @param canBeResolved Whether the download can be resolved to any activity.
     */
    public void onDownloadSucceeded(
            DownloadInfo downloadInfo, int notificationId, long downloadId, boolean canBeResolved) {
        if (getSnackbarManager() == null) return;
        Snackbar snackbar = Snackbar.make(
                mContext.getString(R.string.download_succeeded_message, downloadInfo.getFileName()),
                this, Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_DOWNLOAD_SUCCEEDED);
        // TODO(qinmin): Coalesce snackbars if multiple downloads finish at the same time.
        snackbar.setDuration(SNACKBAR_DURATION_IN_MILLISECONDS).setSingleLine(false);
        ActionDataInfo info = null;
        if (canBeResolved || downloadInfo.isOfflinePage()) {
            info = new ActionDataInfo(downloadInfo, notificationId, downloadId);
        }
        // Show downloads app if the download cannot be resolved to any activity.
        snackbar.setAction(
                mContext.getString(R.string.open_downloaded_label), info);
        getSnackbarManager().showSnackbar(snackbar);
    }

    /**
     * Called to display the download failed snackbar.
     *
     * @param errorMessage     The message to show on the snackbar.
     * @param showAllDownloads Whether to show all downloads in case the failure is caused by
     *                         duplicated files.
     */
    public void onDownloadFailed(String errorMessage, boolean showAllDownloads) {
        if (getSnackbarManager() == null) return;
        // TODO(qinmin): Coalesce snackbars if multiple downloads finish at the same time.
        Snackbar snackbar = Snackbar
                .make(errorMessage, this, Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_DOWNLOAD_FAILED)
                .setSingleLine(false)
                .setDuration(SNACKBAR_DURATION_IN_MILLISECONDS);
        if (showAllDownloads) {
            snackbar.setAction(
                    mContext.getString(R.string.open_downloaded_label),
                    null);
        }
        getSnackbarManager().showSnackbar(snackbar);
    }

    public SnackbarManager getSnackbarManager() {
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (activity != null && ApplicationStatus.hasVisibleActivities()
                && activity instanceof SnackbarManager.SnackbarManageable) {
            return ((SnackbarManager.SnackbarManageable) activity).getSnackbarManager();
        }
        return null;
    }
}
