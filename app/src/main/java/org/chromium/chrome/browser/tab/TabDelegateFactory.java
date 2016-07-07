// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.banners.AppBannerManager;
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
     * @param activity The {@link ChromeActivity} that the tab belongs to.
     * @return The {@link WebContentsDelegateAndroid} to be used for this tab.
     */
    public TabWebContentsDelegateAndroid createWebContentsDelegate(
            Tab tab, ChromeActivity activity) {
        return new TabWebContentsDelegateAndroid(tab, activity);
    }

    /**
     * Creates the {@link InterceptNavigationDelegate} the tab will be initialized with.
     * @param tab The associated {@link Tab}.
     * @param activity The {@link ChromeActivity} that the tab belongs to.
     * @return The {@link InterceptNavigationDelegate} to be used for this tab.
     */
    public InterceptNavigationDelegateImpl createInterceptNavigationDelegate(
            Tab tab, ChromeActivity activity) {
        return new InterceptNavigationDelegateImpl(activity, tab);
    }

    /**
     * Creates the {@link ContextMenuPopulator} the tab will be initialized with.
     * @param tab The associated {@link Tab}.
     * @param activity The {@link ChromeActivity} that the tab belongs to.
     * @return The {@link ContextMenuPopulator} to be used for this tab.
     */
    public ContextMenuPopulator createContextMenuPopulator(Tab tab, ChromeActivity activity) {
        return new ChromeContextMenuPopulator(
                new TabContextMenuItemDelegate(tab, activity),
                ChromeContextMenuPopulator.NORMAL_MODE);
    }

    /**
     * Creates the {@link AppBannerManager} the tab will be initialized with.
     * @param tab The associated {@link Tab}.
     * @return {@link AppBannerManager} to be used for the given tab. May be null.
     */
    public AppBannerManager createAppBannerManager(Tab tab) {
        return new AppBannerManager(tab, tab.getApplicationContext());
    }
}
