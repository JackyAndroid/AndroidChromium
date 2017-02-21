// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.UiConfig;

/**
 * Item that allows the user to perform an action on the NTP.
 */
class ActionItem implements NewTabPageItem {
    private static final String TAG = "NtpCards";

    private final SuggestionsCategoryInfo mCategoryInfo;

    // The position (index) of this item within its section, for logging purposes.
    private int mPosition;
    private boolean mImpressionTracked = false;
    private boolean mDismissable;

    public ActionItem(SuggestionsCategoryInfo categoryInfo) {
        mCategoryInfo = categoryInfo;
    }

    @Override
    public int getType() {
        return NewTabPageItem.VIEW_TYPE_ACTION;
    }

    public int getPosition() {
        return mPosition;
    }

    public void setPosition(int position) {
        mPosition = position;
    }

    public static class ViewHolder extends CardViewHolder {
        private ActionItem mActionListItem;

        public ViewHolder(final NewTabPageRecyclerView recyclerView,
                final NewTabPageManager manager, UiConfig uiConfig) {
            super(R.layout.new_tab_page_action_card, recyclerView, uiConfig);

            itemView.findViewById(R.id.action_button)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int category = mActionListItem.mCategoryInfo.getCategory();
                            manager.trackSnippetCategoryActionClick(
                                    category, mActionListItem.mPosition);
                            mActionListItem.mCategoryInfo.performEmptyStateAction(
                                    manager, recyclerView.getNewTabPageAdapter());
                        }
                    });

            new ImpressionTracker(itemView, new ImpressionTracker.Listener() {
                @Override
                public void onImpression() {
                    if (mActionListItem != null && !mActionListItem.mImpressionTracked) {
                        mActionListItem.mImpressionTracked = true;
                        manager.trackSnippetCategoryActionImpression(
                                mActionListItem.mCategoryInfo.getCategory(),
                                mActionListItem.mPosition);
                    }
                }
            });
        }

        @Override
        public boolean isDismissable() {
            return false;
        }

        public void onBindViewHolder(ActionItem item) {
            mActionListItem = item;
        }
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof ViewHolder;
        ((ViewHolder) holder).onBindViewHolder(this);
    }

    /** Set whether this item can be dismissed.*/
    public void setDismissable(boolean dismissable) {
        this.mDismissable = dismissable;
    }
}
