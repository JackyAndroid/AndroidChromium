// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.Context;
import android.view.ContextMenu;

import org.chromium.base.ObserverList.RewindableIterator;
import org.chromium.chrome.browser.contextmenu.ContextMenuHelper;
import org.chromium.chrome.browser.contextmenu.ContextMenuParams;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulator;

/**
 * A simple wrapper around a {@link ContextMenuPopulator} to handle observer notification.
 */
public class TabContextMenuPopulator implements ContextMenuPopulator {
    private final ContextMenuPopulator mPopulator;
    private final Tab mTab;

    /**
     * Constructs an instance of a {@link ContextMenuPopulator} and delegate calls to
     * {@code populator}.
     * @param populator The {@link ContextMenuPopulator} to delegate calls to.
     * @param tab The {@link Tab} that is using this context menu.
     */
    public TabContextMenuPopulator(ContextMenuPopulator populator, Tab tab) {
        mPopulator = populator;
        mTab = tab;
    }

    @Override
    public boolean shouldShowContextMenu(ContextMenuParams params) {
        return mPopulator.shouldShowContextMenu(params);
    }

    @Override
    public void buildContextMenu(ContextMenu menu, Context context, ContextMenuParams params) {
        mPopulator.buildContextMenu(menu, context, params);
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onContextMenuShown(mTab, menu);
        }
    }

    @Override
    public boolean onItemSelected(ContextMenuHelper helper, ContextMenuParams params, int itemId) {
        return mPopulator.onItemSelected(helper, params, itemId);
    }
}