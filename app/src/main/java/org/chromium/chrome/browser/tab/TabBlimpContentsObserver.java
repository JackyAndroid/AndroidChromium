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

    public TabBlimpContentsObserver(Tab tab) {
        mTab = tab;
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
     */
    @Override
    public void onLoadingStateChanged(boolean loading) {
        // TODO(dtrainor): Investigate if we need to pipe through a more accurate PageTransition
        // here.
        mTab.handleDidCommitProvisonalLoadForFrame(mTab.getUrl(), PageTransition.TYPED);
    }
}
