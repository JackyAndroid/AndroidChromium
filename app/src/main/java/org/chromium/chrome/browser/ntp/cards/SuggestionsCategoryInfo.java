// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import org.chromium.base.Log;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.snippets.CategoryInt;
import org.chromium.chrome.browser.ntp.snippets.ContentSuggestionsCardLayout.ContentSuggestionsCardLayoutEnum;

import org.chromium.chrome.browser.ntp.snippets.KnownCategories;

/**
 * Contains meta information about a Category. Equivalent of the CategoryInfo class in
 * components/ntp_snippets/category_info.h.
 */
public class SuggestionsCategoryInfo {
    private static final String TAG = "NtpCards";

    /**
     * Id of the category.
     */
    @CategoryInt
    private final int mCategory;

    /**
     * Localized title of the category.
     */
    private final String mTitle;

    /**
     * Layout of the cards to be used to display suggestions in this category.
     */
    @ContentSuggestionsCardLayoutEnum
    private final int mCardLayout;

    /**
     * Whether the category supports a "More" action, that triggers fetching more suggestions for
     * the category, while keeping the current ones.
     * @see ActionItem
     */
    private final boolean mHasFetchMoreAction;

    /**
     * Whether the category supports a "Reload" action, that triggers fetching new suggestions to
     * replace the current ones.
     * @see ActionItem
     */
    private final boolean mHasReloadAction;

    /**
     * Whether the category supports a "ViewAll" action, that triggers displaying all the content
     * related to the current categories.
     * @see ActionItem
     * @see #performViewAllAction(NewTabPageManager)
     */
    private final boolean mHasViewAllAction;

    /** Whether this category should be shown if it offers no suggestions. */
    private final boolean mShowIfEmpty;

    /**
     * Description text to use on the status card when there are no suggestions in this category.
     */
    private final String mNoSuggestionsMessage;

    public SuggestionsCategoryInfo(@CategoryInt int category, String title,
            @ContentSuggestionsCardLayoutEnum int cardLayout, boolean hasMoreAction,
            boolean hasReloadAction, boolean hasViewAllAction, boolean showIfEmpty,
            String noSuggestionsMessage) {
        mCategory = category;
        mTitle = title;
        mCardLayout = cardLayout;
        mHasFetchMoreAction = hasMoreAction;
        mHasReloadAction = hasReloadAction;
        mHasViewAllAction = hasViewAllAction;
        mShowIfEmpty = showIfEmpty;
        mNoSuggestionsMessage = noSuggestionsMessage;
    }

    public String getTitle() {
        return mTitle;
    }

    @CategoryInt
    public int getCategory() {
        return mCategory;
    }

    @ContentSuggestionsCardLayoutEnum
    public int getCardLayout() {
        return mCardLayout;
    }

    public boolean hasFetchMoreAction() {
        return mHasFetchMoreAction;
    }

    public boolean hasReloadAction() {
        return mHasReloadAction;
    }

    public boolean hasViewAllAction() {
        return mHasViewAllAction;
    }

    public boolean showIfEmpty() {
        return mShowIfEmpty;
    }

    /**
     * Returns the string to use as description for the status card that is displayed when there
     * are no suggestions available for the provided category.
     */
    public String getNoSuggestionsMessage() {
        return mNoSuggestionsMessage;
    }

    /**
     * Performs the View All action for the provided category, navigating navigating to the view
     * showing all the content.
     */
    public void performViewAllAction(NewTabPageManager manager) {
        switch (mCategory) {
            case KnownCategories.BOOKMARKS:
                manager.navigateToBookmarks();
                break;
            case KnownCategories.DOWNLOADS:
                manager.navigateToDownloadManager();
                break;
            case KnownCategories.FOREIGN_TABS:
                manager.navigateToRecentTabs();
                break;
            case KnownCategories.PHYSICAL_WEB_PAGES:
            case KnownCategories.RECENT_TABS:
            case KnownCategories.ARTICLES:
            default:
                Log.wtf(TAG, "'Empty State' action called for unsupported category: %d", mCategory);
                break;
        }
    }
}
