// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import android.content.Context;
import android.view.ContextMenu;

/**
 * A delegate responsible for populating context menus and processing results from
 * {@link ContextMenuHelper}.
 */
public interface ContextMenuPopulator {
    /**
     * Determines whether or not a context menu should be shown for {@code params}.
     * @param params The {@link ContextMenuParams} that represent what should be shown in the
     *               context menu.
     * @return       Whether or not a context menu should be shown.
     */
    public boolean shouldShowContextMenu(ContextMenuParams params);

    /**
     * Should be used to populate {@code menu} with the correct context menu items.
     * @param menu    The menu to populate.
     * @param context A {@link Context} instance.
     * @param params  The parameters that represent what should be shown in the context menu.
     */
    public void buildContextMenu(ContextMenu menu, Context context, ContextMenuParams params);

    /**
     * Called when a context menu item has been selected.
     * @param helper The {@link ContextMenuHelper} driving the menu operations.
     * @param params The parameters that represent what is being shown in the context menu.
     * @param itemId The id of the selected menu item.
     * @return       Whether or not the selection was handled.
     */
    public boolean onItemSelected(ContextMenuHelper helper, ContextMenuParams params, int itemId);
}