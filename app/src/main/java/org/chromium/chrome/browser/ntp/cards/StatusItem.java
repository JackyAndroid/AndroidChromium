// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.content.Context;
import android.support.annotation.StringRes;

import org.chromium.chrome.R;

/**
 * Card that is shown when the user needs to be made aware of some information about their
 * configuration that affects the NTP suggestions.
 */
public abstract class StatusItem extends OptionalLeaf implements StatusCardViewHolder.DataSource {

    protected StatusItem(NodeParent parent) {
        super(parent);
    }

    public static StatusItem createNoSuggestionsItem(SuggestionsSection parentSection) {
        return new NoSuggestionsItem(parentSection);
    }

    private static class NoSuggestionsItem extends StatusItem {
        private final String mDescription;
        public NoSuggestionsItem(SuggestionsSection parentSection) {
            super(parentSection);
            mDescription = parentSection.getCategoryInfo().getNoSuggestionsMessage();
        }

        @Override
        @StringRes
        public int getHeader() {
            return R.string.ntp_status_card_title_no_suggestions;
        }

        @Override
        public String getDescription() {
            return mDescription;
        }

        @Override
        @StringRes
        public int getActionLabel() {
            return 0;
        }

        @Override
        public void performAction(Context context) {
            assert false;
        }
    }

    @Override
    @ItemViewType
    protected int getItemViewType() {
        return ItemViewType.STATUS;
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof StatusCardViewHolder;
        ((StatusCardViewHolder) holder).onBindViewHolder(this);
    }
}
