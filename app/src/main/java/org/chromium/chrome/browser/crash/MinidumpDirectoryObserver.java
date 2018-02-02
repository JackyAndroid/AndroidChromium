// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.FileObserver;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.PathUtils;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.components.minidump_uploader.CrashFileManager;

import java.io.File;

/**
 * The FileObserver to monitor the minidump directory.
 */
public class MinidumpDirectoryObserver extends FileObserver {

    private static final String TAG = "MinidumpDirObserver";

    public MinidumpDirectoryObserver() {
        // The file observer detects MOVED_TO for child processes.
        super(new File(PathUtils.getCacheDirectory(),
                CrashFileManager.CRASH_DUMP_DIR).toString(), FileObserver.MOVED_TO);
    }

    /**
     *  When a minidump is detected, upload it to Google crash server
     */
    @Override
    public void onEvent(int event, String path) {
        // This is executed on a thread dedicated to FileObserver.
        if (CrashFileManager.isMinidumpMIMEFirstTry(path)) {
            Context appContext = ContextUtils.getApplicationContext();
            try {
                Intent intent =
                        MinidumpUploadService.createFindAndUploadLastCrashIntent(appContext);
                appContext.startService(intent);
                Log.i(TAG, "Detects a new minidump %s send intent to MinidumpUploadService", path);
                RecordUserAction.record("MobileBreakpadUploadAttempt");
            } catch (SecurityException e) {
                // For KitKat and below, there was a framework bug which cause us to not be able to
                // find our own crash uploading service. Ignore a SecurityException here on older
                // OS versions since the crash will eventually get uploaded on next start.
                // crbug/542533
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    throw e;
                }
            }
        }
    }
}
