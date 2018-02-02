// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.content.browser.ContentViewCore;

/**
 * A set of convenience methods used for interacting with {@link TabList}s and {@link TabModel}s.
 */
public class TabModelUtils {
    private TabModelUtils() { }

    /**
     * @param model The {@link TabModel} to act on.
     * @param index The index of the {@link Tab} to close.
     * @return      {@code true} if the {@link Tab} was found.
     */
    public static boolean closeTabByIndex(TabModel model, int index) {
        Tab tab = model.getTabAt(index);
        if (tab == null) return false;

        return model.closeTab(tab);
    }

    /**
     * @param model The {@link TabModel} to act on.
     * @param tabId The id of the {@link Tab} to close.
     * @return      {@code true} if the {@link Tab} was found.
     */
    public static boolean closeTabById(TabModel model, int tabId) {
        return closeTabById(model, tabId, false);
    }

    /**
     * @param model   The {@link TabModel} to act on.
     * @param tabId   The id of the {@link Tab} to close.
     * @param canUndo Whether or not this closure can be undone.
     * @return        {@code true} if the {@link Tab} was found.
     */
    public static boolean closeTabById(TabModel model, int tabId, boolean canUndo) {
        Tab tab = TabModelUtils.getTabById(model, tabId);
        if (tab == null) return false;

        return model.closeTab(tab, true, false, canUndo);
    }

    /**
     * @param model The {@link TabModel} to act on.
     * @return      {@code true} if the {@link Tab} was found.
     */
    public static boolean closeCurrentTab(TabModel model) {
        Tab tab = TabModelUtils.getCurrentTab(model);
        if (tab == null) return false;

        return model.closeTab(tab);
    }

    /**
     * Find the index of the {@link Tab} with the specified id.
     * @param model The {@link TabModel} to act on.
     * @param tabId The id of the {@link Tab} to find.
     * @return      Specified {@link Tab} index or {@link TabList#INVALID_TAB_INDEX} if the
     *              {@link Tab} is not found
     */
    public static int getTabIndexById(TabList model, int tabId) {
        int count = model.getCount();

        for (int i = 0; i < count; i++) {
            Tab tab = model.getTabAt(i);
            if (tab.getId() == tabId) return i;
        }

        return TabModel.INVALID_TAB_INDEX;
    }

    /**
     * Find the {@link Tab} with the specified id.
     * @param model The {@link TabModel} to act on.
     * @param tabId The id of the {@link Tab} to find.
     * @return      Specified {@link Tab} or {@code null} if the {@link Tab} is not found
     */
    public static Tab getTabById(TabList model, int tabId) {
        int index = getTabIndexById(model, tabId);
        if (index == TabModel.INVALID_TAB_INDEX) return null;
        return model.getTabAt(index);
    }

    /**
     * Find the {@link Tab} index whose URL matches the specified URL.
     * @param model The {@link TabModel} to act on.
     * @param url   The URL to search for.
     * @return      Specified {@link Tab} or {@code null} if the {@link Tab} is not found
     */
    public static int getTabIndexByUrl(TabList model, String url) {
        int count = model.getCount();

        for (int i = 0; i < count; i++) {
            if (model.getTabAt(i).getUrl().contentEquals(url)) return i;
        }

        return TabModel.INVALID_TAB_INDEX;
    }

    /**
     * Get the currently selected {@link Tab} id.
     * @param model The {@link TabModel} to act on.
     * @return      The id of the currently selected {@link Tab}.
     */
    public static int getCurrentTabId(TabList model) {
        Tab tab = getCurrentTab(model);
        if (tab == null) return Tab.INVALID_TAB_ID;

        return tab.getId();
    }

    /**
     * Get the currently selected {@link Tab}.
     * @param model The {@link TabModel} to act on.
     * @returns     The current {@link Tab} or {@code null} if no {@link Tab} is selected
     */
    public static Tab getCurrentTab(TabList model) {
        int index = model.index();
        if (index == TabModel.INVALID_TAB_INDEX) return null;

        return model.getTabAt(index);
    }

    /**
     * @param model The {@link TabModel} to act on.
     * @return      The currently active {@link ContentViewCore}, or {@code null} if no {@link Tab}
     *              is selected or the selected {@link Tab} has no current {@link ContentViewCore}.
     */
    public static ContentViewCore getCurrentContentViewCore(TabList model) {
        Tab tab = getCurrentTab(model);
        if (tab == null) return null;

        return tab.getContentViewCore();
    }

    /**
     * A helper method that automatically passes {@link TabSelectionType#FROM_USER} as the selection
     * type to {@link TabModel#setIndex(int, TabSelectionType)}.
     * @param model The {@link TabModel} to act on.
     * @param index The index of the {@link Tab} to select.
     */
    public static void setIndex(TabModel model, int index) {
        model.setIndex(index, TabSelectionType.FROM_USER);
    }

}