// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import static android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;

import android.os.SystemClock;

import org.chromium.base.metrics.RecordHistogram;

import java.util.concurrent.TimeUnit;

/**
 * Centralizes UMA data collection for Android-specific memory conditions.
 */
public class MemoryUma {
    // AndroidMemoryNotificationBackground defined in tools/metrics/histograms/histograms.xml.
    private static final int BACKGROUND_TRIM_UI_HIDDEN = 0;
    private static final int BACKGROUND_TRIM_BACKGROUND = 1;
    private static final int BACKGROUND_TRIM_MODERATE = 2;
    private static final int BACKGROUND_TRIM_COMPLETE = 3;
    private static final int BACKGROUND_MAX = 4;

    // AndroidMemoryNotificationForeground defined in tools/metrics/histograms/histograms.xml.
    private static final int FOREGROUND_TRIM_MODERATE = 0;
    private static final int FOREGROUND_TRIM_LOW = 1;
    private static final int FOREGROUND_TRIM_CRITICAL = 2;
    private static final int FOREGROUND_LOW = 3;
    private static final int FOREGROUND_MAX = 4;

    // Timestamp of the last time we received a LowMemory call since Chrome is in foreground.
    private long mLastLowMemoryMsec = -1;

    public void onStop() {
        mLastLowMemoryMsec = -1;
    }

    public void onLowMemory() {
        memoryNotificationForeground(FOREGROUND_LOW);
        long now = SystemClock.elapsedRealtime();
        if (mLastLowMemoryMsec >= 0) {
            RecordHistogram.recordCustomTimesHistogram("MemoryAndroid.LowMemoryTimeBetween",
                    now - mLastLowMemoryMsec, 0, TimeUnit.MINUTES.toMillis(10),
                    TimeUnit.MILLISECONDS, 50);
        }
        mLastLowMemoryMsec = now;
    }

    public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_COMPLETE) {
            memoryNotificationBackground(BACKGROUND_TRIM_COMPLETE);
        } else if (level >= TRIM_MEMORY_MODERATE) {
            memoryNotificationBackground(BACKGROUND_TRIM_MODERATE);
        } else if (level >= TRIM_MEMORY_BACKGROUND) {
            memoryNotificationBackground(BACKGROUND_TRIM_BACKGROUND);
        } else if (level >= TRIM_MEMORY_UI_HIDDEN) {
            memoryNotificationBackground(BACKGROUND_TRIM_UI_HIDDEN);
        } else if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            memoryNotificationForeground(FOREGROUND_TRIM_CRITICAL);
        } else if (level >= TRIM_MEMORY_RUNNING_LOW) {
            memoryNotificationForeground(FOREGROUND_TRIM_LOW);
        } else if (level >= TRIM_MEMORY_RUNNING_MODERATE) {
            memoryNotificationForeground(FOREGROUND_TRIM_MODERATE);
        }
    }

    private static void memoryNotificationForeground(int notification) {
        assert notification >= 0 && notification < FOREGROUND_MAX;
        RecordHistogram.recordEnumeratedHistogram("MemoryAndroid.NotificationForeground",
                notification, FOREGROUND_MAX);
    }

    private static void memoryNotificationBackground(int notification) {
        assert notification >= 0 && notification < BACKGROUND_MAX;
        RecordHistogram.recordEnumeratedHistogram("MemoryAndroid.NotificationBackground",
                notification, BACKGROUND_MAX);
    }
}
