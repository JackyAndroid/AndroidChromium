// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;

import java.util.List;

/**
 * Observer of tab changes for all tabs owned by a {@link TabModelSelector}.
 */
public class TabModelSelectorTabObserver extends EmptyTabObserver {

    private final TabModelSelector mTabModelSelector;
    private final TabModelObserver mTabModelObserver;

    private TabModelSelectorObserver mSelectorObserver;

    /**
     * Constructs an observer that should be notified of tabs changes for all tabs owned
     * by a specified {@link TabModelSelector}.  Any Tabs created after this call will be
     * observed as well, and Tabs removed will no longer have their information broadcast.
     *
     * <p>
     * {@link #destroy()} must be called to unregister this observer.
     *
     * @param selector The selector that owns the Tabs that should notify this observer.
     */
    public TabModelSelectorTabObserver(TabModelSelector selector) {
        mTabModelSelector = selector;

        mTabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didAddTab(Tab tab, TabLaunchType type) {
                tab.addObserver(TabModelSelectorTabObserver.this);
            }

            @Override
            public void didCloseTab(Tab tab) {
                tab.removeObserver(TabModelSelectorTabObserver.this);
            }
        };

        List<TabModel> tabModels = selector.getModels();
        if (tabModels.isEmpty()) {
            mSelectorObserver = new EmptyTabModelSelectorObserver() {
                @Override
                public void onNewTabCreated(Tab tab) {
                    assert false : "onChange should have happened and unregistered this listener.";
                }

                @Override
                public void onChange() {
                    mTabModelSelector.removeObserver(this);
                    mSelectorObserver = null;
                    registerModelObservers();
                }
            };
            mTabModelSelector.addObserver(mSelectorObserver);
        } else {
            registerModelObservers();
        }
    }

    private void registerModelObservers() {
        List<TabModel> tabModels = mTabModelSelector.getModels();
        for (int i = 0; i < tabModels.size(); i++) {
            TabModel tabModel = tabModels.get(i);
            tabModel.addObserver(mTabModelObserver);

            TabList comprehensiveTabList = tabModel.getComprehensiveModel();
            for (int j = 0; j < comprehensiveTabList.getCount(); j++) {
                comprehensiveTabList.getTabAt(j).addObserver(this);
            }
        }
    }

    /**
     * Destroys the observer and removes itself as a listener for Tab updates.
     */
    public void destroy() {
        if (mSelectorObserver != null) {
            mTabModelSelector.removeObserver(mSelectorObserver);
            mSelectorObserver = null;
        }

        List<TabModel> tabModels = mTabModelSelector.getModels();
        for (int i = 0; i < tabModels.size(); i++) {
            TabModel tabModel = tabModels.get(i);
            tabModel.removeObserver(mTabModelObserver);

            TabList comprehensiveTabList = tabModel.getComprehensiveModel();
            for (int j = 0; j < comprehensiveTabList.getCount(); j++) {
                comprehensiveTabList.getTabAt(j).removeObserver(this);
            }
        }
    }
}
