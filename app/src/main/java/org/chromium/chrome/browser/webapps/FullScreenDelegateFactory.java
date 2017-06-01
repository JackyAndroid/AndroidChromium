// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import org.chromium.chrome.browser.contextmenu.ChromeContextMenuPopulator;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulator;
import org.chromium.chrome.browser.tab.BrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabContextMenuItemDelegate;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabStateBrowserControlsVisibilityDelegate;

/**
 * A {@link TabDelegateFactory} class to be used in all {@link Tab} instances owned
 * by a {@link FullScreenActivity}.
 */
public class FullScreenDelegateFactory extends TabDelegateFactory {
    @Override
    public ContextMenuPopulator createContextMenuPopulator(Tab tab) {
        return new ChromeContextMenuPopulator(new TabContextMenuItemDelegate(tab),
                ChromeContextMenuPopulator.FULLSCREEN_TAB_MODE);
    }

    @Override
    public BrowserControlsVisibilityDelegate createBrowserControlsVisibilityDelegate(Tab tab) {
        return new TabStateBrowserControlsVisibilityDelegate(tab) {
            @Override
            public boolean isHidingBrowserControlsEnabled() {
                return !isShowingBrowserControlsEnabled();
            }
        };
    }
}
