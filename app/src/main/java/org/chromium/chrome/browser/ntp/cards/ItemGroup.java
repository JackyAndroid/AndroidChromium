// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import java.util.List;

/**
 * A group of items.
 */
public interface ItemGroup {
    /**
     * @return A list of items contained in this group. The list should not be modified.
     */
    List<NewTabPageItem> getItems();

    /**
     * Defines the actions an object can be notified about when there are changes inside of
     * an {@link ItemGroup}.
     */
    interface Observer {
        /** Non specific notification about changes inside of the group. */
        void notifyGroupChanged(ItemGroup group, int itemCountBefore, int itemCountAfter);

        /** Notification about an item having been added to the group. */
        void notifyItemInserted(ItemGroup group, int itemPosition);

        /** Notification about an item having been removed from the group. */
        void notifyItemRemoved(ItemGroup group, int itemPosition);
    }
}
