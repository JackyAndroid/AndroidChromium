// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.banners;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Region;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.ui.UiUtils;

/**
 * View that appears on the screen as the user scrolls on the page and can be swiped away.
 * Meant to be tacked onto the {@link org.chromium.content.browser.ContentViewCore}'s view and
 * alerted when either the page scroll position or viewport size changes.
 *
 * GENERAL BEHAVIOR
 * This View is brought onto the screen by sliding upwards from the bottom of the screen.  Afterward
 * the View slides onto and off of the screen vertically as the user scrolls upwards or
 * downwards on the page.  Users dismiss the View by swiping it away horizontally.
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
 *
 * HORIZONTAL SCROLL CALCULATIONS
 * Horizontal drags and swipes are used to dismiss the View.  Translating the View far enough
 * horizontally (with "enough" defined by the DISMISS_SWIPE_THRESHOLD AND DISMISS_FLING_THRESHOLD)
 * triggers an animation that removes the View from the hierarchy.  Failing to meet the threshold
 * will result in the View being translated back to the center of the screen.
 *
 * Because the fling velocity handed in by Android is highly inaccurate and often indicates
 * that a fling is moving in an opposite direction than expected, the scroll direction is tracked
 * to determine which direction the user was dragging the View when the fling was initiated.  When a
 * fling is completed, the more forgiving FLING_THRESHOLD is used to determine how far a user must
 * swipe to dismiss the View rather than try to use the fling velocity.
 */
public abstract class SwipableOverlayView extends ScrollView {
    private static final float ALPHA_THRESHOLD = 0.25f;
    private static final float DISMISS_SWIPE_THRESHOLD = 0.75f;
    private static final float FULL_THRESHOLD = 0.5f;
    private static final float VERTICAL_FLING_SHOW_THRESHOLD = 0.2f;
    private static final float VERTICAL_FLING_HIDE_THRESHOLD = 0.9f;
    private static final long REATTACH_FADE_IN_MS = 250;
    protected static final float ZERO_THRESHOLD = 0.001f;

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_SCROLLING = 1;
    private static final int GESTURE_FLINGING = 2;

    private static final int DRAGGED_LEFT = -1;
    private static final int DRAGGED_CANCEL = 0;
    private static final int DRAGGED_RIGHT = 1;

    protected static final long MS_ANIMATION_DURATION = 250;
    private static final long MS_DISMISS_FLING_THRESHOLD = MS_ANIMATION_DURATION * 2;
    private static final long MS_SLOW_DISMISS = MS_ANIMATION_DURATION * 3;

    /** Resets the state of the SwipableOverlayView, as needed. */
    protected class SwipableOverlayViewTabObserver extends EmptyTabObserver {
        @Override
        public void onDidNavigateMainFrame(Tab tab, String url, String baseUrl,
                boolean isNavigationToDifferentPage, boolean isFragmentNavigation,
                int statusCode) {
            setDoStayInvisible(false);
        }
    }

    // Detects when the user is dragging the View.
    private final GestureDetector mGestureDetector;

    // Detects when the user is dragging the ContentViewCore.
    private final GestureStateListener mGestureStateListener;

    // Listens for changes in the layout.
    private final View.OnLayoutChangeListener mLayoutChangeListener;

    // Monitors for animation completions and resets the state.
    private final AnimatorListenerAdapter mAnimatorListenerAdapter;

    // Interpolator used for the animation.
    private final Interpolator mInterpolator;

    // Observes the Tab.
    private final TabObserver mTabObserver;

    // Tracks whether the user is scrolling or flinging.
    private int mGestureState;

    // Animation currently being used to translate the View.
    private AnimatorSet mCurrentAnimation;

    // Direction the user is horizontally dragging.
    private int mDragDirection;

    // How quickly the user is horizontally dragging.
    private float mDragXPerMs;

    // WHen the user first started dragging.
    private long mDragStartMs;

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

    // Whether or not the View has been, or is being, dismissed.
    private boolean mIsDismissed;

    // The ContentViewCore to which the overlay is added.
    private ContentViewCore mContentViewCore;

    // Keeps the View from becoming visible when it normally would.
    private boolean mDoStayInvisible;

    // Whether the View should be allowed to be swiped away.
    private boolean mIsSwipable = true;

