// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.content_public.browser.LoadUrlParams;

import java.util.List;

/**
 * TabModelSelector is a wrapper class containing both a normal and an incognito TabModel.
 * This class helps the app know which mode it is currently in, and which TabModel it should
 * be using.
 */
public interface TabModelSelector {
    /**
     * A delegate interface to push close all tabs requests.
     */
    public interface CloseAllTabsDelegate {
        /**
         * Sends a request to close all tabs for a {@link TabModel}.
         * @param incognito Whether the tabs to be closed are incognito.
         * @return Whether the request was handled successfully.
         */
        boolean closeAllTabsRequest(boolean incognito);
    }

    /**
     * Set the current model. This won't cause an animation, but will still change the stack that is
     * currently visible if the tab switcher is open.
     */
    void selectModel(boolean incognito);

    /**
     * Get a specific tab model
     * @return Never returns null.  Returns a stub when real model is uninitialized.
     */
    TabModel getModel(boolean incognito);

    /**
     * @return a list for the underlying models
     */
    List<TabModel> getModels();

    /**
     * @return the model at {@code index} or null if no model exist for that index.
     */
    @VisibleForTesting
    TabModel getModelAt(int index);

    /**
     * Get the current tab model.
     * @return Never returns null.  Returns a stub when real model is uninitialized.
     */
    TabModel getCurrentModel();

    /**
     * Convenience function to get the current tab on the current model
     * @return Current tab or null if none exists or if the model is not initialized.
     */
    Tab getCurrentTab();

    /**
     * Convenience function to get the current tab id on the current model.
     * @return Id of the current tab or {@link Tab#INVALID_TAB_ID} if no tab is selected or the
     *         model is not initialized.
     */
    int getCurrentTabId();

    /**
     * Convenience function to get the {@link TabModel} for a {@link Tab} specified by
     * {@code id}.
     * @param id The id of the {@link Tab} to find the {@link TabModel} for.
     * @return   The {@link TabModel} that owns the {@link Tab} specified by {@code id}.
     */
    TabModel getModelForTabId(int id);

    /**
     * @return The index of the current {@link TabModel}.
     */
    int getCurrentModelIndex();

    /**
     * @return If the incognito {@link TabModel} is current.
     */
    boolean isIncognitoSelected();

    /**
     * Opens a new tab.
     *
     * @param loadUrlParams parameters of the url load
     * @param type Describes how the new tab is being opened.
     * @param parent The parent tab for this new tab (or null if one does not exist).
     * @param incognito Whether to open the new tab in incognito mode.
     * @return The newly opened tab.
     */
    Tab openNewTab(LoadUrlParams loadUrlParams, TabLaunchType type, Tab parent, boolean incognito);

    /**
     * Searches through all children models for the specified Tab and closes the tab if it exists.
     * @param tab the non-null tab to close
     * @return true if the tab was found
     */
    boolean closeTab(Tab tab);

    /**
     * Close all tabs across all tab models
     */
    void closeAllTabs();

    /**
     * Close all tabs across all tab models
     * @param uponExit true iff the tabs are being closed upon application exit (after user presses
     *                 the system back button)
     */
    void closeAllTabs(boolean uponExit);

    /**
     * Get total tab count across all tab models
     */
    int getTotalTabCount();

    /**
     * Search all TabModels for a tab with the specified id.
     * @return specified tab or null if tab is not found
     */
    Tab getTabById(int id);

    /**
     * Add an observer to be notified of changes to the TabModelSelector.
     * @param observer The {@link TabModelSelectorObserver} to notify.
     */
    void addObserver(TabModelSelectorObserver observer);

    /**
     * Removes an observer of TabModelSelector changes..
     * @param observer The {@link TabModelSelectorObserver} to remove.
     */
    void removeObserver(TabModelSelectorObserver observer);

    /**
     * Calls {@link TabModel#commitAllTabClosures()} on all {@link TabModel}s owned by this
     * selector.
     */
    void commitAllTabClosures();

    /**
     * Sets the delegate to handle the requests to close tabs in a single model.
     * @param delegate The delegate to be used.
     */
    void setCloseAllTabsDelegate(CloseAllTabsDelegate delegate);

    /**
     * @return Whether the tab state for this {@link TabModelSelector} has been initialized.
     */
    boolean isTabStateInitialized();

    /**
     * Destroy all owned {@link TabModel}s and {@link Tab}s referenced by this selector.
     */
    void destroy();
}
