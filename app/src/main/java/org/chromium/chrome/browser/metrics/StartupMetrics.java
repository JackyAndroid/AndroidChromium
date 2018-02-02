// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.content.Intent;
import android.os.Handler;

import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;

/**
 * Keeps track of actions taken on startup.
 */
public class StartupMetrics {
    // Actions we keep track of. Do not change indices, add new actions at the end only
    // and update MAX_INDEX.
    private static final int NO_ACTIVITY = 0;
    private static final int OPENED_NTP = 1;
    private static final int FOCUSED_OMNIBOX = 2;
    private static final int OPENED_BOOKMARKS = 3;
    private static final int OPENED_RECENTS = 4;
    private static final int OPENED_HISTORY = 5;
    private static final int OPENED_TAB_SWITCHER = 6;
    private static final int MAX_INDEX = 7;

    // Keeps track of the actions invoked in the first RECORDING_THRESHOLD_NS.
    private int mFirstActionTaken = NO_ACTIVITY;

    private boolean mIsMainIntent;
    private Handler mHandler;
    // This ensures metrics are recorded only once per updateIntent(...) call.
    private boolean mShouldRecordHistogram;

    // Startup time is measured by two different time sources.
    // {@code mStartTimeNanoMonotonic} is measured from a monotonic time source with nanosecond
    // precision whereas {@code mStartTimeMilli} is measured from the wall clock with millisecond
    // precision. The monotonic time source may not persist across reboots whereas the wall clock
    // is subject to change by the user.
    private long mStartTimeNanoMonotonic;
    private long mStartTimeMilli;

    private static StartupMetrics sInstance;

    // Record only the first 10s.
    private static final long RECORDING_THRESHOLD_NS = 10000000000L;
    private static final int MILLI_SEC_PER_MINUTE = 60000;
    private static final int MINUTES_PER_30DAYS = 43200;
    // Bucket sizes are exponential so we get minute level granularity for first 10 minutes.
    private static final int NUM_BUCKETS = 50;

    public static StartupMetrics getInstance() {
        if (sInstance == null) {
            sInstance = new StartupMetrics();
        }
        return sInstance;
    }

    // Singleton
    private StartupMetrics() {
        mHandler = new Handler();
    }

    /**
     * Starts collecting metrics for the latest intent sent to DocumentActivity or
     * ChromeTabbedActivity. This happens on every intent coming from launcher/external app
     * for DocumentActivity and every time we get onStart() in ChromeTabbedActivity mode.
     */
    public void updateIntent(Intent intent) {
        mIsMainIntent = intent != null && Intent.ACTION_MAIN.equals(intent.getAction());
        mFirstActionTaken = NO_ACTIVITY;
        mStartTimeNanoMonotonic = System.nanoTime();
        mStartTimeMilli = System.currentTimeMillis();
        mShouldRecordHistogram = true;
    }

    private boolean isShortlyAfterChromeStarted() {
        return (System.nanoTime() - mStartTimeNanoMonotonic) <= RECORDING_THRESHOLD_NS;
    }

    private void setFirstAction(int type) {
        if (!isShortlyAfterChromeStarted() || mFirstActionTaken != NO_ACTIVITY) return;
        mFirstActionTaken = type;
    }

    /** Records that the new tab page has been opened. */
    public void recordOpenedNTP() {
        setFirstAction(OPENED_NTP);
    }

    /** Records that the omnibox has been focused. */
    public void recordFocusedOmnibox() {
        setFirstAction(FOCUSED_OMNIBOX);
    }

    /** Records that the bookmarks page has been opened. */
    public void recordOpenedBookmarks() {
        setFirstAction(OPENED_BOOKMARKS);
    }

    /** Records that the recents page has been opened. */
    public void recordOpenedRecents() {
        setFirstAction(OPENED_RECENTS);
    }

    /** Records that the history page has been opened. */
    public void recordOpenedHistory() {
        setFirstAction(OPENED_HISTORY);
    }

    /** Records that the tab switcher has been accessed. */
    public void recordOpenedTabSwitcher() {
        setFirstAction(OPENED_TAB_SWITCHER);
    }

    /** Records the startup data in a histogram. Should only be called after native is loaded. */
    public void recordHistogram(boolean onStop) {
        if (!mShouldRecordHistogram) return;
        if (!isShortlyAfterChromeStarted() || mFirstActionTaken != NO_ACTIVITY || onStop) {
            String histogramName = mIsMainIntent ? "MobileStartup.MainIntentAction" :
                    "MobileStartup.NonMainIntentAction";
            RecordHistogram.recordEnumeratedHistogram(histogramName, mFirstActionTaken, MAX_INDEX);
            mShouldRecordHistogram = false;
            long lastUsedTimeMilli = ContextUtils.getAppSharedPreferences().getLong(
                    UmaSessionStats.LAST_USED_TIME_PREF, 0);
            if (mIsMainIntent && (lastUsedTimeMilli > 0) && (mStartTimeMilli > lastUsedTimeMilli)
                    && (mStartTimeMilli - lastUsedTimeMilli > Integer.MAX_VALUE)) {
                // Measured in minutes and capped at a day with a bucket precision of 6 minutes.
                RecordHistogram.recordCustomCountHistogram("MobileStartup.TimeSinceLastUse",
                        (int) (mStartTimeMilli - lastUsedTimeMilli) / MILLI_SEC_PER_MINUTE, 1,
                        MINUTES_PER_30DAYS, NUM_BUCKETS);
            }
        } else {
            // Call back later to record the histogram after 10s have elapsed.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    recordHistogram(false);
                }
            }, (RECORDING_THRESHOLD_NS - (System.nanoTime() - mStartTimeNanoMonotonic)) / 1000000);
        }
    }
}
