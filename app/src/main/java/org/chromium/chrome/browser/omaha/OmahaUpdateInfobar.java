// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omaha;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.Log;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.infobar.ConfirmInfoBar;
import org.chromium.chrome.browser.infobar.InfoBar;
import org.chromium.chrome.browser.infobar.InfoBarListeners;

import java.io.File;
import java.util.concurrent.TimeUnit;


/**
 * An InfoBar implementation that displays a message and a button that sends a user to the
 * given URL.
 */
public class OmahaUpdateInfobar extends ConfirmInfoBar {
    private static final String TAG = "OmahaUpdateInfobar";
    private static final int ACTION_CLOSED = 0;
    private static final int ACTION_CLICKED_UPDATE_SUCCESS = 1;
    private static final int ACTION_CLICKED_UPDATE_FAIL = 2;
    private static final int ACTION_DISMISSED = 3;
    private static final int ACTION_MAX = 3;

    private final String mUrl;
    private final Context mActivityContext;
    private boolean mActionTaken;
    private final long mShownTime;

    /**
     * Listens for the InfoBar being dismissed and checks whether it was caused by a user action
     * on the InfoBar or another event which caused the InfoBar to be dimissed implicitly
     * (e.g. user entered a new url in the omnibox.)
      */
    private static class DismissListener implements InfoBarListeners.Confirm {
        @Override
        public void onInfoBarDismissed(InfoBar infoBar) {
            assert infoBar instanceof OmahaUpdateInfobar;

            OmahaUpdateInfobar infoBarInstance = (OmahaUpdateInfobar) infoBar;
            // If the user hasn't taken an action and the infobar is getting dismissed, then
            // record that it was dismissed.
            if (!infoBarInstance.mActionTaken) {
                infoBarInstance.recordHistograms(ACTION_DISMISSED);
            }
        }

        @Override
        public void onConfirmInfoBarButtonClicked(ConfirmInfoBar infoBar, boolean confirm) {
            // Ignored.
        }
    }

    public OmahaUpdateInfobar(Context activityContext, String message, String buttonMessage,
                String url) {
        super(new DismissListener(), R.drawable.infobar_warning, null, message, null, buttonMessage,
                null);
        mActivityContext = activityContext;
        mUrl = url;
        mShownTime = SystemClock.uptimeMillis();
        recordInternalStorageSize();
    }

    private void recordHistograms(int action) {
        RecordHistogram.recordEnumeratedHistogram("GoogleUpdate.InfoBar.ActionTaken",
                action, ACTION_MAX);
        RecordHistogram.recordMediumTimesHistogram("GoogleUpdate.InfoBar.TimeShown",
                SystemClock.uptimeMillis() - mShownTime, TimeUnit.MILLISECONDS);
    }

    private void recordInternalStorageSize() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                File path = Environment.getDataDirectory();
                StatFs statFs = new StatFs(path.getAbsolutePath());
                int size;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    size = getSize(statFs);
                } else {
                    size = getSizeUpdatedApi(statFs);
                }
                RecordHistogram.recordLinearCountHistogram(
                        "GoogleUpdate.InfoBar.InternalStorageSizeAvailable", size, 1, 200, 100);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static int getSizeUpdatedApi(StatFs statFs) {
        return (int) statFs.getAvailableBytes() / (1024 * 1024);
    }

    @SuppressWarnings("deprecation")
    private static int getSize(StatFs statFs) {
        int blockSize = statFs.getBlockSize();
        int availableBlocks = statFs.getAvailableBlocks();
        int size = (blockSize * availableBlocks) / (1024 * 1024);
        return size;
    }

    @Override
    public void onCloseButtonClicked() {
        mActionTaken = true;
        recordHistograms(ACTION_CLOSED);
        super.onCloseButtonClicked();
    }

    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
        mActionTaken = true;
        dismissJavaOnlyInfoBar();

        // Fire an intent to open the URL.
        try {
            Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mUrl));
            mActivityContext.startActivity(launchIntent);
            recordHistograms(ACTION_CLICKED_UPDATE_SUCCESS);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to launch Activity for: " + mUrl);
            recordHistograms(ACTION_CLICKED_UPDATE_FAIL);
        }
    }

    /** Returns the URL that is supposed to be opened when the button is clicked. */
    @VisibleForTesting
    public String getUrl() {
        return mUrl;
    }
}
