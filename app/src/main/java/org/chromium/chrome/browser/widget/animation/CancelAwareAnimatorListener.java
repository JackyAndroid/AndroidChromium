// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;

/**
 * An {@link AnimatorListenerAdapter} that distinguishes cancel and end signal. Subclasses should
 * override {@link #onStart(Animator)}, {@link #onEnd(Animator)} and {@link #onCancel(Animator)}
 * instead of the standard callback functions.
 */
public class CancelAwareAnimatorListener extends AnimatorListenerAdapter {
    private boolean mIsCancelled;

    @Override
    public final void onAnimationStart(Animator animation) {
        mIsCancelled = false;
        onStart(animation);
    }

    @Override
    public final void onAnimationCancel(Animator animation) {
        mIsCancelled = true;
        onCancel(animation);
    }

    @Override
    public final void onAnimationEnd(Animator animation) {
        if (mIsCancelled) return;
        onEnd(animation);
    }

    /**
     * Notifies the start of the animator.
     */
    public void onStart(Animator animator) { }

    /**
     * Notifies that the animator was cancelled.
     */
    public void onCancel(Animator animator) { }

    /**
     * Notifies that the animator has finished running. This method will not be called if the
     * animator is canclled.
     */
    public void onEnd(Animator animator) { }
}
