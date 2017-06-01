// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;

/**
 * This class acts as a controller for determining where tabs should be inserted
 * into a tab strip model. See tab_strip_model_order_controller.cc and
 * tab_strip_model.cc
 */
public class TabModelOrderController {

    private static final int NO_TAB = -1;
    private final TabModelSelector mTabModelSelector;

    public TabModelOrderController(TabModelSelector modelSelector) {
        mTabModelSelector = modelSelector;
    }

    /**
     * Determine the insertion index of the next tab. If it's not the result of
     * a link being pressed, the provided index will be returned.
     *
     * @param type The launch type of the new tab.
     * @param position The provided position.
     * @return Where to insert the tab.
     */
    public int determineInsertionIndex(TabLaunchType type, int position, Tab newTab) {
        if (linkClicked(type)) {
            position = determineInsertionIndex(type, newTab);
        }

        if (willOpenInForeground(type, newTab.isIncognito())) {
            // Forget any existing relationships, we don't want to make things
            // too confusing by having multiple groups active at the same time.
            forgetAllOpeners();
        }

        return position;
    }

    /**
     * Determine the insertion index of the next tab.
     *
     * @param type The launch type of the new tab.
     * @return Where to insert the tab.
     */
    public int determineInsertionIndex(TabLaunchType type, Tab newTab) {
        TabModel currentModel = mTabModelSelector.getCurrentModel();
        Tab currentTab = TabModelUtils.getCurrentTab(currentModel);
        if (currentTab == null) {
            assert (currentModel.getCount() == 0);
            return 0;
        }
        int currentId = currentTab.getId();
        int currentIndex = TabModelUtils.getTabIndexById(currentModel, currentId);

        if (sameModelType(currentModel, newTab)) {
            if (willOpenInForeground(type, newTab.isIncognito())) {
                // If the tab was opened in the foreground, insert it adjacent to
                // the tab that opened that link.
                return currentIndex + 1;
            } else {
                // If the tab was opened in the background, position at the end of
                // it's 'group'.
                int index = getIndexOfLastTabOpenedBy(currentId, currentIndex);
                if (index != NO_TAB) {
                    return index + 1;
                } else {
                    return currentIndex + 1;
                }
            }
        } else {
            // If the tab is opening in the other model type, just put it at the end.
            return mTabModelSelector.getModel(newTab.isIncognito()).getCount();
        }
    }

    /**
     * Returns the index of the last tab in the model opened by the specified
     * opener, starting at startIndex. To clarify, the tabs are traversed in the
     * descending order of their position in the model. This means that the tab
     * furthest in the stack with the given opener id will be returned.
     *
     * @param openerId The opener of interest.
     * @param startIndex The start point of the search.
     * @return The last tab if found, NO_TAB otherwise.
     */
    private int getIndexOfLastTabOpenedBy(int openerId, int startIndex) {
        TabModel currentModel = mTabModelSelector.getCurrentModel();
        int count = currentModel.getCount();
        for (int i = count - 1; i >= startIndex; i--) {
            Tab tab = currentModel.getTabAt(i);
            if (tab.getParentId() == openerId && tab.isGroupedWithParent()) {
                return i;
            }
        }
        return NO_TAB;
    }

    /**
     * Clear the opener attribute on all tabs in the model.
     */
    void forgetAllOpeners() {
        TabModel currentModel = mTabModelSelector.getCurrentModel();
        int count = currentModel.getCount();
        for (int i = 0; i < count; i++) {
            currentModel.getTabAt(i).setGroupedWithParent(false);
        }
    }

    /**
     * Determine if a launch type is the result of linked being clicked.
     */
    static boolean linkClicked(TabLaunchType type) {
        return type == TabLaunchType.FROM_LINK
                || type == TabLaunchType.FROM_LONGPRESS_FOREGROUND
                || type == TabLaunchType.FROM_LONGPRESS_BACKGROUND;
    }

    /**
     * Determine if a launch type will result in the tab being opened in the
     * foreground.
     * @param type               The type of opening event.
     * @param isNewTabIncognito  True if the new opened tab is incognito.
     * @return                   True if the tab will be in the foreground
     */
    public boolean willOpenInForeground(TabLaunchType type, boolean isNewTabIncognito) {
        // Restore is handling the active index by itself.
        if (type == TabLaunchType.FROM_RESTORE) return false;
        return type != TabLaunchType.FROM_LONGPRESS_BACKGROUND
                || (!mTabModelSelector.isIncognitoSelected() && isNewTabIncognito);
    }

    /**
     * @return {@code true} If both tabs have the same model type, {@code false} otherwise.
     */
    static boolean sameModelType(TabModel model, Tab tab) {
        return model.isIncognito() == tab.isIncognito();
    }

}
