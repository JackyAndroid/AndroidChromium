// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tabmodel.IncognitoTabModel.IncognitoTabModelDelegate;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager.TabCreator;

/**
 * Stores all the variables needed to create an Incognito TabModelImpl when it is needed.
 */
class IncognitoTabModelImplCreator implements IncognitoTabModelDelegate {
    private final TabCreator mRegularTabCreator;
    private final TabCreator mIncognitoTabCreator;
    private final TabModelSelectorUma mUma;
    private final TabModelOrderController mOrderController;
    private final TabContentManager mTabContentManager;
    private final TabPersistentStore mTabSaver;
    private final TabModelDelegate mModelDelegate;

    /**
     * Constructor for an IncognitoTabModelImplCreator, used by {@link IncognitoTabModel}.
     *
     * Creating an instance of this class does not create the Incognito TabModelImpl immediately.
     * The {@link IncognitoTabModel} will use this class to create the real TabModelImpl when it
     * will actually be used.
     *
     * @param regularTabCreator   Creates regular tabs.
     * @param incognitoTabCreator Creates incognito tabs.
     * @param uma                 Handles UMA tracking for the model.
     * @param orderController     Determines the order for inserting new Tabs.
     * @param tabContentManager   Manages the display content of the tab.
     * @param tabSaver            Handler for saving tabs.
     * @param modelDelegate       Delegate to handle external dependencies and interactions.
     */
    public IncognitoTabModelImplCreator(TabCreator regularTabCreator,
            TabCreator incognitoTabCreator, TabModelSelectorUma uma,
            TabModelOrderController orderController, TabContentManager tabContentManager,
            TabPersistentStore tabSaver, TabModelDelegate modelDelegate) {
        mRegularTabCreator = regularTabCreator;
        mIncognitoTabCreator = incognitoTabCreator;
        mUma = uma;
        mOrderController = orderController;
        mTabContentManager = tabContentManager;
        mTabSaver = tabSaver;
        mModelDelegate = modelDelegate;
    }

    @Override
    public TabModel createTabModel() {
        return new TabModelImpl(true, false, mRegularTabCreator, mIncognitoTabCreator, mUma,
                mOrderController, mTabContentManager, mTabSaver, mModelDelegate, false);
    }

    @Override
    public boolean doIncognitoTabsExist() {
        return TabWindowManager.getInstance().getIncognitoTabCount() > 0;
    }
}
