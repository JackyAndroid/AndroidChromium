// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.content.Intent;

import org.chromium.content.browser.DownloadInfo;

/**
 * Class for reporting the status of a download.
 */
public interface DownloadNotifier {
    /**
     * Add a download successful notification.
     * @param downloadInfo info about the successful download.
     * @param intent Intent to launch when clicking the download notification.
     */
    void notifyDownloadSuccessful(DownloadInfo downloadInfo, Intent intent);

    /**
     * Add a download failed notification.
     * @param downloadInfo info about the failed download.
     */
    void notifyDownloadFailed(DownloadInfo downloadInfo);

    /**
     * @param downloadInfo info about in progress download.
     * @param startTimeInMillis the startTime of the download, measured in milliseconds, between the
     *        current time and midnight, January 1, 1970 UTC. Useful to keep progress notifications
     *        sorted by time.
     */
    void notifyDownloadProgress(DownloadInfo downloadInfo, long startTimeInMillis);

    /**
     * Cancel the notification for a download.
     * @param downloadId the downloadId of the cancelled download.
     */
    void cancelNotification(int downloadId);
}
