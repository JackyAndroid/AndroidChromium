// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ntp.snippets.CategoryInt;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus.CategoryStatusEnum;
import org.chromium.chrome.browser.ntp.snippets.SectionHeader;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A group of suggestions, with a header, a status card, and a progress indicator.
 */
public class SuggestionsSection implements ItemGroup {
    private final List<SnippetArticle> mSuggestions = new ArrayList<>();
    private final SectionHeader mHeader;
    private final StatusItem mStatus;
    private final ProgressItem mProgressIndicator = new ProgressItem();
    private final ActionItem mMoreButton;
    private final Observer mObserver;
    private final SuggestionsCategoryInfo mCategoryInfo;

    public SuggestionsSection(SuggestionsCategoryInfo info, Observer observer) {
        mHeader = new SectionHeader(info.getTitle());
        mCategoryInfo = info;
        mObserver = observer;
        mMoreButton = new ActionItem(info);
        mStatus = StatusItem.createNoSuggestionsItem(info);
    }

    @Override
    public List<NewTabPageItem> getItems() {
        // Note: Keep this coherent with the various notify** calls on ItemGroup.Observer
        List<NewTabPageItem> items = new ArrayList<>();
        items.add(mHeader);
        items.addAll(mSuggestions);

        if (mSuggestions.isEmpty()) items.add(mStatus);
        if (mCategoryInfo.hasMoreButton() || mSuggestions.isEmpty()) items.add(mMoreButton);
        if (mSuggestions.isEmpty()) items.add(mProgressIndicator);

        return Collections.unmodifiableList(items);
    }

    public void removeSuggestion(SnippetArticle suggestion) {
        int removedIndex = mSuggestions.indexOf(suggestion);
        if (removedIndex == -1) return;

        mSuggestions.remove(removedIndex);
        if (mMoreButton != null) mMoreButton.setDismissable(!hasSuggestions());

        // Note: Keep this coherent with getItems()
        int globalRemovedIndex = removedIndex + 1; // Header has index 0 in the section.
        mObserver.notifyItemRemoved(this, globalRemovedIndex);

        // If we still have some suggestions, we are done. Otherwise, we'll have to notify about the
        // status-related items that are now present.
        if (hasSuggestions()) return;
        mObserver.notifyItemInserted(this, globalRemovedIndex); // Status card.
        if (!mCategoryInfo.hasMoreButton()) {
            mObserver.notifyItemInserted(this, globalRemovedIndex + 1); // Action card.
        }
        mObserver.notifyItemInserted(this, globalRemovedIndex + 2); // Progress indicator.
    }

    public void removeSuggestionById(String idWithinCategory) {
        for (SnippetArticle suggestion : mSuggestions) {
            if (suggestion.mIdWithinCategory.equals(idWithinCategory)) {
                removeSuggestion(suggestion);
                return;
            }
        }
    }

    public boolean hasSuggestions() {
        return !mSuggestions.isEmpty();
    }

    public int getSuggestionsCount() {
        return mSuggestions.size();
    }

    public void setSuggestions(List<SnippetArticle> suggestions, @CategoryStatusEnum int status) {
        copyThumbnails(suggestions);

        int itemCountBefore = getItems().size();
        setStatusInternal(status);

        mSuggestions.clear();
        mSuggestions.addAll(suggestions);

        if (mMoreButton != null) {
            mMoreButton.setPosition(mSuggestions.size());
            mMoreButton.setDismissable(mSuggestions.isEmpty());
        }
        mObserver.notifyGroupChanged(this, itemCountBefore, getItems().size());
    }

    /** Sets the status for the section. Some statuses can cause the suggestions to be cleared. */
    public void setStatus(@CategoryStatusEnum int status) {
        int itemCountBefore = getItems().size();
        setStatusInternal(status);
        mObserver.notifyGroupChanged(this, itemCountBefore, getItems().size());
    }

    private void setStatusInternal(@CategoryStatusEnum int status) {
        if (!SnippetsBridge.isCategoryStatusAvailable(status)) mSuggestions.clear();

        mProgressIndicator.setVisible(SnippetsBridge.isCategoryLoading(status));
    }

    @CategoryInt
    public int getCategory() {
        return mCategoryInfo.getCategory();
    }

    private void copyThumbnails(List<SnippetArticle> suggestions) {
        for (SnippetArticle suggestion : suggestions) {
            int index = mSuggestions.indexOf(suggestion);
            if (index == -1) continue;

            suggestion.setThumbnailBitmap(mSuggestions.get(index).getThumbnailBitmap());
        }
    }

    /**
     * The dismiss sibling is an item that should be dismissed at the same time as the provided
     * one. For example, if we want to dismiss a status card that has a More button attached, the
     * button is the card's dismiss sibling. This function return the adapter position delta to
     * apply to get to the sibling from the provided item. For the previous example, it would return
     * {@code +1}, as the button comes right after the status card.
     *
     * @return a position delta to apply to the position of the provided item to get the adapter
     * position of the item to animate. Returns {@code 0} if there is no dismiss sibling.
     */
    public int getDismissSiblingPosDelta(NewTabPageItem item) {
        // The only dismiss siblings we have so far are the More button and the status card.
        // Exit early if there is no More button.
        if (mMoreButton == null) return 0;

        // When there are suggestions we won't have contiguous status and action items.
        if (hasSuggestions()) return 0;

        // The sibling of the more button is the status card, that should be right above.
        if (item == mMoreButton) return -1;

        // The sibling of the status card is the more button when it exists, should be right below.
        if (item == mStatus) return 1;

        return 0;
    }

    @VisibleForTesting
    ActionItem getActionItem() {
        return mMoreButton;
    }

    @VisibleForTesting
    StatusItem getStatusItem() {
        return mStatus;
    }
}
