// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.content.Context;

import org.chromium.chrome.R;

/**
 * Card that is shown when the user needs to be made aware of some information about their
 * configuration that affects the NTP suggestions.
 */
public class StatusItem implements NewTabPageItem {
    private final int mHeaderStringId;
    private final int mDescriptionStringId;
    private final int mActionStringId;

    protected StatusItem(int headerStringId, int descriptionStringId, int actionStringId) {
        mHeaderStringId = headerStringId;
        mDescriptionStringId = descriptionStringId;
        mActionStringId = actionStringId;
    }

    public static StatusItem createNoSuggestionsItem(SuggestionsCategoryInfo categoryInfo) {
        return new StatusItem(R.string.ntp_status_card_title_no_suggestions,
                categoryInfo.getNoSuggestionDescription(), 0);
    }

    protected void performAction(Context context) {}

    protected boolean hasAction() {
        return mActionStringId != 0;
    }

    @Override
    public int getType() {
        return NewTabPageItem.VIEW_TYPE_STATUS;
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof StatusCardViewHolder;
        ((StatusCardViewHolder) holder).onBindViewHolder(this);
    }

    public int getHeader() {
        return mHeaderStringId;
    }

    public int getDescription() {
        return mDescriptionStringId;
    }

    public int getActionLabel() {
        return mActionStringId;
    }
}
