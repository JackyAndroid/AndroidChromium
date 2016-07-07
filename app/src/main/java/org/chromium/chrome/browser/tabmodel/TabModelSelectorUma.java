// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import android.app.Activity;
import android.os.SystemClock;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;

/**
 * Centralizes UMA data collection for TabModelSelector. All calls must be made from the UI thread.
 */
public final class TabModelSelectorUma implements ApplicationStatus.ActivityStateListener {
    // TabRestoreUserAction defined in tools/metrics/histograms/histograms.xml.
    private static final int USER_WAITED_FOR_RESTORE_COMPLETION = 0;
    private static final int USER_LEFT_TAB_DURING_RESTORE = 1;
    private static final int USER_LEFT_CHROME_DURING_RESTORE = 2;
    private static final int USER_ACTION_DURING_RESTORE_MAX = 3;

    // Id of the active tab being restored. Equals -1 when the active tab is not being restored.
    private int mRestoredTabId = -1;
    // Timestamp of the beginning of the most recent tab restore.
    private long mRestoreStartedAtMsec = -1;

    TabModelSelectorUma(Activity activity) {
        ApplicationStatus.registerStateListenerForActivity(this, activity);
    }

    /**
     * Cleans up any external dependencies of this class.
     */
    public void destroy() {
        ApplicationStatus.unregisterActivityStateListener(this);
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        if (newState != ActivityState.STOPPED) return;
        if (mRestoredTabId != -1) {
            recordUserActionDuringTabRestore(USER_LEFT_CHROME_DURING_RESTORE);
            mRestoredTabId = -1;
        }
    }

    void userSwitchedToTab() {
        RecordUserAction.record("MobileTabSwitched");
    }

    void onShowTab(int tabId, boolean isBeingRestored) {
        if (mRestoredTabId != -1 && tabId != mRestoredTabId) {
            recordUserActionDuringTabRestore(USER_LEFT_TAB_DURING_RESTORE);
            mRestoredTabId = -1;
        }
        if (isBeingRestored) {
            mRestoredTabId = tabId;
            mRestoreStartedAtMsec = nowMsec();
        }
    }

    void onTabClosing(int tabId) {
        if (mRestoredTabId != -1 && tabId == mRestoredTabId) {
            recordUserActionDuringTabRestore(USER_LEFT_TAB_DURING_RESTORE);
            mRestoredTabId = -1;
        }
    }

    void onTabCrashed(int tabId) {
        if (mRestoredTabId != -1 && tabId == mRestoredTabId) {
            mRestoredTabId = -1;
        }
    }

    void onPageLoadFinished(int tabId) {
        if (mRestoredTabId != -1 && tabId == mRestoredTabId) {
            recordUserActionDuringTabRestore(USER_WAITED_FOR_RESTORE_COMPLETION);
            mRestoredTabId = -1;
        }
    }

    void onPageLoadFailed(int tabId) {
        if (mRestoredTabId != -1 && tabId == mRestoredTabId) {
            assert mRestoreStartedAtMsec != -1;
            // If the pageload fails very quickly we cannot argue that the user "waited for
            // completion".
            if (nowMsec() - mRestoreStartedAtMsec >= 5000) {
                recordUserActionDuringTabRestore(USER_WAITED_FOR_RESTORE_COMPLETION);
            }
            mRestoredTabId = -1;
        }
    }

    void onTabsViewShown() {
        if (mRestoredTabId != -1) {
            recordUserActionDuringTabRestore(USER_LEFT_TAB_DURING_RESTORE);
            mRestoredTabId = -1;
        }
    }

    private static long nowMsec() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * Logs action to a UMA histogram.
     */
    private void recordUserActionDuringTabRestore(int action) {
        assert action >= 0 && action < USER_ACTION_DURING_RESTORE_MAX;
        RecordHistogram.recordEnumeratedHistogram(
                "Tab.RestoreUserPersistence", action, USER_ACTION_DURING_RESTORE_MAX);
    }
}
