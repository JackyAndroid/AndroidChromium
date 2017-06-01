// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;

/**
 * This class serves as a callback from TabModel to TabModelSelector. The number of methods in this
 * class should be reduced to a minimum. http://crbug.com/263579
 */
public interface TabModelDelegate {

    /**
     * Requests the specified to be shown.
     * @param tab The tab that is requested to be shown.
     * @param type The reason why this tab was requested to be shown.
     */
    void requestToShowTab(Tab tab, TabSelectionType type);

    /**
     * Delegate a request to close all tabs in a model.
     * @param incognito Whether the model is incognito.
     * @return Whether the request was handled.
     */
    boolean closeAllTabsRequest(boolean incognito);

    // TODO(aurimas): clean these methods up.
    TabModel getCurrentModel();
    TabModel getModel(boolean incognito);
    boolean isInOverviewMode();
    boolean isSessionRestoreInProgress();
    void selectModel(boolean incognito);
}
