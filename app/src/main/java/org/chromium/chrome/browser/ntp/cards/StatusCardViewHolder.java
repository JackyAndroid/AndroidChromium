// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.UiConfig;

/**
 * ViewHolder for Status and Promo cards.
 */
public class StatusCardViewHolder extends CardViewHolder {
    private final TextView mTitleView;
    private final TextView mBodyView;
    private final Button mActionView;

    public StatusCardViewHolder(NewTabPageRecyclerView parent, UiConfig config) {
        super(R.layout.new_tab_page_status_card, parent, config);
        mTitleView = (TextView) itemView.findViewById(R.id.status_title);
        mBodyView = (TextView) itemView.findViewById(R.id.status_body);
        mActionView = (Button) itemView.findViewById(R.id.status_action_button);
    }

    public void onBindViewHolder(final StatusItem item) {
        super.onBindViewHolder();

        mTitleView.setText(item.getHeader());
        mBodyView.setText(item.getDescription());

        if (item.hasAction()) {
            mActionView.setText(item.getActionLabel());
            mActionView.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    item.performAction(v.getContext());
                }
            });
            mActionView.setVisibility(View.VISIBLE);
        } else {
            mActionView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean isDismissable() {
        return false;
    }
}
