// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

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
     * Returns the initial URL for the Document with the given ID.
     * @param tabId The ID for the document to return the url for.
     * @return The initial URL for the entry if it was found, null otherwise.
     */
    String getInitialUrlForDocument(int tabId);
}
