// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.content.Context;
import android.content.Intent;

import org.chromium.base.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Callable helper for {@link MinidumpPreparationService}.
 *
 * This class will append a logcat file to a minidump file for upload.
 */
public class MinidumpPreparationCallable implements Callable<Boolean> {

    private static final String TAG = "DumpPrepCallable";

    private static final String LOGCAT_CONTENT_DISPOSITION =
            "Content-Disposition: form-data; name=\"logcat\"; filename=\"logcat\"";

    private static final String LOGCAT_CONTENT_TYPE =
            "Content-Type: text/plain";

    private final File mLogcatFile;
    private final File mMinidumpFile;
    private final Intent mRedirectIntent;
    private final Context mContext;
    private final CrashFileManager mFileManager;

    public MinidumpPreparationCallable(
            Context context,
            File miniDumpFile,
            File logcatFile,
            Intent redirectIntent) {
        mContext = context;
        mLogcatFile = logcatFile;
        mMinidumpFile = miniDumpFile;
        mRedirectIntent = redirectIntent;
        mFileManager = new CrashFileManager(context.getCacheDir());
    }

    /**
     * Read the boundary from the first lien of the file.
     */
    private static String getBoundary(File processMinidumpFile) throws IOException {
        BufferedReader bReader = null;
        try {
            bReader = new BufferedReader(new FileReader(processMinidumpFile));
            return bReader.readLine();
        } finally {
            if (bReader != null) {
                bReader.close();
            }
        }
    }

    /**
     * Write the invoking {@link MinidumpPreparationCallable}s logcat data to
     * the specified target {@link File}.
     *
     * Target file is overwritten, not appended to the end.
     *
     * @param targetFile File to which logcat data should be written.
     * @param boundary String MIME boundary to prepend.
     * @throws IOException if something goes wrong.
     */
    private static void writeLogcat(File targetFile, List<String> logcat, String boundary)
            throws IOException {
        BufferedWriter bWriter = null;
        try {
            bWriter = new BufferedWriter(new FileWriter(targetFile, false));
            bWriter.write(boundary);
            bWriter.newLine();
            // Next we write the logcat data in a MIME block.
            bWriter.write(LOGCAT_CONTENT_DISPOSITION);
            bWriter.newLine();
            bWriter.write(LOGCAT_CONTENT_TYPE);
            bWriter.newLine();
            bWriter.newLine();
            // Emits the contents of the buffer into the output file.
            for (String ln : logcat) {
                bWriter.write(ln);
                bWriter.newLine();
            }
        } finally {
            if (bWriter != null) {
                bWriter.close();
            }
        }
    }

    /**
     * Append the minidump file data to the specified target {@link File}.
     *
     * @param processMinidumpFile File containing data to append.
     * @param targetFile File to which data should be appended.
     * @throws IOException when standard IO errors occur.
     */
    private static void appendMinidump(
            File processMinidumpFile, File targetFile) throws IOException {
        BufferedInputStream bIn = null;
        BufferedOutputStream bOut = null;
        try {
            byte[] buf = new byte[256];
            bIn = new BufferedInputStream(new FileInputStream(processMinidumpFile));
            bOut = new BufferedOutputStream(new FileOutputStream(targetFile, true));
            int count;
            while ((count = bIn.read(buf)) != -1) {
                bOut.write(buf, 0, count);
            }
        } finally {
            if (bIn != null) bIn.close();
            if (bOut != null) bOut.close();
        }
    }

    private boolean augmentTargetFile(List<String> logcat) {
        File targetFile = null;
        try {
            targetFile = mFileManager.createNewTempFile(mMinidumpFile.getName() + ".try0");

            String boundary = getBoundary(mMinidumpFile);
            if (boundary == null) {
                return false;
            }
            writeLogcat(targetFile, logcat, boundary);
            // Finally Reopen and append the original minidump MIME sections
            // including the leading boundary.
            appendMinidump(mMinidumpFile, targetFile);
            if (!mMinidumpFile.delete()) {
                Log.w(TAG, "Fail to delete minidump file: " + mMinidumpFile.getName());
            }
            return true;
        } catch (IOException e) {
            String msg = String.format(
                    "Error while tyring to annotate minidump file %s with logcat data",
                    mMinidumpFile.getAbsoluteFile());
            Log.w(TAG, msg, e);
            if (targetFile != null) {
                CrashFileManager.deleteFile(targetFile);
            }
            return false;
        }
    }

    private List<String> getLogcatAsList() throws IOException {
        BufferedReader r = null;
        try {
            List<String> logcat = new LinkedList<String>();
            if (mLogcatFile != null) {
                r = new BufferedReader(new FileReader(mLogcatFile));
                String ln;
                while ((ln = r.readLine()) != null) {
                    logcat.add(ln);
                }
            }
            return Collections.unmodifiableList(logcat);
        } finally {
            if (r != null) {
                r.close();
            }
        }
    }

    @Override
    public Boolean call() throws IOException {
        // By default set the basic minidump to be uploaded. That way, even if
        // there are errors augmenting the minidump with logcat data something
        // can still upload something.
        List<String> logcat = getLogcatAsList();
        boolean success = true;
        if (!logcat.isEmpty()) {
            success = augmentTargetFile(logcat);
        }
        if (mRedirectIntent != null) {
            mContext.startService(mRedirectIntent);
        }
        return success;
    }
}
