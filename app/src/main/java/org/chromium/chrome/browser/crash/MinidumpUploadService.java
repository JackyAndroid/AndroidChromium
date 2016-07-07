// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.preference.PreferenceManager;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;

import java.io.File;

/**
 * Service that is responsible for uploading crash minidumps to the Google crash server.
 */
public class MinidumpUploadService extends IntentService {

    private static final String TAG = "MinidmpUploadService";

    // Intent actions
    private static final String ACTION_FIND_LAST =
            "com.google.android.apps.chrome.crash.ACTION_FIND_LAST";
    @VisibleForTesting
    static final String ACTION_FIND_ALL =
            "com.google.android.apps.chrome.crash.ACTION_FIND_ALL";
    @VisibleForTesting
    static final String ACTION_UPLOAD =
            "com.google.android.apps.chrome.crash.ACTION_UPLOAD";

    // Intent bundle keys
    @VisibleForTesting
    static final String FILE_TO_UPLOAD_KEY = "minidump_file";
    static final String UPLOAD_LOG_KEY = "upload_log";
    static final String FINISHED_LOGCAT_EXTRACTION_KEY = "upload_extraction_completed";

    /**
     * The number of times we will try to upload a crash.
     */
    @VisibleForTesting
    static final int MAX_TRIES_ALLOWED = 3;

    public MinidumpUploadService() {
        super(TAG);
        setIntentRedelivery(true);
    }

