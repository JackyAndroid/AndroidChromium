// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.base.TraceEvent;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;

/**
 * An interface to return a {@link TabCreator} either for regular or incognito tabs.
 */
public interface TabCreatorManager {
    /**
     * Creates Tabs.  If the TabCreator creates Tabs asynchronously, null pointers will be returned
     * everywhere instead of a Tab.
     *
     * TODO(dfalcantara): Hunt down more places where we don't actually need to return a Tab.
     */
    public abstract class TabCreator {
        /**
         * @return Whether the TabCreator creates Tabs asynchronously.
         */
        public abstract boolean createsTabsAsynchronously();

        /**
         * Creates a new tab and posts to UI.
         * @param loadUrlParams parameters of the url load.
         * @param type Information about how the tab was launched.
         * @param parent the parent tab, if present.
         * @return The new tab.
         */
        public abstract Tab createNewTab(
                LoadUrlParams loadUrlParams, TabModel.TabLaunchType type, Tab parent);

        /**
         * On restore, allows us to create a frozen version of a tab using saved tab state we read
         * from disk.
         * @param state    The tab state that the tab can be restored from.
         * @param id       The id to give the new tab.
         * @param index    The index for where to place the tab.
         */
        public abstract Tab createFrozenTab(TabState state, int id, int index);

        /**
         * Creates a new tab and loads the specified URL in it. This is a convenience method for
         * {@link #createNewTab} with the default {@link LoadUrlParams} and no parent tab.
         *
         * @param url the URL to open.
         * @param type the type of action that triggered that launch. Determines how the tab is
         *             opened (for example, in the foreground or background).
         * @return the created tab.
         */
        public abstract Tab launchUrl(String url, TabModel.TabLaunchType type);

        /**
         * Creates a Tab to host the given WebContents.
         * @param webContents The web contents to create a tab around.
         * @param parentId    The id of the parent tab.
         * @param type        The TabLaunchType describing how this tab was created.
         * @param url         URL to show in the Tab. (Needed only for asynchronous tab creation.)
         * @return            Whether a Tab was created successfully.
         */
        public abstract boolean createTabWithWebContents(
                WebContents webContents, int parentId, TabLaunchType type, String url);

        /**
         * Creates a tab around the native web contents pointer.
         * @param webContents The web contents to create a tab around.
         * @param parentId    The id of the parent tab.
         * @param type        The TabLaunchType describing how this tab was created.
         * @return            Whether a Tab was created successfully.
         */
        public final boolean createTabWithWebContents(
                WebContents webContents, int parentId, TabLaunchType type) {
            return createTabWithWebContents(webContents, parentId, type, webContents.getUrl());
        }

        /**
         * Creates a new tab and loads the NTP.
         */
        public final void launchNTP() {
            try {
                TraceEvent.begin("TabCreator.launchNTP");
                launchUrl(UrlConstants.NTP_URL, TabModel.TabLaunchType.FROM_MENU_OR_OVERVIEW);
            } finally {
                TraceEvent.end("TabCreator.launchNTP");
            }
        }
    }

    /**
     * @return A {@link TabCreator} that will create either regular or incognito tabs.
     * @param incognito True if the method should return the TabCreator for incognito tabs, false
     *                  for regular tabs.
     */
    TabCreator getTabCreator(boolean incognito);
}
