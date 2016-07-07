// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import org.chromium.base.annotations.SuppressFBWarnings;

/**
 * A throttled version of {@link ProgressBar}.  This steals calls to
 * {@link android.view.View#postInvalidateOnAnimation} and delays them if necessary to reach a
 * specific FPS.
 */
public class SlowedProgressBar extends ProgressBar {
    private long mLastDrawTimeMs = 0;
    private boolean mPendingInvalidation = false;
    private static final int MIN_MS_PER_FRAME = 66;
    private int mTargetProgress;

    private final Runnable mInvalidationRunnable = new Runnable() {
        @Override
        public void run() {
            mPendingInvalidation = false;
            invalidate();
        }
    };

    private final Runnable mUpdateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            setProgressInternal(mTargetProgress);
        }
    };

    /**
     * Create a new progress bar with range 0...100 and initial progress of 0.
     * @param context the application environment.
     */
    public SlowedProgressBar(Context context) {
        super(context, null);
    }

    /**
     * Create a new progress bar with range 0...100 and initial progress of 0.
     * @param context the application environment.
     * @param attrs the xml attributes that should be used to initialize this view.
     */
    public SlowedProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Create a new progress bar with range 0...100 and initial progress of 0.
     * @param context the application environment.
     * @param attrs the xml attributes that should be used to initialize this view.
     * @param defStyle the default style to apply to this view.
     */
    public SlowedProgressBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Keep track of the last time we drew.
     */
    @Override
    public void onDraw(Canvas canvas) {
        mLastDrawTimeMs = System.currentTimeMillis();
        super.onDraw(canvas);
    }

    /**
     * Delay invalidations to be no more than 1 per MIN_MS_PER_FRAME;
     * never allow more than one invalidation outstanding.
     */
    @Override
    public void postInvalidateOnAnimation() {
        if (mPendingInvalidation) return;
        long nextDrawTime = mLastDrawTimeMs + MIN_MS_PER_FRAME;
        long delay = Math.max(0, nextDrawTime - System.currentTimeMillis());
        mPendingInvalidation = true;
        postOnAnimationDelayed(mInvalidationRunnable, delay);
    }

    /**
     * Ensure invalidations always occurs in the scope of animation.
     * @param progress The progress value to set the visuals to.
     */
    @SuppressFBWarnings("CHROMIUM_SYNCHRONIZED_METHOD")
    @Override
    public synchronized void setProgress(int progress) {
        if (mTargetProgress == progress) return;
        mTargetProgress = progress;
        removeCallbacks(mUpdateProgressRunnable);
        postOnAnimation(mUpdateProgressRunnable);
    }

    /**
     * Called to update the progress visuals.
     * @param progress The progress value to set the visuals to.
     */
    protected void setProgressInternal(int progress) {
        super.setProgress(progress);
    }
}
