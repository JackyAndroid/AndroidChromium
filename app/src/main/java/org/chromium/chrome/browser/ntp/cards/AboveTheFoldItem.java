// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

/**
 * Item in the RecyclerView that will hold the above the fold contents for the NTP, i.e. the
 * logo, tiles and search bar that are initially visible when opening the NTP.
 *
 * When using the new NTP UI, that is based on a RecyclerView, the view containing the entire
 * content of the old UI is put in the AboveTheFoldItem, and inserted in the RecyclerView.
 * Other elements coming after it and initially off-screen are just added to the RecyclerView after
 * that.
 */
class AboveTheFoldItem extends Leaf {
    @Override
    @ItemViewType
    protected int getItemViewType() {
        return ItemViewType.ABOVE_THE_FOLD;
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        // Nothing to do.
    }
}
