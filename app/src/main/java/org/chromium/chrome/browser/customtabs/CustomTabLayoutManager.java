// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.view.ViewGroup;

import org.chromium.chrome.browser.compositor.layouts.LayoutManagerDocument;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerHost;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.dom_distiller.ReaderModeManagerDelegate;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

/**
 * A simple LayoutManager that shows multiple tabs without animation.
 */
public class CustomTabLayoutManager extends LayoutManagerDocument {

    TabModelObserver mTabModelObserver = new EmptyTabModelObserver() {
        @Override
        public void didAddTab(Tab tab, TabLaunchType type) {
            if (type == TabLaunchType.FROM_RESTORE) {
                getActiveLayout().onTabRestored(time(), tab.getId());
            } else {
                int newIndex = TabModelUtils.getTabIndexById(getTabModelSelector().getModel(false),
                        tab.getId());
                getActiveLayout().onTabCreated(time(), tab.getId(), newIndex,
                        getTabModelSelector().getCurrentTabId(), false, false, 0f, 0f);
            }
        }

        @Override
        public void didCloseTab(int tabId, boolean incognito) {
            getActiveLayout().onTabSelected(time(), getTabModelSelector().getCurrentTabId(),
                    tabId, false);
        }
    };

    public CustomTabLayoutManager(LayoutManagerHost host) {
        super(host);
    }

    @Override
    public void init(TabModelSelector selector, TabCreatorManager creator,
            TabContentManager content, ViewGroup androidContentContainer,
            ContextualSearchManagementDelegate contextualSearchDelegate,
            ReaderModeManagerDelegate readerModeDelegate,
            DynamicResourceLoader dynamicResourceLoader) {
        super.init(selector, creator, content, androidContentContainer, contextualSearchDelegate,
                readerModeDelegate, dynamicResourceLoader);
        for (TabModel model : selector.getModels()) model.addObserver(mTabModelObserver);
    }
}
