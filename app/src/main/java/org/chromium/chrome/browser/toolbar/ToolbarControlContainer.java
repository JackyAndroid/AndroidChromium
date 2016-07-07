// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.contextualsearch.SwipeRecognizer;
import org.chromium.chrome.browser.widget.ClipDrawableProgressBar.DrawingInfo;
import org.chromium.chrome.browser.widget.ControlContainer;
import org.chromium.chrome.browser.widget.ViewResourceFrameLayout;
import org.chromium.ui.UiUtils;
import org.chromium.ui.resources.dynamics.ViewResourceAdapter;

/**
 * Layout for the browser controls (omnibox, menu, tab strip, etc..).
 */
public class ToolbarControlContainer extends FrameLayout implements ControlContainer {
    private final float mTabStripHeight;

    private Toolbar mToolbar;
    private ToolbarViewResourceFrameLayout mToolbarContainer;
    private View mMenuBtn;

    private final SwipeRecognizer mSwipeRecognizer;
    private EdgeSwipeHandler mSwipeHandler;

    /**
     * Constructs a new control container.
     * <p>
     * This constructor is used when inflating from XML.
     *
     * @param context The context used to build this view.
     * @param attrs The attributes used to determine how to construct this view.
     */
    public ToolbarControlContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTabStripHeight = context.getResources().getDimension(R.dimen.tab_strip_height);
        mSwipeRecognizer = new SwipeRecognizerImpl(context);
    }

    @Override
    public ViewResourceAdapter getToolbarResourceAdapter() {
        return mToolbarContainer.getResourceAdapter();
    }

    @Override
    public void getProgressBarDrawingInfo(DrawingInfo drawingInfoOut) {
        // TODO(yusufo): Avoid casting to the layout without making the interface bigger.
        ((ToolbarLayout) mToolbar).getProgressBar().getDrawingInfo(drawingInfoOut);
    }

    @Override
    public int getToolbarBackgroundColor() {
        return ((ToolbarLayout) mToolbar).getToolbarDataProvider().getPrimaryColor();
    }

    @Override
    public void setSwipeHandler(EdgeSwipeHandler handler) {
        mSwipeHandler = handler;
        mSwipeRecognizer.setSwipeHandler(handler);
    }

    @Override
    public void onFinishInflate() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbarContainer = (ToolbarViewResourceFrameLayout) findViewById(R.id.toolbar_container);
        mMenuBtn = findViewById(R.id.menu_button);

        if (mToolbar instanceof ToolbarTablet) {
            // On tablet, draw a fake tab strip and toolbar until the compositor is ready to draw
            // the real tab strip. (On phone, the toolbar is made entirely of Android views, which
            // are already initialized.)
            setBackgroundResource(R.drawable.toolbar_background);
        }

        assert mToolbar != null;
        assert mMenuBtn != null;

        super.onFinishInflate();
    }

    /**
     * Invalidate the entire capturing bitmap region.
     */
    public void invalidateBitmap() {
        ((ToolbarViewResourceAdapter) getToolbarResourceAdapter()).forceInvalidate();
    }

    /**
     * Update whether the control container is ready to have the bitmap representation of
     * itself be captured.
     */
    public void setReadyForBitmapCapture(boolean ready) {
        mToolbarContainer.mReadyForBitmapCapture = ready;
    }

    /**
     * The layout that handles generating the toolbar view resource.
     */
    // Only publicly visible due to lint warnings.
    public static class ToolbarViewResourceFrameLayout extends ViewResourceFrameLayout {
        private boolean mReadyForBitmapCapture;

        public ToolbarViewResourceFrameLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected ViewResourceAdapter createResourceAdapter() {
            return new ToolbarViewResourceAdapter(
                    this, (Toolbar) findViewById(R.id.toolbar));
        }

        @Override
        protected boolean isReadyForCapture() {
            return mReadyForBitmapCapture;
        }
    }

    private static class ToolbarViewResourceAdapter extends ViewResourceAdapter {
        private final int mToolbarActualHeightPx;
        private final int mTabStripHeightPx;
        private final int[] mTempPosition = new int[2];

        private final View mToolbarContainer;
        private final Toolbar mToolbar;

        /** Builds the resource adapter for the toolbar. */
        public ToolbarViewResourceAdapter(View toolbarContainer, Toolbar toolbar) {
            super(toolbarContainer);

            mToolbarContainer = toolbarContainer;
            mToolbar = toolbar;
            int containerHeightResId = R.dimen.control_container_height;
            if (mToolbar instanceof CustomTabToolbar) {
                containerHeightResId = R.dimen.custom_tabs_control_container_height;
            }
            mToolbarActualHeightPx = toolbarContainer.getResources().getDimensionPixelSize(
                    containerHeightResId);
            mTabStripHeightPx = toolbarContainer.getResources().getDimensionPixelSize(
                    R.dimen.tab_strip_height);
        }

        /**
         * Force this resource to be recaptured in full, ignoring the checks
         * {@link #invalidate(Rect)} does.
         */
        public void forceInvalidate() {
            super.invalidate(null);
        }

        @Override
        public boolean isDirty() {
            return mToolbar != null && mToolbar.isReadyForTextureCapture() && super.isDirty();
        }

        @Override
        protected void onCaptureStart(Canvas canvas, Rect dirtyRect) {
            // Erase the canvas because assets drawn are not fully opaque and therefore painting
            // twice would be bad.
            canvas.save();
            canvas.clipRect(
                    0, 0,
                    mToolbarContainer.getWidth(), mToolbarContainer.getHeight());
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            canvas.restore();
            dirtyRect.set(0, 0, mToolbarContainer.getWidth(), mToolbarContainer.getHeight());

            mToolbar.setTextureCaptureMode(true);

            super.onCaptureStart(canvas, dirtyRect);
        }

        @Override
        protected void onCaptureEnd() {
            mToolbar.setTextureCaptureMode(false);
        }

        @Override
        protected void computeContentPadding(Rect outContentPadding) {
            outContentPadding.set(0, mTabStripHeightPx, mToolbarContainer.getWidth(),
                    mToolbarActualHeightPx);
        }

        @Override
        protected void computeContentAperture(Rect outContentAperture) {
            mToolbar.getLocationBarContentRect(outContentAperture);
            mToolbar.getPositionRelativeToContainer(mToolbarContainer, mTempPosition);
            outContentAperture.offset(mTempPosition[0], mTempPosition[1]);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Don't eat the event if we don't have a handler.
        if (mSwipeHandler == null) return false;

        // If we have ACTION_DOWN in this context, that means either no child consumed the event or
        // this class is the top UI at the event position. Then, we don't need to feed the event to
        // mGestureDetector here because the event is already once fed in onInterceptTouchEvent().
        // Moreover, we have to return true so that this class can continue to intercept all the
        // subsequent events.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !isOnTabStrip(event)) {
            return true;
        }

        return mSwipeRecognizer.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mSwipeHandler == null) return false;

        return mSwipeRecognizer.onTouchEvent(event);
    }

    private boolean isOnTabStrip(MotionEvent e) {
        return e.getY() <= mTabStripHeight;
    }

    private class SwipeRecognizerImpl extends SwipeRecognizer {
        public SwipeRecognizerImpl(Context context) {
            super(context);
        }

        @Override
        public boolean shouldRecognizeSwipe(MotionEvent e1, MotionEvent e2) {
            if (isOnTabStrip(e1)) return false;
            if (mToolbar.shouldIgnoreSwipeGesture()) return false;
            if (UiUtils.isKeyboardShowing(getContext(), ToolbarControlContainer.this)) return false;
            return true;
        }
    }
}
