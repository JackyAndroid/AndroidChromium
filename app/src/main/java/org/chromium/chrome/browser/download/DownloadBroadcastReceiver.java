// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.chromium.chrome.browser.util.IntentUtils;

/**
 * This {@link BroadcastReceiver} handles clicks to download notifications and their action buttons.
 * Clicking on an in-progress or failed download will open the download manager. Clicking on
 * a complete, successful download will open the file. Clicking on the resume button of a paused
 * download will relaunch the browser process and try to resume the download from where it is
 * stopped.
 */
public class DownloadBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case DownloadManager.ACTION_NOTIFICATION_CLICKED:
                openDownload(context, intent);
                break;
            case DownloadNotificationService.ACTION_DOWNLOAD_RESUME:
            case DownloadNotificationService.ACTION_DOWNLOAD_CANCEL:
            case DownloadNotificationService.ACTION_DOWNLOAD_PAUSE:
            case DownloadNotificationService.ACTION_DOWNLOAD_OPEN:
                performDownloadOperation(context, intent);
                break;
            default:
                break;
        }
    }

    /**
     * Called to open a given download item that is downloaded by the android DownloadManager.
     * @param context Context of the receiver.
     * @param intent Intent from the android DownloadManager.
     */
    private void openDownload(final Context context, Intent intent) {
        long ids[] =
                intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);
        if (ids == null || ids.length == 0) {
            DownloadManagerService.openDownloadsPage(context);
            return;
        }
        long id = ids[0];
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = manager.getUriForDownloadedFile(id);
        if (uri == null) {
            // Open the downloads page
            DownloadManagerService.openDownloadsPage(context);
        } else {
            String downloadFilename = IntentUtils.safeGetStringExtra(
                    intent, DownloadNotificationService.EXTRA_DOWNLOAD_FILE_PATH);
            boolean isSupportedMimeType =  IntentUtils.safeGetBooleanExtra(
                    intent, DownloadNotificationService.EXTRA_IS_SUPPORTED_MIME_TYPE, false);
            Intent launchIntent = DownloadManagerService.getLaunchIntentFromDownloadId(
                    context, downloadFilename, id, isSupportedMimeType);
            if (!DownloadUtils.fireOpenIntentForDownload(context, launchIntent)) {
                DownloadManagerService.openDownloadsPage(context);
            }
        }
    }

    /**
     * Called to perform a download operation. This will call the DownloadNotificationService
     * to start the browser process asynchronously, and resume or cancel the download afterwards.
     * @param context Context of the receiver.
     * @param intent Intent retrieved from the notification.
     */
    private void performDownloadOperation(final Context context, Intent intent) {
        if (DownloadNotificationService.isDownloadOperationIntent(intent)) {
            Intent launchIntent = new Intent(intent);
            launchIntent.setComponent(new ComponentName(
                    context.getPackageName(), DownloadNotificationService.class.getName()));
            context.startService(launchIntent);
        }
    }
}
