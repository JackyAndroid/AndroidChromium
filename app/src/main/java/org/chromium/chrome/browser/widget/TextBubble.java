// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * UI component that handles showing text bubbles.
 */
public abstract class TextBubble extends PopupWindow implements OnLayoutChangeListener {
    /** How much of the anchor should be overlapped. */
    private final float mYOverlapPercentage;

    private final Rect mCachedPaddingRect = new Rect();

    private int mXPosition;
    private int mYPosition;
    private int mWidth;
    private int mHeight;

    private View mAnchorView;
    private View mContentView;

    /**
     * Constructs a TextBubble that will point at a particular view.
     * @param context            Context to draw resources from.
     * @param yOverlapPercentage How much the arrow should overlap the view.
     */
    public TextBubble(Context context, float yOverlapPercentage) {
        super(context);
        mYOverlapPercentage = yOverlapPercentage;

        setBackgroundDrawable(new BubbleBackgroundDrawable(context));
        getBackground().getPadding(mCachedPaddingRect);

        mContentView = createContent(context);
        setContentView(mContentView);
        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Creates the View that contains everything that should be displayed inside the bubble.
     */
    protected abstract View createContent(Context context);

    /**
     * Shows a text bubble anchored to the given view.
     *
     * @param anchorView The view that the bubble should be anchored to.
     */
    public void show(View anchorView) {
        mAnchorView = anchorView;
        calculateNewPosition();
        showAtCalculatedPosition();
    }

    /**
     * Calculates the new position for the bubble, updating mXPosition, mYPosition, mYOffset and
     * the bubble arrow offset information without updating the UI. To see the changes,
     * showAtCalculatedPosition should be called explicitly.
     */
    private void calculateNewPosition() {
        measureContentView();

        // Center the bubble below of the anchor, arrow pointing upward.  The overlap determines how
        // much of the bubble's arrow overlaps the anchor view.
        int[] anchorCoordinates = {0, 0};
        mAnchorView.getLocationOnScreen(anchorCoordinates);
        anchorCoordinates[0] += mAnchorView.getWidth() / 2;
        anchorCoordinates[1] += (int) (mAnchorView.getHeight() * (1.0 - mYOverlapPercentage));

        mWidth = mContentView.getMeasuredWidth()
                + mCachedPaddingRect.left + mCachedPaddingRect.right;
        mHeight = mContentView.getMeasuredHeight()
                + mCachedPaddingRect.top + mCachedPaddingRect.bottom;
        mXPosition = anchorCoordinates[0] - (mWidth / 2);
        mYPosition = anchorCoordinates[1];

        // Make sure the bubble stays on screen.
        View rootView = mAnchorView.getRootView();
        if (mXPosition > rootView.getWidth() - mWidth) {
            mXPosition = rootView.getWidth() - mWidth;
        } else if (mXPosition < 0) {
            mXPosition = 0;
        }

        // Center the tip of the arrow.
        int tipCenterXPosition = anchorCoordinates[0] - mXPosition;
        ((BubbleBackgroundDrawable) getBackground()).setBubbleArrowXCenter(tipCenterXPosition);

        // Update the popup's dimensions.
        setWidth(MeasureSpec.makeMeasureSpec(mWidth, MeasureSpec.EXACTLY));
        setHeight(MeasureSpec.makeMeasureSpec(mHeight, MeasureSpec.EXACTLY));
    }

    private void measureContentView() {
        View rootView = mAnchorView.getRootView();
        getBackground().getPadding(mCachedPaddingRect);

        // The maximum width of the bubble is determined by how wide the root view is.
        int maxContentWidth =
                rootView.getWidth() - mCachedPaddingRect.left - mCachedPaddingRect.right;

        // The maximum height of the bubble is determined by the available space below the anchor.
        int anchorYOverlap = (int) -(mYOverlapPercentage * mAnchorView.getHeight());
        int maxContentHeight = getMaxAvailableHeight(mAnchorView, anchorYOverlap)
                - mCachedPaddingRect.top - mCachedPaddingRect.bottom;

        int contentWidthSpec = MeasureSpec.makeMeasureSpec(maxContentWidth, MeasureSpec.AT_MOST);
        int contentHeightSpec = MeasureSpec.makeMeasureSpec(maxContentHeight, MeasureSpec.AT_MOST);
        mContentView.measure(contentWidthSpec, contentHeightSpec);
    }

    /**
     * Shows the TextBubble in the precalculated position.
     */
    private void showAtCalculatedPosition() {
        mAnchorView.addOnLayoutChangeListener(this);
        showAtLocation(mAnchorView.getRootView(), Gravity.TOP | Gravity.START,
                mXPosition, mYPosition);
    }

    /**
     * Updates the position information and checks whether any positional change will occur. This
     * method doesn't change the {@link TextBubble} if it is showing.
     * @return Whether the TextBubble needs to be redrawn.
     */
    private boolean updatePosition() {
        BubbleBackgroundDrawable background = (BubbleBackgroundDrawable) getBackground();

        int previousX = mXPosition;
        int previousY = mYPosition;
        int previousOffset = background.getBubbleArrowXCenter();
        calculateNewPosition();

        return previousX != mXPosition || previousY != mYPosition
                || previousOffset != background.getBubbleArrowXCenter();
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        boolean willDisappear = !mAnchorView.isShown();
        boolean changePosition = updatePosition();
        if (willDisappear) {
            dismiss();
        } else if (changePosition) {
            update(mXPosition, mYPosition, mWidth, mHeight);
        }
    }

    @Override
    public void dismiss() {
        if (mAnchorView != null) mAnchorView.removeOnLayoutChangeListener(this);
        super.dismiss();
    }

    /**
     * Drawable representing a bubble with a arrow pointing upward at something.
     */
    private static class BubbleBackgroundDrawable extends Drawable {
        private final Drawable mBubbleContentsDrawable;
        private final BitmapDrawable mBubbleArrowDrawable;
        private int mBubbleArrowXCenter;

        BubbleBackgroundDrawable(Context context) {
            mBubbleContentsDrawable = ApiCompatibilityUtils.getDrawable(
                    context.getResources(), R.drawable.menu_bg);
            mBubbleArrowDrawable = (BitmapDrawable) ApiCompatibilityUtils.getDrawable(
                    context.getResources(), R.drawable.bubble_point_white);
        }

        @Override
        public void draw(Canvas canvas) {
            mBubbleContentsDrawable.draw(canvas);
            mBubbleArrowDrawable.draw(canvas);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            if (bounds == null) return;

            // The arrow hugs the top boundary and pushes the rest of the rectangular portion of the
            // callout beneath it.
            int halfArrowWidth = mBubbleArrowDrawable.getIntrinsicWidth() / 2;
            int arrowLeft = mBubbleArrowXCenter + bounds.left - halfArrowWidth;
            int arrowRight = arrowLeft + mBubbleArrowDrawable.getIntrinsicWidth();
            mBubbleArrowDrawable.setBounds(
                    arrowLeft,
                    bounds.top,
                    arrowRight,
                    bounds.top + mBubbleArrowDrawable.getIntrinsicHeight());

            // Adjust the background of the callout to account for the side margins and the arrow.
            Rect bubblePadding = new Rect();
            mBubbleContentsDrawable.getPadding(bubblePadding);
            mBubbleContentsDrawable.setBounds(
                    bounds.left,
                    bounds.top + mBubbleArrowDrawable.getIntrinsicHeight() - bubblePadding.top,
                    bounds.right,
                    bounds.bottom);
        }

        @Override
        public void setAlpha(int alpha) {
            mBubbleContentsDrawable.setAlpha(alpha);
            mBubbleArrowDrawable.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            // Not supported.
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public boolean getPadding(Rect padding) {
            mBubbleContentsDrawable.getPadding(padding);

            padding.set(padding.left,
                    Math.max(padding.top, mBubbleArrowDrawable.getIntrinsicHeight()),
                    padding.right,
                    padding.bottom);
            return true;
        }

        /**
         * Updates where the bubble arrow should be centered along the x-axis.
         * @param xOffset The offset of the bubble arrow.
         */
        public void setBubbleArrowXCenter(int xOffset) {
            mBubbleArrowXCenter = xOffset;
            onBoundsChange(getBounds());
        }

        /**
         * @return the current x center for the bubble arrow.
         */
        public int getBubbleArrowXCenter() {
            return mBubbleArrowXCenter;
        }
    }
}
