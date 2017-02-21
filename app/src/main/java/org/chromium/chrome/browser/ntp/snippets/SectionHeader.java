// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import org.chromium.chrome.browser.ntp.cards.NewTabPageItem;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;

/**
 * Represents the data for a header of a group of snippets
 */
public class SectionHeader implements NewTabPageItem {
    /** Whether the header should be shown. */
    private final boolean mVisible;

    /** The header text to be shown. */
    private final String mHeaderText;

    public SectionHeader(String headerText) {
        // TODO(mvanouwerkerk): Configure mVisible in the constructor when we have a global status
        // section without a visible header.
        mVisible = true;

        this.mHeaderText = headerText;
    }

    @Override
    public int getType() {
        return NewTabPageItem.VIEW_TYPE_HEADER;
    }

    public boolean isVisible() {
        return mVisible;
    }

    public String getHeaderText() {
        return mHeaderText;
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof SectionHeaderViewHolder;
        ((SectionHeaderViewHolder) holder).onBindViewHolder(this);
    }
}