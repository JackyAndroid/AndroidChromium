// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.view.LayoutInflater;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.UiConfig;
import org.chromium.chrome.browser.ntp.cards.MarginResizer;
import org.chromium.chrome.browser.ntp.cards.NewTabPageRecyclerView;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.util.MathUtils;

/**
 * View holder for the header of a section of cards.
 */
public class SectionHeaderViewHolder extends NewTabPageViewHolder {
    private static final double SCROLL_HEADER_HEIGHT_PERCENTAGE = 0.7;

    private final int mMaxSnippetHeaderHeight;

    private SectionHeader mHeaderListItem;

    public SectionHeaderViewHolder(final NewTabPageRecyclerView recyclerView, UiConfig config) {
        super(LayoutInflater.from(recyclerView.getContext())
                        .inflate(R.layout.new_tab_page_snippets_header, recyclerView, false));
        mMaxSnippetHeaderHeight = itemView.getResources().getDimensionPixelSize(
                R.dimen.snippets_article_header_height);
        MarginResizer.createWithViewAdapter(itemView, config);
    }

    public void onBindViewHolder(SectionHeader header) {
        mHeaderListItem = header;
        ((TextView) itemView).setText(mHeaderListItem.getHeaderText());
        updateDisplay(0, false);
    }

    /**
     * @return The header height we want to set.
     */
    private int getHeaderHeight(int amountScrolled, boolean canTransition) {
        if (!mHeaderListItem.isVisible()) return 0;

        // If the header cannot transition but is visible - set the height to the maximum so
        // it always displays
        if (!canTransition) return mMaxSnippetHeaderHeight;

        // Check if snippet header top is within range to start showing. Set the header height,
        // this is a percentage of how much is scrolled. The balance of the scroll will be used
        // to display the peeking card.
        return MathUtils.clamp((int) (amountScrolled * SCROLL_HEADER_HEIGHT_PERCENTAGE),
                0, mMaxSnippetHeaderHeight);
    }

    /**
     * Update the view for the fade in/out and heading height.
     * @param amountScrolled the number of pixels scrolled, or how far away from the bottom of the
     *                       screen we got.
     * @param canTransition whether we should animate the header sliding in. When {@code false},
     *                      the header will always be fully visible.
     */
    public void updateDisplay(int amountScrolled, boolean canTransition) {
        int headerHeight = getHeaderHeight(amountScrolled, canTransition);

        itemView.setAlpha((float) headerHeight / mMaxSnippetHeaderHeight);
        getParams().height = headerHeight;

        // This request layout is needed to let the rest of the elements know about the modified
        // dimensions of this one. Otherwise scrolling fast can make the peeking card go completely
        // below the fold for example.
        itemView.requestLayout();
    }
}
