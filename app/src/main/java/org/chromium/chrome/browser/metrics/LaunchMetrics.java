// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.util.Pair;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Used for recording metrics about Chrome launches that need to be recorded before the native
 * library may have been loaded.  Metrics are cached until the library is known to be loaded, then
 * committed to the MetricsService all at once.
 */
@JNINamespace("metrics")
public class LaunchMetrics {

    /**
     * Creating an instance of a subclass of this class automatically adds it to a list of objects
     * that are committed when the native library is available.
     */
    private abstract static class CachedHistogram {
        private static final List<CachedHistogram> sEvents = new ArrayList<CachedHistogram>();

        protected final String mHistogramName;

        /**
         * @param histogramName Name of the histogram to record.
         */
        protected CachedHistogram(String histogramName) {
            mHistogramName = histogramName;
            sEvents.add(this);
        }

        /** Commits the histogram. Expects the native library to be loaded. */
        protected abstract void commitAndClear();
    }

    /**
     * Caches an action that will be recorded after native side is loaded.
     */
    public static class ActionEvent extends CachedHistogram {
        private int mCount;

        public ActionEvent(String actionName) {
            super(actionName);
        }

        public void record() {
            if (LibraryLoader.isInitialized()) {
                recordWithNative();
            } else {
                mCount++;
            }
        }

        private void recordWithNative() {
            RecordUserAction.record(mHistogramName);
        }

        @Override
        protected void commitAndClear() {
            while (mCount > 0) {
                recordWithNative();
                mCount--;
            }
        }
    }

    /** Caches a set of integer histogram samples. */
    public static class SparseHistogramSample extends CachedHistogram {
        private final List<Integer> mSamples = new ArrayList<Integer>();

        public SparseHistogramSample(String histogramName) {
            super(histogramName);
        }

        public void record(int sample) {
            if (LibraryLoader.isInitialized()) {
                recordWithNative(sample);
            } else {
                mSamples.add(sample);
            }
        }

        private void recordWithNative(int sample) {
            RecordHistogram.recordSparseSlowlyHistogram(mHistogramName, sample);
        }

        @Override
        protected void commitAndClear() {
            for (Integer sample : mSamples) {
                recordWithNative(sample);
            }
            mSamples.clear();
        }
    }

    /** Caches a set of enumerated histogram samples. */
    public static class EnumeratedHistogramSample extends CachedHistogram {
        private final List<Integer> mSamples = new ArrayList<Integer>();
        private final int mMaxValue;

        public EnumeratedHistogramSample(String histogramName, int maxValue) {
            super(histogramName);
            mMaxValue = maxValue;
        }

        public void record(int sample) {
            if (LibraryLoader.isInitialized()) {
                recordWithNative(sample);
            } else {
                mSamples.add(sample);
            }
        }

        private void recordWithNative(int sample) {
            RecordHistogram.recordEnumeratedHistogram(mHistogramName, sample, mMaxValue);
        }

        @Override
        protected void commitAndClear() {
            for (Integer sample : mSamples) {
                recordWithNative(sample);
            }
            mSamples.clear();
        }
    }

    /** Caches a set of times histogram samples. */
    public static class TimesHistogramSample extends CachedHistogram {
        private final List<Long> mSamples = new ArrayList<Long>();
        private final TimeUnit mTimeUnit;

        public TimesHistogramSample(String histogramName, TimeUnit timeUnit) {
            super(histogramName);
            mTimeUnit = timeUnit;
        }

        public void record(long sample) {
            if (LibraryLoader.isInitialized()) {
                recordWithNative(sample);
            } else {
                mSamples.add(sample);
            }
        }

        private void recordWithNative(long sample) {
            RecordHistogram.recordTimesHistogram(mHistogramName, sample, mTimeUnit);
        }

        @Override
        protected void commitAndClear() {
            for (Long sample : mSamples) {
                recordWithNative(sample);
            }
            mSamples.clear();
        }
    }

    // Each list item is a pair of the url and where it was added from e.g. from the add to
    // homescreen menu item, an app banner, or unknown. The mapping of int source values to
    // their string names is found in the C++ ShortcutInfo struct.
    private static final List<Pair<String, Integer>> sActivityUrls =
            new ArrayList<Pair<String, Integer>>();
    private static final List<Pair<String, Integer>> sTabUrls =
            new ArrayList<Pair<String, Integer>>();

    private static final List<Long> sWebappHistogramTimes = new ArrayList<Long>();

    /**
     * Records the launch of a standalone Activity for a URL (i.e. a WebappActivity)
     * added from a specific source.
     * @param url URL that kicked off the Activity's creation.
     * @param source integer id of the source from where the URL was added.
     */
    public static void recordHomeScreenLaunchIntoStandaloneActivity(String url, int source) {
        sActivityUrls.add(new Pair<String, Integer>(url, source));
    }

    /**
     * Records the launch of a Tab for a URL (i.e. a Home screen shortcut).
     * @param url URL that kicked off the Tab's creation.
     * @param source integer id of the source from where the URL was added.
     */
    public static void recordHomeScreenLaunchIntoTab(String url, int source) {
        sTabUrls.add(new Pair<String, Integer>(url, source));
    }

    /**
     * Records the time it took to look up from disk whether a MAC is valid during webapp startup.
     * @param time the number of milliseconds it took to finish.
     */
    public static void recordWebappHistogramTimes(long time) {
        sWebappHistogramTimes.add(time);
    }

    /**
     * Calls out to native code to record URLs that have been launched via the Home screen.
     * This intermediate step is necessary because Activity.onCreate() may be called when
     * the native library has not yet been loaded.
     * @param webContents WebContents for the current Tab.
     */
    public static void commitLaunchMetrics(WebContents webContents) {
        for (Pair<String, Integer> item : sActivityUrls) {
            nativeRecordLaunch(true, item.first, item.second, webContents);
        }
        sActivityUrls.clear();

        for (Pair<String, Integer> item : sTabUrls) {
            nativeRecordLaunch(false, item.first, item.second, webContents);
        }
        sTabUrls.clear();

        // Record generic cached events.
        for (CachedHistogram event : CachedHistogram.sEvents) event.commitAndClear();
    }

    /**
     * Records metrics about the state of the homepage on launch.
     * @param showHomeButton Whether the home button is shown.
     * @param homepageIsNtp Whether the homepage is set to the NTP.
     * @param homepageUrl The value of the homepage URL.
     */
    public static void recordHomePageLaunchMetrics(
            boolean showHomeButton, boolean homepageIsNtp, String homepageUrl) {
        if (homepageUrl == null) {
            homepageUrl = "";
            assert !showHomeButton : "Homepage should be disabled for a null URL";
        }
        nativeRecordHomePageLaunchMetrics(showHomeButton, homepageIsNtp, homepageUrl);
    }

    private static native void nativeRecordLaunch(
            boolean standalone, String url, int source, WebContents webContents);
    private static native void nativeRecordHomePageLaunchMetrics(
            boolean showHomeButton, boolean homepageIsNtp, String homepageUrl);
}
