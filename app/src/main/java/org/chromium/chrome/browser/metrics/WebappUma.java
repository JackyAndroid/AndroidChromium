// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.os.SystemClock;

import org.chromium.base.metrics.RecordHistogram;

import java.util.concurrent.TimeUnit;

/**
 * Centralizes UMA data collection for web apps.
 */
public class WebappUma {
    // SplashscreenColorStatus defined in tools/metrics/histograms/histograms.xml.
    public static final int SPLASHSCREEN_COLOR_STATUS_DEFAULT = 0;
    public static final int SPLASHSCREEN_COLOR_STATUS_CUSTOM = 1;
    public static final int SPLASHSCREEN_COLOR_STATUS_MAX = 2;

    // SplashscreenHidesReason defined in tools/metrics/histograms/histograms.xml.
    public static final int SPLASHSCREEN_HIDES_REASON_PAINT = 0;
    public static final int SPLASHSCREEN_HIDES_REASON_LOAD_FINISHED = 1;
    public static final int SPLASHSCREEN_HIDES_REASON_LOAD_FAILED = 2;
    public static final int SPLASHSCREEN_HIDES_REASON_CRASH = 3;
    public static final int SPLASHSCREEN_HIDES_REASON_MAX = 4;

    // SplashscreenBackgroundColorType defined in tools/metrics/histograms/histograms.xml.
    public static final int SPLASHSCREEN_ICON_TYPE_NONE = 0;
    public static final int SPLASHSCREEN_ICON_TYPE_FALLBACK = 1;
    public static final int SPLASHSCREEN_ICON_TYPE_CUSTOM = 2;
    public static final int SPLASHSCREEN_ICON_TYPE_CUSTOM_SMALL = 3;
    public static final int SPLASHSCREEN_ICON_TYPE_MAX = 4;

    // Histogram names are defined in tools/metrics/histograms/histograms.xml.
    public static final String HISTOGRAM_SPLASHSCREEN_BACKGROUNDCOLOR =
            "Webapp.Splashscreen.BackgroundColor";
    public static final String HISTOGRAM_SPLASHSCREEN_DURATION =
            "Webapp.Splashscreen.Duration";
    public static final String HISTOGRAM_SPLASHSCREEN_HIDES =
            "Webapp.Splashscreen.Hides";
    public static final String HISTOGRAM_SPLASHSCREEN_ICON_TYPE =
            "Webapp.Splashscreen.Icon.Type";
    public static final String HISTOGRAM_SPLASHSCREEN_ICON_SIZE =
            "Webapp.Splashscreen.Icon.Size";
    public static final String HISTOGRAM_SPLASHSCREEN_THEMECOLOR =
            "Webapp.Splashscreen.ThemeColor";

    private int mSplashScreenBackgroundColor = SPLASHSCREEN_COLOR_STATUS_MAX;
    private int mSplashScreenIconType = SPLASHSCREEN_ICON_TYPE_MAX;
    private int mSplashScreenIconSize = -1;
    private int mSplashScreenThemeColor = SPLASHSCREEN_COLOR_STATUS_MAX;
    private long mSplashScreenVisibleTime = 0;

    private boolean mCommitted = false;

    /**
     * Signal that the splash screen is now visible. This is being used to
     * record for how long the splash screen is left visible.
     */
    public void splashscreenVisible() {
        assert mSplashScreenVisibleTime == 0;
        mSplashScreenVisibleTime = SystemClock.elapsedRealtime();
    }

    /**
     * Records the type of background color on the splash screen.
     * @param type flag representing the type of color.
     */
    public void recordSplashscreenBackgroundColor(int type) {
        assert !mCommitted;
        assert type >= 0 && type < SPLASHSCREEN_COLOR_STATUS_MAX;
        mSplashScreenBackgroundColor = type;
    }

    /**
     * Signal that the splash screen is now hidden. It is used to record for how
     * long the splash screen was left visible. It is also used to know what
     * event triggered the splash screen to be hidden.
     * @param type flag representing the reason why the splash screen was hidden.
     */
    public void splashscreenHidden(int reason) {
        assert reason >= 0 && reason < SPLASHSCREEN_HIDES_REASON_MAX;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_HIDES,
                reason, SPLASHSCREEN_HIDES_REASON_MAX);

        assert mSplashScreenVisibleTime != 0;
        RecordHistogram.recordMediumTimesHistogram(HISTOGRAM_SPLASHSCREEN_DURATION,
                SystemClock.elapsedRealtime() - mSplashScreenVisibleTime, TimeUnit.MILLISECONDS);
    }

    /**
     * Records the type of icon on the splash screen.
     * @param type flag representing the type of icon.
     */
    public void recordSplashscreenIconType(int type) {
        assert !mCommitted;
        assert type >= 0 && type < SPLASHSCREEN_ICON_TYPE_MAX;
        mSplashScreenIconType = type;
    }

    public void recordSplashscreenIconSize(int size) {
        assert !mCommitted;
        assert size >= 0;
        mSplashScreenIconSize = size;
    }

    /**
     * Records the type of theme color on the splash screen.
     * @param type flag representing the type of color.
     */
    public void recordSplashscreenThemeColor(int type) {
        assert !mCommitted;
        assert type >= 0 && type < SPLASHSCREEN_COLOR_STATUS_MAX;
        mSplashScreenThemeColor = type;
    }

    /**
     * Records all metrics that could not be recorded because the native library
     * was not loaded yet.
     */
    public void commitMetrics() {
        if (mCommitted) return;

        mCommitted = true;

        assert mSplashScreenBackgroundColor != SPLASHSCREEN_COLOR_STATUS_MAX;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_BACKGROUNDCOLOR,
                mSplashScreenBackgroundColor,
                SPLASHSCREEN_COLOR_STATUS_MAX);
        mSplashScreenBackgroundColor = SPLASHSCREEN_COLOR_STATUS_MAX;

        assert mSplashScreenIconType != SPLASHSCREEN_ICON_TYPE_MAX;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_ICON_TYPE,
                mSplashScreenIconType,
                SPLASHSCREEN_ICON_TYPE_MAX);

        if (mSplashScreenIconType == SPLASHSCREEN_ICON_TYPE_NONE) {
            assert mSplashScreenIconSize == -1;
        } else {
            assert mSplashScreenIconSize >= 0;
            RecordHistogram.recordCount1000Histogram(HISTOGRAM_SPLASHSCREEN_ICON_SIZE,
                    mSplashScreenIconSize);
        }
        mSplashScreenIconType = SPLASHSCREEN_ICON_TYPE_MAX;
        mSplashScreenIconSize = -1;

        assert mSplashScreenThemeColor != SPLASHSCREEN_COLOR_STATUS_MAX;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_THEMECOLOR,
                mSplashScreenThemeColor,
                SPLASHSCREEN_COLOR_STATUS_MAX);
        mSplashScreenThemeColor = SPLASHSCREEN_COLOR_STATUS_MAX;
    }
}
