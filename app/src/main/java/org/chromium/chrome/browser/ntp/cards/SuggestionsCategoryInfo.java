// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.support.annotation.StringRes;

import org.chromium.base.Log;
import org.chromium.chrome.R;
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
     * Whether the category supports a "More" button. The button either triggers
     * a fixed action (like opening a native page) or, if there is no such fixed
     * action, it queries the provider for more suggestions.
     */
    private final boolean mHasMoreButton;

    /** Whether this category should be shown if it offers no suggestions. */
    private final boolean mShowIfEmpty;

    public SuggestionsCategoryInfo(@CategoryInt int category, String title,
            @ContentSuggestionsCardLayoutEnum int cardLayout, boolean hasMoreButton,
            boolean showIfEmpty) {
        mCategory = category;
        mTitle = title;
        mCardLayout = cardLayout;
        mHasMoreButton = hasMoreButton;
        mShowIfEmpty = showIfEmpty;
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

    public boolean hasMoreButton() {
        return mHasMoreButton;
    }

    public boolean showIfEmpty() {
        return mShowIfEmpty;
    }

    /**
     * Performs the appropriate action for the provided category, for the case where there are no
     * suggestions available. In general, this consists in navigating to the view showing all the
     * content, or fetching new content.
     */
    public void performEmptyStateAction(NewTabPageManager manager, NewTabPageAdapter adapter) {
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
                Log.wtf(TAG, "'Empty State' action called for unsupported category: %d", mCategory);
                break;
            case KnownCategories.ARTICLES:
            default:
                // TODO(dgn): For now, we assume any unknown sections are remote sections and just
                // reload all remote sections. crbug.com/656008
                adapter.reloadSnippets();
                break;
        }
    }

    /**
     * Returns the string to use as description for the status card that is displayed when there
     * are no suggestions available for the provided category.
     */
    @StringRes
    public int getNoSuggestionDescription() {
        switch (mCategory) {
            case KnownCategories.BOOKMARKS:
                return R.string.ntp_status_card_no_bookmarks;
            case KnownCategories.DOWNLOADS:
            case KnownCategories.FOREIGN_TABS:
            case KnownCategories.PHYSICAL_WEB_PAGES:
            case KnownCategories.RECENT_TABS:
                Log.wtf(TAG, "Requested description for unsupported category: %d", mCategory);
                return 0;
            case KnownCategories.ARTICLES:
            default:
                // TODO(dgn): For now, we assume any unknown sections are remote sections and just
                // reuse the string for ARTICLES. crbug.com/656008
                return R.string.ntp_status_card_no_articles;
        }
    }
}
