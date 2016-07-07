// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.content.Context;
import android.content.Intent;
import android.util.Patterns;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts logcat out of Android devices and elide PII sensitive info from it.
 *
 * Elided information includes: Emails, IP address, MAC address, URL/domains as well as Javascript
 * console messages.
 *
 * Caller provides a list of minidump files as well as an intent. This Callable will then extract
 * the most recent logcat and save a copy for each minidump.
 *
 * Upon completion, each minidump + logcat pairs will be passed to the MinidumpPreparationService
 * along with the intent provided here.
 */
public class LogcatExtractionCallable implements Callable<Boolean> {

    private static final String TAG = "LogcatExtraction";
    private static final long HALF_SECOND = 500;

    protected static final int LOGCAT_SIZE = 256; // Number of lines.

    protected static final String EMAIL_ELISION = "XXX@EMAIL.ELIDED";

    @VisibleForTesting
    protected static final String URL_ELISION = "HTTP://WEBADDRESS.ELIDED";

    private static final String GOOD_IRI_CHAR =
            "a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF";

    private static final Pattern IP_ADDRESS = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
            + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
            + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
            + "|[1-9][0-9]|[0-9]))");

    private static final String IRI =
            "[" + GOOD_IRI_CHAR + "]([" + GOOD_IRI_CHAR + "\\-]{0,61}["
            + GOOD_IRI_CHAR + "]){0,1}";

    private static final String GOOD_GTLD_CHAR =
            "a-zA-Z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF";
    private static final String GTLD = "[" + GOOD_GTLD_CHAR + "]{2,63}";
    private static final String HOST_NAME = "(" + IRI + "\\.)+" + GTLD;

    private static final Pattern DOMAIN_NAME =
            Pattern.compile("(" + HOST_NAME + "|" + IP_ADDRESS + ")");

    private static final Pattern WEB_URL = Pattern.compile(
            "(?:\\b|^)((?:(http|https|Http|Https|rtsp|Rtsp):"
            + "\\/\\/(?:(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)"
            + "\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\$\\-\\_"
            + "\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@)?)?"
            + "(?:" + DOMAIN_NAME + ")"
            + "(?:\\:\\d{1,5})?)"
            + "(\\/(?:(?:[" + GOOD_IRI_CHAR + "\\;\\/\\?\\:\\@\\&\\=\\#\\~"
            + "\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])|(?:\\%[a-fA-F0-9]{2}))*)?"
            + "(?:\\b|$)");

    @VisibleForTesting
    protected static final String BEGIN_MICRODUMP = "-----BEGIN BREAKPAD MICRODUMP-----";
    @VisibleForTesting
    protected static final String END_MICRODUMP = "-----END BREAKPAD MICRODUMP-----";

    @VisibleForTesting
    protected static final String IP_ELISION = "1.2.3.4";

    @VisibleForTesting
    protected static final String MAC_ELISION = "01:23:45:67:89:AB";

    private static final String LOGCAT_EXTENSION = ".logcat";

    @VisibleForTesting
    protected static final String CONSOLE_ELISION =
            "[ELIDED:CONSOLE(0)] ELIDED CONSOLE MESSAGE";

    private static final Pattern MAC_ADDRESS =
            Pattern.compile("([0-9a-fA-F]{2}[-:]+){5}[0-9a-fA-F]{2}");

    private static final Pattern CONSOLE_MSG =
            Pattern.compile("\\[\\w*:CONSOLE.*\\].*");

    private static final Pattern MINIDUMP_EXTENSION = Pattern.compile("\\.dmp");

    private static final String[] CHROME_NAMESPACE = new String[] {
            "org.chromium.", "com.google."
    };

    private static final String[] SYSTEM_NAMESPACE = new String[] {
            "android.accessibilityservice", "android.accounts", "android.animation",
            "android.annotation", "android.app", "android.appwidget", "android.bluetooth",
            "android.content", "android.database", "android.databinding", "android.drm",
            "android.gesture", "android.graphics", "android.hardware",
            "android.inputmethodservice", "android.location", "android.media", "android.mtp",
            "android.net", "android.nfc", "android.opengl", "android.os", "android.preference",
            "android.print", "android.printservice", "android.provider", "android.renderscript",
            "android.sax", "android.security", "android.service", "android.speech",
            "android.support", "android.system", "android.telecom", "android.telephony",
            "android.test", "android.text", "android.transition", "android.util", "android.view",
            "android.webkit", "android.widget", "com.android.", "dalvik.", "java.", "javax.",
            "org.apache.", "org.json.", "org.w3c.dom.", "org.xml.", "org.xmlpull."
    };

    private final Context mContext;
    private final String[] mMinidumpFilenames;
    private final Intent mRedirectIntent;

    /**
     * @param filenames List of minidump filenames that need logcat to be attached.
     * @param redirectIntent The intent to be triggered once upon completion.
     */
    public LogcatExtractionCallable(Context context, String[] filenames, Intent redirectIntent) {
        if (context == null) {
            throw new NullPointerException("Context cannot be null.");
        }
        mContext = context;
        mMinidumpFilenames = Arrays.copyOf(filenames, filenames.length);
        mRedirectIntent = redirectIntent;
    }

    @Override
    public Boolean call() {
        Log.i(TAG, "Trying to extract logcat for minidump");
        try {
            // Step 1: Extract a single logcat file.
            File logcatFile = getElidedLogcat();

            // Step 2: Make copies of logcat file for each  minidump then invoke
            // MinidumpPreparationService on each file pair.
            int len = mMinidumpFilenames.length;
            CrashFileManager fileManager = new CrashFileManager(mContext.getCacheDir());
            for (int i = 0; i < len; i++) {
                processMinidump(logcatFile, mMinidumpFilenames[i], fileManager, i == len - 1);
            }
            return true;
        } catch (IOException | InterruptedException e) {
            Log.w(TAG, e.toString());
            return false;
        }
    }

    private void processMinidump(File logcatFile, String name,
            CrashFileManager manager, boolean isLast) throws IOException {
        String toPath = MINIDUMP_EXTENSION.matcher(name).replaceAll(LOGCAT_EXTENSION);
        File toFile = manager.createNewTempFile(toPath);

        // For the last file, we don't need to make extra copies. We'll use the original
        // logcat file but we would pass down the redirect intent so it can be triggered
        // upon completion.
        Intent intent = null;
        if (isLast) {
            move(logcatFile, toFile);
            intent = MinidumpPreparationService.createMinidumpPreparationIntent(mContext,
                    manager.getCrashFile(name), toFile, mRedirectIntent);
        } else {
            copy(logcatFile, toFile);
            intent = MinidumpPreparationService.createMinidumpPreparationIntent(mContext,
                    manager.getCrashFile(name), toFile, null);
        }
        mContext.startService(intent);
    }

    private File getElidedLogcat() throws IOException, InterruptedException {
        List<String> rawLogcat = getLogcat();
        List<String> elidedLogcat =
                Collections.unmodifiableList(processLogcat(rawLogcat));
        return writeLogcat(elidedLogcat);
    }

    private static void copy(File src, File dst) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {
            Log.w(TAG, e.toString());
        } finally {
            try {
                if (in != null) in.close();
            } finally {
                if (out != null) out.close();
            }
        }
    }

    private static void move(File from, File to) {
        if (!from.renameTo(to)) {
            Log.w(TAG, "Fail to rename logcat file");
        }
    }

    @VisibleForTesting
    protected List<String> getLogcat() throws IOException, InterruptedException {
        return getLogcatInternal();
    }

    private static List<String> getLogcatInternal() throws IOException, InterruptedException {
        List<String> rawLogcat = null;
        Integer exitValue = null;
        // In the absence of the android.permission.READ_LOGS permission the
        // the logcat call will just hang.
        Process p = Runtime.getRuntime().exec("logcat -d");
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while (exitValue == null) {
                rawLogcat = extractLogcatFromReader(reader, LOGCAT_SIZE);
                try {
                    exitValue = p.exitValue();
                } catch (IllegalThreadStateException itse) {
                    Thread.sleep(HALF_SECOND);
                }
            }
            if (exitValue != 0) {
                String msg = "Logcat failed: " + exitValue;
                Log.w(TAG, msg);
                throw new IOException(msg);
            }
            return rawLogcat;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Extract microdump-free logcat for more informative crash reports
     *
     * @param reader A buffered reader from which lines of initial logcat is read.
     * @param maxLines The maximum number of lines logcat extracts from minidump.
     *
     * @return Logcat up to specified length as a list of strings.
     * @throws IOException if the buffered reader encounters an I/O error.
     */
    @VisibleForTesting
    protected static List<String> extractLogcatFromReader(
            BufferedReader reader, int maxLines) throws IOException {
        return extractLogcatFromReaderInternal(reader, maxLines);
    }

    private static List<String> extractLogcatFromReaderInternal(
            BufferedReader reader, int maxLines) throws IOException {
        boolean inMicrodump = false;
        List<String> rawLogcat = new LinkedList<>();
        String logLn;
        while ((logLn = reader.readLine()) != null && rawLogcat.size() < maxLines) {
            if (logLn.contains(BEGIN_MICRODUMP)) {
                // If the log contains two begin markers without an end marker
                // in between, we ignore the second begin marker.
                inMicrodump = true;
            } else if (logLn.contains(END_MICRODUMP)) {
                if (!inMicrodump) {
                    // If we have been extracting microdump the whole time,
                    // start over with a clean logcat.
                    rawLogcat.clear();
                } else {
                    inMicrodump = false;
                }
            } else {
                if (!inMicrodump) {
                    rawLogcat.add(logLn);
                }
            }
        }
        return rawLogcat;
    }

    private File writeLogcat(List<String> elidedLogcat) throws IOException {
        CrashFileManager fileManager = new CrashFileManager(mContext.getCacheDir());
        File logcatFile = fileManager.createNewTempFile("logcat.txt");
        PrintWriter pWriter = null;
        try {
            pWriter = new PrintWriter(new FileWriter(logcatFile));
            for (String ln : elidedLogcat) {
                pWriter.println(ln);
            }
            return logcatFile;
        } finally {
            if (pWriter != null) {
                pWriter.close();
            }
        }
    }

    @VisibleForTesting
    protected static List<String> processLogcat(List<String> rawLogcat) {
        List<String> out = new ArrayList<String>(rawLogcat.size());
        for (String ln : rawLogcat) {
            ln = elideEmail(ln);
            ln = elideUrl(ln);
            ln = elideIp(ln);
            ln = elideMac(ln);
            ln = elideConsole(ln);
            out.add(ln);
        }
        return out;
    }

    /**
     * Elides any emails in the specified {@link String} with
     * {@link #EMAIL_ELISION}.
     *
     * @param original String potentially containing emails.
     * @return String with elided emails.
     */
    @VisibleForTesting
    protected static String elideEmail(String original) {
        return Patterns.EMAIL_ADDRESS.matcher(original).replaceAll(EMAIL_ELISION);
    }

    /**
     * Elides any URLs in the specified {@link String} with
     * {@link #URL_ELISION}.
     *
     * @param original String potentially containing URLs.
     * @return String with elided URLs.
     */
    @VisibleForTesting
    protected static String elideUrl(String original) {
        StringBuffer buffer = new StringBuffer(original);
        Matcher matcher = WEB_URL.matcher(buffer);
        int start = 0;
        while (matcher.find(start)) {
            start = matcher.start();
            int end = matcher.end();
            String url = buffer.substring(start, end);
            if (!likelyToBeChromeNamespace(url) && !likelyToBeSystemNamespace(url)) {
                buffer.replace(start, end, URL_ELISION);
                end = start + URL_ELISION.length();
                matcher = WEB_URL.matcher(buffer);
            }
            start = end;
        }
        return buffer.toString();
    }

    public static boolean likelyToBeChromeNamespace(String url) {
        for (String ns : CHROME_NAMESPACE) {
            if (url.startsWith(ns)) {
                return true;
            }
        }
        return false;
    }

    public static boolean likelyToBeSystemNamespace(String url) {
        for (String ns : SYSTEM_NAMESPACE) {
            if (url.startsWith(ns)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Elides any IP addresses in the specified {@link String} with
     * {@link #IP_ELISION}.
     *
     * @param original String potentially containing IPs.
     * @return String with elided IPs.
     */
    @VisibleForTesting
    protected static String elideIp(String original) {
        return Patterns.IP_ADDRESS.matcher(original).replaceAll(IP_ELISION);
    }

    /**
     * Elides any MAC addresses in the specified {@link String} with
     * {@link #MAC_ELISION}.
     *
     * @param original String potentially containing MACs.
     * @return String with elided MACs.
     */
    @VisibleForTesting
    protected static String elideMac(String original) {
        return MAC_ADDRESS.matcher(original).replaceAll(MAC_ELISION);
    }

    /**
     * Elides any console messages in the specified {@link String} with
     * {@link #CONSOLE_ELISION}.
     *
     * @param original String potentially containing console messages.
     * @return String with elided console messages.
     */
    @VisibleForTesting
    protected static String elideConsole(String original) {
        return CONSOLE_MSG.matcher(original).replaceAll(CONSOLE_ELISION);
    }
}
