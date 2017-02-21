// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.CoordinatorLayout.Behavior;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.banners.SwipableOverlayView;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * Parent {@link FrameLayout} holding the infobar and content of a tab. The content could be either
 * a native page or a content view.
 */
public class TabContentViewParent extends FrameLayout {
    private static final int CONTENT_INDEX = 0;

    // A wrapper is needed because infobar's translation is controlled by SwipableOverlayView.
    // Setting infobar's translation directly from this class will cause UI flickering.
    private final FrameLayout mInfobarWrapper;
    private final Behavior<?> mBehavior = new SnackbarAwareBehavior();

    private EmptyTabObserver mTabObserver = new EmptyTabObserver() {
        /**
         * @return the {@link View} to show for the given {@link Tab}.
         */
        private View getViewToShow(Tab tab) {
            if (tab.getNativePage() != null) {
                return tab.getNativePage().getView();
            } else if (tab.getBlimpContents() != null) {
                return tab.getBlimpContents().getView();
            } else {
                return tab.getContentViewCore().getContainerView();
            }
        }

        @Override
        public void onContentChanged(Tab tab) {
            // If the tab is frozen, both native page and content view are not ready.
            if (tab.isFrozen()) return;

            View viewToShow = getViewToShow(tab);
            if (isShowing(viewToShow)) return;

            removeCurrentContent();
            LayoutParams lp = (LayoutParams) viewToShow.getLayoutParams();
            if (lp == null) {
                lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            }
            // Weirdly enough, if gravity is not top, top_margin is not respected by FrameLayout.
            // Yet for many native pages on tablet, top_margin is necessary to not overlap the tab
            // switcher.
            lp.gravity = Gravity.TOP;
            UiUtils.removeViewFromParent(viewToShow);
            addView(viewToShow, CONTENT_INDEX, lp);
            viewToShow.requestFocus();
        }
    };

    public TabContentViewParent(Context context, Tab tab) {
        super(context);
        mInfobarWrapper = new FrameLayout(context);
        mInfobarWrapper.setFocusable(true);
        mInfobarWrapper.setFocusableInTouchMode(true);
        addView(mInfobarWrapper,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tab.addObserver(mTabObserver);
    }

    /**
     * Attach the infobar container to the view hierarchy.
     */
    public void addInfobarView(SwipableOverlayView infobarView, MarginLayoutParams lp) {
        mInfobarWrapper.addView(infobarView, lp);
    }

    /**
     * @return The {@link Behavior} that controls how children of this class animate together.
     */
    public Behavior<?> getBehavior() {
        return mBehavior;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mInfobarWrapper.setTranslationY(0f);
    }

    /**
     * @return Whether the given {@link View} is already in the view hierarchy.
     */
    private boolean isShowing(View view) {
        return view.getParent() == this;
    }

    private void removeCurrentContent() {
        // Native page or content view should always be at index 0.
        if (getChildCount() > 1) removeViewAt(CONTENT_INDEX);
    }

    private static class SnackbarAwareBehavior
            extends CoordinatorLayout.Behavior<TabContentViewParent> {
        @Override
        public boolean layoutDependsOn(CoordinatorLayout parent, TabContentViewParent child,
                View dependency) {
            // Disable coordination on tablet as they appear at different location on tablet.
            return dependency.getId() == R.id.snackbar
                    && !DeviceFormFactor.isTablet(child.getContext());
        }

        @Override
        public boolean onDependentViewChanged(CoordinatorLayout parent, TabContentViewParent child,
                View dependency) {
            child.mInfobarWrapper
                    .setTranslationY(dependency.getTranslationY() - dependency.getHeight());
            return true;
        }

        @Override
        public void onDependentViewRemoved(CoordinatorLayout parent, TabContentViewParent child,
                View dependency) {
            child.mInfobarWrapper.setTranslationY(0);
        }
    }
}
