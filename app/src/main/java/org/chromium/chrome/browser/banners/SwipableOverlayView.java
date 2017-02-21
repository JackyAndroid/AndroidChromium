// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.banners;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Region;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import org.chromium.chrome.browser.tab.TabContentViewParent;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.GestureStateListener;

/**
 * View that slides up from the bottom of the page and slides away as the user scrolls the page.
 * Meant to be tacked onto the {@link org.chromium.content.browser.ContentViewCore}'s view and
 * alerted when either the page scroll position or viewport size changes.
 *
 * GENERAL BEHAVIOR
 * This View is brought onto the screen by sliding upwards from the bottom of the screen.  Afterward
 * the View slides onto and off of the screen vertically as the user scrolls upwards or
 * downwards on the page.
 *
 * VERTICAL SCROLL CALCULATIONS
 * To determine how close the user is to the top of the page, the View must not only be informed of
 * page scroll position changes, but also of changes in the viewport size (which happens as the
 * omnibox appears and disappears, or as the page rotates e.g.).  When the viewport size gradually
 * shrinks, the user is most likely to be scrolling the page downwards while the omnibox comes back
 * into view.
 *
 * When the user first begins scrolling the page, both the scroll position and the viewport size are
 * summed and recorded together.  This is because a pixel change in the viewport height is
 * equivalent to a pixel change in the content's scroll offset:
 * - As the user scrolls the page downward, either the viewport height will increase (as the omnibox
 *   is slid off of the screen) or the content scroll offset will increase.
 * - As the user scrolls the page upward, either the viewport height will decrease (as the omnibox
 *   is brought back onto the screen) or the content scroll offset will decrease.
 *
 * As the scroll offset or the viewport height are updated via a scroll or fling, the difference
 * from the initial value is used to determine the View's Y-translation.  If a gesture is stopped,
 * the View will be snapped back into the center of the screen or entirely off of the screen, based
 * on how much of the View is visible, or where the user is currently located on the page.
 */
public abstract class SwipableOverlayView extends FrameLayout {
    private static final float FULL_THRESHOLD = 0.5f;
    private static final float VERTICAL_FLING_SHOW_THRESHOLD = 0.2f;
    private static final float VERTICAL_FLING_HIDE_THRESHOLD = 0.9f;

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_SCROLLING = 1;
    private static final int GESTURE_FLINGING = 2;

    private static final long ANIMATION_DURATION_MS = 250;

    // Detects when the user is dragging the ContentViewCore.
    private final GestureStateListener mGestureStateListener;

    // Listens for changes in the layout.
    private final View.OnLayoutChangeListener mLayoutChangeListener;

    // Monitors for animation completions and resets the state.
    private final AnimatorListener mAnimatorListener;

    // Interpolator used for the animation.
    private final Interpolator mInterpolator;

    // Tracks whether the user is scrolling or flinging.
    private int mGestureState;

    // Animation currently being used to translate the View.
    private Animator mCurrentAnimation;

    // Used to determine when the layout has changed and the Viewport must be updated.
    private int mParentHeight;

    // Location of the View when the current gesture was first started.
    private float mInitialTranslationY;

    // Offset from the top of the page when the current gesture was first started.
    private int mInitialOffsetY;

    // How tall the View is, including its margins.
    private int mTotalHeight;

    // Whether or not the View ever been fully displayed.
    private boolean mIsBeingDisplayedForFirstTime;

    // The ContentViewCore to which the overlay is added.
    private ContentViewCore mContentViewCore;

    /**
     * Creates a SwipableOverlayView.
     * @param context Context for acquiring resources.
     * @param attrs Attributes from the XML layout inflation.
     */
    public SwipableOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGestureStateListener = createGestureStateListener();
        mGestureState = GESTURE_NONE;
        mLayoutChangeListener = createLayoutChangeListener();
        mAnimatorListener = createAnimatorListener();
        mInterpolator = new DecelerateInterpolator(1.0f);

