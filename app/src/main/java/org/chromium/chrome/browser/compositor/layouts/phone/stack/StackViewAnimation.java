// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone.stack;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import org.chromium.chrome.browser.compositor.layouts.phone.stack.StackAnimation.OverviewAnimationType;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * A factory that builds Android view animations for the tab stack.
 */
public class StackViewAnimation {
    private static final int TAB_OPENED_ANIMATION_DURATION = 300;
    private static final float TAB_OPENED_PIVOT_INSET_DP = 24.f;

    private final float mDpToPx;
    private final float mWidthDp;

    /**
     * Constructor.
     * NOTE: Pass in height and heightMinusBrowserControls if they're ever needed.
     *
     * @param dpToPx                   The density of the device.
     * @param widthDp                  The width of the layout in dp.
     */
    public StackViewAnimation(float dpToPx, float widthDp) {
        mDpToPx = dpToPx;
        mWidthDp = widthDp;
    }

    /**
     * The wrapper method responsible for delegating animation requests to the appropriate helper
     * method.
     * @param type       The type of animation to be created.  This is what determines which helper
     *                   method is called.
     * @param tabs       The tabs that make up the current stack.
     * @param container  The {@link ViewGroup} that {@link View}s can be added to/removed from.
     * @param model      The {@link TabModel} that this animation will influence.
     * @param focusIndex The index of the tab that is the focus of this animation.
     * @return           The resulting {@link Animator} that will animate the Android views.
     */
    public Animator createAnimatorForType(OverviewAnimationType type, StackTab[] tabs,
            ViewGroup container, TabModel model, int focusIndex) {
        Animator animator = null;

        if (model != null) {
            switch (type) {
                case NEW_TAB_OPENED:
                    animator = createNewTabOpenedAnimator(tabs, container, model, focusIndex);
                    break;
                default:
                    break;
            }
        }

        return animator;
    }

    private Animator createNewTabOpenedAnimator(
            StackTab[] tabs, ViewGroup container, TabModel model, int focusIndex) {
        Tab tab = model.getTabAt(focusIndex);
        if (tab == null || !tab.isNativePage()) return null;

        View view = tab.getView();
        if (view == null) return null;

        // Set up the view hierarchy
        if (view.getParent() != null) ((ViewGroup) view.getParent()).removeView(view);
        ViewGroup bgView = new FrameLayout(view.getContext());
        bgView.setBackgroundColor(tab.getBackgroundColor());
        bgView.addView(view);
        container.addView(
                bgView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Update any compositor state that needs to change
        if (tabs != null && focusIndex >= 0 && focusIndex < tabs.length) {
            tabs[focusIndex].setAlpha(0.f);
        }

        // Build the view animations
        PropertyValuesHolder xScale = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.f, 1.f);
        PropertyValuesHolder yScale = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.f, 1.f);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.f, 1.f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
                bgView, xScale, yScale, alpha);

        animator.setDuration(TAB_OPENED_ANIMATION_DURATION);
        animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_FOLLOW_THROUGH_CURVE);

        float insetPx = TAB_OPENED_PIVOT_INSET_DP * mDpToPx;

        bgView.setPivotY(TAB_OPENED_PIVOT_INSET_DP);
        bgView.setPivotX(LocalizationUtils.isLayoutRtl() ? mWidthDp * mDpToPx - insetPx : insetPx);
        return animator;
    }
}