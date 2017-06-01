// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;

/**
 * A tree interface to allow the New Tab Page RecyclerView to delegate to other components.
 */
interface TreeNode {
    /**
     * @return The number of items under this subtree.
     * @see android.support.v7.widget.RecyclerView.Adapter#getItemCount()
     */
    int getItemCount();

    /**
     * @param position The position to query
     * @return The view type of the item at {@code position} under this subtree.
     * @see android.support.v7.widget.RecyclerView.Adapter#getItemViewType
     */
    @ItemViewType
    int getItemViewType(int position);

    /**
     * Display the data at {@code position} under this subtree.
     * @param holder The view holder that should be updated.
     * @param position The position of the item under this subtree.
     * @see android.support.v7.widget.RecyclerView.Adapter#onBindViewHolder
     */
    void onBindViewHolder(NewTabPageViewHolder holder, final int position);

    /**
     * @param position The position to query.
     * @return The article at {@code position} under this subtree, or null if the item is not
     * an article.
     */
    SnippetArticle getSuggestionAt(int position);

    /**
     * The dismiss sibling is an item that should be dismissed at the same time as the provided
     * one. For example, if we want to dismiss a status card that has a More button attached, the
     * button is the card's dismiss sibling. This function returns the adapter position delta to
     * apply to get to the sibling from the provided item. For the previous example, it would return
     * {@code +1}, as the button comes right after the status card.
     *
     * @return a position delta to apply to the position of the provided item to get the adapter
     * position of the item to animate. Returns {@code 0} if there is no dismiss sibling.
     */
    int getDismissSiblingPosDelta(int position);
}
