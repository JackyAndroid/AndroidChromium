// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone.stack;

import static org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.AnimatableAnimation.addAnimation;
import static org.chromium.chrome.browser.compositor.layouts.components.LayoutTab.Property.MAX_CONTENT_HEIGHT;
import static org.chromium.chrome.browser.compositor.layouts.components.LayoutTab.Property.SATURATION;
import static org.chromium.chrome.browser.compositor.layouts.components.LayoutTab.Property.SIDE_BORDER_SCALE;
import static org.chromium.chrome.browser.compositor.layouts.components.LayoutTab.Property.TILTY;
import static org.chromium.chrome.browser.compositor.layouts.components.LayoutTab.Property.TOOLBAR_ALPHA;
import static org.chromium.chrome.browser.compositor.layouts.components.LayoutTab.Property.TOOLBAR_Y_OFFSET;
import static org.chromium.chrome.browser.compositor.layouts.phone.stack.StackTab.Property.DISCARD_AMOUNT;
import static org.chromium.chrome.browser.compositor.layouts.phone.stack.StackTab.Property.SCALE;
import static org.chromium.chrome.browser.compositor.layouts.phone.stack.StackTab.Property.SCROLL_OFFSET;
import static org.chromium.chrome.browser.compositor.layouts.phone.stack.StackTab.Property.X_IN_STACK_INFLUENCE;
import static org.chromium.chrome.browser.compositor.layouts.phone.stack.StackTab.Property.X_IN_STACK_OFFSET;
import static org.chromium.chrome.browser.compositor.layouts.phone.stack.StackTab.Property.Y_IN_STACK_INFLUENCE;

import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.base.LocalizationUtils;

class StackAnimationLandscape extends StackAnimation {
    /**
     * Only Constructor.
     */
    public StackAnimationLandscape(float width, float height, float heightMinusBrowserControls,
            float borderFramePaddingTop, float borderFramePaddingTopOpaque,
            float borderFramePaddingLeft) {
        super(width, height, heightMinusBrowserControls, borderFramePaddingTop,
                borderFramePaddingTopOpaque, borderFramePaddingLeft);
    }

    @Override
    protected ChromeAnimation<?> createEnterStackAnimatorSet(
            StackTab[] tabs, int focusIndex, int spacing, float warpSize) {
        ChromeAnimation<Animatable<?>> set = new ChromeAnimation<Animatable<?>>();
        final float initialScrollOffset = StackTab.screenToScroll(0, warpSize);

        for (int i = 0; i < tabs.length; ++i) {
            StackTab tab = tabs[i];

            tab.resetOffset();
            tab.setScale(SCALE_AMOUNT);
            tab.setAlpha(1.f);
            tab.getLayoutTab().setToolbarAlpha(0.f);
            tab.getLayoutTab().setBorderScale(1.f);

            final float scrollOffset = StackTab.screenToScroll(i * spacing, warpSize);

            addAnimation(set, tab.getLayoutTab(), MAX_CONTENT_HEIGHT,
                    tab.getLayoutTab().getUnclampedOriginalContentHeight(),
                    mHeightMinusBrowserControls, ENTER_STACK_ANIMATION_DURATION, 0);
            if (i < focusIndex) {
                addAnimation(set, tab, SCROLL_OFFSET, initialScrollOffset, scrollOffset,
                        ENTER_STACK_ANIMATION_DURATION, 0);
            } else if (i > focusIndex) {
                tab.setScrollOffset(scrollOffset);
                addAnimation(set, tab, X_IN_STACK_OFFSET,
                        (mWidth > mHeight && LocalizationUtils.isLayoutRtl()) ? -mWidth : mWidth,
                        0.0f, ENTER_STACK_ANIMATION_DURATION, 0);
            } else {
                tab.setScrollOffset(scrollOffset);
                addAnimation(set, tab, X_IN_STACK_INFLUENCE, 0.0f, 1.0f,
                        ENTER_STACK_BORDER_ALPHA_DURATION, 0);
                addAnimation(
                        set, tab, SCALE, 1.0f, SCALE_AMOUNT, ENTER_STACK_BORDER_ALPHA_DURATION, 0);
                addAnimation(set, tab.getLayoutTab(), TOOLBAR_ALPHA, 1.f, 0.f,
                        ENTER_STACK_TOOLBAR_ALPHA_DURATION, ENTER_STACK_TOOLBAR_ALPHA_DELAY);
                addAnimation(set, tab.getLayoutTab(), TOOLBAR_Y_OFFSET, 0.f,
                        getToolbarOffsetToLineUpWithBorder(), ENTER_STACK_BORDER_ALPHA_DURATION,
                        TAB_FOCUSED_TOOLBAR_ALPHA_DELAY);
                addAnimation(set, tab.getLayoutTab(), SIDE_BORDER_SCALE, 0.f, 1.f,
                        ENTER_STACK_BORDER_ALPHA_DURATION, TAB_FOCUSED_TOOLBAR_ALPHA_DELAY);
            }
        }

        return set;
    }

