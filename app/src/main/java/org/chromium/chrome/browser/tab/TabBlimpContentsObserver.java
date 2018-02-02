// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import org.chromium.base.ObserverList.RewindableIterator;
import org.chromium.blimp_public.contents.EmptyBlimpContentsObserver;
import org.chromium.ui.base.PageTransition;

/**
 * BlimpContentsObserver used by Tab.
 */
public class TabBlimpContentsObserver extends EmptyBlimpContentsObserver {
    private Tab mTab;
    private boolean mPageIsLoading;

    public TabBlimpContentsObserver(Tab tab) {
        mTab = tab;
        mPageIsLoading = false;
    }

    /**
     * All UI updates related to navigation state should be notified from this method.
     */
    @Override
    public void onNavigationStateChanged() {
        mTab.updateTitle();
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onUrlUpdated(mTab);
        }
    }

    /**
     * Tab can use this to drive what kind of content to show based on the URL.
     * Also sets the loading status of the tab correctly.
     */
    @Override
    public void onLoadingStateChanged(boolean loading) {
        if (loading) {
            // Passing true for toDifferentDocument to update visuals related to location bar.
            mTab.onLoadStarted(true);
        } else {
            mTab.onLoadStopped();
        }
    }

    /**
     * This method is used to update the status of progress bar.
     */
    @Override
    public void onPageLoadingStateChanged(boolean loading) {
        mPageIsLoading = loading;
        if (loading) {
            mTab.didStartPageLoad(mTab.getUrl(), false);

            // Simulate provisional load start and completion events.
            mTab.handleDidStartProvisionalLoadForFrame(true, mTab.getUrl());

            // TODO(dtrainor): Investigate if we need to pipe through a more accurate PageTransition
            // here.
            mTab.handleDidCommitProvisonalLoadForFrame(mTab.getUrl(), PageTransition.TYPED);

            // Currently, blimp doesn't have a way to calculate the progress. So we are sending a
            // fake progress value.
            mTab.notifyLoadProgress(30);
        } else {
            mTab.notifyLoadProgress(100);
            mTab.didFinishPageLoad();
        }
    }

    /**
     * @return a value between 0 and 100 reflecting what percentage of the page load is complete.
     */
    public int getMostRecentProgress() {
        return mPageIsLoading ? 30 : 100;
    }
}
