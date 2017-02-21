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
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
    static final String CRASH_DUMP_LOGFILE = "uploads.log";

    private static final Pattern MINIDUMP_FIRST_TRY_PATTERN =
            Pattern.compile("\\.dmp([0-9]*)$\\z");

    private static final Pattern MINIDUMP_MIME_FIRST_TRY_PATTERN =
            Pattern.compile("\\.dmp([0-9]+)$\\z");

    private static final Pattern MINIDUMP_PATTERN =
            Pattern.compile("\\.dmp([0-9]*)(\\.try[0-9])?\\z");

    private static final Pattern UPLOADED_MINIDUMP_PATTERN = Pattern.compile("\\.up([0-9]*)\\z");

    private static final String NOT_YET_UPLOADED_MINIDUMP_SUFFIX = ".dmp";

    private static final String UPLOADED_MINIDUMP_SUFFIX = ".up";

    private static final String UPLOAD_SKIPPED_MINIDUMP_SUFFIX = ".skipped";

    private static final String UPLOAD_FORCED_MINIDUMP_SUFFIX = ".forced";

    private static final String UPLOAD_ATTEMPT_DELIMITER = ".try";

    @VisibleForTesting
    protected static final String TMP_SUFFIX = ".tmp";

    private static final Pattern TMP_PATTERN = Pattern.compile("\\.tmp\\z");

    // The maximum number of non-uploaded crashes that may be kept in the crash reports directory.
    // Chosen to attempt to balance between keeping a generous number of crashes, and not using up
    // too much filesystem storage space for obsolete crash reports.
    @VisibleForTesting
    protected static final int MAX_CRASH_REPORTS_TO_KEEP = 10;

    // The maximum age, in days, considered acceptable for a crash report. Reports older than this
    // age will be removed. The constant is chosen to be quite conservative, while still allowing
    // users to eventually reclaim filesystem storage space from obsolete crash reports.
    private static final int MAX_CRASH_REPORT_AGE_IN_DAYS = 30;

    /**
     * Comparator used for sorting files by modification date.
     * @return Comparator for prioritizing the more recently modified file
     */
    @VisibleForTesting
    protected static final Comparator<File> sFileComparator =  new Comparator<File>() {
        @Override
        public int compare(File lhs, File rhs) {
            if (lhs.lastModified() == rhs.lastModified()) {
                return lhs.compareTo(rhs);
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

    public static boolean isMinidumpMIMEFirstTry(String path) {
        return MINIDUMP_MIME_FIRST_TRY_PATTERN.matcher(path).find();
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
            return filename.replace(
                    UPLOAD_ATTEMPT_DELIMITER + numTried, UPLOAD_ATTEMPT_DELIMITER + newCount);
        } else {
            return filename + UPLOAD_ATTEMPT_DELIMITER + "1";
        }
    }

    /**
     * Attempts to rename the given file to mark it as a forced upload. This is useful for allowing
     * users to manually initiate previously skipped uploads.
     *
     * @return The renamed file, or null if renaming failed.
     */
    public static File trySetForcedUpload(File fileToUpload) {
        if (fileToUpload.getName().contains(UPLOADED_MINIDUMP_SUFFIX)) {
            Log.w(TAG, "Refusing to reset upload attempt state for a file that has already been "
                            + "successfully uploaded: " + fileToUpload.getName());
            return null;
        }
        File renamedFile = new File(filenameWithForcedUploadState(fileToUpload.getPath()));
        return fileToUpload.renameTo(renamedFile) ? renamedFile : null;
    }

    /**
     * @return True iff the provided File was manually forced (by the user) to be uploaded.
     */
    public static boolean isForcedUpload(File fileToUpload) {
        return fileToUpload.getName().contains(UPLOAD_FORCED_MINIDUMP_SUFFIX);
    }

    /**
     * @return The filename to rename to so as to manually force an upload (including clearing any
     *     previous upload attempt history).
     */
    @VisibleForTesting
    protected static String filenameWithForcedUploadState(String filename) {
        int numTried = readAttemptNumber(filename);
        if (numTried > 0) {
            filename = filename.replace(
                    UPLOAD_ATTEMPT_DELIMITER + numTried, UPLOAD_ATTEMPT_DELIMITER + 0);
        }
        filename = filename.replace(UPLOAD_SKIPPED_MINIDUMP_SUFFIX, UPLOAD_FORCED_MINIDUMP_SUFFIX);
        return filename.replace(NOT_YET_UPLOADED_MINIDUMP_SUFFIX, UPLOAD_FORCED_MINIDUMP_SUFFIX);
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

    /**
     * Marks a crash dump file as successfully uploaded, by renaming the file.
     *
     * Does not immediately delete the file, for testing reasons. However, if renaming fails,
     * attempts to delete the file immediately.
     */
    public static void markUploadSuccess(File crashDumpFile) {
        CrashFileManager.renameCrashDumpFollowingUpload(crashDumpFile, UPLOADED_MINIDUMP_SUFFIX);
    }

    /**
     * Marks a crash dump file's upload being skipped. An upload might be skipped due to lack of
     * user consent, or due to this client being excluded from the sample of clients reporting
     * crashes.
     *
     * Renames the file rather than deleting it, so that the user can manually upload the file later
     * (via chrome://crashes). However, if renaming fails, attempts to delete the file immediately.
     */
    public static void markUploadSkipped(File crashDumpFile) {
        CrashFileManager.renameCrashDumpFollowingUpload(
                crashDumpFile, UPLOAD_SKIPPED_MINIDUMP_SUFFIX);
    }

    /**
     * Renames a crash dump file. However, if renaming fails, attempts to delete the file
     * immediately.
     */
    private static void renameCrashDumpFollowingUpload(File crashDumpFile, String suffix) {
        // The pre-upload filename might have been either "foo.dmpN.tryM" or "foo.forcedN.tryM".
        String newName = crashDumpFile.getPath()
                                 .replace(NOT_YET_UPLOADED_MINIDUMP_SUFFIX, suffix)
                                 .replace(UPLOAD_FORCED_MINIDUMP_SUFFIX, suffix);
        boolean renamed = crashDumpFile.renameTo(new File(newName));
        if (!renamed) {
            Log.w(TAG, "Failed to rename " + crashDumpFile);
            if (!crashDumpFile.delete()) {
                Log.w(TAG, "Failed to delete " + crashDumpFile);
            }
        }
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

    @VisibleForTesting
    protected File[] getAllFilesSorted() {
        File crashDir = getCrashDirectoryIfExists();
        if (crashDir == null) {
            return new File[] {};
        }

        File[] files = crashDir.listFiles();
        Arrays.sort(files, sFileComparator);
        return files;
    }

    public void cleanOutAllNonFreshMinidumpFiles() {
        for (File f : getAllUploadedFiles()) {
            deleteFile(f);
        }
        for (File f : getAllTempFiles()) {
            deleteFile(f);
        }

        Set<String> recentCrashes = new HashSet<String>();
        for (File f : getAllFilesSorted()) {
            // The uploads.log file should always be preserved, as it stores the metadata that
            // powers the chrome://crashes UI.
            if (f.getName().equals(CRASH_DUMP_LOGFILE)) {
                continue;
            }

            // Delete any crash reports that are especially old.
            long ageInMillis = new Date().getTime() - f.lastModified();
            long ageInDays = TimeUnit.DAYS.convert(ageInMillis, TimeUnit.MILLISECONDS);
            if (ageInDays > MAX_CRASH_REPORT_AGE_IN_DAYS) {
                deleteFile(f);
                continue;
            }

            // Delete the oldest crash reports that exceed the cap on the number of allowed reports.
            // Each crash typically has two files associated with it: a .dmp file and a .logcat
            // file. These have the same filename other than the file extension.
            String fileNameSansExtension = f.getName().split("\\.")[0];
            if (recentCrashes.size() < MAX_CRASH_REPORTS_TO_KEEP) {
                recentCrashes.add(fileNameSansExtension);
            } else if (!recentCrashes.contains(fileNameSansExtension)) {
                deleteFile(f);
            }
        }
    }

    @VisibleForTesting
    File[] getMatchingFiles(final Pattern pattern) {
        // Get dump dir and get all files with specified suffix. The path
        // constructed here must match chrome_paths.cc (see case
        // chrome::DIR_CRASH_DUMPS).
        File crashDir = getCrashDirectoryIfExists();
        if (crashDir == null) {
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

    @VisibleForTesting
    File getCrashDirectoryIfExists() {
        File crashDirectory = getCrashDirectory();
        if (!crashDirectory.exists()) {
            Log.w(TAG, crashDirectory.getAbsolutePath() + " does not exist!");
            return null;
        }
        if (!crashDirectory.isDirectory()) {
            Log.w(TAG, crashDirectory.getAbsolutePath() + " is not a directory!");
            return null;
        }
        return crashDirectory;
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

    /**
     * Returns the minidump file with the given local ID, or null if no minidump file has the given
     * local ID.
     * NOTE: Crash files that have already been successfully uploaded are not included.
     *
     * @param localId The local ID of the crash report.
     * @return The matching File, or null if no matching file is found.
     */
    File getCrashFileWithLocalId(String localId) {
        for (File f : getAllFilesSorted()) {
            // Only match non-uploaded or previously skipped files. In particular, do not match
            // successfully uploaded files; nor files which are not minidump files, such as logcat
            // files.
            if (!f.getName().contains(NOT_YET_UPLOADED_MINIDUMP_SUFFIX)
                    && !f.getName().contains(UPLOAD_SKIPPED_MINIDUMP_SUFFIX)
                    && !f.getName().contains(UPLOAD_FORCED_MINIDUMP_SUFFIX)) {
                continue;
            }

            String filenameSansExtension = f.getName().split("\\.")[0];
            if (filenameSansExtension.endsWith(localId)) {
                return f;
            }
        }
        return null;
    }

    File getCrashUploadLogFile() {
        return new File(getCrashDirectory(), CRASH_DUMP_LOGFILE);
    }

    private File[] getAllTempFiles() {
        return getMatchingFiles(TMP_PATTERN);
    }
}
