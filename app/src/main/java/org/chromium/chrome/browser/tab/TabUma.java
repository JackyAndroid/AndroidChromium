// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.os.SystemClock;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.net.NetError;

import java.util.concurrent.TimeUnit;

/**
 * Centralizes UMA data collection for Tab management.
 * This will drive our memory optimization efforts, specially tab restoring and
 * eviction.
 * All calls must be made from the UI thread.
 */
public class TabUma {
    // TabStatus defined in tools/metrics/histograms/histograms.xml.
    static final int TAB_STATUS_MEMORY_RESIDENT = 0;
    static final int TAB_STATUS_RELOAD_EVICTED = 1;
    static final int TAB_STATUS_RELOAD_COLD_START_FG = 6;
    static final int TAB_STATUS_RELOAD_COLD_START_BG = 7;
    static final int TAB_STATUS_LAZY_LOAD_FOR_BG_TAB = 8;
    static final int TAB_STATUS_LIM = 9;

    // TabBackgroundLoadStatus defined in tools/metrics/histograms/histograms.xml.
    static final int TAB_BACKGROUND_LOAD_SHOWN = 0;
    static final int TAB_BACKGROUND_LOAD_LOST = 1;
    static final int TAB_BACKGROUND_LOAD_SKIPPED = 2;
    static final int TAB_BACKGROUND_LOAD_LIM = 3;

    // The enum values for the Tab.RestoreResult histogram. The unusual order is to
    // keep compatibility with the previous instance of the histogram that was using
    // a boolean.
    //
    // Defined in tools/metrics/histograms/histograms.xml.
    private static final int TAB_RESTORE_RESULT_FAILURE_OTHER = 0;
    private static final int TAB_RESTORE_RESULT_SUCCESS = 1;
    private static final int TAB_RESTORE_RESULT_FAILURE_NETWORK_CONNECTIVITY = 2;
    private static final int TAB_RESTORE_RESULT_COUNT = 3;

    // TAB_STATE_* are for TabStateTransferTime and TabTransferTarget histograms.
    // TabState defined in tools/metrics/histograms/histograms.xml.
    private static final int TAB_STATE_INITIAL = 0;
    private static final int TAB_STATE_ACTIVE = 1;
    private static final int TAB_STATE_INACTIVE = 2;
    private static final int TAB_STATE_DETACHED = 3;
    private static final int TAB_STATE_CLOSED = 4;
    private static final int TAB_STATE_MAX = TAB_STATE_CLOSED;

    // Counter of tab shows (as per onShow()) for all tabs.
    private static long sAllTabsShowCount = 0;

    /**
     * State in which the tab was created. This can be used in metric accounting - e.g. to
     * distinguish reasons for a tab to be restored upon first display.
     */
    public enum TabCreationState {
        LIVE_IN_FOREGROUND,
        LIVE_IN_BACKGROUND,
        FROZEN_ON_RESTORE,
        FROZEN_FOR_LAZY_LOAD,
        FROZEN_ON_RESTORE_FAILED,
    }

    private final TabCreationState mTabCreationState;

    // Timestamp when this tab was last shown.
    private long mLastShownTimestamp = -1;

    // Timestamp of the beginning of the current tab restore.
    private long mRestoreStartedAtMillis = -1;

    private long mLastTabStateChangeMillis = -1;
    private int mLastTabState = TAB_STATE_INITIAL;

    // The number of background tabs opened by long pressing on this tab and selecting
    // "Open in a new tab" from the context menu.
    private int mNumBackgroundTabsOpened;

    // Records histogram about which background tab opened from this tab the user switches to
    // first.
    private ChildBackgroundTabShowObserver mChildBackgroundTabShowObserver;

    /**
     * Constructs a new UMA tracker for a specific tab.
     * @param creationState In what state the tab was created.
     */
    public TabUma(TabCreationState creationState) {
        mTabCreationState = creationState;

        mLastTabStateChangeMillis = System.currentTimeMillis();
        if (mTabCreationState == TabCreationState.LIVE_IN_FOREGROUND
                || mTabCreationState == TabCreationState.FROZEN_ON_RESTORE) {
            updateTabState(TAB_STATE_ACTIVE);
        } else if (mTabCreationState == TabCreationState.LIVE_IN_BACKGROUND
                || mTabCreationState == TabCreationState.FROZEN_FOR_LAZY_LOAD) {
            updateTabState(TAB_STATE_INACTIVE);
        } else if (mTabCreationState == TabCreationState.FROZEN_ON_RESTORE_FAILED) {
            // A previous TabUma should have reported an active tab state. Initialize but avoid
            // recording this as a state change.
            mLastTabState = TAB_STATE_ACTIVE;
            updateTabState(TAB_STATE_ACTIVE);
        }
    }

