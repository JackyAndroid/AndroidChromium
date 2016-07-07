// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.Activity;
import android.content.Context;
import android.util.Pair;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.content.browser.DownloadInfo;

/**
 * Class for displaying a snackbar when a download completes.
 */
public class DownloadSnackbarController implements SnackbarManager.SnackbarController {
    private static final int SNACKBAR_DURATION_IN_MILLISECONDS = 5000;
    private final Context mContext;

    public DownloadSnackbarController(Context context) {
        mContext = context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onAction(Object actionData) {
        Pair<DownloadInfo, Long> download = (Pair<DownloadInfo, Long>) actionData;
        DownloadManagerService manager = DownloadManagerService.getDownloadManagerService(mContext);
        manager.openDownloadedContent(download.second);
        manager.removeProgressNotificationForDownload(download.first.getDownloadId());
    }

    @Override
    public void onDismissNoAction(Object actionData) {
    }

    @Override
    public void onDismissForEachType(boolean isTimeout) {
    }

    /**
     * Called to display the download succeeded snackbar.
     *
     * @param downloadInfo Info of the download.
     * @param downloadId Id of the download.
     * @param canBeResolved Whether the download can be resolved to any activity.
     */
    public void onDownloadSucceeded(
            DownloadInfo downloadInfo, final long downloadId, boolean canBeResolved) {
        if (getSnackbarManager() == null) return;
        Snackbar snackbar = Snackbar.make(mContext.getString(
                R.string.download_succeeded_message, downloadInfo.getFileName()), this);
        // TODO(qinmin): Coalesce snackbars if multiple downloads finish at the same time.
        snackbar.setDuration(SNACKBAR_DURATION_IN_MILLISECONDS).setSingleLine(false);
        if (canBeResolved) {
            snackbar.setAction(
                    mContext.getString(R.string.open_downloaded_label),
                    Pair.create(downloadInfo, downloadId));
        }
        getSnackbarManager().showSnackbar(snackbar);
    }

    /**
     * Called to display the download failed snackbar.
     *
     * @param filename File name of the failed download.
     */
    public void onDownloadFailed(String filename) {
        if (getSnackbarManager() == null) return;
        // TODO(qinmin): Coalesce snackbars if multiple downloads finish at the same time.
        String message = mContext.getString(R.string.download_failed_message, filename);
        Snackbar snackbar = Snackbar.make(message, this)
                .setSingleLine(false)
                .setDuration(SNACKBAR_DURATION_IN_MILLISECONDS);
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
