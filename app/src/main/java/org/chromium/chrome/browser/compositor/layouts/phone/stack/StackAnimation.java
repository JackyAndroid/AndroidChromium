// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone.stack;

import static org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.AnimatableAnimation.addAnimation;
import static org.chromium.chrome.browser.compositor.layouts.phone.stack.StackTab.Property.DISCARD_AMOUNT;
import static org.chromium.chrome.browser.compositor.layouts.phone.stack.StackTab.Property.SCROLL_OFFSET;

import android.view.animation.Interpolator;

import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable;
import org.chromium.chrome.browser.compositor.layouts.Layout.Orientation;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * A factory that builds animations for the tab stack.
 */
public abstract class StackAnimation {
    public enum OverviewAnimationType {
        ENTER_STACK,
        NEW_TAB_OPENED,
        TAB_FOCUSED,
        VIEW_MORE,
        REACH_TOP,
        // Commit/uncommit tab discard animations
        DISCARD,
        DISCARD_ALL,
        UNDISCARD,
        // Start pinch animation un-tilt all the tabs.
        START_PINCH,
        // Special animation
        FULL_ROLL,
        // Used for when the current state of the system is not animating
        NONE,
    }

    public static final float SCALE_AMOUNT = 0.90f;
    protected static final float INITIAL_ALPHA_AMOUNT = 0.1f;
    protected static final float INITIAL_SCALE_AMOUNT = 0.75f;

    protected static final int ENTER_STACK_TOOLBAR_ALPHA_DURATION = 100;
    protected static final int ENTER_STACK_TOOLBAR_ALPHA_DELAY = 100;
    protected static final int ENTER_STACK_ANIMATION_DURATION = 300;
    protected static final int ENTER_STACK_RESIZE_DELAY = 10;
    protected static final int ENTER_STACK_BORDER_ALPHA_DURATION = 200;
    protected static final int ENTER_STACK_BORDER_ALPHA_DELAY = 0;
    protected static final float ENTER_STACK_SIZE_RATIO = 0.35f;

    protected static final int TAB_FOCUSED_TOOLBAR_ALPHA_DURATION = 250;
    protected static final int TAB_FOCUSED_TOOLBAR_ALPHA_DELAY = 0;
    protected static final int TAB_FOCUSED_ANIMATION_DURATION = 400;
    protected static final int TAB_FOCUSED_Y_STACK_DURATION = 200;
    protected static final int TAB_FOCUSED_BORDER_ALPHA_DURATION = 200;
    protected static final int TAB_FOCUSED_BORDER_ALPHA_DELAY = 0;
    protected static final int TAB_FOCUSED_MAX_DELAY = 100;

    protected static final int VIEW_MORE_ANIMATION_DURATION = 400;
    protected static final float VIEW_MORE_SIZE_RATIO = 0.75f;
    protected static final int VIEW_MORE_MIN_SIZE = 200;

    protected static final int REACH_TOP_ANIMATION_DURATION = 400;

    protected static final int UNDISCARD_ANIMATION_DURATION = 150;

    protected static final int TAB_OPENED_ANIMATION_DURATION = 300;
    protected static final int TAB_OPENED_BORDER_ALPHA_DURATION = 100;
    protected static final int TAB_OPENED_BORDER_ALPHA_DELAY = 100;

    protected static final int DISCARD_ANIMATION_DURATION = 150;
    protected static final int TAB_REORDER_DURATION = 500;
    protected static final int TAB_REORDER_START_SPAN = 400;

    protected static final int START_PINCH_ANIMATION_DURATION = 75;

    protected static final int FULL_ROLL_ANIMATION_DURATION = 1000;

    protected final float mWidth;
    protected final float mHeight;
    protected final float mHeightMinusBrowserControls;
    protected final float mBorderTopHeight;
    protected final float mBorderTopOpaqueHeight;
    protected final float mBorderLeftWidth;

    /**
     * Protected constructor.
     *
     * @param width                       The width of the layout in dp.
     * @param height                      The height of the layout in dp.
     * @param heightMinusBrowserControls  The height of the layout minus the browser controls in dp.
     * @param borderFramePaddingTop       The top padding of the border frame in dp.
     * @param borderFramePaddingTopOpaque The opaque top padding of the border frame in dp.
     * @param borderFramePaddingLeft      The left padding of the border frame in dp.
     */
    protected StackAnimation(float width, float height, float heightMinusBrowserControls,
            float borderFramePaddingTop, float borderFramePaddingTopOpaque,
            float borderFramePaddingLeft) {
        mWidth = width;
        mHeight = height;
        mHeightMinusBrowserControls = heightMinusBrowserControls;

        mBorderTopHeight = borderFramePaddingTop;
        mBorderTopOpaqueHeight = borderFramePaddingTopOpaque;
        mBorderLeftWidth = borderFramePaddingLeft;
    }