    /**
     * Records the tab restore result into several UMA histograms.
     * @param succeeded Whether or not the tab restore succeeded.
     * @param time The time taken to perform the tab restore.
     * @param perceivedTime The perceived time taken to perform the tab restore.
     * @param errorCode The error code, on failure (as denoted by the |succeeded| parameter).
     */
    private void recordTabRestoreResult(boolean succeeded, long time, long perceivedTime,
            int errorCode) {
        if (succeeded) {
            RecordHistogram.recordEnumeratedHistogram(
                    "Tab.RestoreResult", TAB_RESTORE_RESULT_SUCCESS, TAB_RESTORE_RESULT_COUNT);
            RecordHistogram.recordCountHistogram("Tab.RestoreTime", (int) time);
            RecordHistogram.recordCountHistogram("Tab.PerceivedRestoreTime", (int) perceivedTime);
        } else {
            switch (errorCode) {
                case NetError.ERR_INTERNET_DISCONNECTED:
                case NetError.ERR_NAME_RESOLUTION_FAILED:
                case NetError.ERR_DNS_TIMED_OUT:
                    RecordHistogram.recordEnumeratedHistogram("Tab.RestoreResult",
                            TAB_RESTORE_RESULT_FAILURE_NETWORK_CONNECTIVITY,
                            TAB_RESTORE_RESULT_COUNT);
                    break;
                default:
                    RecordHistogram.recordEnumeratedHistogram("Tab.RestoreResult",
                            TAB_RESTORE_RESULT_FAILURE_OTHER, TAB_RESTORE_RESULT_COUNT);
            }
        }
    }

    /**
     * Records a sample in a histogram of times. This is the Java equivalent of the
     * UMA_HISTOGRAM_LONG_TIMES_100.
     */
    private void recordLongTimesHistogram100(String name, long duration) {
        RecordHistogram.recordCustomTimesHistogram(
                name, TimeUnit.MILLISECONDS.toMillis(duration),
                TimeUnit.MILLISECONDS.toMillis(1), TimeUnit.HOURS.toMillis(1),
                TimeUnit.MILLISECONDS, 100);
    }

    /**
     * Record the tab state transition into histograms.
     * @param prevState Previous state of the tab.
     * @param newState New state of the tab.
     * @param delta Time elapsed from the last state transition in milliseconds.
     */
    private void recordTabStateTransition(int prevState, int newState, long delta) {
        if (prevState == TAB_STATE_ACTIVE && newState == TAB_STATE_INACTIVE) {
            recordLongTimesHistogram100("Tabs.StateTransfer.Time_Active_Inactive", delta);
        } else if (prevState == TAB_STATE_ACTIVE && newState == TAB_STATE_CLOSED) {
            recordLongTimesHistogram100("Tabs.StateTransfer.Time_Active_Closed", delta);
        } else if (prevState == TAB_STATE_INACTIVE && newState == TAB_STATE_ACTIVE) {
            recordLongTimesHistogram100("Tabs.StateTransfer.Time_Inactive_Active", delta);
        } else if (prevState == TAB_STATE_INACTIVE && newState == TAB_STATE_CLOSED) {
            recordLongTimesHistogram100("Tabs.StateTransfer.Time_Inactive_Close", delta);
        }

        if (prevState == TAB_STATE_INITIAL) {
            RecordHistogram.recordEnumeratedHistogram("Tabs.StateTransfer.Target_Initial", newState,
                    TAB_STATE_MAX);
        } else if (prevState == TAB_STATE_ACTIVE) {
            RecordHistogram.recordEnumeratedHistogram("Tabs.StateTransfer.Target_Active", newState,
                    TAB_STATE_MAX);
        } else if (prevState == TAB_STATE_INACTIVE) {
            RecordHistogram.recordEnumeratedHistogram("Tabs.StateTransfer.Target_Inactive",
                    newState, TAB_STATE_MAX);
        }
    }

    /**
     * Records the number of background tabs which were opened from this tab's
     * current URL. Does not record anything if no background tabs were opened.
     */
    private void recordNumBackgroundTabsOpened() {
        if (mNumBackgroundTabsOpened > 0) {
            RecordHistogram.recordCount100Histogram(
                    "Tab.BackgroundTabsOpenedViaContextMenuCount", mNumBackgroundTabsOpened);
        }
        mNumBackgroundTabsOpened = 0;
        mChildBackgroundTabShowObserver = null;
    }