    /**
     * Attempts to populate logcat dumps to be associated with the minidumps
     * if they do not already exists.
     */
    private void tryPopulateLogcat(Intent redirectAction) {
        redirectAction.putExtra(FINISHED_LOGCAT_EXTRACTION_KEY, true);

        Context context = getApplicationContext();
        CrashFileManager fileManager = new CrashFileManager(context.getCacheDir());
        File[] dumps = fileManager.getMinidumpWithoutLogcat();

        if (dumps.length == 0) {
            onHandleIntent(redirectAction);
            return;
        }
        context.startService(LogcatExtractionService.createLogcatExtractionTask(
                context, dumps, redirectAction));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (isMinidumpCleanNeeded()) {
            // Temporarily allowing disk access while fixing. TODO: http://crbug.com/527429
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
            try {
                final CrashFileManager crashFileManager =
                        new CrashFileManager(getApplicationContext().getCacheDir());
                // Cleaning minidumps in a background not to block the Ui thread.
                // NOTE: {@link CrashFileManager#cleanAllMiniDumps()} is not thread-safe and can
                // possibly result in race condition by calling from multiple threads. However, this
                // should only result in warning messages in logs.
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        crashFileManager.cleanAllMiniDumps();
                        return null;
                    }
                }.execute();
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) return;
        if (!intent.getBooleanExtra(FINISHED_LOGCAT_EXTRACTION_KEY, false)) {
            // The current intent was sent before a chance to gather some
            // logcat information. tryPopulateLogcat will re-send the
            // same action once it has a go at gather logcat.
            tryPopulateLogcat(intent);
        } else if (ACTION_FIND_LAST.equals(intent.getAction())) {
            handleFindAndUploadLastCrash(intent);
        } else if (ACTION_FIND_ALL.equals(intent.getAction())) {
            handleFindAndUploadAllCrashes();
        } else if (ACTION_UPLOAD.equals(intent.getAction())) {
            handleUploadCrash(intent);
        } else {
            Log.w(TAG, "Got unknown action from intent: " + intent.getAction());
        }
    }

    /**
     * Creates an intent that when started will find the last created or
     * updated minidump, and try to upload it.
     *
     * @param context the context to use for the intent.
     * @return an Intent to use to start the service.
     */
    public static Intent createFindAndUploadLastCrashIntent(Context context) {
        Intent intent = new Intent(context, MinidumpUploadService.class);
        intent.setAction(ACTION_FIND_LAST);
        return intent;
    }

    /**
     * Stores the successes and failures from uploading crash to UMA,
     * and clears them from breakpad.
     */
    public static void storeBreakpadUploadStatsInUma(ChromePreferenceManager pref) {
        for (int success = pref.getBreakpadUploadSuccessCount(); success > 0; success--) {
            RecordUserAction.record("MobileBreakpadUploadSuccess");
        }

        for (int fail = pref.getBreakpadUploadFailCount(); fail > 0; fail--) {
            RecordUserAction.record("MobileBreakpadUploadFail");
        }

        pref.setBreakpadUploadSuccessCount(0);
        pref.setBreakpadUploadFailCount(0);
    }

    private void handleFindAndUploadLastCrash(Intent intent) {
        CrashFileManager fileManager = new CrashFileManager(getApplicationContext().getCacheDir());
        File[] minidumpFiles = fileManager.getAllMinidumpFilesSorted();
        if (minidumpFiles.length == 0) {
            // Try again later. Maybe the minidump hasn't finished being written.
            Log.d(TAG, "Could not find any crash dumps to upload");
            return;
        }
        File minidumpFile = minidumpFiles[0];
        File logfile = fileManager.getCrashUploadLogFile();
        Intent uploadIntent = createUploadIntent(getApplicationContext(), minidumpFile, logfile);

        // We should have at least one chance to secure logcat to the minidump now.
        uploadIntent.putExtra(FINISHED_LOGCAT_EXTRACTION_KEY, true);
        startService(uploadIntent);
    }

    /**
     * Creates an intent that when started will find all minidumps, and try to upload them.
     *
     * @param context the context to use for the intent.
     * @return an Intent to use to start the service.
     */
    @VisibleForTesting
    static Intent createFindAndUploadAllCrashesIntent(Context context) {
        Intent intent = new Intent(context, MinidumpUploadService.class);
        intent.setAction(ACTION_FIND_ALL);
        return intent;
    }

    private void handleFindAndUploadAllCrashes() {
        CrashFileManager fileManager = new CrashFileManager(getApplicationContext().getCacheDir());
        File[] minidumps = fileManager.getAllMinidumpFiles();
        File logfile = fileManager.getCrashUploadLogFile();
        Log.i(TAG, "Attempting to upload accumulated crash dumps.");
        for (File minidump : minidumps) {
            Intent uploadIntent = createUploadIntent(getApplicationContext(), minidump, logfile);
            startService(uploadIntent);
        }
    }

    /**
     * Creates an intent that when started will find all minidumps, and try to upload them.
     *
     * @param minidumpFile the minidump file to upload.
     * @return an Intent to use to start the service.
     */
    @VisibleForTesting
    public static Intent createUploadIntent(Context context, File minidumpFile, File logfile) {
        Intent intent = new Intent(context, MinidumpUploadService.class);
        intent.setAction(ACTION_UPLOAD);
        intent.putExtra(FILE_TO_UPLOAD_KEY, minidumpFile.getAbsolutePath());
        intent.putExtra(UPLOAD_LOG_KEY, logfile.getAbsolutePath());
        return intent;
    }

    private void handleUploadCrash(Intent intent) {
        String minidumpFileName = intent.getStringExtra(FILE_TO_UPLOAD_KEY);
        if (minidumpFileName == null || minidumpFileName.isEmpty()) {
            Log.w(TAG, "Cannot upload crash data since minidump is absent.");
            return;
        }
        File minidumpFile = new File(minidumpFileName);
        if (!minidumpFile.isFile()) {
            Log.w(TAG, "Cannot upload crash data since specified minidump "
                    + minidumpFileName + " is not present.");
            return;
        }
        int tries = CrashFileManager.readAttemptNumber(minidumpFileName);

        // Since we do not rename a file after reaching max number of tries,
        // files that have maxed out tries will NOT reach this.
        if (tries >= MAX_TRIES_ALLOWED || tries < 0) {
            // Reachable only if the file naming is incorrect by current standard.
            // Thus we log an error instead of recording failure to UMA.
            Log.e(TAG, "Giving up on trying to upload " + minidumpFileName
                    + " after failing to read a valid attempt number.");
            return;
        }

        String logfileName = intent.getStringExtra(UPLOAD_LOG_KEY);
        File logfile = new File(logfileName);

        // Try to upload minidump
        MinidumpUploadCallable minidumpUploadCallable =
                createMinidumpUploadCallable(minidumpFile, logfile);
        @MinidumpUploadCallable.MinidumpUploadStatus int uploadStatus =
                minidumpUploadCallable.call();

        if (uploadStatus == MinidumpUploadCallable.UPLOAD_SUCCESS) {
            // Only update UMA stats if an intended and successful upload.
            ChromePreferenceManager.getInstance(this).incrementBreakpadUploadSuccessCount();
        } else if (uploadStatus == MinidumpUploadCallable.UPLOAD_FAILURE) {
            // Unable to upload minidump. Incrementing try number and restarting.

            // Only create another attempt if we have successfully renamed
            // the file.
            String newName = CrashFileManager.tryIncrementAttemptNumber(minidumpFile);
            if (newName != null) {
                if (++tries < MAX_TRIES_ALLOWED) {
                    // TODO(nyquist): Do this as an exponential backoff.
                    MinidumpUploadRetry.scheduleRetry(getApplicationContext());
                } else {
                    // Only record failure to UMA after we have maxed out the allotted tries.
                    ChromePreferenceManager.getInstance(this).incrementBreakpadUploadFailCount();
                    Log.d(TAG, "Giving up on trying to upload " + minidumpFileName + "after "
                            + tries + " number of tries.");
                }
            } else {
                Log.w(TAG, "Failed to rename minidump " + minidumpFileName);
            }
        }
    }

    /**
     * Factory method for creating minidump callables.
     *
     * This may be overridden for tests.
     *
     * @param minidumpFile the File to upload.
     * @param logfile the Log file to write to upon successful uploads.
     * @return a new MinidumpUploadCallable.
     */
    @VisibleForTesting
    MinidumpUploadCallable createMinidumpUploadCallable(File minidumpFile, File logfile) {
        return new MinidumpUploadCallable(minidumpFile, logfile, getApplicationContext());
    }

    /**
     * Attempts to upload all minidump files  using the given {@link android.content.Context}.
     *
     * Note that this method is asynchronous. All that is guaranteed is that
     * upload attempts will be enqueued.
     *
     * This method is safe to call from the UI thread.
     *
     * @param context Context of the application.
     */
    public static void tryUploadAllCrashDumps(Context context) {
        Intent findAndUploadAllCrashesIntent = createFindAndUploadAllCrashesIntent(context);
        context.startService(findAndUploadAllCrashesIntent);
    }

    /**
     * Checks whether it is the first time restrictions for cellular uploads should apply. Is used
     * to determine whether unsent crash uploads should be deleted which should happen only once.
     */
    @VisibleForTesting
    protected boolean isMinidumpCleanNeeded() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        // If cellular upload logic is enabled and the preference used for that is not initialized
        // then this is the first time that logic is enabled.
        boolean cleanNeeded =
                !sharedPreferences.contains(MinidumpUploadCallable.PREF_LAST_UPLOAD_DAY)
                && PrivacyPreferencesManager.getInstance(getApplicationContext()).isUploadLimited();

        // Initialize the preference with default value to make sure the above check works only
        // once.
        if (cleanNeeded) {
            sharedPreferences.edit().putInt(MinidumpUploadCallable.PREF_LAST_UPLOAD_DAY, 0).apply();
        }
        return cleanNeeded;
    }
}
