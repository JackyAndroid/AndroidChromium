// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Region;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.NewTabPageLayout;
import org.chromium.chrome.browser.ntp.snippets.SectionHeaderViewHolder;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.util.ViewUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple wrapper on top of a RecyclerView that will acquire focus when tapped.  Ensures the
 * New Tab page receives focus when clicked.
 */
public class NewTabPageRecyclerView extends RecyclerView {
    private static final String TAG = "NtpCards";
    private static final Interpolator DISMISS_INTERPOLATOR = new FastOutLinearInInterpolator();
    private static final int DISMISS_ANIMATION_TIME_MS = 300;

    private final GestureDetector mGestureDetector;
    private final LinearLayoutManager mLayoutManager;
    private final int mToolbarHeight;
    private final int mMaxHeaderHeight;

    /**
     * Total height of the items being dismissed.  Tracked to allow the bottom space to compensate
     * for their removal animation and avoid moving the scroll position.
     */
    private int mCompensationHeight;

    /**
     * Height compensation value for each item being dismissed. Since dismissals sometimes include
     * sibling elements, and these don't get the standard treatment, we track the total height
     * associated with the element the user interacted with.
     */
    private final Map<ViewHolder, Integer> mCompensationHeightMap = new HashMap<>();

    /** View used to calculate the position of the cards' snap point. */
    private View mAboveTheFoldView;

    /** Whether the RecyclerView and its children should react to touch events. */
    private boolean mTouchEnabled = true;

    /** Whether the above-the-fold left space for a peeking card to be displayed. */
    private boolean mHasSpaceForPeekingCard;

