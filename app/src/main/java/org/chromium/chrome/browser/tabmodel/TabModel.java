// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;

/**
 * TabModel organizes all the open tabs and allows you to create new ones.  Regular and Incognito
 * tabs are kept in different TabModels.
 */
public interface TabModel extends TabList {
    /**
     * A list of the various ways tabs can be launched.
     */
    public enum TabLaunchType {
        /**
         * Opened from a link.  Sets up a relationship between the newly created tab and its parent.
         */
        FROM_LINK,

        /** Opened by an external app. */
        FROM_EXTERNAL_APP,

        /**
         * Catch-all for Tabs opened by Chrome UI not covered by more specific TabLaunchTypes.
         * Examples include:
         * - Tabs created by the options menu.
         * - Tabs created via the New Tab button in the tab stack overview.
         * - Tabs created via Push Notifications.
         * - Tabs opened via a keyboard shortcut.
         */
        FROM_CHROME_UI,

        /**
         * Opened during the restoration process on startup or when merging two instances of
         * Chrome in Android N+ multi-instance mode.
         */
        FROM_RESTORE,

        /**
         * Opened from the long press context menu.  Will be brought to the foreground.
         * Like FROM_CHROME_UI, but also sets up a parent/child relationship like FROM_LINK.
         */
        FROM_LONGPRESS_FOREGROUND,

        /**
         * Opened from the long press context menu.  Will not be brought to the foreground.
         * Like FROM_CHROME_UI, but also sets up a parent/child relationship like FROM_LINK.
         */
        FROM_LONGPRESS_BACKGROUND,

        /**
         * Changed windows by moving from one activity to another. Will be opened in the foreground.
         */
        FROM_REPARENTING
    }

    /**
     * A list of the various ways tabs can be selected.
     */
    public enum TabSelectionType {
        /** Selection of adjacent tab when the active tab is closed in foreground. */
        FROM_CLOSE,

        /** Selection of adjacent tab when the active tab is closed upon app exit. */
        FROM_EXIT,

        /** Selection of newly created tab (e.g. for a URL intent or NTP). */
        FROM_NEW,

        /** User-originated switch to existing tab or selection of main tab on app startup. */
        FROM_USER
    }

    /**
     * @return The profile associated with the current model.
     */
    public Profile getProfile();

    /**
     * Unregisters and destroys the specified tab, and then switches to the previous tab.
     * @param tab The non-null tab to close
     * @return true if the tab was found
     */
    public boolean closeTab(Tab tab);

    /**
     * Unregisters and destroys the specified tab, and then switches to the previous tab.
     *
     * @param tab The non-null tab to close
     * @param animate true iff the closing animation should be displayed
     * @param uponExit true iff the tab is being closed upon application exit (after user presses
     *                 the system back button)
     * @param canUndo Whether or not this action can be undone. If this is {@code true} and
     *                {@link #supportsPendingClosures()} is {@code true}, this {@link Tab}
     *                will not actually be closed until {@link #commitTabClosure(int)} or
     *                {@link #commitAllTabClosures()} is called, but it will be effectively removed
     *                from this list. To get a comprehensive list of all tabs, including ones that
     *                have been partially closed, use the {@link TabList} from
     *                {@link #getComprehensiveModel()}.
     * @return true if the tab was found
     */
    public boolean closeTab(Tab tab, boolean animate, boolean uponExit, boolean canUndo);

    /**
     * Returns which tab would be selected if the specified tab {@code id} were closed.
     * @param id The ID of tab which would be closed.
     * @return The id of the next tab that would be visible.
     */
    public Tab getNextTabIfClosed(int id);

    /**
     * Close all the tabs on this model.
     */
    public void closeAllTabs();

    /**
     * Close all tabs on this model. If allowDelegation is true, the model has the option
     * of not closing all tabs and delegating the closure to another class.
     * @param allowDelegation true iff the model may delegate the close all request.
     *                        false iff the model must close all tabs.
     * @param uponExit true iff the tabs are being closed upon application exit (after user presses
     *                 the system back button)
     */
    public void closeAllTabs(boolean allowDelegation, boolean uponExit);

    /**
     * @return Whether or not this model supports pending closures.
     */
    public boolean supportsPendingClosures();

    /**
     * Commits all pending closures, closing all tabs that had a chance to be undone.
     */
    public void commitAllTabClosures();

    /**
     * Commits a pending closure specified by {@code tabId}.
     * @param tabId The id of the {@link Tab} to commit the pending closure.
     */
    public void commitTabClosure(int tabId);

    /**
     * Cancels a pending {@link Tab} closure, bringing the tab back into this model.  Note that this
     * will select the rewound {@link Tab}.
     * @param tabId The id of the {@link Tab} to undo.
     */
    public void cancelTabClosure(int tabId);

    /**
     * Opens the most recently closed tab, bringing the tab back into its original tab model or
     * this model if the original model no longer exists.
     */
    public void openMostRecentlyClosedTab();

    /**
     * @return The complete {@link TabList} this {@link TabModel} represents.  Note that this may
     *         be different than this actual {@link TabModel} if it supports pending closures
     *         {@link #supportsPendingClosures()}, as this will include all pending closure tabs.
     */
    public TabList getComprehensiveModel();

    /**
     * Selects a tab by its index.
     * @param i    The index of the tab to select.
     * @param type The type of selection.
     */
    public void setIndex(int i, final TabSelectionType type);

    /**
     * Moves a tab to a new index.
     * @param id       The id of the tab to move.
     * @param newIndex The new place to put the tab.
     */
    public void moveTab(int id, int newIndex);

    /**
     * To be called when this model should be destroyed.  The model should no longer be used after
     * this.
     *
     * <p>
     * As a result of this call, all {@link Tab}s owned by this model should be destroyed.
     */
    public void destroy();

    /**
     * Adds a newly created tab to this model.
     * @param tab   The tab to be added.
     * @param index The index where the tab should be inserted. The model may override the index.
     * @param type  How the tab was opened.
     */
    void addTab(Tab tab, int index, TabLaunchType type);

    /**
     * Removes the given tab from the model without destroying it. The tab should be inserted into
     * another model to avoid leaking as after this the link to the old Activity will be broken.
     * @param tab The tab to remove.
     */
    void removeTab(Tab tab);

    /**
     * Subscribes a {@link TabModelObserver} to be notified about changes to this model.
     * @param observer The observer to be subscribed.
     */
    void addObserver(TabModelObserver observer);

    /**
     * Unsubscribes a previously subscribed {@link TabModelObserver}.
     * @param observer The observer to be unsubscribed.
     */
    void removeObserver(TabModelObserver observer);
}