    /**
     * Updates saved TabState and its timestamp. Records the state transition into the histogram.
     * @param newState New state of the tab.
     */
    void updateTabState(int newState) {
        long now = System.currentTimeMillis();
        recordTabStateTransition(mLastTabState, newState, now - mLastTabStateChangeMillis);
        mLastTabStateChangeMillis = now;
        mLastTabState = newState;
    }

    /**
     * Called upon tab display.
     * @param selectionType determines how the tab was being shown
     * @param previousTimestampMillis time of the previous display or creation time for the tabs
     *                                opened in background and not yet displayed
     * @param rank The MRU rank for this tab within the model.
     */
    void onShow(TabSelectionType selectionType, long previousTimestampMillis, int rank) {
        long now = SystemClock.elapsedRealtime();
        // Do not collect the tab switching data for the first switch to a tab after the cold start
        // and for the tab switches that were not user-originated (e.g. the user closes the last
        // incognito tab and the current normal mode tab is shown).
        if (mLastShownTimestamp != -1 && selectionType == TabSelectionType.FROM_USER) {
            long age = now - mLastShownTimestamp;
            RecordHistogram.recordCountHistogram("Tab.SwitchedToForegroundAge", (int) age);
            RecordHistogram.recordCountHistogram("Tab.SwitchedToForegroundMRURank", rank);
        }

        increaseTabShowCount();
        boolean isOnBrowserStartup = sAllTabsShowCount == 1;
        boolean performsLazyLoad = mTabCreationState == TabCreationState.FROZEN_FOR_LAZY_LOAD
                && mLastShownTimestamp == -1;

        int status;
        if (mRestoreStartedAtMillis == -1 && !performsLazyLoad) {
            // The tab is *not* being restored or loaded lazily on first display.
            status = TAB_STATUS_MEMORY_RESIDENT;
        } else if (mLastShownTimestamp == -1) {
            // This is first display and the tab is being restored or loaded lazily.
            if (isOnBrowserStartup) {
                status = TAB_STATUS_RELOAD_COLD_START_FG;
            } else if (mTabCreationState == TabCreationState.FROZEN_ON_RESTORE) {
                status = TAB_STATUS_RELOAD_COLD_START_BG;
            } else if (mTabCreationState == TabCreationState.FROZEN_FOR_LAZY_LOAD) {
                status = TAB_STATUS_LAZY_LOAD_FOR_BG_TAB;
            } else {
                assert mTabCreationState == TabCreationState.LIVE_IN_FOREGROUND
                        || mTabCreationState == TabCreationState.LIVE_IN_BACKGROUND;
                status = TAB_STATUS_RELOAD_EVICTED;
            }
        } else {
            // The tab is being restored and this is *not* the first time the tab is shown.
            status = TAB_STATUS_RELOAD_EVICTED;
        }

        // Record only user-visible switches to existing tabs. Do not record displays of newly
        // created tabs (FROM_NEW) or selections of the previous tab that happen when we close the
        // tab opened from intent while exiting Chrome (FROM_CLOSE).
        if (selectionType == TabSelectionType.FROM_USER) {
            RecordHistogram.recordEnumeratedHistogram(
                    "Tab.StatusWhenSwitchedBackToForeground", status, TAB_STATUS_LIM);
        }

        // Record Tab.BackgroundLoadStatus.
        if (mLastShownTimestamp == -1) {
            if (mTabCreationState == TabCreationState.LIVE_IN_BACKGROUND) {
                if (mRestoreStartedAtMillis == -1) {
                    RecordHistogram.recordEnumeratedHistogram("Tab.BackgroundLoadStatus",
                            TAB_BACKGROUND_LOAD_SHOWN, TAB_BACKGROUND_LOAD_LIM);
                } else {
                    RecordHistogram.recordEnumeratedHistogram("Tab.BackgroundLoadStatus",
                            TAB_BACKGROUND_LOAD_LOST, TAB_BACKGROUND_LOAD_LIM);

                    if (previousTimestampMillis > 0) {
                        RecordHistogram.recordMediumTimesHistogram(
                                "Tab.LostTabAgeWhenSwitchedToForeground",
                                System.currentTimeMillis() - previousTimestampMillis,
                                TimeUnit.MILLISECONDS);
                    }
                }
            } else if (mTabCreationState == TabCreationState.FROZEN_FOR_LAZY_LOAD) {
                assert mRestoreStartedAtMillis == -1;
                RecordHistogram.recordEnumeratedHistogram("Tab.BackgroundLoadStatus",
                        TAB_BACKGROUND_LOAD_SKIPPED, TAB_BACKGROUND_LOAD_LIM);
            }
        }

        // Record "tab age upon first display" metrics. previousTimestampMillis is persisted through
        // cold starts.
        if (mLastShownTimestamp == -1 && previousTimestampMillis > 0) {
            if (isOnBrowserStartup) {
                RecordHistogram.recordCountHistogram("Tabs.ForegroundTabAgeAtStartup",
                        (int) millisecondsToMinutes(System.currentTimeMillis()
                                                             - previousTimestampMillis));
            } else if (selectionType == TabSelectionType.FROM_USER) {
                RecordHistogram.recordCountHistogram("Tab.AgeUponRestoreFromColdStart",
                        (int) millisecondsToMinutes(System.currentTimeMillis()
                                                             - previousTimestampMillis));
            }
        }

        mLastShownTimestamp = now;

        updateTabState(TAB_STATE_ACTIVE);
    }

