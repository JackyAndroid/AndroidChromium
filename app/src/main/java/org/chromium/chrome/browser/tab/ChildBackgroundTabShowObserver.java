// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import org.chromium.base.metrics.RecordHistogram;

import java.util.ArrayList;

/**
 * Observes background tabs opened from the tab with {@link #mParentId}. Records to UMA which of the
 * background tabs the user switches to first.
 */
class ChildBackgroundTabShowObserver extends EmptyTabObserver {
    /**
     * The ID of the parent tab of the background tabs.
     */
    private int mParentTabId;

    /**
     * List of tabs opened from {@link #mParentId} sorted according to their
     * creation order. The oldest tab is at index 0.
     */
    private final ArrayList<Tab> mTabCreationOrder = new ArrayList<Tab>();

    /**
     * Creates an instance of {@link ChildBackgroundTabShowObserver}.
     * @param parentTabId The id of the parent tab of the background tabs.
     */
    public ChildBackgroundTabShowObserver(int parentTabId) {
        mParentTabId = parentTabId;
    }

    /**
     * Called when a background tab is opened from {@link #mParentId}.
     * @param tab The background tab which was opened.
     */
    public void onBackgroundTabOpened(Tab tab) {
        assert mParentTabId == tab.getParentId();

        mTabCreationOrder.add(tab);
        tab.addObserver(this);
    }

    @Override
    public void onShown(Tab tab) {
        int rank = mTabCreationOrder.indexOf(tab);
        int reverseRank = mTabCreationOrder.size() - rank - 1;

        // Record which tab the user switches to first by recording the creation order of the tab
        // that the user switches to. Record both the "Creation Rank" and the
        // "Reverse Creation Rank" because we want to know whether most users switch to the
        // newest background tab or oldest background tab first.
        RecordHistogram.recordCount100Histogram("Tabs.FirstSwitchedToForegroundCreationRank", rank);
        RecordHistogram.recordCount100Histogram(
                "Tabs.FirstSwitchedToForegroundCreationReverseRank", reverseRank);

        for (Tab stopObservingTab : mTabCreationOrder) {
            stopObservingTab.removeObserver(this);
        }
        mTabCreationOrder.clear();
    }

    @Override
    public void onDestroyed(Tab tab) {
        mTabCreationOrder.remove(tab);
    }
}