    @Override
    protected ChromeAnimation<?> createTabFocusedAnimatorSet(
            StackTab[] tabs, int focusIndex, int spacing, float warpSize) {
        ChromeAnimation<Animatable<?>> set = new ChromeAnimation<Animatable<?>>();
        for (int i = 0; i < tabs.length; ++i) {
            StackTab tab = tabs[i];
            LayoutTab layoutTab = tab.getLayoutTab();

            addTiltScrollAnimation(set, layoutTab, 0.0f, TAB_FOCUSED_ANIMATION_DURATION, 0);
            addAnimation(set, tab, DISCARD_AMOUNT, tab.getDiscardAmount(), 0.0f,
                    TAB_FOCUSED_ANIMATION_DURATION, 0);

            if (i < focusIndex) {
                // For tabs left of the focused tab move them left to 0.
                addAnimation(set, tab, SCROLL_OFFSET, tab.getScrollOffset(),
                        Math.max(0.0f, tab.getScrollOffset() - mWidth - spacing),
                        TAB_FOCUSED_ANIMATION_DURATION, 0);
            } else if (i > focusIndex) {
                // We also need to animate the X Translation to move them right
                // off the screen.
                float coveringTabPosition = layoutTab.getX();
                float distanceToBorder = LocalizationUtils.isLayoutRtl()
                        ? coveringTabPosition + layoutTab.getScaledContentWidth()
                        : mWidth - coveringTabPosition;
                float clampedDistanceToBorder = MathUtils.clamp(distanceToBorder, 0, mWidth);
                float delay = TAB_FOCUSED_MAX_DELAY * clampedDistanceToBorder / mWidth;
                addAnimation(set, tab, X_IN_STACK_OFFSET, tab.getXInStackOffset(),
                        tab.getXInStackOffset()
                                + (LocalizationUtils.isLayoutRtl() ? -mWidth : mWidth),
                        (TAB_FOCUSED_ANIMATION_DURATION - (long) delay), (long) delay);
            } else {
                // This is the focused tab.  We need to scale it back to
                // 1.0f, move it to the top of the screen, and animate the
                // X Translation so that it looks like it is zooming into the
                // full screen view.  We move the card to the top left and extend it out so
                // it becomes a full card.
                tab.setXOutOfStack(0);
                tab.setYOutOfStack(0.0f);
                layoutTab.setBorderScale(1.f);

                addAnimation(set, tab, SCROLL_OFFSET, tab.getScrollOffset(),
                        StackTab.screenToScroll(0, warpSize), TAB_FOCUSED_ANIMATION_DURATION, 0);
                addAnimation(
                        set, tab, SCALE, tab.getScale(), 1.0f, TAB_FOCUSED_ANIMATION_DURATION, 0);
                addAnimation(set, tab, X_IN_STACK_INFLUENCE, tab.getXInStackInfluence(), 0.0f,
                        TAB_FOCUSED_ANIMATION_DURATION, 0);
                addAnimation(set, tab, Y_IN_STACK_INFLUENCE, tab.getYInStackInfluence(), 0.0f,
                        TAB_FOCUSED_Y_STACK_DURATION, 0);

                addAnimation(set, tab.getLayoutTab(), MAX_CONTENT_HEIGHT,
                        tab.getLayoutTab().getMaxContentHeight(),
                        tab.getLayoutTab().getUnclampedOriginalContentHeight(),
                        TAB_FOCUSED_ANIMATION_DURATION, 0);
                tab.setYOutOfStack(mHeight - mHeightMinusBrowserControls - mBorderTopHeight);

                if (layoutTab.shouldStall()) {
                    addAnimation(set, layoutTab, SATURATION, 1.0f, 0.0f,
                            TAB_FOCUSED_BORDER_ALPHA_DURATION, TAB_FOCUSED_BORDER_ALPHA_DELAY);
                }
                addAnimation(set, tab.getLayoutTab(), TOOLBAR_ALPHA, layoutTab.getToolbarAlpha(),
                        1.f, TAB_FOCUSED_TOOLBAR_ALPHA_DURATION, TAB_FOCUSED_TOOLBAR_ALPHA_DELAY);
                addAnimation(set, tab.getLayoutTab(), TOOLBAR_Y_OFFSET,
                        getToolbarOffsetToLineUpWithBorder(), 0.f,
                        TAB_FOCUSED_TOOLBAR_ALPHA_DURATION, TAB_FOCUSED_TOOLBAR_ALPHA_DELAY);
                addAnimation(set, tab.getLayoutTab(), SIDE_BORDER_SCALE, 1.f, 0.f,
                        TAB_FOCUSED_TOOLBAR_ALPHA_DURATION, TAB_FOCUSED_TOOLBAR_ALPHA_DELAY);
            }
        }

        return set;
    }