    /**
     * Creates a SwipableOverlayView.
     * @param context Context for acquiring resources.
     * @param attrs Attributes from the XML layout inflation.
     */
    public SwipableOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        SimpleOnGestureListener gestureListener = createGestureListener();
        mGestureDetector = new GestureDetector(context, gestureListener);
        mGestureStateListener = createGestureStateListener();
        mGestureState = GESTURE_NONE;
        mLayoutChangeListener = createLayoutChangeListener();
        mAnimatorListenerAdapter = createAnimatorListenerAdapter();
        mInterpolator = new DecelerateInterpolator(1.0f);
        mTabObserver = createTabObserver();

        // We make this view 'draw' to provide a placeholder for its animations.
        setWillNotDraw(false);
    }

    /**
     * Indicates whether the View should be allowed to be swiped away.
     * @param swipable Whether the View is reacts to horizontal gestures.
     */
    protected void setIsSwipable(boolean swipable) {
        mIsSwipable = swipable;
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

    public void addToParentView(ViewGroup parentView) {
        if (parentView != null && parentView.indexOfChild(this) == -1) {
            parentView.addView(this, createLayoutParams());

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

    /**
     * Call with {@code true} when a higher priority bottom element is visible to keep the View
     * from ever becoming visible.  Call with {@code false} to restore normal visibility behavior.
     * @param doStayInvisible Whether the View should stay invisible even when they would
     *        normally become visible.
     */
    public void setDoStayInvisible(boolean doStayInvisible) {
        mDoStayInvisible = doStayInvisible;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mDoStayInvisible) {
            ObjectAnimator.ofFloat(this, View.ALPHA, 0.f, 1.f).setDuration(REATTACH_FADE_IN_MS)
                    .start();
            setVisibility(VISIBLE);
        }
        if (!isAllowedToAutoHide()) setTranslationY(0.0f);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!isAllowedToAutoHide()) setTranslationY(0.0f);
    }

    /**
     * @return TabObserver that can be used to monitor a Tab.
     */
    protected TabObserver createTabObserver() {
        return new SwipableOverlayViewTabObserver();
    }

    /**
     * @return TabObserver that is used to monitor a Tab.
     */
    public TabObserver getTabObserver() {
        return mTabObserver;
    }

    /**
     * See {@link #android.view.ViewGroup.onLayout(boolean, int, int, int, int)}.
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Hide the View when the keyboard is showing.
        boolean isShowing = (getVisibility() == View.VISIBLE);
        if (UiUtils.isKeyboardShowing(getContext(), this)) {
            if (isShowing) {
                setVisibility(View.INVISIBLE);
            }
        } else {
            if (!isShowing && !mDoStayInvisible) {
                setVisibility(View.VISIBLE);
            }
        }

        // Update the viewport height when the parent View's height changes (e.g. after rotation).
        int currentParentHeight = getParent() == null ? 0 : ((View) getParent()).getHeight();
        if (mParentHeight != currentParentHeight) {
            mParentHeight = currentParentHeight;
            mGestureState = GESTURE_NONE;
            if (mCurrentAnimation != null) mCurrentAnimation.end();
        }

        // Update the known effective height of the View.
        mTotalHeight = getMeasuredHeight();
        if (getLayoutParams() instanceof MarginLayoutParams) {
            MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
            mTotalHeight += params.topMargin + params.bottomMargin;
        }

        super.onLayout(changed, l, t, r, b);
    }

    /**
     * See {@link #android.view.View.onTouchEvent(MotionEvent)}.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mIsSwipable) return false;

        if (mGestureDetector.onTouchEvent(event)) return true;
        if (mCurrentAnimation != null) return true;

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            onFinishHorizontalGesture();
            return true;
        }
        return false;
    }

    /**
     * Creates a listener that monitors horizontal gestures performed on the View.
     * @return The SimpleOnGestureListener that will monitor the View.
     */
    private SimpleOnGestureListener createGestureListener() {
        return new SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                mGestureState = GESTURE_SCROLLING;
                mDragDirection = DRAGGED_CANCEL;
                mDragXPerMs = 0;
                mDragStartMs = e.getEventTime();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distX, float distY) {
                float distance = e2.getX() - e1.getX();
                setTranslationX(getTranslationX() + distance);
                setAlpha(calculateAnimationAlpha());

                // Because the Android-calculated fling velocity is highly unreliable, we track what
                // direction the user is dragging the View from here.
                mDragDirection = distance < 0 ? DRAGGED_LEFT : DRAGGED_RIGHT;
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                mGestureState = GESTURE_FLINGING;

                // The direction and speed of the Android-given velocity feels completely disjoint
                // from what the user actually perceives.
                float androidXPerMs = Math.abs(vX) / 1000.0f;

                // Track how quickly the user has translated the view to this point.
                float dragXPerMs = Math.abs(getTranslationX()) / (e2.getEventTime() - mDragStartMs);

                // Check if the velocity from the user's drag is higher; if so, use that one
                // instead since that often feels more correct.
                mDragXPerMs = mDragDirection * Math.max(androidXPerMs, dragXPerMs);
                onFinishHorizontalGesture();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                onViewClicked();
                return true;
            }

            @Override
            public void onShowPress(MotionEvent e) {
                onViewPressed(e);
            }
        };
    }

    /**
     * Called at the end of a user gesture on the banner to either return the banner to a neutral
     * position in the center of the screen or dismiss it entirely.
     */
    private void onFinishHorizontalGesture() {
        mDragDirection = determineFinalHorizontalLocation();
        if (mDragDirection == DRAGGED_CANCEL) {
            // Move the View back to the center of the screen.
            createHorizontalSnapAnimation(true);
        } else {
            // User swiped the View away.  Dismiss it.
            onViewSwipedAway();
            dismiss(true);
        }
    }

    /**
     * Creates a listener than monitors the ContentViewCore for scrolls and flings.
     * The listener updates the location of this View to account for the user's gestures.
     * @return GestureStateListener to send to the ContentViewCore.
     */
    private GestureStateListener createGestureStateListener() {
        return new GestureStateListener() {
            @Override
            public void onFlingStartGesture(int vx, int vy, int scrollOffsetY, int scrollExtentY) {
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
    void createVerticalSnapAnimation(boolean visible) {
        float translationY = visible ? 0.0f : mTotalHeight;
        float yDifference = Math.abs(translationY - getTranslationY()) / mTotalHeight;
        long duration = (long) (MS_ANIMATION_DURATION * yDifference);
        createAnimation(1.0f, 0, translationY, duration);
    }

    /**
     * Create an animation that snaps the View into position horizontally.
     * @param visible If true, snaps the View to the bottom-center of the screen.  If false,
     *                translates the View to the side of the screen.
     */
    private void createHorizontalSnapAnimation(boolean visible) {
        if (visible) {
            // Move back to the center of the screen.
            createAnimation(1.0f, 0.0f, getTranslationY(), MS_ANIMATION_DURATION);
        } else {
            if (mDragDirection == DRAGGED_CANCEL) {
                // No direction was selected
                mDragDirection = DRAGGED_LEFT;
            }

            float finalX = mDragDirection * getWidth();

            // Determine how long it will take for the banner to leave the screen.
            long duration = MS_ANIMATION_DURATION;
            switch (mGestureState) {
                case GESTURE_FLINGING:
                    duration = (long) calculateMsRequiredToFlingOffScreen();
                    break;
                case GESTURE_NONE:
                    // Explicitly use a slow animation to help educate the user about swiping.
                    duration = MS_SLOW_DISMISS;
                    break;
                default:
                    break;
            }

            createAnimation(0.0f, finalX, getTranslationY(), duration);
        }
    }

    /**
     * Dismisses the View, animating it moving off of the screen if needed.
     * @param horizontally True if the View is being dismissed to the side of the screen.
     */
    protected boolean dismiss(boolean horizontally) {
        if (getParent() == null || mIsDismissed) return false;

        mIsDismissed = true;
        if (horizontally) {
            createHorizontalSnapAnimation(false);
        } else {
            createVerticalSnapAnimation(false);
        }
        return true;
    }

    /**
     * @return Whether or not the View has been dismissed.
     */
    protected boolean isDismissed() {
        return mIsDismissed;
    }

    /**
     * Calculates how transparent the View should be.
     *
     * The transparency value is proportional to how far the View has been swiped away from the
     * center of the screen.  The {@link ALPHA_THRESHOLD} determines at what point the View should
     * start fading away.
     * @return The alpha value to use for the View.
     */
    private float calculateAnimationAlpha() {
        float percentageSwiped = Math.abs(getTranslationX() / getWidth());
        float percentageAdjusted = Math.max(0.0f, percentageSwiped - ALPHA_THRESHOLD);
        float alphaRange = 1.0f - ALPHA_THRESHOLD;
        return 1.0f - percentageAdjusted / alphaRange;
    }

    private int computeScrollDifference(int scrollOffsetY, int scrollExtentY) {
        return scrollOffsetY + scrollExtentY - mInitialOffsetY;
    }

    /**
     * Determine where the View needs to move.  If the user hasn't tried hard enough to dismiss
     * the View, move it back to the center.
     * @return DRAGGED_CANCEL if the View should return to a neutral center position.
     *         DRAGGED_LEFT if the View should be dismissed to the left.
     *         DRAGGED_RIGHT if the View should be dismissed to the right.
     */
    private int determineFinalHorizontalLocation() {
        if (mGestureState == GESTURE_FLINGING) {
            // Because of the unreliability of the fling velocity, we ignore it and instead rely on
            // the direction the user was last dragging the View.  Moreover, we lower the
            // translation threshold for dismissal, requiring the View to translate off screen
            // within a reasonable time frame.
            float msRequired = calculateMsRequiredToFlingOffScreen();
            if (msRequired > MS_DISMISS_FLING_THRESHOLD) return DRAGGED_CANCEL;
        } else if (mGestureState == GESTURE_SCROLLING) {
            // Check if the user has dragged the View far enough to be dismissed.
            float dismissPercentage = DISMISS_SWIPE_THRESHOLD;
            float dismissThreshold = getWidth() * dismissPercentage;
            if (Math.abs(getTranslationX()) < dismissThreshold) return DRAGGED_CANCEL;
        }

        return mDragDirection;
    }

    /**
     * Assuming a linear velocity, determine how long it would take for the View to translate off
     * of the screen.
     */
    private float calculateMsRequiredToFlingOffScreen() {
        float remainingDifference = mDragDirection * getWidth() - getTranslationX();
        return Math.abs(remainingDifference / mDragXPerMs);
    }

    /**
     * Creates an animation that slides the View to the given location and visibility.
     * @param alpha How opaque the View should be at the end.
     * @param x X-coordinate of the final translation.
     * @param y Y-coordinate of the final translation.
     * @param duration How long the animation should run for.
     */
    private void createAnimation(float alpha, float x, float y, long duration) {
        Animator alphaAnimator =
                ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat("alpha", getAlpha(), alpha));
        Animator translationXAnimator =
                ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat("translationX", getTranslationX(), x));
        Animator translationYAnimator =
                ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat("translationY", getTranslationY(), y));

        mCurrentAnimation = new AnimatorSet();
        mCurrentAnimation.setDuration(Math.max(duration, 0));
        mCurrentAnimation.playTogether(alphaAnimator, translationXAnimator, translationYAnimator);
        mCurrentAnimation.addListener(mAnimatorListenerAdapter);
        mCurrentAnimation.setInterpolator(mInterpolator);
        mCurrentAnimation.start();
    }

    /**
     * Creates an AnimatorListenerAdapter that cleans up after an animation is completed.
     * @return {@link AnimatorListenerAdapter} to use for animations.
     */
    private AnimatorListenerAdapter createAnimatorListenerAdapter() {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mIsDismissed) removeFromParentView();

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
     * Cancels the current animation, if the View isn't being dismissed.
     * @return True if the animation was canceled or wasn't running, false otherwise.
     */
    private boolean cancelCurrentAnimation() {
        if (!mayCancelCurrentAnimation()) return false;
        if (mCurrentAnimation != null) mCurrentAnimation.cancel();
        return true;
    }

    /**
     * Determines whether or not the animation can be interrupted.  Animations may not be canceled
     * when the View is being dismissed or when it's coming onto screen for the first time.
     * @return Whether or not the animation may be interrupted.
     */
    private boolean mayCancelCurrentAnimation() {
        return !mIsBeingDisplayedForFirstTime && !mIsDismissed;
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
        float translationX = getTranslationX();
        float translationY = getTranslationY();
        setTranslationX(0);
        setTranslationY(0);
        boolean result = super.gatherTransparentRegion(region);
        // Restoring TranslationX/Y invalidates this view unnecessarily. However, this function
        // is called as part of layout, which implies a full redraw is about to occur anyway.
        setTranslationX(translationX);
        setTranslationY(translationY);
        return result;
    }

    /**
     * Called when the View has been swiped away by the user.
     */
    protected abstract void onViewSwipedAway();

    /**
     * Called when the View has been clicked.
     */
    protected abstract void onViewClicked();

    /**
     * Called when the View needs to show that it's been pressed.
     */
    protected abstract void onViewPressed(MotionEvent event);
}
