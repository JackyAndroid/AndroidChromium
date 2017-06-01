// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.support.annotation.IntDef;
import android.view.View;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.ContextMenuItemId;
import org.chromium.chrome.browser.ntp.ContextMenuManager.Delegate;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.UiConfig;
import org.chromium.chrome.browser.ntp.snippets.SnippetsConfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Item that allows the user to perform an action on the NTP.
 * Note: Use {@link #refreshVisibility()} to update the visibility of the button instead of calling
 * {@link #setVisible(boolean)} directly.
 */
class ActionItem extends OptionalLeaf {
    @IntDef({ACTION_NONE, ACTION_VIEW_ALL, ACTION_FETCH_MORE, ACTION_RELOAD})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Action {}
    public static final int ACTION_NONE = 0;
    public static final int ACTION_VIEW_ALL = 1;
    public static final int ACTION_FETCH_MORE = 2;
    public static final int ACTION_RELOAD = 3;

    private final SuggestionsCategoryInfo mCategoryInfo;
    private final SuggestionsSection mParentSection;

    @Action
    private int mCurrentAction = ACTION_NONE;
    private boolean mImpressionTracked;

    public ActionItem(SuggestionsSection section) {
        super(section);
        mCategoryInfo = section.getCategoryInfo();
        mParentSection = section;
    }

    /** Call this instead of {@link #setVisible(boolean)} to update the visibility. */
    public void refreshVisibility() {
        mCurrentAction = findAppropriateAction();
        setVisible(mCurrentAction != ACTION_NONE);
    }

    @Override
    public int getItemViewType() {
        return ItemViewType.ACTION;
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof ViewHolder;
        ((ViewHolder) holder).onBindViewHolder(this);
    }

    private int getPosition() {
        // TODO(dgn): looks dodgy. Confirm that's what we want.
        return mParentSection.getSuggestionsCount();
    }

    @VisibleForTesting
    void performAction(NewTabPageManager manager, NewTabPageAdapter adapter) {
        manager.trackSnippetCategoryActionClick(mCategoryInfo.getCategory(), getPosition());

        switch (mCurrentAction) {
            case ACTION_VIEW_ALL:
                mCategoryInfo.performViewAllAction(manager);
                return;
            case ACTION_FETCH_MORE:
                manager.getSuggestionsSource().fetchSuggestions(
                        mCategoryInfo.getCategory(), mParentSection.getDisplayedSuggestionIds());
                mParentSection.onFetchMore();
                return;
            case ACTION_RELOAD:
                // TODO(dgn): reload only the current section. https://crbug.com/634892
                adapter.reloadSnippets();
                return;
            case ACTION_NONE:
            default:
                // Should never be reached.
                assert false;
        }
    }

    @Action
    private int findAppropriateAction() {
        boolean hasSuggestions = mParentSection.hasSuggestions();
        if (mCategoryInfo.hasViewAllAction()) return ACTION_VIEW_ALL;
        if (hasSuggestions && mCategoryInfo.hasFetchMoreAction()) return ACTION_FETCH_MORE;
        if (!hasSuggestions && mCategoryInfo.hasReloadAction()) return ACTION_RELOAD;
        return ACTION_NONE;
    }

    public static class ViewHolder extends CardViewHolder implements ContextMenuManager.Delegate {
        private ActionItem mActionListItem;

        public ViewHolder(final NewTabPageRecyclerView recyclerView,
                final NewTabPageManager manager, UiConfig uiConfig) {
            super(R.layout.new_tab_page_action_card, recyclerView, uiConfig, manager);

            itemView.findViewById(R.id.action_button)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mActionListItem.performAction(
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
                                mActionListItem.getPosition());
                    }
                }
            });
        }

        @Override
        public boolean isDismissable() {
            return SnippetsConfig.isSectionDismissalEnabled()
                    && !mActionListItem.mParentSection.hasSuggestions();
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

        public void onBindViewHolder(ActionItem item) {
            super.onBindViewHolder();
            mActionListItem = item;
        }
    }
}