    /**
     * The factory method that creates the particular factory method based on the orientation
     * parameter.
     *
     * @param width                       The width of the layout in dp.
     * @param height                      The height of the layout in dp.
     * @param heightMinusBrowserControls  The height of the layout minus the browser controls in dp.
     * @param borderFramePaddingTop       The top padding of the border frame in dp.
     * @param borderFramePaddingTopOpaque The opaque top padding of the border frame in dp.
     * @param borderFramePaddingLeft      The left padding of the border frame in dp.
     * @param orientation                 The orientation that will be used to create the
     *                                    appropriate {@link StackAnimation}.
     * @return                            The TabSwitcherAnimationFactory instance.
     */
    public static StackAnimation createAnimationFactory(float width, float height,
            float heightMinusBrowserControls, float borderFramePaddingTop,
            float borderFramePaddingTopOpaque, float borderFramePaddingLeft, int orientation) {
        StackAnimation factory = null;
        switch (orientation) {
            case Orientation.LANDSCAPE:
                factory = new StackAnimationLandscape(width, height, heightMinusBrowserControls,
                        borderFramePaddingTop, borderFramePaddingTopOpaque, borderFramePaddingLeft);
                break;
            case Orientation.PORTRAIT:
            default:
                factory = new StackAnimationPortrait(width, height, heightMinusBrowserControls,
                        borderFramePaddingTop, borderFramePaddingTopOpaque, borderFramePaddingLeft);
                break;
        }

        return factory;
    }

    /**
     * The wrapper method responsible for delegating the animations request to the appropriate
     * helper method.  Not all parameters are used for each request.
     *
     * @param type          The type of animation to be created.  This is what
     *                      determines which helper method is called.
     * @param tabs          The tabs that make up the current stack that will
     *                      be animated.
     * @param focusIndex    The index of the tab that is the focus of this animation.
     * @param sourceIndex   The index of the tab that triggered this animation.
     * @param spacing       The default spacing between the tabs.
     * @param scrollOffset  The scroll offset in the current orientation.
     * @param warpSize      The warp size of the transform from scroll space to screen space.
     * @param discardRange  The range of the discard amount value.
     * @return              The resulting TabSwitcherAnimation that will animate the tabs.
     */
    public ChromeAnimation<?> createAnimatorSetForType(OverviewAnimationType type, StackTab[] tabs,
            int focusIndex, int sourceIndex, int spacing, float scrollOffset, float warpSize,
            float discardRange) {
        ChromeAnimation<?> set = null;

        if (tabs != null) {
            switch (type) {
                case ENTER_STACK:
                    set = createEnterStackAnimatorSet(tabs, focusIndex, spacing, warpSize);
                    break;
                case TAB_FOCUSED:
                    set = createTabFocusedAnimatorSet(tabs, focusIndex, spacing, warpSize);
                    break;
                case VIEW_MORE:
                    set = createViewMoreAnimatorSet(tabs, sourceIndex);
                    break;
                case REACH_TOP:
                    set = createReachTopAnimatorSet(tabs, warpSize);
                    break;
                case DISCARD:
                // Purposeful fall through
                case DISCARD_ALL:
                // Purposeful fall through
                case UNDISCARD:
                    set = createUpdateDiscardAnimatorSet(tabs, spacing, warpSize, discardRange);
                    break;
                case NEW_TAB_OPENED:
                    set = createNewTabOpenedAnimatorSet(tabs, focusIndex, discardRange);
                    break;
                case START_PINCH:
                    set = createStartPinchAnimatorSet(tabs);
                    break;
                case FULL_ROLL:
                    set = createFullRollAnimatorSet(tabs);
                    break;
                case NONE:
                    break;
            }
        }
        return set;
    }

    protected abstract float getScreenSizeInScrollDirection();

    protected abstract float getScreenPositionInScrollDirection(StackTab tab);

    protected abstract void addTiltScrollAnimation(ChromeAnimation<Animatable<?>> set,
            LayoutTab tab, float end, int duration, int startTime);

    /**
     * @return The direction the tab should come from as it is created.  -1 means top/right, 1 means
     *         bottom/left.
     */
    protected abstract int getTabCreationDirection();

    /**
     * Responsible for generating the animations that shows the stack
     * being entered.
     *
     * @param tabs       The tabs that make up the stack.  These are the
     *                   tabs that will be affected by the TabSwitcherAnimation.
     * @param focusIndex The focused index.  In this case, this is the index of
     *                   the tab that was being viewed before entering the stack.
     * @param spacing    The default spacing between tabs.
     * @param warpSize   The warp size of the transform from scroll space to screen space.
     * @return           The TabSwitcherAnimation instance that will tween the
     *                   tabs to create the appropriate animation.
     */
    protected abstract ChromeAnimation<?> createEnterStackAnimatorSet(
            StackTab[] tabs, int focusIndex, int spacing, float warpSize);

