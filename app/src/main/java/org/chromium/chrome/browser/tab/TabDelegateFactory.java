// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import org.chromium.chrome.browser.contextmenu.ChromeContextMenuPopulator;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulator;
import org.chromium.components.navigation_interception.InterceptNavigationDelegate;
import org.chromium.components.web_contents_delegate_android.WebContentsDelegateAndroid;

/**
 * A factory class to create {@link Tab} related delegates.
 */
public class TabDelegateFactory {
    /**
     * Creates the {@link WebContentsDelegateAndroid} the tab will be initialized with.
     * @param tab The associated {@link Tab}.
     * @return The {@link WebContentsDelegateAndroid} to be used for this tab.
     */
    public TabWebContentsDelegateAndroid createWebContentsDelegate(Tab tab) {
        return new TabWebContentsDelegateAndroid(tab);
    }

    /**
     * Creates the {@link InterceptNavigationDelegate} the tab will be initialized with.
     * @param tab The associated {@link Tab}.
     * @return The {@link InterceptNavigationDelegate} to be used for this tab.
     */
    public InterceptNavigationDelegateImpl createInterceptNavigationDelegate(Tab tab) {
        return new InterceptNavigationDelegateImpl(tab);
    }

    /**
     * Creates the {@link ContextMenuPopulator} the tab will be initialized with.
     * @param tab The associated {@link Tab}.
     * @return The {@link ContextMenuPopulator} to be used for this tab.
     */
    public ContextMenuPopulator createContextMenuPopulator(Tab tab) {
        return new ChromeContextMenuPopulator(new TabContextMenuItemDelegate(tab),
                ChromeContextMenuPopulator.NORMAL_MODE);
    }

    /**
     * Return true if app banners are to be permitted in this tab.
     * @param tab The associated {@link Tab}.
     * @return true if app banners are permitted, and false otherwise.
     */
    public boolean canShowAppBanners(Tab tab) {
        return true;
    }

    /**
     * Creates the {@link BrowserControlsVisibilityDelegate} the tab will be initialized with.
     * @param tab The associated {@link Tab}.
     * @return {@link BrowserControlsVisibilityDelegate} to be used for the given tab.
     */
    public BrowserControlsVisibilityDelegate createBrowserControlsVisibilityDelegate(Tab tab) {
        return new TabStateBrowserControlsVisibilityDelegate(tab);
    }

    public TabDelegateFactory createNewTabDelegateFactory() {
        return new TabDelegateFactory();
    }
}
