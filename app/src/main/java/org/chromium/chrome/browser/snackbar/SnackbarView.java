// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.CoordinatorLayout.LayoutParams;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.CompositorViewHolderBehavior;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * Visual representation of a snackbar. On phone it matches the width of the activity; on tablet it
 * has a fixed width and is anchored at the start-bottom corner of the current window.
 */
class SnackbarView {
    private static final int MAX_LINES = 5;

    private final Activity mActivity;
    private final ViewGroup mView;
    private final TemplatePreservingTextView mMessageView;
    private final TextView mActionButtonView;
    private final ImageView mProfileImageView;
    private final int mAnimationDuration;
    private final boolean mIsTablet;
    private ViewGroup mOriginalParent;
    private ViewGroup mParent;
    private Snackbar mSnackbar;

    // Variables used to calculate the virtual keyboard's height.
    private Rect mCurrentVisibleRect = new Rect();
    private Rect mPreviousVisibleRect = new Rect();

    private OnLayoutChangeListener mLayoutListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            adjustViewPosition();
        }
    };

    /**
     * Creates an instance of the {@link SnackbarView}.
     * @param activity The activity that displays the snackbar.
     * @param listener An {@link OnClickListener} that will be called when the action button is
     *                 clicked.
     * @param snackbar The snackbar to be displayed.
     */
    SnackbarView(Activity activity, OnClickListener listener, Snackbar snackbar) {
        mActivity = activity;
        mIsTablet = DeviceFormFactor.isTablet(activity);
        mOriginalParent = findParentView(activity);
        mParent = mOriginalParent;
        mView = (ViewGroup) LayoutInflater.from(activity).inflate(
                R.layout.snackbar, mParent, false);
        mAnimationDuration = mView.getResources()
                .getInteger(android.R.integer.config_mediumAnimTime);
        mMessageView = (TemplatePreservingTextView) mView.findViewById(R.id.snackbar_message);
        mActionButtonView = (TextView) mView.findViewById(R.id.snackbar_button);
        mActionButtonView.setOnClickListener(listener);
        mProfileImageView = (ImageView) mView.findViewById(R.id.snackbar_profile_image);

        updateInternal(snackbar, false);
    }

    void show() {
        addToParent();
        mView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                mView.removeOnLayoutChangeListener(this);
                mView.setTranslationY(mView.getHeight() + getLayoutParams().bottomMargin);
                Animator animator = ObjectAnimator.ofFloat(mView, View.TRANSLATION_Y, 0);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.setDuration(mAnimationDuration);
                startAnimatorOnSurfaceView(animator);
            }
        });
    }

    void dismiss() {
        // Disable action button during animation.
        mActionButtonView.setEnabled(false);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(mAnimationDuration);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mParent.removeOnLayoutChangeListener(mLayoutListener);
                mParent.removeView(mView);
            }
        });
        Animator moveDown = ObjectAnimator.ofFloat(mView, View.TRANSLATION_Y,
                mView.getHeight() + getLayoutParams().bottomMargin);
        moveDown.setInterpolator(new DecelerateInterpolator());
        Animator fadeOut = ObjectAnimator.ofFloat(mView, View.ALPHA, 0f);
        fadeOut.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);

        animatorSet.playTogether(fadeOut, moveDown);
        startAnimatorOnSurfaceView(animatorSet);
    }

    void adjustViewPosition() {
        mParent.getWindowVisibleDisplayFrame(mCurrentVisibleRect);
        // Only update if the visible frame has changed, otherwise there will be a layout loop.
        if (!mCurrentVisibleRect.equals(mPreviousVisibleRect)) {
            mPreviousVisibleRect.set(mCurrentVisibleRect);

            int keyboardHeight = mParent.getHeight() - mCurrentVisibleRect.bottom
                    + mCurrentVisibleRect.top;
            MarginLayoutParams lp = getLayoutParams();
            lp.bottomMargin = keyboardHeight;
            if (mIsTablet) {
                int margin = mParent.getResources()
                        .getDimensionPixelSize(R.dimen.snackbar_margin_tablet);
                ApiCompatibilityUtils.setMarginStart(lp, margin);
                lp.bottomMargin += margin;
                int width = mParent.getResources()
                        .getDimensionPixelSize(R.dimen.snackbar_width_tablet);
                lp.width = Math.min(width, mParent.getWidth() - 2 * margin);
            }
            mView.setLayoutParams(lp);
        }
    }

    /**
     * @see SnackbarManager#overrideParent(ViewGroup)
     */
    void overrideParent(ViewGroup overridingParent) {
        mParent.removeOnLayoutChangeListener(mLayoutListener);
        mParent = overridingParent == null ? mOriginalParent : overridingParent;
        if (isShowing()) {
            ((ViewGroup) mView.getParent()).removeView(mView);
        }
        addToParent();
    }

    boolean isShowing() {
        return mView.isShown();
    }

    /**
     * Sends an accessibility event to mMessageView announcing that this window was added so that
     * the mMessageView content description is read aloud if accessibility is enabled.
     */
    void announceforAccessibility() {
        mMessageView.announceForAccessibility(mMessageView.getContentDescription() + " "
                + mView.getResources().getString(R.string.bottom_bar_screen_position));
    }

    /**
     * Updates the view to display data from the given snackbar. No-op if the view is already
     * showing the given snackbar.
     * @param snackbar The snackbar to display
     * @return Whether update has actually been executed.
     */
    boolean update(Snackbar snackbar) {
        return updateInternal(snackbar, true);
    }

    private void addToParent() {
        // LayoutParams in CoordinatorLayout and FrameLayout cannot be used interchangeably. Thus
        // we create new LayoutParams every time.
        if (mParent instanceof CoordinatorLayout) {
            CoordinatorLayout.LayoutParams lp = new LayoutParams(getLayoutParams());
            lp.gravity = Gravity.BOTTOM | Gravity.START;
            lp.setBehavior(new CompositorViewHolderBehavior());
            mParent.addView(mView, lp);
        } else if (mParent instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(getLayoutParams());
            lp.gravity = Gravity.BOTTOM | Gravity.START;
            mParent.addView(mView, lp);
        } else {
            assert false : "Only FrameLayout and CoordinatorLayout are supported to show snackbars";
        }

        // Why setting listener on parent? It turns out that if we force a relayout in the layout
        // change listener of the view itself, the force layout flag will be reset to 0 when
        // layout() returns. Therefore we have to do request layout on one level above the requested
        // view.
        mParent.addOnLayoutChangeListener(mLayoutListener);
    }

    private boolean updateInternal(Snackbar snackbar, boolean animate) {
        if (mSnackbar == snackbar) return false;
        mSnackbar = snackbar;
        mMessageView.setMaxLines(snackbar.getSingleLine() ? 1 : MAX_LINES);
        mMessageView.setTemplate(snackbar.getTemplateText());
        setViewText(mMessageView, snackbar.getText(), animate);
        String actionText = snackbar.getActionText();

        int backgroundColor = snackbar.getBackgroundColor();
        if (backgroundColor == 0) {
            backgroundColor = ApiCompatibilityUtils.getColor(mView.getResources(),
                    R.color.snackbar_background_color);
        }

        if (mIsTablet) {
            // On tablet, snackbars have rounded corners.
            mView.setBackgroundResource(R.drawable.snackbar_background_tablet);
            GradientDrawable backgroundDrawable = (GradientDrawable) mView.getBackground().mutate();
            backgroundDrawable.setColor(backgroundColor);
        } else {
            mView.setBackgroundColor(backgroundColor);
        }

        if (actionText != null) {
            mActionButtonView.setVisibility(View.VISIBLE);
            setViewText(mActionButtonView, snackbar.getActionText(), animate);
        } else {
            mActionButtonView.setVisibility(View.GONE);
        }
        Bitmap profileImage = snackbar.getProfileImage();
        if (profileImage != null) {
            mProfileImageView.setVisibility(View.VISIBLE);
            mProfileImageView.setImageBitmap(profileImage);
        } else {
            mProfileImageView.setVisibility(View.GONE);
        }
        return true;
    }

    /**
     * @return The parent {@link ViewGroup} that {@link #mView} will be added to.
     */
    private ViewGroup findParentView(Activity activity) {
        if (activity instanceof ChromeActivity) {
            return ((ChromeActivity) activity).getCompositorViewHolder();
        } else {
            return (ViewGroup) activity.findViewById(android.R.id.content);
        }
    }

    /**
     * Starts the {@link Animator} with {@link SurfaceView} optimization disabled. If a
     * {@link SurfaceView} is not present in the given {@link Activity}, start the {@link Animator}
     * in the normal way.
     */
    private void startAnimatorOnSurfaceView(Animator animator) {
        if (mActivity instanceof ChromeActivity) {
            ((ChromeActivity) mActivity).getWindowAndroid().startAnimationOverContent(animator);
        } else {
            animator.start();
        }
    }

    private MarginLayoutParams getLayoutParams() {
        return (MarginLayoutParams) mView.getLayoutParams();
    }

    private void setViewText(TextView view, CharSequence text, boolean animate) {
        if (view.getText().toString().equals(text)) return;
        view.animate().cancel();
        if (animate) {
            view.setAlpha(0.0f);
            view.setText(text);
            view.animate().alpha(1.f).setDuration(mAnimationDuration).setListener(null);
        } else {
            view.setText(text);
        }
    }
}