    /**
     * Responsible for generating the animations that shows a tab being
     * focused (the stack is being left).
     *
     * @param tabs       The tabs that make up the stack.  These are the
     *                   tabs that will be affected by the TabSwitcherAnimation.
     * @param focusIndex The focused index.  In this case, this is the index of
     *                   the tab clicked and is being brought up to view.
     * @param spacing    The default spacing between tabs.
     * @param warpSize   The warp size of the transform from scroll space to screen space.
     * @return           The TabSwitcherAnimation instance that will tween the
     *                   tabs to create the appropriate animation.
     */
    protected abstract ChromeAnimation<?> createTabFocusedAnimatorSet(
            StackTab[] tabs, int focusIndex, int spacing, float warpSize);

    /**
     * Responsible for generating the animations that Shows more of the selected tab.
     *
     * @param tabs          The tabs that make up the stack.  These are the
     *                      tabs that will be affected by the TabSwitcherAnimation.
     * @param selectedIndex The selected index. In this case, this is the index of
     *                      the tab clicked and is being brought up to view.
     * @return              The TabSwitcherAnimation instance that will tween the
     *                      tabs to create the appropriate animation.
     */
    protected abstract ChromeAnimation<?> createViewMoreAnimatorSet(
            StackTab[] tabs, int selectedIndex);

    /**
     * Responsible for generating the TabSwitcherAnimation that moves the tabs up so they
     * reach the to top the screen.
     *
     * @param tabs          The tabs that make up the stack.  These are the
     *                      tabs that will be affected by the TabSwitcherAnimation.
     * @param warpSize     The warp size of the transform from scroll space to screen space.
     * @return              The TabSwitcherAnimation instance that will tween the
     *                      tabs to create the appropriate animation.
     */
    protected abstract ChromeAnimation<?> createReachTopAnimatorSet(
            StackTab[] tabs, float warpSize);

    /**
     * Responsible for generating the animations that moves the tabs back in from
     * discard attempt or commit the current discard (if any). It also re-even the tabs
     * if one of then is removed.
     *
     * @param tabs         The tabs that make up the stack. These are the
     *                     tabs that will be affected by the TabSwitcherAnimation.
     * @param spacing      The default spacing between tabs.
     * @param warpSize     The warp size of the transform from scroll space to screen space.
     * @param discardRange The maximum value the discard amount.
     * @return             The TabSwitcherAnimation instance that will tween the
     *                     tabs to create the appropriate animation.
     */
    protected ChromeAnimation<?> createUpdateDiscardAnimatorSet(
            StackTab[] tabs, int spacing, float warpSize, float discardRange) {
        ChromeAnimation<Animatable<?>> set = new ChromeAnimation<Animatable<?>>();

        int dyingTabsCount = 0;
        float firstDyingTabOffset = 0;
        for (int i = 0; i < tabs.length; ++i) {
            StackTab tab = tabs[i];

            addTiltScrollAnimation(set, tab.getLayoutTab(), 0.0f, UNDISCARD_ANIMATION_DURATION, 0);

            if (tab.isDying()) {
                dyingTabsCount++;
                if (dyingTabsCount == 1) {
                    firstDyingTabOffset = getScreenPositionInScrollDirection(tab);
                }
            }
        }

        Interpolator interpolator = BakedBezierInterpolator.FADE_OUT_CURVE;

        int newIndex = 0;
        for (int i = 0; i < tabs.length; ++i) {
            StackTab tab = tabs[i];
            long startTime = (long) Math.max(0, TAB_REORDER_START_SPAN
                            / getScreenSizeInScrollDirection()
                            * (getScreenPositionInScrollDirection(tab) - firstDyingTabOffset));
            if (tab.isDying()) {
                float discard = tab.getDiscardAmount();
                if (discard == 0.0f) discard = isDefaultDiscardDirectionPositive() ? 0.0f : -0.0f;
                float s = Math.copySign(1.0f, discard);
                long duration = (long) (DISCARD_ANIMATION_DURATION
                        * (1.0f - Math.abs(discard / discardRange)));
                addAnimation(set, tab, DISCARD_AMOUNT, discard, discardRange * s, duration,
                        startTime, false, interpolator);
            } else {
                if (tab.getDiscardAmount() != 0.f) {
                    addAnimation(set, tab, DISCARD_AMOUNT, tab.getDiscardAmount(), 0.0f,
                            UNDISCARD_ANIMATION_DURATION, 0);
                }

                float newScrollOffset = StackTab.screenToScroll(spacing * newIndex, warpSize);

                // If the tab is not dying we want to readjust it's position
                // based on the new spacing requirements.  For a fully discarded tab, just
                // put it in the right place.
                if (tab.getDiscardAmount() >= discardRange) {
                    tab.setScrollOffset(newScrollOffset);
                    tab.setScale(SCALE_AMOUNT);
                } else {
                    float start = tab.getScrollOffset();
                    if (start != newScrollOffset) {
                        addAnimation(set, tab, SCROLL_OFFSET, start, newScrollOffset,
                                TAB_REORDER_DURATION, startTime);
                    }
                }
                newIndex++;
            }
        }
        return set;
    }

