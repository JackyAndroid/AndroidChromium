// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.StringDef;

import org.chromium.base.Log;
import org.chromium.base.StreamUtil;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Service that is responsible for uploading crash minidumps to the Google crash server.
 */
public class MinidumpUploadService extends IntentService {
    private static final String TAG = "MinidmpUploadService";
    // Intent actions
    private static final String ACTION_FIND_LAST =
            "com.google.android.apps.chrome.crash.ACTION_FIND_LAST";

    @VisibleForTesting
    static final String ACTION_FIND_ALL = "com.google.android.apps.chrome.crash.ACTION_FIND_ALL";

    @VisibleForTesting
    static final String ACTION_UPLOAD = "com.google.android.apps.chrome.crash.ACTION_UPLOAD";

    @VisibleForTesting
    static final String ACTION_FORCE_UPLOAD =
            "com.google.android.apps.chrome.crash.ACTION_FORCE_UPLOAD";

    // Intent bundle keys
    @VisibleForTesting
    static final String FILE_TO_UPLOAD_KEY = "minidump_file";
    static final String UPLOAD_LOG_KEY = "upload_log";
    static final String FINISHED_LOGCAT_EXTRACTION_KEY = "upload_extraction_completed";
    static final String LOCAL_CRASH_ID_KEY = "local_id";

    /**
     * The number of times we will try to upload a crash.
     */
    @VisibleForTesting
    static final int MAX_TRIES_ALLOWED = 3;

    /**
     * Histogram related constants.
     */
    private static final String HISTOGRAM_NAME_PREFIX = "Tab.AndroidCrashUpload_";
    private static final int HISTOGRAM_MAX = 2;
    private static final int FAILURE = 0;
    private static final int SUCCESS = 1;

    @StringDef({BROWSER, RENDERER, GPU, OTHER})
    public @interface ProcessType {}
    static final String BROWSER = "Browser";
    static final String RENDERER = "Renderer";
    static final String GPU = "GPU";
    static final String OTHER = "Other";

    static final String[] TYPES = {BROWSER, RENDERER, GPU, OTHER};

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
        } else if (ACTION_FORCE_UPLOAD.equals(intent.getAction())) {
            handleForceUploadCrash(intent);
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
     */
    public static void storeBreakpadUploadStatsInUma(ChromePreferenceManager pref) {
        for (String type : TYPES) {
            for (int success = pref.getCrashSuccessUploadCount(type); success > 0; success--) {
                RecordHistogram.recordEnumeratedHistogram(
                        HISTOGRAM_NAME_PREFIX + type,
                        SUCCESS,
                        HISTOGRAM_MAX);
            }
            for (int fail = pref.getCrashFailureUploadCount(type); fail > 0; fail--) {
                RecordHistogram.recordEnumeratedHistogram(
                        HISTOGRAM_NAME_PREFIX + type,
                        FAILURE,
                        HISTOGRAM_MAX);
            }

            pref.setCrashSuccessUploadCount(type, 0);
            pref.setCrashFailureUploadCount(type, 0);
        }
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
            incrementCrashSuccessUploadCount(getNewNameAfterSuccessfulUpload(minidumpFileName));
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
                    incrementCrashFailureUploadCount(newName);
                    Log.d(TAG, "Giving up on trying to upload " + minidumpFileName + "after "
                            + tries + " number of tries.");
                }
            } else {
                Log.w(TAG, "Failed to rename minidump " + minidumpFileName);
            }
        }
    }

    private static String getNewNameAfterSuccessfulUpload(String fileName) {
        return fileName.replace("dmp", "up");
    }

    @ProcessType
    @VisibleForTesting
    protected static String getCrashType(String fileName) {
        // Read file and get the line containing name="ptype".
        BufferedReader fileReader = null;
        try {
            fileReader = new BufferedReader(new FileReader(fileName));
            String line;
            while ((line = fileReader.readLine()) != null) {
                if (line.equals("Content-Disposition: form-data; name=\"ptype\"")) {
                    // Crash type is on the line after the next line.
                    fileReader.readLine();
                    String crashType = fileReader.readLine();
                    if (crashType == null) {
                        return OTHER;
                    }

                    if (crashType.equals("browser")) {
                        return BROWSER;
                    }

                    if (crashType.equals("renderer")) {
                        return RENDERER;
                    }

                    if (crashType.equals("gpu-process")) {
                        return GPU;
                    }

                    return OTHER;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Error while reading crash file.", e.toString());
        } finally {
            StreamUtil.closeQuietly(fileReader);
        }
        return OTHER;
    }

    /**
     * Increment the count of success/failure by 1 and distinguish between different types of
     * crashes by looking into the file.
     * @param fileName is the name of a minidump file that contains the type of crash.
     */
    private void incrementCrashSuccessUploadCount(String fileName) {
        ChromePreferenceManager.getInstance(this)
            .incrementCrashSuccessUploadCount(getCrashType(fileName));
    }

    private void incrementCrashFailureUploadCount(String fileName) {
        ChromePreferenceManager.getInstance(this)
            .incrementCrashFailureUploadCount(getCrashType(fileName));
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
     * Attempts to upload all minidump files using the given {@link android.content.Context}.
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
     * Attempts to upload the crash report with the given local ID.
     *
     * Note that this method is asynchronous. All that is guaranteed is that
     * upload attempts will be enqueued.
     *
     * This method is safe to call from the UI thread.
     *
     * @param context the context to use for the intent.
     * @param localId The local ID of the crash report.
     */
    @CalledByNative
    public static void tryUploadCrashDumpWithLocalId(Context context, String localId) {
        Intent intent = new Intent(context, MinidumpUploadService.class);
        intent.setAction(ACTION_FORCE_UPLOAD);
        intent.putExtra(LOCAL_CRASH_ID_KEY, localId);
        context.startService(intent);
    }

    private void handleForceUploadCrash(Intent intent) {
        String localId = intent.getStringExtra(LOCAL_CRASH_ID_KEY);
        if (localId == null || localId.isEmpty()) {
            Log.w(TAG, "Cannot force crash upload since local crash id is absent.");
            return;
        }

        Context context = getApplicationContext();
        CrashFileManager fileManager = new CrashFileManager(context.getCacheDir());
        File minidumpFile = fileManager.getCrashFileWithLocalId(localId);
        if (minidumpFile == null) {
            Log.w(TAG, "Could not find a crash dump with local ID " + localId);
            return;
        }
        File renamedMinidumpFile = fileManager.trySetForcedUpload(minidumpFile);
        if (renamedMinidumpFile == null) {
            Log.w(TAG, "Could not rename the file " + minidumpFile.getName() + " for re-upload");
            return;
        }
        File logfile = fileManager.getCrashUploadLogFile();
        Intent uploadIntent = createUploadIntent(context, renamedMinidumpFile, logfile);

        // This method is intended to be used for manually triggering an attempt to upload an
        // already existing crash dump. Such a crash dump should already have had a chance to attach
        // the logcat to the minidump. Moreover, it's almost certainly too late to try to extract
        // the logcat now, since typically some time has passed between the crash and the user's
        // manual upload attempt.
        uploadIntent.putExtra(FINISHED_LOGCAT_EXTRACTION_KEY, true);
        startService(uploadIntent);
    }
}
