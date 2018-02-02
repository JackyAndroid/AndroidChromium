// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ProgressBar;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.components.variations.VariationsAssociatedData;
import org.chromium.ui.UiUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * Progress bar for use in the Toolbar view.
 */
public class ToolbarProgressBar extends ClipDrawableProgressBar {

    private static final String ANIMATION_FIELD_TRIAL_NAME = "ProgressBarAnimationAndroid";
    private static final String PROGRESS_BAR_UPDATE_COUNT_HISTOGRAM =
            "Omnibox.ProgressBarUpdateCount";
    private static final String PROGRESS_BAR_BREAK_POINT_UPDATE_COUNT_HISTOGRAM =
            "Omnibox.ProgressBarBreakPointUpdateCount";

    /**
     * Interface for progress bar animation interpolation logics.
     */
    interface AnimationLogic {
        /**
         * Resets internal data. It must be called on every loading start.
         * @param startProgress The progress for the animation to start at. This is used when the
         *                      animation logic switches.
         */
        void reset(float startProgress);

        /**
         * Returns interpolated progress for animation.
         *
         * @param targetProgress Actual page loading progress.
         * @param frameTimeSec   Duration since the last call.
         * @param resolution     Resolution of the displayed progress bar. Mainly for rounding.
         */
        float updateProgress(float targetProgress, float frameTimeSec, int resolution);
    }

    // The amount of time in ms that the progress bar has to be stopped before the indeterminate
    // animation starts.
    private static final long ANIMATION_START_THRESHOLD = 5000;

    private static final float THEMED_BACKGROUND_WHITE_FRACTION = 0.2f;
    private static final float THEMED_FOREGROUND_BLACK_FRACTION = 0.64f;
    private static final float ANIMATION_WHITE_FRACTION = 0.4f;

    private static final long PROGRESS_FRAME_TIME_CAP_MS = 50;
    private long mAlphaAnimationDurationMs = 140;
    private long mHidingDelayMs = 100;

    private boolean mIsStarted;
    private float mTargetProgress;
    private int mTargetProgressUpdateCount;
    private AnimationLogic mAnimationLogic;
    private boolean mAnimationInitialized;
    private int mMarginTop;
    private ViewGroup mControlContainer;
    private int mProgressStartCount;
    private int mThemeColor;

    /** Whether the smooth-indeterminate animation is running. */
    private boolean mIsRunningSmoothIndeterminate;

    /** If the animation logic being used for the progress bar is smooth-indeterminate. */
    private boolean mIsUsingSmoothIndeterminate;

