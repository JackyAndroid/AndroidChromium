// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import org.chromium.base.annotations.CalledByNative;

/**
 * Utilities to support startup metrics - Android version.
 */
public class UmaUtils {

    private static long sApplicationStartWallClockMs;

    private static boolean sRunningApplicationStart;

    /**
     * Record the time at which the activity started. This should be called asap after
     * the start of the activity's onCreate function.
     */
    public static void recordMainEntryPointTime() {
        // We can't simply pass this down through a JNI call, since the JNI for chrome
        // isn't initialized until we start the native content browser component, and we
        // then need the start time in the C++ side before we return to Java. As such we
        // save it in a static that the C++ can fetch once it has initialized the JNI.
        sApplicationStartWallClockMs = System.currentTimeMillis();
    }

    /**
     * Whether the application is in the early stage since the browser process start. The
     * "application start" ends right after the last histogram related to browser startup is
     * recorded. Currently, the very first navigation commit in the lifetime of the process ends the
     * "application start".
     * Must only be called on the UI thread.
     */
    public static boolean isRunningApplicationStart() {
        return sRunningApplicationStart;
    }

    /**
     * Marks/unmarks the "application start" stage of the browser process lifetime.
     * Must only be called on the UI thread.
     */
    public static void setRunningApplicationStart(boolean isAppStart) {
        sRunningApplicationStart = isAppStart;
    }

    @CalledByNative
    public static long getMainEntryPointTime() {
        return sApplicationStartWallClockMs;
    }

}
