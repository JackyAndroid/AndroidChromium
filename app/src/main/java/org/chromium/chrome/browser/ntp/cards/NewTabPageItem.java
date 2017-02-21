// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.support.annotation.IntDef;
import android.support.v7.widget.RecyclerView.Adapter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Base type for anything to add to the new tab page */
public interface NewTabPageItem {
    /**
     * View type values for the items that will be held by the NTP's RecyclerView.
     * @see Adapter#getItemViewType(int)
     * @see NewTabPageItem#getType()
     */
    @IntDef({VIEW_TYPE_ABOVE_THE_FOLD, VIEW_TYPE_HEADER, VIEW_TYPE_SNIPPET, VIEW_TYPE_SPACING,
            VIEW_TYPE_STATUS, VIEW_TYPE_PROGRESS, VIEW_TYPE_ACTION, VIEW_TYPE_PROMO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {}

    /**
     * View type for the above the fold item
     * @see Adapter#getItemViewType(int)
     */
    public static final int VIEW_TYPE_ABOVE_THE_FOLD = 1;

    /**
     * View type for card group headers
     * @see Adapter#getItemViewType(int)
     */
    public static final int VIEW_TYPE_HEADER = 2;

    /**
     * View type for snippet cards
     * @see Adapter#getItemViewType(int)
     */
    public static final int VIEW_TYPE_SNIPPET = 3;

    /**
      * View type for a {@link SpacingItem} used to provide spacing at the end of the list.
      * @see Adapter#getItemViewType(int)
      */
    public static final int VIEW_TYPE_SPACING = 4;

    /**
     * View type for a {@link StatusItem}, the card displaying status information
     * @see Adapter#getItemViewType(int)
     */
    public static final int VIEW_TYPE_STATUS = 5;

    /**
     * View type for a {@link ProgressItem}, the progress indicator.
     * @see Adapter#getItemViewType(int)
     */
    public static final int VIEW_TYPE_PROGRESS = 6;

    /**
     * View type for a {@link ActionItem}, an action button.
     * @see Adapter#getItemViewType(int)
     */
    public static final int VIEW_TYPE_ACTION = 7;

    /**
     * View type for a {@link Footer}.
     * @see Adapter#getItemViewType(int)
     */
    public static final int VIEW_TYPE_FOOTER = 8;

    /**
     * View type for a {@link SigninPromoItem}.
     * @see Adapter#getItemViewType(int)
     */
    public static final int VIEW_TYPE_PROMO = 9;

    /**
      * Returns the type ({@link ViewType}) of this list item. This is so we can
      * distinguish between different elements that are held in a single RecyclerView holder.
      *
      * @return the type of this list item.
      */
    @ViewType
    public int getType();

    /**
     * Update the given {@link NewTabPageViewHolder} with data from this item.
     * @param holder The {@link NewTabPageViewHolder} to update.
     */
    void onBindViewHolder(NewTabPageViewHolder holder);
}