// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import org.chromium.base.Log;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Prepares the minidump for first upload attempt by combining both the
 * minidump file and the logcat file.
 */
public class MinidumpPreparationService extends IntentService {
    private static final String TAG = "DumpPrepService";
    private static final String LOGCAT_FILE_KEY = "logcat";
    private static final String MINIDUMP_FILE_KEY = "minidump";
    private static final String REDIRECT_INTENT_KEY = "redirect_intent";

    public static Intent createMinidumpPreparationIntent(
            Context context,
            File minidumpFile,
            File logcatFile,
            Intent redirectIntent) {
        Intent intent = new Intent(context, MinidumpPreparationService.class);
        if (minidumpFile != null) {
            intent.putExtra(MINIDUMP_FILE_KEY, minidumpFile.getName());
        }
        if (logcatFile != null) {
            intent.putExtra(LOGCAT_FILE_KEY, logcatFile.getName());
        }
        if (redirectIntent != null) {
            intent.putExtra(REDIRECT_INTENT_KEY, redirectIntent);
        }
        return intent;
    }

    public MinidumpPreparationService() {
        super(TAG);
        setIntentRedelivery(true);
    }

    private Callable<Boolean> createMinidumpPreparationCallable(Context context, Intent intent) {
        String minidumpFilePath = intent.getStringExtra(MINIDUMP_FILE_KEY);
        String logcatFilePath = intent.getStringExtra(LOGCAT_FILE_KEY);
        CrashFileManager fileManager = new CrashFileManager(context.getCacheDir());
        File minidumpFile = fileManager.getCrashFile(minidumpFilePath);
        File logcatFile = fileManager.getCrashFile(logcatFilePath);
        Intent redirectIntent = intent.getParcelableExtra(REDIRECT_INTENT_KEY);
        return new MinidumpPreparationCallable(
                getApplicationContext(), minidumpFile, logcatFile, redirectIntent);
    }

    private void handleMinidumpPreparationRequest(Intent intent) {
        try {
            if (!createMinidumpPreparationCallable(getApplicationContext(), intent).call()) {
                Log.w(TAG, "Fail to prepare minidump with logcat!");
            }
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        handleMinidumpPreparationRequest(intent);
    }
}
