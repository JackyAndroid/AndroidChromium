// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.chrome.browser.tab.Tab;

/**
 * Observes changes to the tab model selector.
 */
public interface TabModelSelectorObserver {
    /**
     * Called whenever the {@link TabModel} has changed.
     */
    void onChange();

    /**
     * Called when a new tab is created.
     */
    void onNewTabCreated(Tab tab);

    /**
     * Called when a different tab model has been selected.
     * @param newModel The newly selected tab model.
     * @param oldModel The previously selected tab model.
     */
    void onTabModelSelected(TabModel newModel, TabModel oldModel);

    /**
     * Called when the tab state has been initialized and the current tab count and tab model states
     * are reliable.
     */
    void onTabStateInitialized();
}
