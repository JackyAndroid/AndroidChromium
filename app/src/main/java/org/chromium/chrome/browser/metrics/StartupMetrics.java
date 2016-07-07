// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.content.Intent;
import android.os.Handler;

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
    private long mSessionStartTimestamp;
    private Handler mHandler;
    private boolean mRecordedHistogram;

    private static StartupMetrics sInstance;

    // Record only the first 10s.
    private static final long RECORDING_THRESHOLD_NS = 10000000000L;

    public static StartupMetrics getInstance() {
        if (sInstance == null) {
            sInstance = new StartupMetrics();
        }
        return sInstance;
    }

    // Singleton
    private StartupMetrics() {
        mSessionStartTimestamp = System.nanoTime();
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
        mSessionStartTimestamp = System.nanoTime();
        mRecordedHistogram = false;
    }

    private boolean isShortlyAfterChromeStarted() {
        return (System.nanoTime() - mSessionStartTimestamp) <= RECORDING_THRESHOLD_NS;
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
        if (mRecordedHistogram) return;
        if (!isShortlyAfterChromeStarted() || mFirstActionTaken != NO_ACTIVITY || onStop) {
            String histogramName = mIsMainIntent ? "MobileStartup.MainIntentAction" :
                    "MobileStartup.NonMainIntentAction";
            RecordHistogram.recordEnumeratedHistogram(histogramName, mFirstActionTaken, MAX_INDEX);
            mRecordedHistogram = true;
        } else {
            // Call back later to record the histogram after 10s have elapsed.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    recordHistogram(false);
                }
            } , (RECORDING_THRESHOLD_NS - (System.nanoTime() - mSessionStartTimestamp)) / 1000000);
        }
    }

}
