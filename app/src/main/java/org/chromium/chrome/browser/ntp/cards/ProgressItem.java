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
class ProgressItem implements NewTabPageItem {
    private boolean mVisible = false;

    @Override
    public int getType() {
        return NewTabPageItem.VIEW_TYPE_PROGRESS;
    }

    public boolean isVisible() {
        return mVisible;
    }

    public void setVisible(boolean visible) {
        mVisible = visible;
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof ProgressViewHolder;
        ((ProgressViewHolder) holder).onBindViewHolder(this);
    }
}
