// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;

/**
 * TabModel organizes all the open tabs and allows you to create new ones. There are two TabModels
 * in the app at this time: normal and incognito. More could be added to allow for windows or
 * something.
 */
public interface TabModel extends TabList {
    /**
     * A list of the various ways tabs can be launched.
     */
    public enum TabLaunchType {
        FROM_LINK,             // Opened from a link.
        FROM_EXTERNAL_APP,     // Opened by and external app.
        FROM_MENU_OR_OVERVIEW, // Opened from the options menu or the tab stack overview.
        FROM_RESTORE,          // Opened after restoring state from storage.
        // Opened from the long press menu. Like FROM_MENU but also sets up a parent/child
        // relationship like FROM_LINK. FOREGROUND and BACKGROUND indicates whether the current tab
        // should be automatically switched to the new tab or not.
        FROM_LONGPRESS_FOREGROUND,
        FROM_LONGPRESS_BACKGROUND,
        FROM_INSTANT,          // Tab was created by instant.
        FROM_KEYBOARD          // Opened from a physical keyboard via shortcut.
    }

    /**
     * A list of the various ways tabs can eb selected.
     */
    public enum TabSelectionType {
        FROM_CLOSE, // Selection of adjacent tab when the active tab is closed in foreground.
        FROM_EXIT,  // Selection of adjacent tab when the active tab is closed upon app exit.
        FROM_NEW,   // Selection of newly created tab (e.g. for a url intent or NTP).
        FROM_USER   // User-originated switch to existing tab or selection of main tab on app
                    // startup.
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
