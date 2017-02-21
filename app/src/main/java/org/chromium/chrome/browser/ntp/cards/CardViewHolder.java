// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.support.annotation.CallSuper;
import android.support.annotation.DrawableRes;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.UiConfig;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.util.ViewUtils;

/**
 * Holder for a generic card.
 *
 * Specific behaviors added to the cards:
 *
 * - Cards can peek above the fold if there is enough space.
 *
 * - When peeking, tapping on cards will make them request a scroll up (see
 *   {@link NewTabPageRecyclerView#scrollToFirstCard()}). Tap events in non-peeking state will be
 *   routed through {@link #onCardTapped()} for subclasses to override.
 *
 * - Cards will get some lateral margins when the viewport is sufficiently wide.
 *   (see {@link UiConfig#DISPLAY_STYLE_WIDE})
 *
 * Note: If a subclass overrides {@link #onBindViewHolder()}, it should call the
 * parent implementation to reset the private state when a card is recycled.
 */
public class CardViewHolder extends NewTabPageViewHolder {
    private static final Interpolator TRANSITION_INTERPOLATOR = new FastOutSlowInInterpolator();

    /** Value used for max peeking card height and padding. */
    private final int mMaxPeekPadding;

    /**
     * Due to the card background being a 9 patch file - the card border shadow will be part of
     * the card width and height. This value will be used to adjust values to account for the
     * borders.
     */
    private final int mCards9PatchAdjustment;

    private final NewTabPageRecyclerView mRecyclerView;

    private final UiConfig mUiConfig;
    private final MarginResizer mMarginResizer;

    /**
     * To what extent the card is "peeking". 0 means the card is not peeking at all and spans the
     * full width of its parent. 1 means it is fully peeking and will be shown with a margin.
     */
    private float mPeekingPercentage;

    @DrawableRes
    private int mBackground;

