// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import org.chromium.base.ObserverList;
import org.chromium.base.ObserverList.RewindableIterator;

/**
 * A progress bar that smoothly animates incremental updates.
 * <p>
 * Consumers of this class need to be aware that calls to {@link #getProgress()} will return
 * the currently visible progress value and not the one set in the last call to
 * {@link #setProgress(int)}.
 */
public class SmoothProgressBar extends ProgressBar {

    /**
     * Allows observation of visual changes to the progress bar.
     */
    public interface ProgressChangeListener {
        /**
         * Triggered when the visible progress has changed.
         * @param progress The current progress value.
         */
        void onProgressChanged(int progress);

        /**
         * Triggered when the visibility of the progress bar has changed.
         * @param visibility The visibility of the progress bar.
         */
        void onProgressVisibilityChanged(int visibility);
    }

    private static final int MAX = 100;

    // The amount of time between subsequent progress updates. 16ms is chosen to make 60fps.
    private static final long PROGRESS_UPDATE_DELAY_MS = 16;

    private final ObserverList<ProgressChangeListener> mObservers;
    private final RewindableIterator<ProgressChangeListener> mObserversIterator;

    private boolean mIsAnimated = false;
    private int mTargetProgress;

    // Since the progress bar is being animated, the internal progress bar resolution should be
    // at least fine as the width, not MAX. This multiplier will be applied to input progress
    // to convert to a finer scale.
    private int mResolutionMutiplier = 1;

    private Runnable mUpdateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (getProgress() == mTargetProgress) return;
            if (!mIsAnimated) {
                setProgressInternal(mTargetProgress);
                return;
            }
            // Every time, the progress bar get's at least 20% closer to mTargetProcess.
            // Add 3 to guarantee progressing even if they only differ by 1.
            setProgressInternal(getProgress() + (mTargetProgress - getProgress() + 3) / 4);
            postOnAnimationDelayed(this, PROGRESS_UPDATE_DELAY_MS);
        }
    };

    /**
     * Create a new progress bar with range 0...100 and initial progress of 0.
     * @param context the application environment.
     * @param attrs the xml attributes that should be used to initialize this view.
     */
    public SmoothProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setMax(MAX * mResolutionMutiplier);

        mObservers = new ObserverList<ProgressChangeListener>();
        mObserversIterator = mObservers.rewindableIterator();

    }

    /**
     * Adds an observer to be notified of progress changes.
     * @param observer The observer to be added.
     */
    public void addProgressChangeListener(ProgressChangeListener observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes an observer to be notified of progress changes.
     * @param observer The observer to be removed.
     */
    public void removeProgressChangeListener(ProgressChangeListener observer) {
        mObservers.removeObserver(observer);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        int normalizedProgress = getProgress() / mResolutionMutiplier;

        // Choose an integer resolution multiplier that makes the scale at least fine as the width.
        mResolutionMutiplier = Math.max(1, (w + MAX - 1) / MAX);
        setMax(mResolutionMutiplier * MAX);
        setProgressInternal(normalizedProgress * mResolutionMutiplier);
    }

    @Override
    public void setProgress(int progress) {
        final int targetProgress = progress * mResolutionMutiplier;
        if (mTargetProgress == targetProgress) return;
        mTargetProgress = targetProgress;
        removeCallbacks(mUpdateProgressRunnable);
        postOnAnimation(mUpdateProgressRunnable);
    }

    /**
     * Sets whether to animate incremental updates or not.
     * @param isAnimated True if it is needed to animate incremental updates.
     */
    public void setAnimated(boolean isAnimated) {
        mIsAnimated = isAnimated;
    }

    /**
     * Called to update the progress visuals.
     * @param progress The progress value to set the visuals to.
     */
    protected void setProgressInternal(int progress) {
        super.setProgress(progress);

        if (mObserversIterator != null) {
            for (mObserversIterator.rewind(); mObserversIterator.hasNext();) {
                mObserversIterator.next().onProgressChanged(progress);
            }
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        if (mObserversIterator != null) {
            for (mObserversIterator.rewind(); mObserversIterator.hasNext();) {
                mObserversIterator.next().onProgressVisibilityChanged(visibility);
            }
        }
    }
}
