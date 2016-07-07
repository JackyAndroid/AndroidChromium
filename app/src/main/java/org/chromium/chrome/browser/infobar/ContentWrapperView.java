// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

import java.util.ArrayList;

/**
 * A wrapper class designed to allow swapping out of child Views for animations.
 *
 * Once an InfoBar has been hidden and removed from the InfoBarContainer, it cannot be reused
 * because the main panel is discarded after the hiding animation.
 */
public class ContentWrapperView extends FrameLayout {
    // Index of the child View that will get swapped out during transitions.
    private static final int CONTENT_INDEX = 0;

    private final int mGravity;
    private final InfoBar mInfoBar;

    private View mViewToHide;
    private View mViewToShow;

    /**
     * Constructs a ContentWrapperView object.
     * @param context The context to create this View with.
     */
    public ContentWrapperView(Context context, InfoBar infoBar, View panel) {
        // Set up this ViewGroup.
        super(context);
        mInfoBar = infoBar;
        mGravity = Gravity.TOP;

        // Set up this view.
        Resources resources = context.getResources();
        LayoutParams wrapParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        setLayoutParams(wrapParams);
        setBackgroundColor(ApiCompatibilityUtils.getColor(resources, R.color.infobar_background));

        // Add a separator line that delineates different InfoBars.
        View separator = new View(context);
        separator.setBackgroundColor(
                ApiCompatibilityUtils.getColor(resources, R.color.infobar_background_separator));
        addView(separator, new LayoutParams(LayoutParams.MATCH_PARENT, getBoundaryHeight(context),
                mGravity));

        // Add the InfoBar content.
        addChildView(panel);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return !mInfoBar.areControlsEnabled();
    }

    /**
     * Calculates how tall the InfoBar boundary should be in pixels.
     * XHDPI devices and above get a double-tall boundary.
     * @return The height of the boundary.
     */
    static int getBoundaryHeight(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return density < 2.0f ? 1 : 2;
    }

    /**
     * @return the current View representing the InfoBar.
     */
    public boolean hasChildView() {
        // If there's a View that can be replaced, there will be at least two children for the View.
        // One of the Views will always be the InfoBar separator.
        return getChildCount() > 1;
    }

    /**
     * Detaches the View currently being shown and returns it for reparenting.
     * @return the View that is currently being shown.
     */
    public View detachCurrentView() {
        assert getChildCount() > 1;
        View view = getChildAt(CONTENT_INDEX);
        removeView(view);
        return view;
    }

    /**
     * Adds a View to this layout, before the InfoBar separator.
     * @param viewToAdd The View to add.
     */
    private void addChildView(View viewToAdd) {
        addView(viewToAdd, CONTENT_INDEX, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, mGravity));
    }

    /**
     * Prepares the animation needed to hide the current View and show the new one.
     * @param viewToShow View that will replace the currently shown child of this FrameLayout.
     */
    public void prepareTransition(View viewToShow) {
        assert mViewToHide == null && mViewToShow == null;

        // If it exists, the View that is being replaced will be the non-separator child and will
        // we in the second position.
        assert getChildCount() <= 2;
        if (hasChildView()) {
            mViewToHide = getChildAt(CONTENT_INDEX);
        }

        mViewToShow = viewToShow;
        assert mViewToHide != null || mViewToShow != null;
        assert mViewToHide != mViewToShow;
    }

    /**
     * Called when the animation is starting.
     */
    public void startTransition() {
        if (mViewToShow != null) {
            // Move the View to this container.
            ViewParent parent = mViewToShow.getParent();
            ((ViewGroup) parent).removeView(mViewToShow);
            addChildView(mViewToShow);

            // We're transitioning between two views; set the alpha so it doesn't pop in.
            if (mViewToHide != null) mViewToShow.setAlpha(0.0f);
        }
    }

    /**
     * Called when the animation is done.
     * At this point, we can get rid of the View that used to represent the InfoBar and re-enable
     * controls.
     */
    public void finishTransition() {
        if (mViewToHide != null) {
            removeView(mViewToHide);
        }
        getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        requestLayout();

        mViewToHide = null;
        mViewToShow = null;
        mInfoBar.setControlsEnabled(true);
    }

    /**
     * Returns the height of the View being shown.
     * If no new View is going to replace the current one (i.e. the InfoBar is being hidden), the
     * height is 0.
     */
    private int getViewToShowHeight() {
        return mViewToShow == null ? 0 : mViewToShow.getHeight();
    }

    /**
     * Returns the height of the View being hidden.
     * If there wasn't a View in the container (i.e. the InfoBar is being animated onto the screen),
     * then the height is 0.
     */
    private int getViewToHideHeight() {
        return mViewToHide == null ? 0 : mViewToHide.getHeight();
    }

    /**
     * @return the difference in height between the View being shown and the View being hidden.
     */
    public int getTransitionHeightDifference() {
        return getViewToShowHeight() - getViewToHideHeight();
    }

    /**
     * Creates animations for transitioning between the two Views.
     * @param animators ArrayList to append the transition Animators to.
     */
    public void getAnimationsForTransition(ArrayList<Animator> animators) {
        if (mViewToHide != null && mViewToShow != null) {
            ObjectAnimator hideAnimator;
            hideAnimator = ObjectAnimator.ofFloat(mViewToHide, "alpha", 1.0f, 0.0f);
            animators.add(hideAnimator);

            ObjectAnimator showAnimator;
            showAnimator = ObjectAnimator.ofFloat(mViewToShow, "alpha", 0.0f, 1.0f);
            animators.add(showAnimator);
        }
    }

    /**
     * Calculates a Rect that prevents this ContentWrapperView from overlapping its siblings.
     * Because of the way the InfoBarContainer stores its children, Android will cause the InfoBars
     * to overlap when a bar is slid towards the top of the screen.  This calculates a bounding box
     * around this ContentWrapperView that clips the InfoBar to be drawn solely in the space it was
     * occupying before being translated anywhere.
     * @return the calculated bounding box
     */
    public Rect getClippingRect() {
        int maxHeight = Math.max(getViewToHideHeight(), getViewToShowHeight());
        return new Rect(getLeft(), getTop(), getRight(), getTop() + maxHeight);
    }
}
