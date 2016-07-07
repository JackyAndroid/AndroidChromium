// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.text.Selection;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.ui.UiUtils;

/**
 * Location bar for tablet form factors.
 */
public class LocationBarTablet extends LocationBarLayout {

    private static final int KEYBOARD_MODE_CHANGE_DELAY_MS = 300;
    private static final long MAX_NTP_KEYBOARD_FOCUS_DURATION_MS = 200;

    private final Property<LocationBarTablet, Float> mUrlFocusChangePercentProperty =
            new Property<LocationBarTablet, Float>(Float.class, "") {
                @Override
                public Float get(LocationBarTablet object) {
                    return object.mUrlFocusChangePercent;
                }

                @Override
                public void set(LocationBarTablet object, Float value) {
                    setUrlFocusChangePercent(value);
                }
            };

    private final Runnable mKeyboardResizeModeTask = new Runnable() {
        @Override
        public void run() {
            getWindowDelegate().setWindowSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    };

    private View mBookmarkButton;
    private float mUrlFocusChangePercent;
    private Animator mUrlFocusChangeAnimator;
    private View[] mTargets;
    private final Rect mCachedTargetBounds = new Rect();

    /**
     * Constructor used to inflate from XML.
     */
    public LocationBarTablet(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBookmarkButton = findViewById(R.id.bookmark_button);
        mTargets = new View[] { mUrlBar, mDeleteButton };
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mTargets == null) return true;

        View selectedTarget = null;
        float selectedDistance = 0;
        // newX and newY are in the coordinates of the selectedTarget.
        float newX = 0;
        float newY = 0;
        for (View target : mTargets) {
            if (!target.isShown()) continue;

            mCachedTargetBounds.set(0, 0, target.getWidth(), target.getHeight());
            offsetDescendantRectToMyCoords(target, mCachedTargetBounds);
            float x = event.getX();
            float y = event.getY();
            float dx = distanceToRange(
                    mCachedTargetBounds.left, mCachedTargetBounds.right, x);
            float dy = distanceToRange(
                    mCachedTargetBounds.top, mCachedTargetBounds.bottom, y);
            float distance = Math.abs(dx) + Math.abs(dy);
            if (selectedTarget == null || distance < selectedDistance) {
                selectedTarget = target;
                selectedDistance = distance;
                newX = x + dx;
                newY = y + dy;
            }
        }

        if (selectedTarget == null) return false;

        event.setLocation(newX, newY);
        return selectedTarget.onTouchEvent(event);
    }

    // Returns amount by which to adjust to move value inside the given range.
    private static float distanceToRange(float min, float max, float value) {
        return value < min ? (min - value) : value > max ? (max - value) : 0;
    }

    @Override
    public void onUrlFocusChange(final boolean hasFocus) {
        super.onUrlFocusChange(hasFocus);

        removeCallbacks(mKeyboardResizeModeTask);

        if (mUrlFocusChangeAnimator != null && mUrlFocusChangeAnimator.isRunning()) {
            mUrlFocusChangeAnimator.cancel();
            mUrlFocusChangeAnimator = null;
        }

        if (getToolbarDataProvider().getNewTabPageForCurrentTab() == null) {
            finishUrlFocusChange(hasFocus);
            return;
        }

        Rect rootViewBounds = new Rect();
        getRootView().getLocalVisibleRect(rootViewBounds);
        float screenSizeRatio = (rootViewBounds.height()
                / (float) (Math.max(rootViewBounds.height(), rootViewBounds.width())));
        mUrlFocusChangeAnimator =
                ObjectAnimator.ofFloat(this, mUrlFocusChangePercentProperty, hasFocus ? 1f : 0f);
        mUrlFocusChangeAnimator.setDuration(
                (long) (MAX_NTP_KEYBOARD_FOCUS_DURATION_MS * screenSizeRatio));
        mUrlFocusChangeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mIsCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                mIsCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mIsCancelled) return;
                finishUrlFocusChange(hasFocus);
            }
        });
        mUrlFocusChangeAnimator.start();
    }

    private void finishUrlFocusChange(boolean hasFocus) {
        if (hasFocus) {
            if (mSecurityButton.getVisibility() == VISIBLE) mSecurityButton.setVisibility(GONE);
            if (getWindowDelegate().getWindowSoftInputMode()
                    != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN) {
                getWindowDelegate().setWindowSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            }
            UiUtils.showKeyboard(mUrlBar);
        } else {
            if (mSecurityButton.getVisibility() == GONE
                    && mSecurityButton.getDrawable() != null
                    && mSecurityButton.getDrawable().getIntrinsicWidth() > 0
                    && mSecurityButton.getDrawable().getIntrinsicHeight() > 0) {
                mSecurityButton.setVisibility(VISIBLE);
            }
            UiUtils.hideKeyboard(mUrlBar);
            Selection.setSelection(mUrlBar.getText(), 0);
            // Convert the keyboard back to resize mode (delay the change for an arbitrary
            // amount of time in hopes the keyboard will be completely hidden before making
            // this change).
            if (getWindowDelegate().getWindowSoftInputMode()
                    != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) {
                postDelayed(mKeyboardResizeModeTask, KEYBOARD_MODE_CHANGE_DELAY_MS);
            }
        }
    }

    /**
     * Updates percentage of current the URL focus change animation.
     * @param percent 1.0 is 100% focused, 0 is completely unfocused.
     */
    private void setUrlFocusChangePercent(float percent) {
        mUrlFocusChangePercent = percent;

        NewTabPage ntp = getToolbarDataProvider().getNewTabPageForCurrentTab();
        if (ntp != null) ntp.setUrlFocusChangeAnimationPercent(percent);
    }

    @Override
    protected void updateDeleteButtonVisibility() {
        boolean enabled = shouldShowDeleteButton();
        mDeleteButton.setVisibility(enabled ? VISIBLE : GONE);
        mBookmarkButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void updateLayoutParams() {
        // Calculate the bookmark/delete button margins.
        final MarginLayoutParams micLayoutParams =
                (MarginLayoutParams) mMicButton.getLayoutParams();
        int micSpace = ApiCompatibilityUtils.getMarginEnd(micLayoutParams);
        if (mMicButton.getVisibility() != View.GONE) micSpace += mMicButton.getWidth();

        final MarginLayoutParams deleteLayoutParams =
                (MarginLayoutParams) mDeleteButton.getLayoutParams();
        final MarginLayoutParams bookmarkLayoutParams =
                (MarginLayoutParams) mBookmarkButton.getLayoutParams();

        ApiCompatibilityUtils.setMarginEnd(deleteLayoutParams, micSpace);
        ApiCompatibilityUtils.setMarginEnd(bookmarkLayoutParams, micSpace);

        mDeleteButton.setLayoutParams(deleteLayoutParams);
        mBookmarkButton.setLayoutParams(bookmarkLayoutParams);

        super.updateLayoutParams();
    }
}
