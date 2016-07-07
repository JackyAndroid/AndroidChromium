// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.content.Intent;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;

/**
 * Extends the regular TabModel interface so that it can be aware of, and work with, Android's
 * Recents menu.
 */
public interface DocumentTabModel extends TabModel {
    /** Stores information about a DocumentActivity. */
    @SuppressFBWarnings({"URF_UNREAD", "UUF_UNUSED"})
    public static final class Entry {
        public final int tabId;
        public boolean canGoBack;
        public String initialUrl;
        public String currentUrl;
        public boolean isTabStateReady;
        public boolean isDirty;
        public Tab placeholderTab;

        private TabState mTabState;

        public Entry(int tabId) {
            this.tabId = tabId;
        }

        public Entry(int tabId, String initialUrl) {
            this.tabId = tabId;
            this.initialUrl = initialUrl;
        }

        public Entry(int tabId, TabState tabState) {
            this.tabId = tabId;
            this.mTabState = tabState;
            this.isTabStateReady = true;
        }

        /**
         * Caches the TabState if the TabState has a serialized WebContentsState. Otherwise clears
         * the cached TabState to prevent crashes from a partial restoration.
         */
        public void setTabState(TabState tabState) {
            mTabState = (tabState == null || tabState.contentsState == null) ? null : tabState;
        }

        /** @return TabState that was cached for the Entry. */
        public TabState getTabState() {
            return mTabState;
        }
    }

    /**
     * Delays an action until the TabList initialization has reached a certain state.
     * Adds itself to the Observer list if the TabList is not ready, and is automatically removed
     * once its run condition is satisfied and the action is run.
     */
    public abstract static class InitializationObserver {
        private final DocumentTabModel mTabModel;

        public InitializationObserver(DocumentTabModel tabModel) {
            mTabModel = tabModel;
        }

        /** @return Whether or not the TabList is initialized enough for the observer to run. */
        public abstract boolean isSatisfied(int currentState);

        /** @return Whether or not the Activity owning the observer has died. */
        public abstract boolean isCanceled();

        /** Perform whatever action the observer is waiting for. */
        protected abstract void runImmediately();

        /** Perform the action if the TabList is ready, or observe the TabList until it is. */
        public final void runWhenReady() {
            ThreadUtils.assertOnUiThread();
            if (isSatisfied(mTabModel.getCurrentInitializationStage())) {
                runImmediately();
            } else {
                mTabModel.addInitializationObserver(this);
            }
        }
    }

    /**
     * Begin setting up the C++-side counterpart to this class.
     */
    void initializeNative();

    /**
     * @return Whether the native-side pointer has been initialized.
     */
    boolean isNativeInitialized();

    /**
     * Returns the initial URL for the Document with the given ID.
     * @param tabId The ID for the document to return the url for.
     * @return The initial URL for the entry if it was found, null otherwise.
     */
    String getInitialUrlForDocument(int tabId);

    /**
     * Returns the current URL for the Document with the given ID.
     * @param tabId The ID for the document to return the url for.
     * @return The current URL for the entry if it was found, null otherwise.
     */
    String getCurrentUrlForDocument(int tabId);

    /**
     * Returns whether or not an attempt to restore the TabState for the given tab ID has finished.
     * @param tabId The ID for the document.
     * @return Whether or not an attempt to restore the tab state has finished, or true if there
     *         is no Entry with the given ID.
     */
    boolean isTabStateReady(int tabId);

    /**
     * Returns the tab state for the given file, loading it from disk if necessary.
     * @param tabId ID of the tab to restore.
     * @return TabState if it exists, null otherwise.
     */
    TabState getTabStateForDocument(int tabId);

    /**
     * Checks whether or not there is an Entry for the given Tab.
     * @param tabId ID of the tab to check for.
     * @return Whether or not the Entry exists.
     */
    boolean hasEntryForTabId(int tabId);

    /**
     * Check if a tab/task may be retargeted by an Intent.
     * @param tabId ID of the tab.
     * @return Whether or not the given tab ID may be retargeted.
     */
    boolean isRetargetable(int tabId);

    /**
     * Closes the Tab at a particular index.
     * @param index Index of the tab to close.
     * @return Whether the was successfully closed.
     */
    @VisibleForTesting
    boolean closeTabAt(int index);

    /**
     * Compares the current list of documents from the system to the internal entry map and creates
     * historical tabs for entries that exist in the internal map and not in the system database.
     * Those entries are then removed from the internal list to ensure there will be only one
     * recently closed tab per entry.
     */
    void updateRecentlyClosed();

    /**
     * Updates an entry in the TabModel.
     * @param intent Intent of the Activity that is modifying the TabModel.
     * @param tab Tab being updated.
     */
    void updateEntry(Intent intent, Tab tab);

    /**
     * Adds the given Tab to this TabModel.
     * @param intent Intent of the DocumentActivity.
     * @param tab Tab to add.
     */
    void addTab(Intent intent, Tab tab);

    /**
     * @return The stage of initialization that the DocumentTabModel is currently going through.
     */
    int getCurrentInitializationStage();

    /**
     * Adds an InitializationObserver to the DocumentTabModel.
     */
    void addInitializationObserver(InitializationObserver observer);

    /**
     * Records the ID of the last shown Tab.
     * @param id ID of the last shown Tab.
     * @return Whether or not the ID had to be updated.
     */
    boolean setLastShownId(int id);

    /**
     * Called to begin loading tab state from disk. It will load the prioritized tab first
     * synchronously and then the rest of the tabs asynchronously in the background.
     */
    void startTabStateLoad();
}
