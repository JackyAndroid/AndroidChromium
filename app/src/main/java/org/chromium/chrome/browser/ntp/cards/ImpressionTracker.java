// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

/**
 * A class that helps with tracking impressions.
 */
public class ImpressionTracker
        implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    /**
     * The Listener will be called back on each impression. Whenever at least 2/3 of the view's
     * height is visible, that counts as an impression. Note that this will get called often while
     * the view is visible; it's the implementer's responsibility to count only one impression or
     * reset the {@link ImpressionTracker}.
     *
     * @see ImpressionTracker#reset(View)
     * @see ImpressionTracker#wasTriggered()
     */
    public interface Listener {
        void onImpression();
    }

    /**
     * Currently tracked View. Can be {@code null} if the tracker was cleared.
     * @see #reset(View)
     */
    @Nullable
    private View mView;
    private final Listener mListener;
    private boolean mTriggered;

    /**
     * Creates an {@link ImpressionTracker}. {@code view} can be {@code null} if the tracked should
     * not be registered on any View at construction time.
     */
    public ImpressionTracker(@Nullable View view, Listener listener) {
        mListener = listener;
        reset(view);
    }

    /**
     * Changes the view the tracker should observe.
     * @param view The new View to observe. Set to {@code null} to completely stop observing.
     */
    public void reset(@Nullable View view) {
        // Unregister the listeners for the current view.
        if (mView != null) {
            mView.removeOnAttachStateChangeListener(this);
            if (ViewCompat.isAttachedToWindow(mView)) {
                mView.getViewTreeObserver().removeOnPreDrawListener(this);
            }
        }

        // Register the listeners for the new view.
        mView = view;
        if (mView != null) {
            // Listen to onPreDraw only if view is potentially visible (attached to the window).
            mView.addOnAttachStateChangeListener(this);
            if (ViewCompat.isAttachedToWindow(mView)) {
                mView.getViewTreeObserver().addOnPreDrawListener(this);
            }
        }
    }

    /** @return whether this observer called {@link Listener#onImpression()} at least once. */
    public boolean wasTriggered() {
        return mTriggered;
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        mView.getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        mView.getViewTreeObserver().removeOnPreDrawListener(this);
    }

    @Override
    public boolean onPreDraw() {
        ViewParent parent = mView.getParent();
        if (parent != null) {
            Rect rect = new Rect(0, 0, mView.getWidth(), mView.getHeight());

            // Track impression if at least 2/3 of the view is visible.
            // |getChildVisibleRect| returns false when the view is empty, which may happen when
            // dismissing or reassigning a View. In this case |rect| appears to be invalid.
            if (parent.getChildVisibleRect(mView, rect, null)
                    && rect.height() >= 2 * mView.getHeight() / 3) {
                mTriggered = true;
                mListener.onImpression();
            }
        }
        // Proceed with the current drawing pass.
        return true;
    }
}