    /**
     * Constructor needed to inflate from XML.
     */
    public NewTabPageRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mGestureDetector =
                new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        boolean retVal = super.onSingleTapUp(e);
                        requestFocus();
                        return retVal;
                    }
                });
        mLayoutManager = new LinearLayoutManager(getContext());
        setLayoutManager(mLayoutManager);

        Resources res = context.getResources();
        mToolbarHeight = res.getDimensionPixelSize(R.dimen.toolbar_height_no_shadow)
                + res.getDimensionPixelSize(R.dimen.toolbar_progress_bar_height);
        mMaxHeaderHeight = res.getDimensionPixelSize(R.dimen.snippets_article_header_height);
    }

    public boolean isFirstItemVisible() {
        return mLayoutManager.findFirstVisibleItemPosition() == 0;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mGestureDetector.onTouchEvent(ev);
        if (!mTouchEnabled) return true;
        return super.onInterceptTouchEvent(ev);
    }

    public void setTouchEnabled(boolean enabled) {
        mTouchEnabled = enabled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mTouchEnabled) return false;

        // Action down would already have been handled in onInterceptTouchEvent
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
            mGestureDetector.onTouchEvent(ev);
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void focusableViewAvailable(View v) {
        // To avoid odd jumps during NTP animation transitions, we do not attempt to give focus
        // to child views if this scroll view already has focus.
        if (hasFocus()) return;
        super.focusableViewAvailable(v);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // Fixes landscape transitions when unfocusing the URL bar: crbug.com/288546
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return super.onCreateInputConnection(outAttrs);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int numberViews = getChildCount();
        for (int i = 0; i < numberViews; ++i) {
            View view = getChildAt(i);
            NewTabPageViewHolder viewHolder = (NewTabPageViewHolder) getChildViewHolder(view);
            if (viewHolder == null) return;
            viewHolder.updateLayoutParams();
        }
        super.onLayout(changed, l, t, r, b);
    }

    public void setAboveTheFoldView(View aboveTheFoldView) {
        mAboveTheFoldView = aboveTheFoldView;
    }

    public void setHasSpaceForPeekingCard(boolean hasSpaceForPeekingCard) {
        mHasSpaceForPeekingCard = hasSpaceForPeekingCard;
    }

    /** Scroll up from the cards' current position and snap to present the first one. */
    public void scrollToFirstCard() {
        // Offset the target scroll by the height of the omnibox (the top padding).
        final int targetScroll = mAboveTheFoldView.getHeight() - mAboveTheFoldView.getPaddingTop();
        // If (somehow) the peeking card is tapped while midway through the transition,
        // we need to account for how much we have already scrolled.
        smoothScrollBy(0, targetScroll - computeVerticalScrollOffset());
    }

    /**
     * Updates the space added at the end of the list to make sure the above/below the fold
     * distinction can be preserved.
     */
    public void refreshBottomSpacing() {
        ViewHolder bottomSpacingViewHolder = findBottomSpacer();

        // It might not be in the layout yet if it's not visible or ready to be displayed.
        if (bottomSpacingViewHolder == null) return;

        assert bottomSpacingViewHolder.getItemViewType() == NewTabPageItem.VIEW_TYPE_SPACING;
        bottomSpacingViewHolder.itemView.requestLayout();
    }

    /**
     * Calculates the height of the bottom spacing item, such that there is always enough content
     * below the fold to push the header up to to the top of the screen.
     */
    int calculateBottomSpacing() {
        int aboveTheFoldPosition = getNewTabPageAdapter().getAboveTheFoldPosition();
        int firstVisiblePos = mLayoutManager.findFirstVisibleItemPosition();
        if (aboveTheFoldPosition == RecyclerView.NO_POSITION
                || firstVisiblePos == RecyclerView.NO_POSITION) {
            return 0;
        }

        // We have enough items to fill the view, since the above-the-fold item is not even visible.
        if (firstVisiblePos > aboveTheFoldPosition) {
            return 0;
        }

        ViewHolder lastContentItem = findLastContentItem();
        ViewHolder aboveTheFold = findViewHolderForAdapterPosition(aboveTheFoldPosition);

        int bottomSpacing = getHeight() - mToolbarHeight;
        if (lastContentItem == null || aboveTheFold == null) {
            // This can happen in several cases, where some elements are not visible and the
            // RecyclerView didn't already attach them. We handle it by just adding space to make
            // sure that we never run out and force the UI to jump around and get stuck in a
            // position that breaks the animations. The height will be properly adjusted at the
            // next pass. Known cases that make it necessary:
            //  - The card list is refreshed while the NTP is not shown, for example when changing
            //    the sync settings.
            //  - Dismissing a snippet and having the status card coming to take its place.
            //  - Refresh while being below the fold, for example by tapping the status card.

            if (aboveTheFold != null) bottomSpacing -= aboveTheFold.itemView.getBottom();

            Log.w(TAG, "The RecyclerView items are not attached, can't determine the content "
                            + "height: snap=%s, last=%s. Using full height: %d ",
                    aboveTheFold, lastContentItem, bottomSpacing);
        } else {
            int contentHeight =
                    lastContentItem.itemView.getBottom() - aboveTheFold.itemView.getBottom();
            bottomSpacing -= contentHeight - mCompensationHeight;
        }

        return Math.max(0, bottomSpacing);
    }

    public void updatePeekingCardAndHeader() {
        NewTabPageLayout aboveTheFoldView = findAboveTheFoldView();
        if (aboveTheFoldView == null) return;

        SectionHeaderViewHolder header = findFirstHeader();
        if (header == null) return;

        header.updateDisplay(computeVerticalScrollOffset(), mHasSpaceForPeekingCard);

        CardViewHolder firstCard = findFirstCard();
        if (firstCard != null) updatePeekingCard(firstCard);

        // Update the space at the bottom, which needs to know about the height of the header.
        refreshBottomSpacing();
    }

    /**
     * Updates the peeking state of the provided card. Relies on the dimensions of the header to
     * be correct, prefer {@link #updatePeekingCardAndHeader} that updates both together.
     */
    public void updatePeekingCard(CardViewHolder peekingCard) {
        SectionHeaderViewHolder header = findFirstHeader();
        if (header == null) {
            // No header, we must have scrolled quite far. Fallback to a non animated (full bleed)
            // card.
            peekingCard.updatePeek(0, /* shouldAnimate */ false);
            return;
        }

        // If we have the card offset field trial enabled, don't peek at all.
        if (CardsVariationParameters.getFirstCardOffsetDp() != 0) {
            peekingCard.updatePeek(0, /* shouldAnimate */ false);
            return;
        }

        // Here we consider that if the header is animating (is not completely expanded), the card
        // should as well. In that case, the space below the header is what we have available.
        boolean shouldAnimate = header.itemView.getHeight() < mMaxHeaderHeight;
        peekingCard.updatePeek(getHeight() - header.itemView.getBottom(), shouldAnimate);
    }

    public NewTabPageAdapter getNewTabPageAdapter() {
        return (NewTabPageAdapter) getAdapter();
    }

    public LinearLayoutManager getLinearLayoutManager() {
        return mLayoutManager;
    }

    /**
     * Returns the approximate adapter position that the user has scrolled to. The purpose of this
     * value is that it can be stored and later retrieved to restore a scroll position that is
     * familiar to the user, showing (part of) the same content the user was previously looking at.
     * This position is valid for that purpose regardless of device orientation changes. Note that
     * if the underlying data has changed in the meantime, different content would be shown for this
     * position.
     */
    public int getScrollPosition() {
        return mLayoutManager.findFirstVisibleItemPosition();
    }

    /**
     * Finds the view holder for the first header.
     * @return The {@code ViewHolder} of the header, or null if it is not present.
     */
    private SectionHeaderViewHolder findFirstHeader() {
        int firstHeaderPosition = getNewTabPageAdapter().getFirstHeaderPosition();
        if (firstHeaderPosition == RecyclerView.NO_POSITION) return null;

        ViewHolder viewHolder = findViewHolderForAdapterPosition(firstHeaderPosition);
        if (!(viewHolder instanceof SectionHeaderViewHolder)) return null;

        return (SectionHeaderViewHolder) viewHolder;
    }

    /**
     * Finds the view holder for the first card.
     * @return The {@code ViewHolder} for the first card, or null if it is not present.
     */
    private CardViewHolder findFirstCard() {
        int firstCardPosition = getNewTabPageAdapter().getFirstCardPosition();
        if (firstCardPosition == RecyclerView.NO_POSITION) return null;

        ViewHolder viewHolder = findViewHolderForAdapterPosition(firstCardPosition);
        if (!(viewHolder instanceof CardViewHolder)) return null;

        return (CardViewHolder) viewHolder;
    }

    /**
     * Finds the view holder for the last content item: the footer.
     * @return The {@code ViewHolder} of the last content item, or null if it is not present.
     */
    private ViewHolder findLastContentItem() {
        int lastContentItemPosition = getNewTabPageAdapter().getLastContentItemPosition();
        if (lastContentItemPosition == RecyclerView.NO_POSITION) return null;

        ViewHolder viewHolder = findViewHolderForAdapterPosition(lastContentItemPosition);
        if (viewHolder instanceof Footer.ViewHolder) return viewHolder;

        return null;
    }

    /**
     * Finds the view holder for the bottom spacer.
     * @return The {@code ViewHolder} of the bottom spacer, or null if it is not present.
     */
    private ViewHolder findBottomSpacer() {
        int bottomSpacerPosition = getNewTabPageAdapter().getBottomSpacerPosition();
        if (bottomSpacerPosition == RecyclerView.NO_POSITION) return null;

        return findViewHolderForAdapterPosition(bottomSpacerPosition);
    }

    /**
     * Finds the above the fold view.
     * @return The View for above the fold or null, if it is not present.
     */
    public NewTabPageLayout findAboveTheFoldView() {
        int aboveTheFoldPosition = getNewTabPageAdapter().getAboveTheFoldPosition();
        if (aboveTheFoldPosition == RecyclerView.NO_POSITION) return null;

        ViewHolder viewHolder = findViewHolderForAdapterPosition(aboveTheFoldPosition);
        if (viewHolder == null) return null;

        View view = viewHolder.itemView;
        if (!(view instanceof NewTabPageLayout)) return null;

        return (NewTabPageLayout) view;
    }

    /** Called when an item is in the process of being removed from the view. */
    public void onItemDismissStarted(ViewHolder viewHolder) {
        assert !mCompensationHeightMap.containsKey(viewHolder);

        int dismissedHeight = viewHolder.itemView.getHeight();

        ViewHolder siblingViewHolder = getNewTabPageAdapter().getDismissSibling(viewHolder);
        if (siblingViewHolder != null) {
            dismissedHeight += siblingViewHolder.itemView.getHeight();
        }

        mCompensationHeightMap.put(viewHolder, dismissedHeight);
        mCompensationHeight += dismissedHeight;
        refreshBottomSpacing();
    }

    /** Called when an item has finished being removed from the view. */
    public void onItemDismissFinished(ViewHolder viewHolder) {
        assert mCompensationHeightMap.containsKey(viewHolder);
        mCompensationHeight -= mCompensationHeightMap.remove(viewHolder);

        assert mCompensationHeight >= 0;
        refreshBottomSpacing();
    }

    /**
     * If the RecyclerView is currently scrolled to between regionStart and regionEnd, smooth scroll
     * out of the region. flipPoint is the threshold used to decide which bound of the region to
     * scroll to. It returns whether the view was scrolled.
     */
    private boolean scrollOutOfRegion(int regionStart, int flipPoint, int regionEnd) {
        final int currentScroll = computeVerticalScrollOffset();

        if (currentScroll < regionStart || currentScroll > regionEnd) return false;

        if (currentScroll < flipPoint) {
            smoothScrollBy(0, regionStart - currentScroll);
        } else {
            smoothScrollBy(0, regionEnd - currentScroll);
        }
        return true;
    }

    /**
     * If the RecyclerView is currently scrolled to between regionStart and regionEnd, smooth scroll
     * out of the region to the nearest edge.
     */
    private boolean scrollOutOfRegion(int regionStart, int regionEnd) {
        return scrollOutOfRegion(regionStart, (regionStart + regionEnd) / 2, regionEnd);
    }

    /**
     * Snaps the scroll point of the RecyclerView to prevent the user from scrolling to midway
     * through a transition and to allow peeking card behaviour.
     */
    public void snapScroll(View fakeBox, int parentScrollY, int parentHeight) {
        // Snap scroll to prevent resting in the middle of the omnibox transition.
        final int searchBoxTransitionLength = getResources()
                .getDimensionPixelSize(R.dimen.ntp_search_box_transition_length);
        int fakeBoxUpperBound = fakeBox.getTop() + fakeBox.getPaddingTop();
        if (scrollOutOfRegion(fakeBoxUpperBound - searchBoxTransitionLength, fakeBoxUpperBound)) {
            // The snap scrolling regions should never overlap.
            return;
        }

        // Snap scroll to prevent resting in the middle of the peeking card transition
        // and to allow the peeking card to peek a bit before snapping back.
        CardViewHolder peekingCardViewHolder = findFirstCard();
        if (peekingCardViewHolder != null && isFirstItemVisible()) {
            if (!mHasSpaceForPeekingCard) return;

            ViewHolder firstHeaderViewHolder = findFirstHeader();
            // It is possible to have a card but no header, for example the sign in promo.
            // That one does not peek.
            if (firstHeaderViewHolder == null) return;

            View peekingCardView = peekingCardViewHolder.itemView;
            View headerView = firstHeaderViewHolder.itemView;
            final int peekingHeight = getResources().getDimensionPixelSize(
                    R.dimen.snippets_padding_and_peeking_card_height);

            // |A + B - C| gives the offset of the peeking card relative to the Recycler View,
            // so scrolling to this point would put the peeking card at the top of the
            // screen. Remove the |headerView| height which gets dynamically increased with
            // scrolling.
            // |A + B - C - D| will scroll us so that the peeking card is just off the bottom
            // of the screen.
            // Finally, we get |A + B - C - D + E| because the transition starts from the
            // peeking card's resting point, which is |E| from the bottom of the screen.
            int start = peekingCardView.getTop()  // A.
                    + parentScrollY // B.
                    - headerView.getHeight()  // C.
                    - parentHeight  // D.
                    + peekingHeight;  // E.

            // The height of the region in which the the peeking card will snap.
            int snapScrollHeight = peekingHeight + headerView.getHeight();

            scrollOutOfRegion(start,
                              start + snapScrollHeight,
                              start + snapScrollHeight);
        }
    }

    @Override
    public boolean gatherTransparentRegion(Region region) {
        ViewUtils.gatherTransparentRegionsForOpaqueView(this, region);
        return true;
    }

    /**
     * Animates the card being swiped to the right as if the user had dismissed it. Any changes to
     * the animation here should be reflected also in
     * {@link #updateViewStateForDismiss(float, ViewHolder)} and reset in
     * {@link CardViewHolder#onBindViewHolder()}.
     * @param suggestion The item to be dismissed.
     */
    public void dismissItemWithAnimation(SnippetArticle suggestion) {
        // We need to recompute the position, as it might have changed.
        final int position = getNewTabPageAdapter().getSuggestionPosition(suggestion);
        if (position == RecyclerView.NO_POSITION) {
            // The item does not exist anymore, so ignore.
            return;
        }

        final View itemView = mLayoutManager.findViewByPosition(position);
        if (itemView == null) {
            // The view is not visible anymore, skip the animation.
            getNewTabPageAdapter().dismissItem(position);
            return;
        }

        final ViewHolder viewHolder = getChildViewHolder(itemView);
        if (!((NewTabPageViewHolder) viewHolder).isDismissable()) {
            // The item is not dismissable (anymore), so ignore.
            return;
        }

        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(ObjectAnimator.ofFloat(itemView, View.ALPHA, 0f),
                ObjectAnimator.ofFloat(itemView, View.TRANSLATION_X, (float) itemView.getWidth()));

        animation.setDuration(DISMISS_ANIMATION_TIME_MS);
        animation.setInterpolator(DISMISS_INTERPOLATOR);
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                NewTabPageRecyclerView.this.onItemDismissStarted(viewHolder);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                getNewTabPageAdapter().dismissItem(position);
                NewTabPageRecyclerView.this.onItemDismissFinished(viewHolder);
            }
        });
        animation.start();
    }

    /**
     * Update the view's state as it is being swiped away. Any changes to the animation here should
     * be reflected also in {@link #dismissItemWithAnimation(SnippetArticle)} and reset in
     * {@link CardViewHolder#onBindViewHolder()}.
     * @param dX The amount of horizontal displacement caused by user's action.
     * @param viewHolder The view holder containing the view to be updated.
     */
    public void updateViewStateForDismiss(float dX, ViewHolder viewHolder) {
        if (!((NewTabPageViewHolder) viewHolder).isDismissable()) return;

        viewHolder.itemView.setTranslationX(dX);

        float input = Math.abs(dX) / viewHolder.itemView.getMeasuredWidth();
        float alpha = 1 - DISMISS_INTERPOLATOR.getInterpolation(input);
        viewHolder.itemView.setAlpha(alpha);
    }
}