        // We make this view 'draw' to provide a placeholder for its animations.
        setWillNotDraw(false);
    }

    /**
     * Watches the given ContentViewCore for scrolling changes.
     */
    public void setContentViewCore(ContentViewCore contentViewCore) {
        if (mContentViewCore != null) {
            mContentViewCore.removeGestureStateListener(mGestureStateListener);
        }

        mContentViewCore = contentViewCore;
        if (mContentViewCore != null) {
            mContentViewCore.addGestureStateListener(mGestureStateListener);
        }
    }

    /**
     * @return the ContentViewCore that this View is monitoring.
     */
    protected ContentViewCore getContentViewCore() {
        return mContentViewCore;
    }

    protected void addToParentView(TabContentViewParent parentView) {
        if (getParent() == null) {
            parentView.addInfobarView(this, createLayoutParams());

            // Listen for the layout to know when to animate the View coming onto the screen.
            addOnLayoutChangeListener(mLayoutChangeListener);
        }
    }

    /**
     * Removes the SwipableOverlayView from its parent and stops monitoring the ContentViewCore.
     * @return Whether the View was removed from its parent.
     */
    public boolean removeFromParentView() {
        if (getParent() == null) return false;

        ((ViewGroup) getParent()).removeView(this);
        removeOnLayoutChangeListener(mLayoutChangeListener);
        return true;
    }

    /**
     * Creates a set of LayoutParams that makes the View hug the bottom of the screen.  Override it
     * for other types of behavior.
     * @return LayoutParams for use when adding the View to its parent.
     */
    public ViewGroup.MarginLayoutParams createLayoutParams() {
        return new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isAllowedToAutoHide()) setTranslationY(0.0f);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!isAllowedToAutoHide()) setTranslationY(0.0f);
    }

    /**
     * See {@link #android.view.ViewGroup.onLayout(boolean, int, int, int, int)}.
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Update the viewport height when the parent View's height changes (e.g. after rotation).
        int currentParentHeight = getParent() == null ? 0 : ((View) getParent()).getHeight();
        if (mParentHeight != currentParentHeight) {
            mParentHeight = currentParentHeight;
            mGestureState = GESTURE_NONE;
            if (mCurrentAnimation != null) mCurrentAnimation.end();
        }

        // Update the known effective height of the View.
        MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
        mTotalHeight = getMeasuredHeight() + params.topMargin + params.bottomMargin;

        super.onLayout(changed, l, t, r, b);
    }

    /**
     * Creates a listener than monitors the ContentViewCore for scrolls and flings.
     * The listener updates the location of this View to account for the user's gestures.
     * @return GestureStateListener to send to the ContentViewCore.
     */
    private GestureStateListener createGestureStateListener() {
        return new GestureStateListener() {
            @Override
            public void onFlingStartGesture(int scrollOffsetY, int scrollExtentY) {
                if (!isAllowedToAutoHide() || !cancelCurrentAnimation()) return;
                beginGesture(scrollOffsetY, scrollExtentY);
                mGestureState = GESTURE_FLINGING;
            }

            @Override
            public void onFlingEndGesture(int scrollOffsetY, int scrollExtentY) {
                if (mGestureState != GESTURE_FLINGING) return;
                mGestureState = GESTURE_NONE;

                int finalOffsetY = computeScrollDifference(scrollOffsetY, scrollExtentY);
                updateTranslation(scrollOffsetY, scrollExtentY);

                boolean isScrollingDownward = finalOffsetY > 0;

                boolean isVisibleInitially = mInitialTranslationY < mTotalHeight;
                float percentageVisible = 1.0f - (getTranslationY() / mTotalHeight);
                float visibilityThreshold = isVisibleInitially
                        ? VERTICAL_FLING_HIDE_THRESHOLD : VERTICAL_FLING_SHOW_THRESHOLD;
                boolean isVisibleEnough = percentageVisible > visibilityThreshold;

                boolean show = !isScrollingDownward;
                if (isVisibleInitially) {
                    // Check if the View was moving off-screen.
                    boolean isHiding = getTranslationY() > mInitialTranslationY;
                    show &= isVisibleEnough || !isHiding;
                } else {
                    // When near the top of the page, there's not much room left to scroll.
                    boolean isNearTopOfPage = finalOffsetY < (mTotalHeight * FULL_THRESHOLD);
                    show &= isVisibleEnough || isNearTopOfPage;
                }
                createVerticalSnapAnimation(show);
            }

            @Override
            public void onScrollStarted(int scrollOffsetY, int scrollExtentY) {
                if (!isAllowedToAutoHide() || !cancelCurrentAnimation()) return;
                beginGesture(scrollOffsetY, scrollExtentY);
                mGestureState = GESTURE_SCROLLING;
            }

            @Override
            public void onScrollEnded(int scrollOffsetY, int scrollExtentY) {
                if (mGestureState != GESTURE_SCROLLING) return;
                mGestureState = GESTURE_NONE;

                int finalOffsetY = computeScrollDifference(scrollOffsetY, scrollExtentY);
                updateTranslation(scrollOffsetY, scrollExtentY);

                boolean isNearTopOfPage = finalOffsetY < (mTotalHeight * FULL_THRESHOLD);
                boolean isVisibleEnough = getTranslationY() < mTotalHeight * FULL_THRESHOLD;
                createVerticalSnapAnimation(isNearTopOfPage || isVisibleEnough);
            }

            @Override
            public void onScrollOffsetOrExtentChanged(int scrollOffsetY, int scrollExtentY) {
                // This function is called for both fling and scrolls.
                if (mGestureState == GESTURE_NONE || !cancelCurrentAnimation()) return;
                updateTranslation(scrollOffsetY, scrollExtentY);
            }

            private void updateTranslation(int scrollOffsetY, int scrollExtentY) {
                float translation = mInitialTranslationY
                        + computeScrollDifference(scrollOffsetY, scrollExtentY);
                translation = Math.max(0.0f, Math.min(mTotalHeight, translation));
                setTranslationY(translation);
            }
        };
    }

    /**
     * Creates a listener that is used only to animate the View coming onto the screen.
     * @return The SimpleOnGestureListener that will monitor the View.
     */
    private View.OnLayoutChangeListener createLayoutChangeListener() {
        return new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                removeOnLayoutChangeListener(mLayoutChangeListener);

                // Animate the View coming in from the bottom of the screen.
                setTranslationY(mTotalHeight);
                mIsBeingDisplayedForFirstTime = true;
                createVerticalSnapAnimation(true);
                mCurrentAnimation.start();
            }
        };
    }

    /**
     * Create an animation that snaps the View into position vertically.
     * @param visible If true, snaps the View to the bottom-center of the screen.  If false,
     *                translates the View below the bottom-center of the screen so that it is
     *                effectively invisible.
     */
    private void createVerticalSnapAnimation(boolean visible) {
        float translationY = visible ? 0.0f : mTotalHeight;
        float yDifference = Math.abs(translationY - getTranslationY()) / mTotalHeight;
        long duration = Math.max(0, (long) (ANIMATION_DURATION_MS * yDifference));

        mCurrentAnimation = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, translationY);
        mCurrentAnimation.setDuration(duration);
        mCurrentAnimation.addListener(mAnimatorListener);
        mCurrentAnimation.setInterpolator(mInterpolator);
        mCurrentAnimation.start();
    }

    private int computeScrollDifference(int scrollOffsetY, int scrollExtentY) {
        return scrollOffsetY + scrollExtentY - mInitialOffsetY;
    }

    /**
     * Creates an AnimatorListenerAdapter that cleans up after an animation is completed.
     * @return {@link AnimatorListenerAdapter} to use for animations.
     */
    private AnimatorListener createAnimatorListener() {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mGestureState = GESTURE_NONE;
                mCurrentAnimation = null;
                mIsBeingDisplayedForFirstTime = false;
            }
        };
    }

    /**
     * Records the conditions of the page when a gesture is initiated.
     */
    private void beginGesture(int scrollOffsetY, int scrollExtentY) {
        mInitialTranslationY = getTranslationY();
        boolean isInitiallyVisible = mInitialTranslationY < mTotalHeight;
        int startingY = isInitiallyVisible ? scrollOffsetY : Math.min(scrollOffsetY, mTotalHeight);
        mInitialOffsetY = startingY + scrollExtentY;
    }

    /**
     * Cancels the current animation, unless the View is coming onto the screen for the first time.
     * @return True if the animation was canceled or wasn't running, false otherwise.
     */
    private boolean cancelCurrentAnimation() {
        if (mIsBeingDisplayedForFirstTime) return false;
        if (mCurrentAnimation != null) mCurrentAnimation.cancel();
        return true;
    }

    /**
     * @return Whether the SwipableOverlayView is allowed to hide itself on scroll.
     */
    protected boolean isAllowedToAutoHide() {
        return true;
    }

    /**
     * Override gatherTransparentRegion to make this view's layout a placeholder for its
     * animations. This is only called during layout, so it doesn't really make sense to apply
     * post-layout properties like it does by default. Together with setWillNotDraw(false),
     * this ensures no child animation within this view's layout will be clipped by a SurfaceView.
     */
    @Override
    public boolean gatherTransparentRegion(Region region) {
        float translationY = getTranslationY();
        setTranslationY(0);
        boolean result = super.gatherTransparentRegion(region);
        // Restoring TranslationY invalidates this view unnecessarily. However, this function
        // is called as part of layout, which implies a full redraw is about to occur anyway.
        setTranslationY(translationY);
        return result;
    }
}
