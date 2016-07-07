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
 * Service that extracts the logcat dump and saves it to a file.
 */
public class LogcatExtractionService extends IntentService {
    private static final String TAG = "LogcatExtraction";

    private static final String MINIDUMP_FILENAMES = "minidump_filenames";
    private static final String REDIRECT_INTENT = "redirect_intent";

    public static Intent createLogcatExtractionTask(
            Context context, File[] files, Intent redirectIntent) {
        Intent intent = new Intent(context, LogcatExtractionService.class);
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; ++i) {
            names[i] = files[i].getName();
        }
        intent.putExtra(MINIDUMP_FILENAMES, names);
        intent.putExtra(REDIRECT_INTENT, redirectIntent);
        return intent;
    }

    public LogcatExtractionService() {
        super(TAG);
        setIntentRedelivery(true);
    }

    private static Callable<Boolean> createLogcatExtractionServiceCallable(
            Context context, String[] filenames, Intent redirectIntent) {
        return new LogcatExtractionCallable(context, filenames, redirectIntent);
    }

    private void handleLogcatExtractionRequest(Intent intent) {
        String[] minidumpFileNames = intent.getStringArrayExtra(MINIDUMP_FILENAMES);
        if (minidumpFileNames == null) {
            Log.w(TAG, "Minidump filename not found. Cannot extra logcat.");
            return;
        }

        Intent redirectIntent = intent.getParcelableExtra(REDIRECT_INTENT);

        try {
            Context ctx = getApplicationContext();
            createLogcatExtractionServiceCallable(ctx, minidumpFileNames, redirectIntent).call();
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        handleLogcatExtractionRequest(intent);
    }
}