    /**
     * @param layoutId resource id of the layout to inflate and to use as card.
     * @param recyclerView ViewGroup that will contain the newly created view.
     * @param uiConfig The NTP UI configuration object used to adjust the card UI.
     */
    public CardViewHolder(
            int layoutId, final NewTabPageRecyclerView recyclerView, UiConfig uiConfig) {
        super(inflateView(layoutId, recyclerView));

        mCards9PatchAdjustment = recyclerView.getResources().getDimensionPixelSize(
                R.dimen.snippets_card_9_patch_adjustment);

        mMaxPeekPadding = recyclerView.getResources().getDimensionPixelSize(
                R.dimen.snippets_padding_and_peeking_card_height);

        mRecyclerView = recyclerView;

        itemView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPeeking()) {
                    recyclerView.scrollToFirstCard();
                } else {
                    onCardTapped();
                }
            }
        });

        itemView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
                if (!isPeeking()) {
                    CardViewHolder.this.createContextMenu(menu);
                }
            }
        });

        mUiConfig = uiConfig;

        mMarginResizer = MarginResizer.createWithViewAdapter(itemView, mUiConfig);

        // Configure the resizer to use negative margins on regular display to balance out the
        // lateral shadow of the card 9-patch and avoid a rounded corner effect.
        mMarginResizer.setMargins(-mCards9PatchAdjustment);
    }

    /**
     * Called when the NTP cards adapter is requested to update the currently visible ViewHolder
     * with data.
     */
    @CallSuper
    protected void onBindViewHolder() {
        // Reset the peek status to avoid recycled view holders to be peeking at the wrong moment.
        if (getAdapterPosition() != mRecyclerView.getNewTabPageAdapter().getFirstCardPosition()) {
            // Not the first card, we can't peek anyway.
            setPeekingPercentage(0f);
        } else {
            mRecyclerView.updatePeekingCard(this);
        }

        // Reset the transparency and translation in case a dismissed card is being recycled.
        itemView.setAlpha(1f);
        itemView.setTranslationX(0f);

        // Make sure we use the right background.
        updateLayoutParams();
    }

    @Override
    public void updateLayoutParams() {
        // Nothing to do for dismissed cards.
        if (getAdapterPosition() == RecyclerView.NO_POSITION) return;

        // Each card has the full elevation effect (the shadow) in the 9-patch. If the next item is
        // a card a negative bottom margin is set so the next card is overlaid slightly on top of
        // this one and hides the bottom shadow.
        boolean hasCardAbove =
                isCard(mRecyclerView.getAdapter().getItemViewType(getAdapterPosition() - 1));
        boolean hasCardBelow =
                isCard(mRecyclerView.getAdapter().getItemViewType(getAdapterPosition() + 1));

        getParams().bottomMargin = hasCardBelow ? -mCards9PatchAdjustment : 0;

        @DrawableRes
        int selectedBackground = selectBackground(hasCardAbove, hasCardBelow);
        if (mBackground != selectedBackground) {
            mBackground = selectedBackground;
            ViewUtils.setNinePatchBackgroundResource(itemView, selectedBackground);
        }
    }

    /**
     * Change the width, padding and child opacity of the card to give a smooth transition as the
     * user scrolls.
     * @param availableSpace space (pixels) available between the bottom of the screen and the
     *                       above-the-fold section, where the card can peek.
     * @param canPeek whether the screen size allows having a peeking card.
     */
    public void updatePeek(int availableSpace, boolean canPeek) {
        float peekingPercentage;

        if (!canPeek) {
            peekingPercentage = 0f;
        } else {
            // If 1 padding unit (|mMaxPeekPadding|) is visible, the card is fully peeking. This is
            // reduced as the card is scrolled up, until 2 padding units are visible and the card is
            // not peeking anymore at all. Anything not between 0 and 1 is clamped.
            peekingPercentage =
                    MathUtils.clamp(2f - (float) availableSpace / mMaxPeekPadding, 0f, 1f);
        }

        setPeekingPercentage(peekingPercentage);
    }

    /**
     * @return Whether the card is peeking.
     */
    public boolean isPeeking() {
        return mPeekingPercentage > 0f;
    }

    /**
     * Override this to react when the card is tapped. This method will not be called if the card is
     * currently peeking.
     */
    protected void onCardTapped() {}

    /**
     * Override this to provide a context menu for the card. This method will not be called if the
     * card is currently peeking.
     * @param menu The menu to add menu items to.
     */
    protected void createContextMenu(ContextMenu menu) {}

    private void setPeekingPercentage(float peekingPercentage) {
        if (mPeekingPercentage == peekingPercentage) return;

        mPeekingPercentage = peekingPercentage;

        int peekPadding = (int) (mMaxPeekPadding
                * TRANSITION_INTERPOLATOR.getInterpolation(1f - peekingPercentage));

        // Modify the padding so as the margin increases, the padding decreases, keeping the card's
        // contents in the same position. The top and bottom remain the same.
        int lateralPadding;
        if (mUiConfig.getCurrentDisplayStyle() != UiConfig.DISPLAY_STYLE_WIDE) {
            lateralPadding = peekPadding;
        } else {
            lateralPadding = mMaxPeekPadding;
        }
        itemView.setPadding(lateralPadding, mMaxPeekPadding, lateralPadding, mMaxPeekPadding);

        // Adjust the margins by |mCards9PatchAdjustment| so the card width
        // is the actual width not including the elevation shadow, so we can have full bleed.
        mMarginResizer.setMargins(mMaxPeekPadding - (peekPadding + mCards9PatchAdjustment));

        // Set the opacity of the card content to be 0 when peeking and 1 when full width.
        int itemViewChildCount = ((ViewGroup) itemView).getChildCount();
        for (int i = 0; i < itemViewChildCount; ++i) {
            View snippetChild = ((ViewGroup) itemView).getChildAt(i);
            snippetChild.setAlpha(peekPadding / (float) mMaxPeekPadding);
        }
    }

    private static View inflateView(int resourceId, ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(resourceId, parent, false);
    }

    public static boolean isCard(@NewTabPageItem.ViewType int type) {
        switch (type) {
            case NewTabPageItem.VIEW_TYPE_SNIPPET:
            case NewTabPageItem.VIEW_TYPE_STATUS:
            case NewTabPageItem.VIEW_TYPE_ACTION:
            case NewTabPageItem.VIEW_TYPE_PROMO:
                return true;
            case NewTabPageItem.VIEW_TYPE_ABOVE_THE_FOLD:
            case NewTabPageItem.VIEW_TYPE_HEADER:
            case NewTabPageItem.VIEW_TYPE_SPACING:
            case NewTabPageItem.VIEW_TYPE_PROGRESS:
            case NewTabPageItem.VIEW_TYPE_FOOTER:
                return false;
            default:
                assert false;
        }
        return false;
    }

    @DrawableRes
    protected int selectBackground(boolean hasCardAbove, boolean hasCardBelow) {
        if (hasCardAbove && hasCardBelow) return R.drawable.ntp_card_middle;
        if (!hasCardAbove && hasCardBelow) return R.drawable.ntp_card_top;
        if (hasCardAbove && !hasCardBelow) return R.drawable.ntp_card_bottom;
        return R.drawable.ntp_card_single;
    }

    protected NewTabPageRecyclerView getRecyclerView() {
        return mRecyclerView;
    }
}