    /**
     * This is used to determine the discard direction when user just clicks X to close a tab.
     * On portrait, positive direction (x) is right hand side.
     * On landscape, positive direction (y) is towards bottom.
     * @return True, if default discard direction is positive.
     */
    protected abstract boolean isDefaultDiscardDirectionPositive();

    /**
     * Responsible for generating the animations that shows a new tab being opened.
     *
     * @param tabs          The tabs that make up the stack.  These are the
     *                      tabs that will be affected by the TabSwitcherAnimation.
     * @param focusIndex    The focused index.  In this case, this is the index of
     *                      the tab that was just created.
     * @param discardRange  The maximum value the discard amount.
     * @return              The TabSwitcherAnimation instance that will tween the
     *                      tabs to create the appropriate animation.
     */
    // TODO(dtrainor): Remove this after confirming nothing uses this.
    protected ChromeAnimation<?> createNewTabOpenedAnimatorSet(
            StackTab[] tabs, int focusIndex, float discardRange) {
        if (focusIndex < 0 || focusIndex >= tabs.length) return null;
        ChromeAnimation<Animatable<?>> set = new ChromeAnimation<Animatable<?>>();

        StackTab tab = tabs[focusIndex];
        tab.getLayoutTab().setVisible(false);
        tab.setXInStackInfluence(0.0f);
        tab.setYInStackInfluence(0.0f);
        tab.setDiscardFromClick(true);
        tab.setDiscardOriginX(tab.getLayoutTab().getOriginalContentWidth());
        tab.setDiscardOriginY(tab.getLayoutTab().getOriginalContentHeight() / 2.f);
        tab.getLayoutTab().setAlpha(0.0f);
        tab.getLayoutTab().setBorderAlpha(0.0f);
        addAnimation(set, tab, DISCARD_AMOUNT, getTabCreationDirection() * discardRange, 0.0f,
                TAB_OPENED_ANIMATION_DURATION, 0, false,
                ChromeAnimation.getAccelerateInterpolator());
        return set;
    }

    /**
     * Responsible for generating the animations that flattens tabs when a pinch begins.
     *
     * @param tabs The tabs that make up the stack. These are the tabs that will
     *             be affected by the animations.
     * @return     The TabSwitcherAnimation instance that will tween the tabs to
     *             create the appropriate animation.
     */
    protected ChromeAnimation<?> createStartPinchAnimatorSet(StackTab[] tabs) {
        ChromeAnimation<Animatable<?>> set = new ChromeAnimation<Animatable<?>>();

        for (int i = 0; i < tabs.length; ++i) {
            addTiltScrollAnimation(
                    set, tabs[i].getLayoutTab(), 0, START_PINCH_ANIMATION_DURATION, 0);
        }

        return set;
    }

    /**
     * Responsible for generating the animations that make all the tabs do a full roll.
     *
     * @param tabs The tabs that make up the stack. These are the tabs that will be affected by the
     *             animations.
     * @return     The TabSwitcherAnimation instance that will tween the tabs to create the
     *             appropriate animation.
     */
    protected ChromeAnimation<?> createFullRollAnimatorSet(StackTab[] tabs) {
        ChromeAnimation<Animatable<?>> set = new ChromeAnimation<Animatable<?>>();

        for (int i = 0; i < tabs.length; ++i) {
            LayoutTab layoutTab = tabs[i].getLayoutTab();
            // Set the pivot
            layoutTab.setTiltX(layoutTab.getTiltX(), layoutTab.getScaledContentHeight() / 2.0f);
            layoutTab.setTiltY(layoutTab.getTiltY(), layoutTab.getScaledContentWidth() / 2.0f);
            // Create the angle animation
            addTiltScrollAnimation(set, layoutTab, -360.0f, FULL_ROLL_ANIMATION_DURATION, 0);
        }

        return set;
    }

    /**
     * @return The offset for the toolbar to line the top up with the opaque component of the
     *         border.
     */
    protected float getToolbarOffsetToLineUpWithBorder() {
        final float toolbarHeight = mHeight - mHeightMinusBrowserControls;
        return toolbarHeight - mBorderTopOpaqueHeight;
    }
}
