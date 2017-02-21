// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.view.ViewGroup.LayoutParams;

import org.chromium.base.TraceEvent;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.OverscrollRefreshHandler;
import org.chromium.third_party.android.swiperefresh.SwipeRefreshLayout;

/**
 * An overscroll handler implemented in terms a modified version of the Android
 * compat library's SwipeRefreshLayout effect.
 */
public class SwipeRefreshHandler implements OverscrollRefreshHandler {
    // Synthetic delay between the {@link #didStopRefreshing()} signal and the
    // call to stop the refresh animation.
    private static final int STOP_REFRESH_ANIMATION_DELAY_MS = 500;

    // Max allowed duration of the refresh animation after a refresh signal,
    // guarding against cases where the page reload fails or takes too long.
    private static final int MAX_REFRESH_ANIMATION_DURATION_MS = 7500;

    // The modified AppCompat version of the refresh effect, handling all core
    // logic, rendering and animation.
    private final SwipeRefreshLayout mSwipeRefreshLayout;

    // The Tab where the swipe occurs.
    private Tab mTab;

    // The ContentViewCore with which the handler is associated. The handler
    // will set/unset itself as the default OverscrollRefreshHandler as the
    // association changes.
    private ContentViewCore mContentViewCore;

    // Async runnable for ending the refresh animation after the page first
    // loads a frame. This is used to provide a reasonable minimum animation time.
    private Runnable mStopRefreshingRunnable;

    // Handles removing the layout from the view hierarchy.  This is posted to ensure it does not
    // conflict with pending Android draws.
    private Runnable mDetachLayoutRunnable;

    // Accessibility utterance used to indicate refresh activation.
    private String mAccessibilityRefreshString;

    /**
     * Simple constructor to use when creating an OverscrollRefresh instance from code.
     *
     * @param context The associated context.
     * @param tab The Tab where the swipe occurs.
     */
    public SwipeRefreshHandler(Context context, Tab tab) {
        mTab = tab;
        mContentViewCore = mTab.getContentViewCore();

        mSwipeRefreshLayout = new SwipeRefreshLayout(context);
        mSwipeRefreshLayout.setLayoutParams(
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mSwipeRefreshLayout.setColorSchemeResources(R.color.light_active_color);
        // SwipeRefreshLayout.LARGE layouts appear broken on JellyBean.
        mSwipeRefreshLayout.setSize(SwipeRefreshLayout.DEFAULT);
        mSwipeRefreshLayout.setEnabled(false);

        setEnabled(true);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                cancelStopRefreshingRunnable();
                mSwipeRefreshLayout.postDelayed(
                        getStopRefreshingRunnable(), MAX_REFRESH_ANIMATION_DURATION_MS);
                if (mAccessibilityRefreshString == null) {
                    int resId = R.string.accessibility_swipe_refresh;
                    mAccessibilityRefreshString =
                            mContentViewCore.getContext().getResources().getString(resId);
                }
                mSwipeRefreshLayout.announceForAccessibility(mAccessibilityRefreshString);
                mTab.reload();
                RecordUserAction.record("MobilePullGestureReload");
            }
        });
        mSwipeRefreshLayout.setOnResetListener(new SwipeRefreshLayout.OnResetListener() {
            @Override
            public void onReset() {
                if (mDetachLayoutRunnable != null) return;
                mDetachLayoutRunnable = new Runnable() {
                    @Override
                    public void run() {
                        mDetachLayoutRunnable = null;
                        detachSwipeRefreshLayoutIfNecessary();
                    }
                };
                mSwipeRefreshLayout.post(mDetachLayoutRunnable);
            }
        });

        mContentViewCore.setOverscrollRefreshHandler(this);
    }

    /**
     * Destroys and cleans up itself.
     */
    public void destroy() {
        setEnabled(false);
        cancelStopRefreshingRunnable();
        mSwipeRefreshLayout.setOnRefreshListener(null);
        mContentViewCore.setOverscrollRefreshHandler(null);
    }

    /**
     * Notify the SwipeRefreshLayout that a refresh action has completed.
     * Defer the notification by a reasonable minimum to ensure sufficient
     * visiblity of the animation.
     */
    public void didStopRefreshing() {
        if (!mSwipeRefreshLayout.isRefreshing()) return;
        cancelStopRefreshingRunnable();
        mSwipeRefreshLayout.postDelayed(
                getStopRefreshingRunnable(), STOP_REFRESH_ANIMATION_DELAY_MS);
    }

    @Override
    public boolean start() {
        attachSwipeRefreshLayoutIfNecessary();
        return mSwipeRefreshLayout.start();
    }

    @Override
    public void pull(float delta) {
        TraceEvent.begin("SwipeRefreshHandler.pull");
        mSwipeRefreshLayout.pull(delta);
        TraceEvent.end("SwipeRefreshHandler.pull");
    }

    @Override
    public void release(boolean allowRefresh) {
        TraceEvent.begin("SwipeRefreshHandler.release");
        mSwipeRefreshLayout.release(allowRefresh);
        TraceEvent.end("SwipeRefreshHandler.release");
    }

    @Override
    public void reset() {
        cancelStopRefreshingRunnable();
        mSwipeRefreshLayout.reset();
    }

    @Override
    public void setEnabled(boolean enabled) {
        mSwipeRefreshLayout.setEnabled(enabled);
        if (!enabled) reset();
    }

    private void cancelStopRefreshingRunnable() {
        if (mStopRefreshingRunnable != null) {
            mSwipeRefreshLayout.removeCallbacks(mStopRefreshingRunnable);
        }
    }

    private void cancelDetachLayoutRunnable() {
        if (mDetachLayoutRunnable != null) {
            mSwipeRefreshLayout.removeCallbacks(mDetachLayoutRunnable);
            mDetachLayoutRunnable = null;
        }
    }

    private Runnable getStopRefreshingRunnable() {
        if (mStopRefreshingRunnable == null) {
            mStopRefreshingRunnable = new Runnable() {
                @Override
                public void run() {
                    mSwipeRefreshLayout.setRefreshing(false);
                }
            };
        }
        return mStopRefreshingRunnable;
    }

    // The animation view is attached/detached on-demand to minimize overlap
    // with composited SurfaceView content.
    private void attachSwipeRefreshLayoutIfNecessary() {
        cancelDetachLayoutRunnable();
        if (mSwipeRefreshLayout.getParent() == null) {
            mContentViewCore.getContainerView().addView(mSwipeRefreshLayout);
        }
    }

    private void detachSwipeRefreshLayoutIfNecessary() {
        cancelDetachLayoutRunnable();
        if (mSwipeRefreshLayout.getParent() != null) {
            mContentViewCore.getContainerView().removeView(mSwipeRefreshLayout);
        }
    }
}