    void onHide() {
        updateTabState(TAB_STATE_INACTIVE);
    }

    /** Called when the corresponding activity is stopped. */
    void onActivityHidden() {
        recordNumBackgroundTabsOpened();
    }

    void onDestroy() {
        updateTabState(TAB_STATE_CLOSED);

        if (mTabCreationState == TabCreationState.LIVE_IN_BACKGROUND
                || mTabCreationState == TabCreationState.FROZEN_FOR_LAZY_LOAD) {
            RecordHistogram.recordBooleanHistogram(
                    "Tab.BackgroundTabShown", mLastShownTimestamp != -1);
        }

        recordNumBackgroundTabsOpened();
    }

    /** Called when restore of the corresponding tab is triggered. */
    void onRestoreStarted() {
        mRestoreStartedAtMillis = SystemClock.elapsedRealtime();
    }

    /** Called when the corresponding tab starts a page load. */
    void onPageLoadStarted() {
        recordNumBackgroundTabsOpened();
    }

    /** Called when the correspoding tab completes a page load. */
    void onPageLoadFinished() {
        // Record only tab restores that the user became aware of. If the restore is triggered
        // speculatively and completes before the user switches to the tab, then this case is
        // reflected in Tab.StatusWhenSwitchedBackToForeground metric.
        if (mRestoreStartedAtMillis != -1 && mLastShownTimestamp >= mRestoreStartedAtMillis) {
            long now = SystemClock.elapsedRealtime();
            long restoreTime = now - mRestoreStartedAtMillis;
            long perceivedRestoreTime = now - mLastShownTimestamp;
            recordTabRestoreResult(true, restoreTime, perceivedRestoreTime, -1);
        }
        mRestoreStartedAtMillis = -1;
    }

    /** Called when the correspoding tab fails a page load. */
    void onLoadFailed(int errorCode) {
        if (mRestoreStartedAtMillis != -1 && mLastShownTimestamp >= mRestoreStartedAtMillis) {
            // Load time is ignored for failed loads.
            recordTabRestoreResult(false, -1, -1, errorCode);
        }
        mRestoreStartedAtMillis = -1;
    }

    /** Called when the renderer of the correspoding tab crashes. */
    void onRendererCrashed() {
        if (mRestoreStartedAtMillis != -1) {
            // TODO(ppi): Add a bucket in Tab.RestoreResult for restores failed due to
            //            renderer crashes and start to track that.
            mRestoreStartedAtMillis = -1;
        }
    }

    /**
     * Called when a user opens a background tab by long pressing and selecting "Open in a new tab"
     * from the context menu.
     * @param backgroundTab The background tab.
     */
    void onBackgroundTabOpenedFromContextMenu(Tab backgroundTab) {
        ++mNumBackgroundTabsOpened;

        if (mChildBackgroundTabShowObserver == null) {
            mChildBackgroundTabShowObserver =
                    new ChildBackgroundTabShowObserver(backgroundTab.getParentId());
        }
        mChildBackgroundTabShowObserver.onBackgroundTabOpened(backgroundTab);
    }

    /**
     * @return The timestamp for when this tab was last shown.
     */
    long getLastShownTimestamp() {
        return mLastShownTimestamp;
    }

    private static void increaseTabShowCount() {
        sAllTabsShowCount++;
    }

    private static long millisecondsToMinutes(long msec) {
        return msec / 1000 / 60;
    }
}
