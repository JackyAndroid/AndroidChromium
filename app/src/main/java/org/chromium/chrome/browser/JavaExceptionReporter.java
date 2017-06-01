// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.support.annotation.UiThread;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

/**
 * This UncaughtExceptionHandler will create a breakpad minidump when there is an uncaught
 * exception.
 *
 * The exception's stack trace will be added to the minidump's data. This allows us to report
 * java-only crashes in the same way that we report all other Chrome crashes.
 */
@JNINamespace("chrome::android")
public class JavaExceptionReporter implements Thread.UncaughtExceptionHandler {
    private Thread.UncaughtExceptionHandler mParent;
    private boolean mHandlingException;

    JavaExceptionReporter(Thread.UncaughtExceptionHandler parent) {
        mParent = parent;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (!mHandlingException) {
            mHandlingException = true;
            nativeReportJavaException(e);
        }
        if (mParent != null) {
            mParent.uncaughtException(t, e);
        }
    }

    /**
     * Report and upload the stack trace as if it was a crash. This is very expensive and should
     * be called rarely and only on the UI thread to avoid corrupting other crash uploads. Ideally
     * only called in idle handlers.
     *
     * @param stackTrace The stack trace to report.
     */
    @UiThread
    public static void reportStackTrace(String stackTrace) {
        assert ThreadUtils.runningOnUiThread();
        nativeReportJavaStackTrace(stackTrace);
    }

    @CalledByNative
    private static void installHandler() {
        Thread.setDefaultUncaughtExceptionHandler(
                new JavaExceptionReporter(Thread.getDefaultUncaughtExceptionHandler()));
    }

    private static native void nativeReportJavaException(Throwable e);
    private static native void nativeReportJavaStackTrace(String stackTrace);
}
