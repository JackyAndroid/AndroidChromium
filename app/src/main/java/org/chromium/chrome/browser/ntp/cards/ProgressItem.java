// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

/**
 * Represents a progress indicator for the recycler view. A visibility flag can be set to be used
 * by its associated view holder when it is bound.
 *
 * @see ProgressViewHolder
 */
class ProgressItem extends OptionalLeaf {
    protected ProgressItem(NodeParent parent) {
        super(parent);
    }

    @Override
    @ItemViewType
    protected int getItemViewType() {
        return ItemViewType.PROGRESS;
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof ProgressViewHolder;
        ((ProgressViewHolder) holder).onBindViewHolder(this);
    }
}
