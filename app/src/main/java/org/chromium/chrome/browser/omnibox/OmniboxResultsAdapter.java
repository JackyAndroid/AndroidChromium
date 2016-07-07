// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.chromium.base.VisibleForTesting;

import java.util.List;

/**
 * Adapter for providing data and views to the omnibox results list.
 */
@VisibleForTesting
public class OmniboxResultsAdapter extends BaseAdapter {

    private final List<OmniboxResultItem> mSuggestionItems;
    private final Context mContext;
    private final LocationBar mLocationBar;
    private OmniboxSuggestionDelegate mSuggestionDelegate;
    private boolean mUseDarkColors = true;

    public OmniboxResultsAdapter(
            Context context,
            LocationBar locationBar,
            List<OmniboxResultItem> suggestionItems) {
        mContext = context;
        mLocationBar = locationBar;
        mSuggestionItems = suggestionItems;
    }

    public void notifySuggestionsChanged() {
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mSuggestionItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mSuggestionItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SuggestionView suggestionView;
        if (convertView instanceof SuggestionView) {
            suggestionView = (SuggestionView) convertView;
        } else {
            suggestionView = new SuggestionView(mContext, mLocationBar);
        }
        suggestionView.init(
                mSuggestionItems.get(position), mSuggestionDelegate, position, mUseDarkColors);
        return suggestionView;
    }

    /**
     * Set the selection delegate for suggestion entries in the adapter.
     *
     * @param delegate The delegate for suggestion selections.
     */
    public void setSuggestionDelegate(OmniboxSuggestionDelegate delegate) {
        mSuggestionDelegate = delegate;
    }

    /**
     * @return The selection delegate for suggestion entries in the adapter.
     */
    @VisibleForTesting
    public OmniboxSuggestionDelegate getSuggestionDelegate() {
        return mSuggestionDelegate;
    }

    /**
     * Specifies the visual state to be used by the suggestions.
     * @param useDarkColors Whether dark colors should be used for fonts and icons.
     */
    public void setUseDarkColors(boolean useDarkColors) {
        mUseDarkColors = useDarkColors;
    }

    /**
     * Handler for actions that happen on suggestion view.
     */
    @VisibleForTesting
    public static interface OmniboxSuggestionDelegate {
        /**
         * Triggered when the user selects one of the omnibox suggestions to navigate to.
         * @param suggestion The OmniboxSuggestion which was selected.
         * @param position Position of the suggestion in the drop down view.
         */
        public void onSelection(OmniboxSuggestion suggestion, int position);

        /**
         * Triggered when the user selects to refine one of the omnibox suggestions.
         * @param suggestion
         */
        public void onRefineSuggestion(OmniboxSuggestion suggestion);

        /**
         * Triggered when the user navigates to one of the suggestions without clicking on it.
         * @param suggestion
         */
        public void onSetUrlToSuggestion(OmniboxSuggestion suggestion);

        /**
         * Triggered before we show a modal dialog triggered through suggestions UI (e.g. the
         * delete suggestions confirmation dialog).
         */
        public void onShowModal();

        /**
         * Triggered during the modal dialog dismissal.
         */
        public void onHideModal();

        /**
         * Triggered when the user indicates they want to delete a suggestion.
         * @param position The position of the suggestion in the drop down view.
         */
        public void onDeleteSuggestion(int position);

        /**
         * Triggered when the user touches the suggestion view.
         */
        public void onGestureDown();

        /**
         * Triggered when text width information is updated.
         * These values should be used to calculate max text widths.
         * @param requiredWidth a new required width.
         * @param matchContentsWidth a new match contents width.
         */
        public void onTextWidthsUpdated(float requiredWidth, float matchContentsWidth);

        /**
         * @return max required width for the suggestion.
         */
        public float getMaxRequiredWidth();

        /**
         * @return max match contents width for the suggestion.
         */
        public float getMaxMatchContentsWidth();
    }

    /**
     * Simple wrapper around the omnibox suggestions provided in the backend and the query that
     * matched it.
     */
    @VisibleForTesting
    public static class OmniboxResultItem {
        private final OmniboxSuggestion mSuggestion;
        private final String mMatchedQuery;

        public OmniboxResultItem(OmniboxSuggestion suggestion, String matchedQuery) {
            mSuggestion = suggestion;
            mMatchedQuery = matchedQuery;
        }

        /**
         * @return The omnibox suggestion for this item.
         */
        public OmniboxSuggestion getSuggestion() {
            return mSuggestion;
        }

        /**
         * @return The user query that triggered this suggestion to be shown.
         */
        public String getMatchedQuery() {
            return mMatchedQuery;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof OmniboxResultItem)) {
                return false;
            }

            OmniboxResultItem item = (OmniboxResultItem) o;
            return mMatchedQuery.equals(item.mMatchedQuery) && mSuggestion.equals(item.mSuggestion);
        }

        @Override
        public int hashCode() {
            return 53 * mMatchedQuery.hashCode() ^ mSuggestion.hashCode();
        }
    }
}
