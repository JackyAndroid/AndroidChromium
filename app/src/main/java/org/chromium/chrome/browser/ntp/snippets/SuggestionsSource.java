// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.graphics.Bitmap;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.ntp.cards.SuggestionsCategoryInfo;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus.CategoryStatusEnum;

import java.util.List;

/**
 * An interface for classes that provide content suggestions.
 */
public interface SuggestionsSource {
    /**
     * An observer for events in the content suggestions service.
     */
    interface Observer {
        /** Called when a category has a new list of content suggestions. */
        void onNewSuggestions(@CategoryInt int category);

        /** Called when a category changed its status. */
        void onCategoryStatusChanged(@CategoryInt int category, @CategoryStatusEnum int newStatus);

        /**
         * Called when a suggestion is invalidated, which means it needs to be removed from the UI
         * immediately. This event may be fired for a category or suggestion that does not
         * currently exist or has never existed and should be ignored in that case.
         */
        void onSuggestionInvalidated(@CategoryInt int category, String idWithinCategory);
    }

    /**
     * Gets the categories in the order in which they should be displayed.
     * @return The categories.
     */
    int[] getCategories();

    /**
     * Gets the status of a category, possibly indicating the reason why it is disabled.
     */
    @CategoryStatusEnum
    int getCategoryStatus(int category);

    /**
     * Gets the meta information of a category.
     */
    SuggestionsCategoryInfo getCategoryInfo(int category);

    /**
     * Gets the current content suggestions for a category, in the order in which they should be
     * displayed. If the status of the category is not one of the available statuses, this will
     * return an empty list.
     */
    List<SnippetArticle> getSuggestionsForCategory(int category);

    /**
     * Fetches the thumbnail image for a content suggestion. A null Bitmap is returned if no image
     * is available. The callback is never called synchronously.
     */
    void fetchSuggestionImage(SnippetArticle suggestion, Callback<Bitmap> callback);

    /**
     * Tells the source to dismiss the content suggestion.
     */
    void dismissSuggestion(SnippetArticle suggestion);

    /**
     * Tells the source to dismiss the category.
     */
    void dismissCategory(@CategoryInt int category);

    /**
     * Checks whether a content suggestion has been visited. The callback is never called
     * synchronously.
     */
    void getSuggestionVisited(SnippetArticle suggestion, Callback<Boolean> callback);

    /**
     * Sets the recipient for update events from the source.
     */
    void setObserver(Observer observer);
}