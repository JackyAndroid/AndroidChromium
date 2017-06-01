// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.content.Context;
import android.support.annotation.IntegerRes;
import android.support.annotation.StringRes;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.ContextMenuItemId;
import org.chromium.chrome.browser.ntp.ContextMenuManager.Delegate;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.UiConfig;
import org.chromium.chrome.browser.ntp.snippets.SnippetsConfig;

/**
 * ViewHolder for Status and Promo cards.
 */
public class StatusCardViewHolder extends CardViewHolder implements ContextMenuManager.Delegate {
    private final TextView mTitleView;
    private final TextView mBodyView;
    private final Button mActionView;

    public StatusCardViewHolder(
            NewTabPageRecyclerView parent, NewTabPageManager newTabPageManager, UiConfig config) {
        super(R.layout.new_tab_page_status_card, parent, config, newTabPageManager);
        mTitleView = (TextView) itemView.findViewById(R.id.status_title);
        mBodyView = (TextView) itemView.findViewById(R.id.status_body);
        mActionView = (Button) itemView.findViewById(R.id.status_action_button);
    }

    /**
     * Interface for data items that will be shown in this card.
     */
    public interface DataSource {
        /**
         * @return Resource ID for the header string.
         */
        @StringRes
        int getHeader();

        /**
         * @return Description string.
         */
        String getDescription();

        /**
         * @return Resource ID for the action label string, or 0 if the card does not have a label.
         */
        @StringRes
        int getActionLabel();

        /**
         * Called when the user clicks on the action button.
         *
         * @param context The context to execute the action in.
         */
        void performAction(Context context);
    }

    public void onBindViewHolder(final DataSource item) {
        super.onBindViewHolder();

        mTitleView.setText(item.getHeader());
        mBodyView.setText(item.getDescription());

        @IntegerRes
        int actionLabel = item.getActionLabel();
        if (actionLabel != 0) {
            mActionView.setText(actionLabel);
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
    protected Delegate getContextMenuDelegate() {
        return this;
    }

    @Override
    public void openItem(int windowDisposition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeItem() {
        getRecyclerView().dismissItemWithAnimation(this);
    }

    @Override
    public String getUrl() {
        return null;
    }

    @Override
    public boolean isItemSupported(@ContextMenuItemId int menuItemId) {
        return menuItemId == ContextMenuManager.ID_REMOVE && isDismissable();
    }

    @Override
    public boolean isDismissable() {
        return SnippetsConfig.isSectionDismissalEnabled();
    }
}
