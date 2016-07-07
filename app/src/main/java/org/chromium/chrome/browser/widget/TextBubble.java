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
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.ui.base.LocalizationUtils;

/**
 * UI component that handles showing text bubbles.
 */
public class TextBubble
        extends PopupWindow implements OnLayoutChangeListener, OnAttachStateChangeListener {
    /** Whether to use the intrinsic padding of the bubble background as padding (boolean). */
    public static final String BACKGROUND_INTRINSIC_PADDING = "Background_Intrinsic_Padding";

    /**
     * Boolean to be used for deciding whether the bubble should be anchored above or below
     * the view
     */
    public static final String UP_DOWN = "Up_Down";

    /** Style resource Id to be used for text inside the bubble. Should be of type int. */
    public static final String TEXT_STYLE_ID = "Text_Style_Id";

    /** Boolean to be used for deciding whether the bubble should be centered to the view */
    public static final String CENTER = "Center";

    public static final String ANIM_STYLE_ID = "Animation_Style";

    private final int mTooltipEdgeMargin;
    private final int mTooltipTopMargin;
    private final int mBubbleTipXMargin;
    private boolean mAnchorBelow = false;
    private boolean mCenterView = true;
    private int mXPosition;
    private int mYPosition;
    private View mAnchorView;
    private final Rect mCachedPaddingRect = new Rect();

    // The text view inside the popup containing the tooltip text.
    private final TextView mTooltipText;

    /**
     * Constructor that uses a bundle object to fetch resources and optional boolean
     * values for the {@link TextBubble}.
     *
     * Use  CENTER for centering the tip to the anchor view.
     *      UP_DOWN for drawing the bubble with tip pointing up or down.
     *          Up is true and Down is false.
     *      LAYOUT_WIDTH_ID Dimension resource Id for the width of the {@link TextView} inside the
     *          bubble. The height is set to half of this value.
     *
     * @param context
     * @param res Bundle object that contains resource ids and optional flags.
     */
    public TextBubble(Context context, Bundle res) {
        mAnchorBelow = (res.containsKey(UP_DOWN) ? res.getBoolean(UP_DOWN) : true);
        mCenterView = (res.containsKey(CENTER) ? res.getBoolean(CENTER) : true);
        mTooltipEdgeMargin =
                context.getResources().getDimensionPixelSize(R.dimen.tooltip_min_edge_margin);
        mTooltipTopMargin =
                context.getResources().getDimensionPixelSize(R.dimen.tooltip_top_margin);
        mBubbleTipXMargin = context.getResources().getDimensionPixelSize(R.dimen.bubble_tip_margin);

        setBackgroundDrawable(new BubbleBackgroundDrawable(context, res));
        setAnimationStyle(res.containsKey(ANIM_STYLE_ID) ? res.getInt(ANIM_STYLE_ID)
                                                         : android.R.style.Animation);

        mTooltipText = new TextView(context);
        ApiCompatibilityUtils.setTextAppearance(mTooltipText,
                (res.containsKey(TEXT_STYLE_ID) ? res.getInt(TEXT_STYLE_ID) : R.style.info_bubble));

        setContentView(mTooltipText);
        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * @return The textview for the bubble text.
     */
    public TextView getBubbleTextView() {
        return mTooltipText;
    }

    /**
     * Shows a text bubble anchored to the given view.
     *
     * @param text The text to be shown.
     * @param anchorView The view that the bubble should be anchored to.
     * @param maxWidth The maximum width of the text bubble.
     * @param maxHeight The maximum height of the text bubble.
     */
    public void showTextBubble(String text, View anchorView, int maxWidth, int maxHeight) {
        mTooltipText.setText(text);
        mTooltipText.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
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
        View offsetView = mAnchorView;
        int xOffset = 0;
        int yOffset = 0;
        if (mAnchorBelow) yOffset = mAnchorView.getHeight();

        while (offsetView != null) {
            xOffset += offsetView.getLeft();
            yOffset += offsetView.getTop();
            if (!(offsetView.getParent() instanceof View)) break;
            offsetView = (View) offsetView.getParent();
        }

        if (mCenterView) {
            // Center the tooltip over the view (calculating the width of the tooltip text).
            xOffset += mAnchorView.getWidth() / 2;
        } else if (LocalizationUtils.isLayoutRtl()) {
            xOffset += mAnchorView.getWidth();
        }

        int tooltipWidth = mTooltipText.getMeasuredWidth();
        xOffset -= tooltipWidth / 2;

        // Account for the padding of the bubble background to ensure it is centered properly.
        getBackground().getPadding(mCachedPaddingRect);
        tooltipWidth += mCachedPaddingRect.left + mCachedPaddingRect.right;
        xOffset -= mCachedPaddingRect.left;

        int defaultXOffset = xOffset;

        View rootView = mAnchorView.getRootView();
        // Make sure the tooltip does not get rendered off the screen.
        if (xOffset + tooltipWidth > rootView.getWidth()) {
            xOffset = rootView.getWidth() - tooltipWidth - mTooltipEdgeMargin;
        } else if (xOffset < 0) {
            xOffset = mTooltipEdgeMargin;
        }

        // Move the bubble arrow to be centered over the anchor view.
        int newOffset = -(xOffset - defaultXOffset);
        if (Math.abs(newOffset) > mTooltipText.getMeasuredWidth() / 2 - mBubbleTipXMargin) {
            newOffset = (mTooltipText.getMeasuredWidth() / 2 - mBubbleTipXMargin)
                    * (int) Math.signum(newOffset);
        }
        ((BubbleBackgroundDrawable) getBackground()).setBubbleArrowXOffset(newOffset);

        if (mAnchorBelow) {
            mXPosition = xOffset;
            mYPosition = yOffset - mTooltipTopMargin;
        } else {
            mXPosition = xOffset;
            mYPosition = mAnchorView.getRootView().getHeight() - yOffset + mTooltipTopMargin;
        }
    }

    /**
     * Shows the TextBubble in the precalculated position. Should be called after mXPosition
     * and MYPosition has been set.
     */
    private void showAtCalculatedPosition() {
        if (mAnchorBelow) {
            showAtLocation(
                    mAnchorView.getRootView(), Gravity.TOP | Gravity.START, mXPosition, mYPosition);
        } else {
            showAtLocation(mAnchorView.getRootView(), Gravity.BOTTOM | Gravity.START, mXPosition,
                    mYPosition);
        }
    }

    // The two functions below are used for the floating animation.

    /**
     * Updates the y offset of the popup bubble (applied in addition to
     * the default calculated offset).
     * @param yoffset The new mYOffset to be used.
     */
    public void setOffsetY(int yoffset) {
        update(mXPosition, mYPosition + yoffset, -1, -1);
    }

    /**
     * Updates the position information and checks whether any positional change will occur. This
     * method doesn't change the {@link TextBubble} if it is showing.
     * @return Whether the TextBubble needs to be redrawn.
     */
    private boolean updatePosition() {
        int previousX = mXPosition;
        int previousY = mYPosition;
        int previousOffset = ((BubbleBackgroundDrawable) getBackground()).getBubbleArrowOffset();
        calculateNewPosition();
        if (previousX != mXPosition || previousY != mYPosition
                || previousOffset
                        != ((BubbleBackgroundDrawable) getBackground()).getBubbleArrowOffset()) {
            return true;
        }
        return false;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        boolean willDisappear = !mAnchorView.isShown();
        boolean changePosition = updatePosition();
        if (willDisappear) {
            dismiss();
        } else if (changePosition) {
            dismiss();
            showAtCalculatedPosition();
        }
    }

    @Override
    public void onViewAttachedToWindow(View v) {}

    @Override
    public void onViewDetachedFromWindow(View v) {
        dismiss();
    }

    /**
     * Drawable for rendering the background for a popup bubble.
     *
     * <p>Using a custom class as the LayerDrawable handles padding oddly and did not allow the
     *    bubble arrow to be rendered below the content portion of the bubble if you specified
     *    padding, which is required to make it look nice.
     */
    static class BubbleBackgroundDrawable extends Drawable {
        private final int mTooltipBorderWidth;
        private final Rect mTooltipContentPadding;

        private final Drawable mBubbleContentsDrawable;
        private final BitmapDrawable mBubbleArrowDrawable;
        private boolean mUp = false;
        private int mBubbleArrowXOffset;

        BubbleBackgroundDrawable(Context context, Bundle res) {
            mUp = (res.containsKey(UP_DOWN) ? res.getBoolean(UP_DOWN) : true);
            mBubbleContentsDrawable = ApiCompatibilityUtils.getDrawable(context.getResources(),
                    R.drawable.bubble_white);
            mBubbleArrowDrawable = (BitmapDrawable) ApiCompatibilityUtils.getDrawable(
                    context.getResources(), R.drawable.bubble_point_white);
            mTooltipBorderWidth =
                    context.getResources().getDimensionPixelSize(R.dimen.tooltip_border_width);

            if (res.getBoolean(BACKGROUND_INTRINSIC_PADDING, false)) {
                mTooltipContentPadding = new Rect();
                mBubbleContentsDrawable.getPadding(mTooltipContentPadding);
            } else {
                int padding = context.getResources().getDimensionPixelSize(
                        R.dimen.tooltip_content_padding);
                mTooltipContentPadding = new Rect(padding, padding, padding, padding);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            mBubbleContentsDrawable.draw(canvas);
            mBubbleArrowDrawable.draw(canvas);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            if (bounds == null) return;

            super.onBoundsChange(bounds);
            int halfArrowWidth = mBubbleArrowDrawable.getIntrinsicWidth() / 2;
            int halfBoundsWidth = bounds.width() / 2;
            if (mUp) {
                int contentsTop = bounds.top + mBubbleArrowDrawable.getIntrinsicHeight()
                        - mTooltipBorderWidth;
                mBubbleContentsDrawable.setBounds(
                        bounds.left, contentsTop, bounds.right, bounds.bottom);
                mBubbleArrowDrawable.setBounds(
                        mBubbleArrowXOffset + halfBoundsWidth - halfArrowWidth, bounds.top,
                        mBubbleArrowXOffset + halfBoundsWidth + halfArrowWidth,
                        bounds.top + mBubbleArrowDrawable.getIntrinsicHeight());
            } else {
                int contentsBottom = bounds.bottom - mBubbleArrowDrawable.getIntrinsicHeight();
                mBubbleContentsDrawable.setBounds(
                        bounds.left, bounds.left, bounds.right, contentsBottom);
                mBubbleArrowDrawable.setBounds(
                        mBubbleArrowXOffset + halfBoundsWidth - halfArrowWidth,
                        contentsBottom - mTooltipBorderWidth,
                        mBubbleArrowXOffset + halfBoundsWidth + halfArrowWidth, contentsBottom
                                + mBubbleArrowDrawable.getIntrinsicHeight() - mTooltipBorderWidth);
            }
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
            padding.set(mTooltipContentPadding);
            if (mUp) {
                padding.set(padding.left, padding.top + mBubbleArrowDrawable.getIntrinsicHeight(),
                        padding.right, padding.bottom);
            } else {
                padding.set(padding.left, padding.top, padding.right,
                        padding.bottom + mBubbleArrowDrawable.getIntrinsicHeight());
            }

            return true;
        }

        /**
         * Updates the additional X Offset for the bubble arrow.  The arrow defaults to being
         * centered in the bubble, so this is delta from the center.
         *
         * @param xOffset The offset of the bubble arrow.
         */
        public void setBubbleArrowXOffset(int xOffset) {
            mBubbleArrowXOffset = xOffset;
            onBoundsChange(getBounds());
        }

        /**
         * @return the current x offset for the bubble arrow.
         */
        public int getBubbleArrowOffset() {
            return mBubbleArrowXOffset;
        }
    }
}
