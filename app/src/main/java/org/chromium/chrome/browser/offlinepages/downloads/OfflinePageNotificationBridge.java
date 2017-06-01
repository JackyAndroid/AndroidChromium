// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.downloads;

import android.content.Context;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadInfo;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.download.DownloadNotifier;
import org.chromium.ui.widget.Toast;

/**
 * Class for dispatching offline page/request related notifications to the
 * {org.chromium.chrome.browser.download.DownloadNotifier}.
 */
public class OfflinePageNotificationBridge {
    /**
    * Update download notification to success.
    * @param context Context to show notifications.
    * @param guid GUID of a request to download a page related to the notification.
    * @param url URL of the page to download.
    * @param displayName Name to be displayed on notification.
    */
    @CalledByNative
    public static void notifyDownloadSuccessful(Context context, String guid, String url,
            String displayName) {
        DownloadNotifier notifier = getDownloadNotifier(context);
        if (notifier == null) return;

        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                                            .setIsOfflinePage(true)
                                            .setDownloadGuid(guid)
                                            .setFileName(displayName)
                                            .setIsResumable(false)
                                            .setIsOffTheRecord(false)
                                            .build();

        notifier.notifyDownloadSuccessful(downloadInfo, -1, false, true);
    }

    /**
     * Update download notification to failure.
     * @param context Context to show notifications.
     * @param guid GUID of a request to download a page related to the notification.
     * @param url URL of the page to download.
     * @param displayName Name to be displayed on notification.
     */
    @CalledByNative
    public static void notifyDownloadFailed(Context context, String guid, String url,
            String displayName) {
        DownloadNotifier notifier = getDownloadNotifier(context);
        if (notifier == null) return;

        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                .setIsOfflinePage(true).setDownloadGuid(guid).setFileName(displayName).build();

        notifier.notifyDownloadFailed(downloadInfo);
    }

    /**
     * Called by offline page backend to notify the user of download progress.
     * @param context Context to show notifications.
     * @param guid GUID of a request to download a page related to the notification.
     * @param url URL of the page to download.
     * @param startTime Time of the request.
     * @param displayName Name to be displayed on notification.
     */
    @CalledByNative
    public static void notifyDownloadProgress(
            Context context, String guid, String url, long startTime, String displayName) {
        DownloadNotifier notifier = getDownloadNotifier(context);
        if (notifier == null) return;

        // Use -1 percentage for interdeterminate progress bar (until we have better value).
        // TODO(qinmin): get the download percentage from native code,
        int percentage = -1;
        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                                            .setIsOfflinePage(true)
                                            .setDownloadGuid(guid)
                                            .setFileName(displayName)
                                            .setFilePath(url)
                                            .setPercentCompleted(percentage)
                                            .setIsOffTheRecord(false)
                                            .setIsResumable(true)
                                            .setTimeRemainingInMillis(0)
                                            .build();

        notifier.notifyDownloadProgress(downloadInfo, startTime, false);
    }

    /**
     * Update download notification to paused.
     * @param context Context to show notifications.
     * @param guid GUID of a request to download a page related to the notification.
     * @param displayName Name to be displayed on notification.
     */
    @CalledByNative
    public static void notifyDownloadPaused(Context context, String guid, String displayName) {
        DownloadNotifier notifier = getDownloadNotifier(context);
        if (notifier == null) return;

        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                .setIsOfflinePage(true).setDownloadGuid(guid).setFileName(displayName).build();

        notifier.notifyDownloadPaused(downloadInfo);
    }

    /**
     * Update download notification to interrupted.
     * @param context Context to show notifications.
     * @param guid GUID of a request to download a page related to the notification.
     * @param displayName Name to be displayed on notification.
     */
    @CalledByNative
    public static void notifyDownloadInterrupted(Context context, String guid, String displayName) {
        DownloadNotifier notifier = getDownloadNotifier(context);
        if (notifier == null) return;

        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                                            .setIsOfflinePage(true)
                                            .setDownloadGuid(guid)
                                            .setFileName(displayName)
                                            .setIsResumable(true)
                                            .build();

        notifier.notifyDownloadInterrupted(downloadInfo, true);
    }

    /**
     * Update download notification to canceled.
     * @param context Context to show notifications.
     * @param guid GUID of a request to download a page related to the notification.
     */
    @CalledByNative
    public static void notifyDownloadCanceled(Context context, String guid) {
        DownloadNotifier notifier = getDownloadNotifier(context);
        if (notifier == null) return;

        notifier.notifyDownloadCanceled(guid);
    }

    /**
     * Shows a "Downloading ..." toast for the requested items already scheduled for download.
     * @param context Context to show toast.
     */
    @CalledByNative
    public static void showDownloadingToast(Context context) {
        Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show();
    }

    private static DownloadNotifier getDownloadNotifier(Context context) {
        return DownloadManagerService.getDownloadManagerService(context).getDownloadNotifier();
    }
}
