// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsible for the Crash Report directory. It routinely scans the directory
 * for new Minidump files and takes appropriate actions by either uploading new
 * crash dumps or deleting old ones.
 */
public class CrashFileManager {
    private static final String TAG = "CrashFileManager";

    @VisibleForTesting
    static final String CRASH_DUMP_DIR = "Crash Reports";

    // This should mirror the C++ CrashUploadList::kReporterLogFilename variable.
    @VisibleForTesting
    static final String CRASH_DUMP_LOGFILE = CRASH_DUMP_DIR + "/uploads.log";

    private static final Pattern MINIDUMP_FIRST_TRY_PATTERN =
            Pattern.compile("\\.dmp([0-9]*)$\\z");

    private static final Pattern MINIDUMP_PATTERN =
            Pattern.compile("\\.dmp([0-9]*)(\\.try[0-9])?\\z");

    private static final Pattern UPLOADED_MINIDUMP_PATTERN = Pattern.compile("\\.up([0-9]*)\\z");

    private static final String UPLOADED_MINIDUMP_SUFFIX = ".up";

    private static final String UPLOAD_ATTEMPT_DELIMITER = ".try";

    @VisibleForTesting
    protected static final String TMP_SUFFIX = ".tmp";

    private static final Pattern TMP_PATTERN = Pattern.compile("\\.tmp\\z");

    /**
     * Comparator used for sorting files by modification
     * Note that the behavior is undecided if the files are created at the same time
     * @return Comparator for prioritizing the more recently modified file
     */
    @VisibleForTesting
    protected static final Comparator<File> sFileComparator =  new Comparator<File>() {
        @Override
        public int compare(File lhs, File rhs) {
            if (lhs == rhs) {
                return 0;
            } else if (lhs.lastModified() < rhs.lastModified()) {
                return 1;
            } else {
                return -1;
            }
        }
    };

    @VisibleForTesting
    static boolean deleteFile(File fileToDelete) {
        boolean isSuccess = fileToDelete.delete();
        if (!isSuccess) {
            Log.w(TAG, "Unable to delete " + fileToDelete.getAbsolutePath());
        }
        return isSuccess;
    }

    public File[] getMinidumpWithoutLogcat() {
        return getMatchingFiles(MINIDUMP_FIRST_TRY_PATTERN);
    }

    public static String tryIncrementAttemptNumber(File mFileToUpload) {
        String newName = filenameWithIncrementedAttemptNumber(mFileToUpload.getPath());
        return mFileToUpload.renameTo(new File(newName)) ? newName : null;
    }

    /**
     * @return The file name to rename to after an addition attempt to upload
     */
    @VisibleForTesting
    public static String filenameWithIncrementedAttemptNumber(String filename) {
        int numTried = readAttemptNumber(filename);
        if (numTried > 0) {
            int newCount = numTried + 1;
            return filename.replaceAll(UPLOAD_ATTEMPT_DELIMITER + numTried,
                    UPLOAD_ATTEMPT_DELIMITER + newCount);
        } else {
            return filename + UPLOAD_ATTEMPT_DELIMITER + "1";
        }
    }

    @VisibleForTesting
    public static int readAttemptNumber(String filename) {
        int tryIndex = filename.lastIndexOf(UPLOAD_ATTEMPT_DELIMITER);
        if (tryIndex >= 0) {
            tryIndex += UPLOAD_ATTEMPT_DELIMITER.length();
            // To avoid out of bound exceptions
            if (tryIndex < filename.length()) {
                // We don't try more than 3 times.
                String numTriesString = filename.substring(
                        tryIndex, tryIndex + 1);
                try {
                    return Integer.parseInt(numTriesString);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    public static boolean tryMarkAsUploaded(File mFileToUpload) {
        return mFileToUpload.renameTo(
                new File(mFileToUpload.getPath().replaceAll(
                        "\\.dmp", UPLOADED_MINIDUMP_SUFFIX)));
    }

    private final File mCacheDir;

    public CrashFileManager(File cacheDir) {
        if (cacheDir == null) {
            throw new NullPointerException("Specified context cannot be null.");
        } else if (!cacheDir.isDirectory()) {
            throw new IllegalArgumentException(cacheDir.getAbsolutePath()
                    + " is not a directory.");
        }
        mCacheDir = cacheDir;
    }

    public File[] getAllMinidumpFiles() {
        return getMatchingFiles(MINIDUMP_PATTERN);
    }

    public File[] getAllMinidumpFilesSorted() {
        File[] minidumps = getAllMinidumpFiles();
        Arrays.sort(minidumps, sFileComparator);
        return minidumps;
    }

    public void cleanOutAllNonFreshMinidumpFiles() {
        for (File f : getAllUploadedFiles()) {
            deleteFile(f);
        }
        for (File f : getAllTempFiles()) {
            deleteFile(f);
        }
    }

    /**
     * Deletes all files including unsent crash reports.
     * Note: This method is called from multiple threads, but it is not thread-safe. It will
     * generate warning messages in logs if race condition occurs.
     */
    @VisibleForTesting
    public void cleanAllMiniDumps() {
        cleanOutAllNonFreshMinidumpFiles();

        for (File f : getAllMinidumpFiles()) {
            deleteFile(f);
        }
    }

    @VisibleForTesting
    File[] getMatchingFiles(final Pattern pattern) {
        // Get dump dir and get all files with specified suffix.. The path
        // constructed here must match chrome_paths.cc (see case
        // chrome::DIR_CRASH_DUMPS).
        File crashDir = getCrashDirectory();
        if (!crashDir.exists()) {
            Log.w(TAG, crashDir.getAbsolutePath() + " does not exist!");
            return new File[] {};
        }
        if (!crashDir.isDirectory()) {
            Log.w(TAG, crashDir.getAbsolutePath() + " is not a directory!");
            return new File[] {};
        }
        File[] minidumps = crashDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                Matcher match = pattern.matcher(filename);
                int tries = readAttemptNumber(filename);
                return match.find() && tries < MinidumpUploadService.MAX_TRIES_ALLOWED;
            }
        });
        return minidumps;
    }

    @VisibleForTesting
    File[] getAllUploadedFiles() {
        return getMatchingFiles(UPLOADED_MINIDUMP_PATTERN);
    }

    @VisibleForTesting
    File getCrashDirectory() {
        return new File(mCacheDir, CRASH_DUMP_DIR);
    }

    public File createNewTempFile(String name) throws IOException {
        File f = new File(getCrashDirectory(), name);
        if (f.exists()) {
            if (f.delete()) {
                f = new File(getCrashDirectory(), name);
            } else {
                Log.w(TAG, "Unable to delete previous logfile"
                        + f.getAbsolutePath());
            }
        }
        return f;
    }

    File getCrashFile(String filename) {
        return new File(getCrashDirectory(), filename);
    }

    File getCrashUploadLogFile() {
        return new File(mCacheDir, CRASH_DUMP_LOGFILE);
    }

    private File[] getAllTempFiles() {
        return getMatchingFiles(TMP_PATTERN);
    }
}
