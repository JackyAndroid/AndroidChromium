// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.graphics.Bitmap;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.ntp.cards.SuggestionsCategoryInfo;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus.CategoryStatusEnum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fake Suggestions source for use in unit and instrumentation tests.
 */
public class FakeSuggestionsSource implements SuggestionsSource {
    private SuggestionsSource.Observer mObserver;
    private final List<Integer> mCategories = new ArrayList<>();
    private final Map<Integer, List<SnippetArticle>> mSuggestions = new HashMap<>();
    private final Map<Integer, Integer> mCategoryStatus = new LinkedHashMap<>();
    private final Map<Integer, SuggestionsCategoryInfo> mCategoryInfo = new HashMap<>();
    private final Map<String, Bitmap> mThumbnails = new HashMap<>();

    private final List<Integer> mDismissedCategories = new ArrayList<>();
    private final Map<Integer, List<SnippetArticle>> mDismissedCategorySuggestions =
            new HashMap<>();
    private final Map<Integer, Integer> mDismissedCategoryStatus = new LinkedHashMap<>();
    private final Map<Integer, SuggestionsCategoryInfo> mDismissedCategoryInfo = new HashMap<>();

    /**
     * Sets the status to be returned for a given category.
     */
    public void setStatusForCategory(@CategoryInt int category,
            @CategoryStatusEnum int status) {
        mCategoryStatus.put(category, status);
        if (status == CategoryStatus.NOT_PROVIDED) {
            mCategories.remove(Integer.valueOf(category));
        } else if (!mCategories.contains(category)) {
            mCategories.add(category);
        }
        if (mObserver != null) mObserver.onCategoryStatusChanged(category, status);
    }

    /**
     * Sets the suggestions to be returned for a given category.
     */
    public void setSuggestionsForCategory(
            @CategoryInt int category, List<SnippetArticle> suggestions) {
        // Copy the suggestions list so that it can't be modified anymore.
        mSuggestions.put(category, new ArrayList<>(suggestions));
        if (mObserver != null) mObserver.onNewSuggestions(category);
    }

    /**
     * Sets the metadata to be returned for a given category.
     */
    public void setInfoForCategory(@CategoryInt int category, SuggestionsCategoryInfo info) {
        mCategoryInfo.put(category, info);
    }

    /**
     * Sets the bitmap to be returned when the thumbnail is requested for a snippet with that id.
     */
    public void setThumbnailForId(String id, Bitmap bitmap) {
        mThumbnails.put(id, bitmap);
    }

    /**
     * Removes the given suggestion from the source and notifies any observer that it has been
     * invalidated.
     */
    public void fireSuggestionInvalidated(@CategoryInt int category, String idWithinCategory) {
        for (SnippetArticle suggestion : mSuggestions.get(category)) {
            if (suggestion.mIdWithinCategory.equals(idWithinCategory)) {
                mSuggestions.get(category).remove(suggestion);
                break;
            }
        }
        mObserver.onSuggestionInvalidated(category, idWithinCategory);
    }

    /**
     * Removes a category from the fake source without notifying anyone.
     */
    public void silentlyRemoveCategory(int category) {
        mSuggestions.remove(category);
        mCategoryStatus.remove(category);
        mCategoryInfo.remove(category);
        mCategories.remove(Integer.valueOf(category));
    }

    @Override
    public void dismissSuggestion(SnippetArticle suggestion) {
        for (List<SnippetArticle> suggestions : mSuggestions.values()) {
            suggestions.remove(suggestion);
        }
    }

    @Override
    public void dismissCategory(@CategoryInt int category) {
        mDismissedCategorySuggestions.put(category, mSuggestions.get(category));
        mDismissedCategoryStatus.put(category, mCategoryStatus.get(category));
        mDismissedCategoryInfo.put(category, mCategoryInfo.get(category));
        mDismissedCategories.add(category);
        silentlyRemoveCategory(category);
    }

    @Override
    public void restoreDismissedCategories() {
        for (int category : mDismissedCategories) {
            mSuggestions.put(category, mDismissedCategorySuggestions.remove(category));
            mCategoryStatus.put(category, mDismissedCategoryStatus.remove(category));
            mCategoryInfo.put(category, mDismissedCategoryInfo.remove(category));
            mCategories.add(category);
        }
        mDismissedCategories.clear();
    }

    @Override
    public void fetchSuggestionImage(SnippetArticle suggestion, Callback<Bitmap> callback) {
        if (mThumbnails.containsKey(suggestion.mIdWithinCategory)) {
            callback.onResult(mThumbnails.get(suggestion.mIdWithinCategory));
        }
    }

    @Override
    public void fetchSuggestions(@CategoryInt int category, String[] displayedSuggestionIds) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObserver(Observer observer) {
        mObserver = observer;
    }

    @Override
    public int[] getCategories() {
        int[] result = new int[mCategories.size()];
        int index = 0;
        for (int id : mCategories) result[index++] = id;
        return result;
    }

    @CategoryStatusEnum
    @Override
    public int getCategoryStatus(@CategoryInt int category) {
        return mCategoryStatus.get(category);
    }

    @Override
    public SuggestionsCategoryInfo getCategoryInfo(int category) {
        return mCategoryInfo.get(category);
    }

    @Override
    public List<SnippetArticle> getSuggestionsForCategory(int category) {
        if (!SnippetsBridge.isCategoryStatusAvailable(mCategoryStatus.get(category))) {
            return Collections.emptyList();
        }
        List<SnippetArticle> result = mSuggestions.get(category);
        return result == null ? Collections.<SnippetArticle>emptyList() : result;
    }
}