    private ToolbarProgressBarAnimatingView mAnimatingView;

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            animateAlphaTo(0.0f);
            mIsRunningSmoothIndeterminate = false;
            if (mAnimatingView != null) mAnimatingView.cancelAnimation();
        }
    };

    private final Runnable mStartSmoothIndeterminate = new Runnable() {
        @Override
        public void run() {
            if (!mIsStarted) return;
            mIsRunningSmoothIndeterminate = true;
            mAnimationLogic.reset(getProgress());
            mProgressAnimator.start();

            int width = Math.abs(getDrawable().getBounds().right - getDrawable().getBounds().left);
            mAnimatingView.update(getProgress() * width);
            mAnimatingView.startAnimation();
        }
    };

    private final TimeAnimator mProgressAnimator = new TimeAnimator();
    {
        mProgressAnimator.setTimeListener(new TimeListener() {
            @Override
            public void onTimeUpdate(TimeAnimator animation, long totalTimeMs, long deltaTimeMs) {
                // Cap progress bar animation frame time so that it doesn't jump too much even when
                // the animation is janky.
                float progress = mAnimationLogic.updateProgress(mTargetProgress,
                        Math.min(deltaTimeMs, PROGRESS_FRAME_TIME_CAP_MS) * 0.001f, getWidth());
                progress = Math.max(progress, 0);
                ToolbarProgressBar.super.setProgress(progress);

                if (mAnimatingView != null) {
                    int width = Math.abs(
                            getDrawable().getBounds().right - getDrawable().getBounds().left);
                    mAnimatingView.update(progress * width);
                }

                if (getProgress() == mTargetProgress) {
                    if (!mIsStarted) postOnAnimationDelayed(mHideRunnable, mHidingDelayMs);
                    mProgressAnimator.end();
                    return;
                }
            }
        });
    }

    /**
     * Creates a toolbar progress bar.
     *
     * @param context the application environment.
     * @param attrs the xml attributes that should be used to initialize this view.
     */
    public ToolbarProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlpha(0.0f);

        // This tells accessibility services that progress bar changes are important enough to
        // announce to the user even when not focused.
        ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    /**
     * Prepare the progress bar for being attached to the window.
     * @param topMargin The progress bar's top margin.
     */
    public void prepareForAttach(int topMargin) {
        LayoutParams curParams = new LayoutParams(getLayoutParams());
        mMarginTop = topMargin;
        curParams.topMargin = mMarginTop;
        setLayoutParams(curParams);
    }

    /**
     * @param container This View's container.
     */
    public void setControlContainer(ViewGroup container) {
        mControlContainer = container;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (mAnimatingView != null) mAnimatingView.setAlpha(alpha);
    }

    @Override
    public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // If the size changed, the animation width needs to be manually updated.
        if (mAnimatingView != null) mAnimatingView.update(width * getProgress());
    }

    /**
     * Initializes animation based on command line configuration. This must be called when native
     * library is ready.
     */
    public void initializeAnimation() {
        if (mAnimationInitialized) return;
        mAnimationInitialized = true;

        assert mAnimationLogic == null;

        String animation = CommandLine.getInstance().getSwitchValue(
                ChromeSwitches.PROGRESS_BAR_ANIMATION);
        if (TextUtils.isEmpty(animation)) {
            animation = VariationsAssociatedData.getVariationParamValue(
                    ANIMATION_FIELD_TRIAL_NAME, ChromeSwitches.PROGRESS_BAR_ANIMATION);
        }

        if (TextUtils.equals(animation, "smooth")) {
            mAnimationLogic = new ProgressAnimationSmooth();
        } else if (TextUtils.equals(animation, "smooth-indeterminate")
                && Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mAnimationLogic = new ProgressAnimationSmooth();

            // The smooth-indeterminate will start running only after 5 seconds has passed with no
            // progress update. Until then, the default behavior will be used.
            mIsUsingSmoothIndeterminate = true;

            LayoutParams animationParams = new LayoutParams(getLayoutParams());
            animationParams.width = 1;
            animationParams.topMargin = mMarginTop;

            mAnimatingView = new ToolbarProgressBarAnimatingView(getContext(), animationParams);

            // The primary theme color may not have been set.
            if (mThemeColor != 0) {
                setThemeColor(mThemeColor, false);
            } else {
                setForegroundColor(getForegroundColor());
            }
            UiUtils.insertAfter(mControlContainer, mAnimatingView, this);
        } else if (TextUtils.equals(animation, "fast-start")) {
            mAnimationLogic = new ProgressAnimationFastStart();
        } else if (TextUtils.equals(animation, "linear")) {
            mAnimationLogic = new ProgressAnimationLinear();
        } else {
            assert TextUtils.isEmpty(animation) || TextUtils.equals(animation, "disabled");
        }
    }

    /**
     * Start showing progress bar animation.
     */
    public void start() {
        mIsStarted = true;
        mProgressStartCount++;

        if (mIsUsingSmoothIndeterminate) {
            removeCallbacks(mStartSmoothIndeterminate);
            postDelayed(mStartSmoothIndeterminate, ANIMATION_START_THRESHOLD);
        }

        mIsRunningSmoothIndeterminate = false;
        mTargetProgressUpdateCount = 0;
        resetProgressUpdateCount();
        super.setProgress(0.0f);
        if (mAnimationLogic != null) mAnimationLogic.reset(0.0f);
        removeCallbacks(mHideRunnable);
        animateAlphaTo(1.0f);
    }

    /**
     * @return True if the progress bar is showing and started.
     */
    public boolean isStarted() {
        return mIsStarted;
    }

    /**
     * Start hiding progress bar animation.
     * @param delayed Whether a delayed fading out animation should be posted.
     */
    public void finish(boolean delayed) {
        mIsStarted = false;

        if (delayed) {
            updateVisibleProgress();
            RecordHistogram.recordCount1000Histogram(PROGRESS_BAR_UPDATE_COUNT_HISTOGRAM,
                    getProgressUpdateCount());
            RecordHistogram.recordCount100Histogram(
                    PROGRESS_BAR_BREAK_POINT_UPDATE_COUNT_HISTOGRAM,
                    mTargetProgressUpdateCount);
        } else {
            removeCallbacks(mHideRunnable);
            animate().cancel();
            if (mAnimatingView != null) {
                removeCallbacks(mStartSmoothIndeterminate);
                mAnimatingView.cancelAnimation();
                mTargetProgress = 0;
            }
            mIsRunningSmoothIndeterminate = false;
            setAlpha(0.0f);
        }
    }

    /**
     * Set alpha show&hide animation duration. This is for faster testing.
     * @param alphaAnimationDurationMs Alpha animation duration in milliseconds.
     */
    @VisibleForTesting
    public void setAlphaAnimationDuration(long alphaAnimationDurationMs) {
        mAlphaAnimationDurationMs = alphaAnimationDurationMs;
    }

    /**
     * Set hiding delay duration. This is for faster testing.
     * @param hidngDelayMs Hiding delay duration in milliseconds.
     */
    @VisibleForTesting
    public void setHidingDelay(long hidngDelayMs) {
        mHidingDelayMs = hidngDelayMs;
    }

    /**
     * @return The number of times the progress bar has been triggered.
     */
    @VisibleForTesting
    public int getStartCountForTesting() {
        return mProgressStartCount;
    }

    /**
     * Reset the number of times the progress bar has been triggered.
     */
    @VisibleForTesting
    public void resetStartCountForTesting() {
        mProgressStartCount = 0;
    }

    private void animateAlphaTo(float targetAlpha) {
        float alphaDiff = targetAlpha - getAlpha();
        if (alphaDiff == 0.0f) return;

        long duration = (long) Math.abs(alphaDiff * mAlphaAnimationDurationMs);

        BakedBezierInterpolator interpolator = BakedBezierInterpolator.FADE_IN_CURVE;
        if (alphaDiff < 0) interpolator = BakedBezierInterpolator.FADE_OUT_CURVE;

        animate().alpha(targetAlpha)
                .setDuration(duration)
                .setInterpolator(interpolator);

        if (mAnimatingView != null) {
            mAnimatingView.animate().alpha(targetAlpha)
                    .setDuration(duration)
                    .setInterpolator(interpolator);
        }
    }

    private void updateVisibleProgress() {
        if (mAnimationLogic == null
                || (mIsUsingSmoothIndeterminate && !mIsRunningSmoothIndeterminate)) {
            super.setProgress(mTargetProgress);
            if (!mIsStarted) postOnAnimationDelayed(mHideRunnable, mHidingDelayMs);
        } else if (!mProgressAnimator.isStarted()) {
            mProgressAnimator.start();
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    // ClipDrawableProgressBar implementation.

    @Override
    public void setProgress(float progress) {
        if (!mIsStarted || mTargetProgress == progress) return;

        if (mIsUsingSmoothIndeterminate) {
            // If the progress bar was updated, reset the callback that triggers the
            // smooth-indeterminate animation.
            removeCallbacks(mStartSmoothIndeterminate);
            if (progress == 1.0) {
                if (mAnimatingView != null) mAnimatingView.cancelAnimation();
            } else if (mAnimatingView != null && !mAnimatingView.isRunning()) {
                postDelayed(mStartSmoothIndeterminate, ANIMATION_START_THRESHOLD);
            }
        }

        mTargetProgressUpdateCount += 1;
        mTargetProgress = progress;
        updateVisibleProgress();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mAnimatingView != null) mAnimatingView.setVisibility(visibility);
    }

    /**
     * Color the progress bar based on the toolbar theme color.
     * @param color The Android color the toolbar is using.
     */
    public void setThemeColor(int color, boolean isIncognito) {
        mThemeColor = color;

        // The default toolbar has specific colors to use.
        if ((ColorUtils.isUsingDefaultToolbarColor(getResources(), color)
                || !ColorUtils.isValidThemeColor(color)) && !isIncognito) {
            setForegroundColor(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.progress_bar_foreground));
            setBackgroundColor(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.progress_bar_background));
            return;
        }

        setForegroundColor(ColorUtils.getThemedAssetColor(color, isIncognito));

        if (mAnimatingView != null
                && (ColorUtils.shouldUseLightForegroundOnBackground(color) || isIncognito)) {
            mAnimatingView.setColor(ColorUtils.getColorWithOverlay(color, Color.WHITE,
                    ANIMATION_WHITE_FRACTION));
        }

        setBackgroundColor(ColorUtils.getColorWithOverlay(color, Color.WHITE,
                THEMED_BACKGROUND_WHITE_FRACTION));
    }

    @Override
    public void setForegroundColor(int color) {
        super.setForegroundColor(color);
        if (mAnimatingView != null) {
            mAnimatingView.setColor(ColorUtils.getColorWithOverlay(color, Color.WHITE,
                    ANIMATION_WHITE_FRACTION));
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return ProgressBar.class.getName();
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setCurrentItemIndex((int) (mTargetProgress * 100));
        event.setItemCount(100);
    }
}