    @Override
    protected ChromeAnimation<?> createViewMoreAnimatorSet(StackTab[] tabs, int selectedIndex) {
        ChromeAnimation<Animatable<?>> set = new ChromeAnimation<Animatable<?>>();

        if (selectedIndex + 1 >= tabs.length) return set;

        float offset = tabs[selectedIndex].getScrollOffset()
                - tabs[selectedIndex + 1].getScrollOffset()
                + (tabs[selectedIndex].getLayoutTab().getScaledContentWidth()
                               * VIEW_MORE_SIZE_RATIO);
        offset = Math.max(VIEW_MORE_MIN_SIZE, offset);
        for (int i = selectedIndex + 1; i < tabs.length; ++i) {
            addAnimation(set, tabs[i], SCROLL_OFFSET, tabs[i].getScrollOffset(),
                    tabs[i].getScrollOffset() + offset, VIEW_MORE_ANIMATION_DURATION, 0);
        }

        return set;
    }

    @Override
    protected ChromeAnimation<?> createReachTopAnimatorSet(StackTab[] tabs, float warpSize) {
        ChromeAnimation<Animatable<?>> set = new ChromeAnimation<Animatable<?>>();

        float screenTarget = 0.0f;
        for (int i = 0; i < tabs.length; ++i) {
            if (screenTarget >= tabs[i].getLayoutTab().getX()) {
                break;
            }
            addAnimation(set, tabs[i], SCROLL_OFFSET, tabs[i].getScrollOffset(),
                    StackTab.screenToScroll(screenTarget, warpSize), REACH_TOP_ANIMATION_DURATION,
                    0);
            screenTarget += tabs[i].getLayoutTab().getScaledContentWidth();
        }

        return set;
    }

    @Override
    protected boolean isDefaultDiscardDirectionPositive() {
        return true;
    }

    @Override
    protected float getScreenPositionInScrollDirection(StackTab tab) {
        return tab.getLayoutTab().getX();
    }

    @Override
    protected void addTiltScrollAnimation(ChromeAnimation<Animatable<?>> set, LayoutTab tab,
            float end, int duration, int startTime) {
        addAnimation(set, tab, TILTY, tab.getTiltY(), end, duration, startTime);
    }

    @Override
    protected float getScreenSizeInScrollDirection() {
        return mWidth;
    }

    @Override
    protected int getTabCreationDirection() {
        return 1;
    }
}
