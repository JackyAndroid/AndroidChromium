// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.chromium.base.Log;

/**
 * This {@link BroadcastReceiver} handles clicks to notifications that
 * downloads from the browser are in progress/complete.  Clicking on an
 * in-progress or failed download will open the download manager.  Clicking on
 * a complete, successful download will open the file.
 */
public class OpenDownloadReceiver extends BroadcastReceiver {
    private static final String TAG = "cr.DownloadReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        if (!DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
            openDownloadsPage(context);
            return;
        }
        long ids[] = intent.getLongArrayExtra(
                DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);
        if (ids == null || ids.length == 0) {
            openDownloadsPage(context);
            return;
        }
        long id = ids[0];
        DownloadManager manager = (DownloadManager) context.getSystemService(
                Context.DOWNLOAD_SERVICE);
        Uri uri = manager.getUriForDownloadedFile(id);
        if (uri == null) {
            // Open the downloads page
            openDownloadsPage(context);
        } else {
            Intent launchIntent = new Intent(Intent.ACTION_VIEW);
            launchIntent.setDataAndType(uri, manager.getMimeTypeForDownloadedFile(id));
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(launchIntent);
            } catch (ActivityNotFoundException e) {
                openDownloadsPage(context);
            }
        }
    }

    /**
     * Open the Activity which shows a list of all downloads.
     * @param context
     */
    private void openDownloadsPage(Context context) {
        Intent pageView = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        pageView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(pageView);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Cannot find Downloads app", e);
        }
    }
}