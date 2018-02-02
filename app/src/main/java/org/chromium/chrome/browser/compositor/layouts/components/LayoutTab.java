// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.components;

import static org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.AnimatableAnimation.createAnimation;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.RectF;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.toolbar.ToolbarPhone;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * {@link LayoutTab} is used to keep track of a thumbnail's bitmap and position and to
 * draw itself onto the GL canvas at the desired Y Offset.
 */
public class LayoutTab implements ChromeAnimation.Animatable<LayoutTab.Property> {
    /**
     * Properties that can be animated by using a
     * {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable}.
     */
    public enum Property {
        BORDER_ALPHA,
        BORDER_SCALE,
        ALPHA,
        SATURATION,
        STATIC_TO_VIEW_BLEND,
        SCALE,
        TILTX,
        TILTY,
        X,
        Y,
        MAX_CONTENT_WIDTH,
        MAX_CONTENT_HEIGHT,
        TOOLBAR_ALPHA,
        DECORATION_ALPHA,
        TOOLBAR_Y_OFFSET,
        SIDE_BORDER_SCALE,
        TOOLBAR_COLOR,
    }

    public static final float ALPHA_THRESHOLD = 1.0f / 255.0f;

    private static final float SNAP_SPEED = 1.0f; // dp per second

    // Public Layout constants.
    public static final float CLOSE_BUTTON_WIDTH_DP = 36.f;

    // Layout constants.
    private static final float SHADOW_OPACITY = 1.f;
    // TODO(dtrainor): Investigate removing this.
    private static final float BORDER_THICKNESS_DP = 4.f;

    // Cached values from values/dimens.xml
    private static float sCompositorButtonSlop; // compositor_button_slop

    private static float sDpToPx;
    private static float sPxToDp;

    private final int mId;
    private final boolean mIsIncognito;

    // Fields initialized in init()
    private float mScale;
    private float mTiltX; // angle in degrees.
    private float mTiltY; // angle in degrees.
    private float mTiltXPivotOffset;
    private float mTiltYPivotOffset;
    private float mX;
    private float mY;
    private float mRenderX;
    private float mRenderY;
    private float mClippedX; // The top left X offset of the clipped rectangle.
    private float mClippedY; // The top left Y offset of the clipped rectangle.
    private float mClippedWidth;
    private float mClippedHeight;
    private float mAlpha;
    private float mSaturation;
    private float mBorderAlpha;
    private float mBorderCloseButtonAlpha;
    private float mBorderScale;
    private float mOriginalContentWidth;  // in dp.
    private float mOriginalContentHeight; // in dp.
    private float mMaxContentWidth;
    private float mMaxContentHeight;
    private float mStaticToViewBlend;
    private float mBrightness;
    private boolean mVisible;
    private boolean mShouldStall;
    private boolean mCanUseLiveTexture;
    private boolean mShowToolbar;
    private boolean mAnonymizeToolbar;
    private float mToolbarAlpha;
    private boolean mInsetBorderVertical;
    private float mToolbarYOffset;
    private float mSideBorderScale;

    private final RectF mBounds = new RectF(); // Pre-allocated to avoid in-frame allocations.
    private final RectF mClosePlacement = new RectF();

    /** Whether we need to draw the decoration (border, shadow, ..) at all. */
    private float mDecorationAlpha;

    /**
     * Whether this tab need to have its title texture generated. As this is not a free operation
     * knowing that we won't show it might save a few cycles and memory.
     */
    private boolean mIsTitleNeeded = false;

    /**
     * Whether initFromHost() has been called since the last call to init().
     */
    private boolean mInitFromHostCalled = false;

    /** The animation set specific to this LayoutTab. */
    private ChromeAnimation<ChromeAnimation.Animatable<?>> mCurrentAnimations;
    private int mInitialThemeColor;
    private int mFinalThemeColor;

    // All the members bellow are initialized from the delayed initialization.
    //
    // Begin section --------------

    /** The color of the background of the tab. Used as the best approximation to fill in. */
    private int mBackgroundColor = Color.WHITE;

    private int mToolbarBackgroundColor = 0xfff2f2f2;

    private int mTextBoxBackgroundColor = Color.WHITE;

    private float mTextBoxAlpha = 1.0f;

    // End section --------------

    /**
     * Default constructor for a {@link LayoutTab}.
     *
     * @param tabId                   The id of the source {@link Tab}.
     * @param isIncognito             Whether the tab in the in the incognito stack.
     * @param maxContentTextureWidth  The maximum width for drawing the content.
     * @param maxContentTextureHeight The maximum height for drawing the content.
     * @param showCloseButton         Whether a close button should be displayed in the corner.
     * @param isTitleNeeded           Whether that tab need a title texture. This is an
     *                                optimization to save cycles and memory. This is
     *                                ignored if the title texture is already set.
     */
    public LayoutTab(int tabId, boolean isIncognito, float maxContentTextureWidth,
            float maxContentTextureHeight, boolean showCloseButton, boolean isTitleNeeded) {
        mId = tabId;
        mIsIncognito = isIncognito;
        init(maxContentTextureWidth, maxContentTextureHeight, showCloseButton, isTitleNeeded);
    }

    /**
     * Initializes a {@link LayoutTab} to its default value so it can be reused.
     *
     * @param maxContentTextureWidth  The maximum width of the page content in dp.
     * @param maxContentTextureHeight The maximum height of the page content in dp.
     * @param showCloseButton         Whether to show the close button on the tab border.
     * @param isTitleNeeded           Whether that tab need a title texture. This is an
     *                                optimization to save cycles and memory. This is
     *                                ignored if the title texture is already set.
     */
    public void init(float maxContentTextureWidth, float maxContentTextureHeight,
            boolean showCloseButton, boolean isTitleNeeded) {
        mAlpha = 1.0f;
        mSaturation = 1.0f;
        mBrightness = 1.0f;
        mBorderAlpha = 1.0f;
        mBorderCloseButtonAlpha = showCloseButton ? 1.f : 0.f;
        mBorderScale = 1.0f;
        mClippedX = 0.0f;
        mClippedY = 0.0f;
        mClippedWidth = Float.MAX_VALUE;
        mClippedHeight = Float.MAX_VALUE;
        mScale = 1.0f;
        mTiltX = 0.0f;
        mTiltY = 0.0f;
        mVisible = true;
        mX = 0.0f;
        mY = 0.0f;
        mRenderX = 0.0f;
        mRenderY = 0.0f;
        mStaticToViewBlend = 0.0f;
        mDecorationAlpha = 1.0f;
        mIsTitleNeeded = isTitleNeeded;
        mCanUseLiveTexture = true;
        mShowToolbar = false;
        mAnonymizeToolbar = false;
        mToolbarAlpha = 1.f;
        mInsetBorderVertical = false;
        mToolbarYOffset = 0.f;
        mSideBorderScale = 1.f;
        mOriginalContentWidth = maxContentTextureWidth;
        mOriginalContentHeight = maxContentTextureHeight;
        mMaxContentWidth = maxContentTextureWidth;
        mMaxContentHeight = maxContentTextureHeight;

        mInitFromHostCalled = false;
    }

    /**
     * Initializes the {@link LayoutTab} from data extracted from a {@link Tab}.
     * As this function may be expensive and can be delayed we initialize it as a separately.
     *
     * @param backgroundColor       The color of the page background.
     * @param fallbackThumbnailId   The id of a cached thumbnail to show if the current
     *                              thumbnail is unavailable, or {@link Tab.INVALID_TAB_ID}
     *                              if none exists.
     * @param shouldStall           Whether the tab should display a desaturated thumbnail and
     *                              wait for the content layer to load.
     * @param canUseLiveTexture     Whether the tab can use a live texture when being displayed.
     * @return True if the init requires the compositor to update.
     */
    public boolean initFromHost(int backgroundColor, boolean shouldStall, boolean canUseLiveTexture,
            int toolbarBackgroundColor, int textBoxBackgroundColor, float textBoxAlpha) {
        mBackgroundColor = backgroundColor;

        boolean needsUpdate = false;

        // If the toolbar color changed, animate between the old and new colors.
        if (mToolbarBackgroundColor != toolbarBackgroundColor && isVisible()
                && mInitFromHostCalled) {
            ChromeAnimation.Animation<ChromeAnimation.Animatable<?>>  themeColorAnimation =
                    createAnimation(this, Property.TOOLBAR_COLOR, 0.0f, 1.0f,
                    ToolbarPhone.THEME_COLOR_TRANSITION_DURATION, 0, false,
                    BakedBezierInterpolator.TRANSFORM_CURVE);

            mInitialThemeColor = mToolbarBackgroundColor;
            mFinalThemeColor = toolbarBackgroundColor;

            if (mCurrentAnimations != null) {
                mCurrentAnimations.updateAndFinish();
            }

            mCurrentAnimations = new ChromeAnimation<ChromeAnimation.Animatable<?>>();
            mCurrentAnimations.add(themeColorAnimation);
            mCurrentAnimations.start();
            needsUpdate = true;
        } else {
            // If the layout tab isn't visible, just set the toolbar color without animating.
            mToolbarBackgroundColor = toolbarBackgroundColor;
        }

        mTextBoxBackgroundColor = textBoxBackgroundColor;
        mTextBoxAlpha = textBoxAlpha;
        mShouldStall = shouldStall;
        mCanUseLiveTexture = canUseLiveTexture;
        mInitFromHostCalled = true;

        return needsUpdate;
    }

    /**
     * Update any animation controlled by this object.
     * @param time The current app time in ms.
     * @return Whether the animations controlled by this LayoutTab are finished.
     */
    public boolean onUpdateAnimation(long time) {
        return mCurrentAnimations == null ? true : mCurrentAnimations.update(time);
    }

    /**
     * @return Whether {@link #initFromHost} needs to be called on this {@link LayoutTab}.
     */
    public boolean isInitFromHostNeeded() {
        return !mInitFromHostCalled;
    }

    /**
     * Helper function that gather the static constants from values/dimens.xml.
     *
     * @param context The Android Context.
     */
    public static void resetDimensionConstants(Context context) {
        Resources res = context.getResources();
        sDpToPx = res.getDisplayMetrics().density;
        sPxToDp = 1.0f / sDpToPx;
        sCompositorButtonSlop = res.getDimension(R.dimen.compositor_button_slop) * sPxToDp;
    }

    /**
     * @return The slop amount in pixel to detect a touch on the tab.
     */
    public static float getTouchSlop() {
        return sCompositorButtonSlop;
    }

    /**
     * @return The scale applied to the the content of the tab. Default is 1.0.
     */
    public float getScale() {
        return mScale;
    }

    /**
     * @param scale The scale to apply on the content of the tab (everything except the borders).
     */
    public void setScale(float scale) {
        mScale = scale;
    }

    /**
     * @param tilt        The tilt angle around the X axis of the tab in degree.
     * @param pivotOffset The offset of the X axis of the tilt pivot.
     */
    public void setTiltX(float tilt, float pivotOffset) {
        mTiltX = tilt;
        mTiltXPivotOffset = pivotOffset;
    }

    /**
     * @return The tilt angle around the X axis of the tab in degree.
     */
    public float getTiltX() {
        return mTiltX;
    }

    /**
     * @return The offset of the X axis of the tilt pivot.
     */
    public float getTiltXPivotOffset() {
        return mTiltXPivotOffset;
    }

    /**
     * @param tilt        The tilt angle around the Y axis of the tab in degree.
     * @param pivotOffset The offset of the Y axis of the tilt pivot.
     */
    public void setTiltY(float tilt, float pivotOffset) {
        mTiltY = tilt;
        mTiltYPivotOffset = pivotOffset;
    }

    /**
     * @return The tilt angle around the Y axis of the tab in degree.
     */
    public float getTiltY() {
        return mTiltY;
    }

    /**
     * @return The offset of the Y axis of the tilt pivot.
     */
    public float getTiltYPivotOffset() {
        return mTiltYPivotOffset;
    }

    /**
     * Set the clipping offset. This apply on the scaled content.
     *
     * @param clippedX The top left X offset of the clipped rectangle.
     * @param clippedY The top left Y offset of the clipped rectangle.
     */
    public void setClipOffset(float clippedX, float clippedY) {
        mClippedX = clippedX;
        mClippedY = clippedY;
    }

    /**
     * Set the clipping sizes. This apply on the scaled content.
     *
     * @param clippedWidth  The width of the clipped rectangle. Float.MAX_VALUE for no clipping.
     * @param clippedHeight The height of the clipped rectangle. Float.MAX_VALUE for no clipping.
     */
    public void setClipSize(float clippedWidth, float clippedHeight) {
        mClippedWidth = clippedWidth;
        mClippedHeight = clippedHeight;
    }

    /**
     * @return The top left X offset of the clipped rectangle.
     */
    public float getClippedX() {
        return mClippedX;
    }

    /**
     * @return The top left Y offset of the clipped rectangle.
     */
    public float getClippedY() {
        return mClippedY;
    }

    /**
     * @return The width of the clipped rectangle. Float.MAX_VALUE for no clipping.
     */
    public float getClippedWidth() {
        return mClippedWidth;
    }

    /**
     * @return The height of the clipped rectangle. Float.MAX_VALUE for no clipping.
     */
    public float getClippedHeight() {
        return mClippedHeight;
    }

    /**
     * @return The maximum drawable width (scaled) of the tab contents in dp.
     */
    public float getScaledContentWidth() {
        return getOriginalContentWidth() * mScale;
    }

    /**
     * @return The maximum drawable height (scaled) of the tab contents in dp.
     */
    public float getScaledContentHeight() {
        return getOriginalContentHeight() * mScale;
    }

    /**
     * @return The maximum drawable width (not scaled) of the tab contents texture.
     */
    public float getOriginalContentWidth() {
        return Math.min(mOriginalContentWidth, mMaxContentWidth);
    }

    /**
     * @return The maximum drawable height (not scaled) of the tab contents texture.
     */
    public float getOriginalContentHeight() {
        return Math.min(mOriginalContentHeight, mMaxContentHeight);
    }

    /**
     * @return The original unclamped width (not scaled) of the tab contents texture.
     */
    public float getUnclampedOriginalContentHeight() {
        return mOriginalContentHeight;
    }

    /**
     * @return The width of the drawn content (clipped and scaled).
     */
    public float getFinalContentWidth() {
        return Math.min(mClippedWidth, getScaledContentWidth());
    }

    /**
     * @return The height of the drawn content (clipped and scaled).
     */
    public float getFinalContentHeight() {
        return Math.min(mClippedHeight, getScaledContentHeight());
    }

    /**
     * @return The maximum width the content can be.
     */
    public float getMaxContentWidth() {
        return mMaxContentWidth;
    }

    /**
     * @return The maximum height the content can be.
     */
    public float getMaxContentHeight() {
        return mMaxContentHeight;
    }

    /**
     * @param width The maximum width the content can be.
     */
    public void setMaxContentWidth(float width) {
        mMaxContentWidth = width;
    }

    /**
     * @param height The maximum height the content can be.
     */
    public void setMaxContentHeight(float height) {
        mMaxContentHeight = height;
    }

    /**
     * @return The id of the tab, same as the id from the Tab in TabModel.
     */
    public int getId() {
        return mId;
    }

    /**
     * @return Whether the underlying tab is incognito or not.
     */
    public boolean isIncognito() {
        return mIsIncognito;
    }

    /**
     * @param y The vertical draw position.
     */
    public void setY(float y) {
        mY = y;
    }

    /**
     * @return The vertical draw position for the update logic.
     */
    public float getY() {
        return mY;
    }

    /**
     * @return The vertical draw position for the renderer.
     */
    public float getRenderY() {
        return mRenderY;
    }

    /**
     * @param x The horizontal draw position.
     */
    public void setX(float x) {
        mX = x;
    }

    /**
     * @return The horizontal draw position for the update logic.
     */
    public float getX() {
        return mX;
    }

    /**
     * @return The horizontal draw position for the renderer.
     */
    public float getRenderX() {
        return mRenderX;
    }

    /**
     * Set the transparency value for all of the tab (the contents,
     * border, etc...).  For components that allow specifying
     * their own alpha values, it will use the min of these two fields.
     *
     * @param f The transparency value for the tab.
     */
    public void setAlpha(float f) {
        mAlpha = f;
    }

    /**
     * @return The transparency value for all of the tab components.
     */
    public float getAlpha() {
        return mAlpha;
    }

    /**
     * @return The opactiy of the tab's shadow.
     */
    public float getShadowOpacity() {
        return SHADOW_OPACITY;
    }

    /**
     * Set the saturation value for the tab contents.
     *
     * @param f The saturation value for the contents.
     */
    public void setSaturation(float f) {
        mSaturation = f;
    }

    /**
     * @return The saturation value for the tab contents.
     */
    public float getSaturation() {
        return mSaturation;
    }

    /**
     * @param alpha The maximum alpha value of the tab border.
     */
    public void setBorderAlpha(float alpha) {
        mBorderAlpha = alpha;
    }

    /**
     * @return The current alpha value at which the tab border is drawn.
     */
    public float getBorderAlpha() {
        return mBorderAlpha;
    }

    /**
     * @return The current alpha value at which the tab border inner shadow is drawn.
     */
    public float getBorderInnerShadowAlpha() {
        return mBorderAlpha * (1.0f - mToolbarAlpha);
    }

    /**
     * @param alpha The maximum alpha value of the close button on the border.
     */
    public void setBorderCloseButtonAlpha(float alpha) {
        mBorderCloseButtonAlpha = alpha;
    }

    /**
     * @return The current alpha value at which the close button on the border is drawn.
     */
    public float getBorderCloseButtonAlpha() {
        return mBorderCloseButtonAlpha;
    }

    /**
     * @param scale The scale factor of the border.
     *              1.0f yields 1:1 pixel with the source image.
     */
    public void setBorderScale(float scale) {
        mBorderScale = scale;
    }

    /**
     * @return The current scale applied on the tab border.
     */
    public float getBorderScale() {
        return mBorderScale;
    }

    /**
     * @param decorationAlpha Whether or not to draw the decoration for this card.
     */
    public void setDecorationAlpha(float decorationAlpha) {
        mDecorationAlpha = decorationAlpha;
    }

    /**
     * @return The opacity of the decoration.
     */
    public float getDecorationAlpha() {
        return mDecorationAlpha;
    }

    /**
     * @param toolbarYOffset The y offset of the toolbar.
     */
    public void setToolbarYOffset(float toolbarYOffset) {
        mToolbarYOffset = toolbarYOffset;
    }

    /**
     * @return The y offset of the toolbar.
     */
    public float getToolbarYOffset() {
        return mToolbarYOffset;
    }

    /**
     * @param scale The scale of the side border (from 0 to 1).
     */
    public void setSideBorderScale(float scale) {
        mSideBorderScale = MathUtils.clamp(scale, 0.f, 1.f);
    }

    /**
     * @return The scale of the side border (from 0 to 1).
     */
    public float getSideBorderScale() {
        return mSideBorderScale;
    }

    /**
     * @param brightness The brightness value to apply to the tab.
     */
    public void setBrightness(float brightness) {
        mBrightness = brightness;
    }

    /**
     * @return The brightness of the tab.
     */
    public float getBrightness() {
        return mBrightness;
    }

    /**
     * @param drawDecoration Whether or not to draw decoration.
     */
    public void setDrawDecoration(boolean drawDecoration) {
        mDecorationAlpha = drawDecoration ? 1.0f : 0.0f;
    }

    /**
     * @param percentageView The blend between the old static tab and the new live one.
     */
    public void setStaticToViewBlend(float percentageView) {
        mStaticToViewBlend = percentageView;
    }

    /**
     * @return The current blend between the old static tab and the new live one.
     */
    public float getStaticToViewBlend() {
        return mStaticToViewBlend;
    }

    /**
     * Computes the Manhattan-ish distance to the edge of the tab.
     * This distance is good enough for click detection.
     *
     * @param x          X coordinate of the hit testing point.
     * @param y          Y coordinate of the hit testing point.
     * @return           The Manhattan-ish distance to the tab.
     */
    public float computeDistanceTo(float x, float y) {
        final RectF bounds = getClickTargetBounds();
        float dx = Math.max(bounds.left - x, x - bounds.right);
        float dy = Math.max(bounds.top - y, y - bounds.bottom);
        return Math.max(0.0f, Math.max(dx, dy));
    }

    /**
     * @return The rectangle that represents the click target of the tab.
     */
    public RectF getClickTargetBounds() {
        final float borderScaled = BORDER_THICKNESS_DP * mBorderScale;
        mBounds.top = mY + mClippedY - borderScaled;
        mBounds.bottom = mY + mClippedY + getFinalContentHeight() + borderScaled;
        mBounds.left = mX + mClippedX - borderScaled;
        mBounds.right = mX + mClippedX + getFinalContentWidth() + borderScaled;
        return mBounds;
    }

    /**
     * Tests if a point is inside the closing button of the tab.
     *
     * @param x     The horizontal coordinate of the hit testing point.
     * @param y     The vertical coordinate of the hit testing point.
     * @param isRTL Whether or not this is an RTL layout.
     * @return      Whether the hit testing point is inside the tab.
     */
    public boolean checkCloseHitTest(float x, float y, boolean isRTL) {
        RectF closeRectangle = getCloseBounds(isRTL);
        return closeRectangle != null ? closeRectangle.contains(x, y) : false;
    }

    /**
     * @param isRTL Whether or not this is an RTL layout.
     * @return      The bounds of the active area of the close button. {@code null} if the close
     *              button is not clickable.
     */
    public RectF getCloseBounds(boolean isRTL) {
        if (!mIsTitleNeeded || !mVisible || mBorderCloseButtonAlpha < 0.5f || mBorderAlpha < 0.5f
                || mBorderScale != 1.0f || Math.abs(mTiltX) > 1.0f || Math.abs(mTiltY) > 1.0f) {
            return null;
        }
        mClosePlacement.set(0, 0, CLOSE_BUTTON_WIDTH_DP, CLOSE_BUTTON_WIDTH_DP);
        if (!isRTL) mClosePlacement.offset(getFinalContentWidth() - mClosePlacement.width(), 0.f);

        if (mClosePlacement.bottom > getFinalContentHeight()
                || mClosePlacement.right > getFinalContentWidth()) {
            return null;
        }
        mClosePlacement.offset(mX + mClippedX, mY + mClippedY);
        mClosePlacement.inset(-sCompositorButtonSlop, -sCompositorButtonSlop);

        return mClosePlacement;
    }

    /**
     * Update snapping to pixel. To be called once every frame.
     *
     * @param dt The delta time between update frames in ms.
     * @return   True if the snapping requests to render at least one more frame.
     */
    public boolean updateSnap(long dt) {
        final float step = dt * SNAP_SPEED / 1000.0f;
        final float x = updateSnap(step, mRenderX, mX);
        final float y = updateSnap(step, mRenderY, mY);
        final boolean change = x != mRenderX || y != mRenderY;
        mRenderX = x;
        mRenderY = y;
        return change;
    }

    private float updateSnap(float step, float current, float ref) {
        if (Math.abs(current - ref) > sPxToDp) return ref;
        final float refRounded = Math.round(ref * sDpToPx) * sPxToDp;
        if (refRounded < ref) {
            current -= step;
            current = Math.max(refRounded, current);
        } else {
            current += step;
            current = Math.min(refRounded, current);
        }
        return current;
    }

    @Override
    public String toString() {
        return Integer.toString(getId());
    }

    /**
     * @param originalContentWidth  The maximum content width for the given orientation.
     * @param originalContentHeight The maximum content height for the given orientation.
     */
    public void setContentSize(float originalContentWidth, float originalContentHeight) {
        mOriginalContentWidth = originalContentWidth;
        mOriginalContentHeight = originalContentHeight;
    }

    /**
     * @return Whether the tab title should be displayed.
     */
    public boolean isTitleNeeded() {
        return mIsTitleNeeded;
    }

    /**
     * @param visible True if the {@link LayoutTab} is visible and need to be drawn.
     */
    public void setVisible(boolean visible) {
        if (!visible && mCurrentAnimations != null) mCurrentAnimations.updateAndFinish();
        mVisible = visible;
    }

    /**
     * @return True if the {@link LayoutTab} is visible and will be drawn.
     */
    public boolean isVisible() {
        return mVisible;
    }

    /**
     * @param shouldStall Whether or not the tab should wait for the live layer to load.
     */
    public void setShouldStall(boolean shouldStall) {
        mShouldStall = shouldStall;
    }

    /**
     * @return Whether or not the tab should wait for the live layer to load.
     */
    public boolean shouldStall() {
        return mShouldStall;
    }

    /**
     * @return Whether the tab can use a live texture to render.
     */
    public boolean canUseLiveTexture() {
        return mCanUseLiveTexture;
    }

    /**
     * @param showToolbar Whether or not to show a toolbar at the top of the content.
     */
    public void setShowToolbar(boolean showToolbar) {
        mShowToolbar = showToolbar;
    }

    /**
     * @return Whether or not to show a toolbar at the top of the content.
     */
    public boolean showToolbar() {
        return mShowToolbar;
    }

    /**
     * This value is only used if {@link #showToolbar()} is {@code true}.
     *
     * @param anonymize Whether or not to anonymize the toolbar (hiding URL, etc.).
     */
    public void setAnonymizeToolbar(boolean anonymize) {
        mAnonymizeToolbar = anonymize;
    }

    /**
     * This value is only used if {@link #showToolbar()} is {@code true}.
     *
     * @return Whether or not to anonymize the toolbar (hiding URL, etc.).
     */
    public boolean anonymizeToolbar() {
        return mAnonymizeToolbar;
    }

    /**
     * @param alpha The alpha of the toolbar.
     */
    public void setToolbarAlpha(float alpha) {
        mToolbarAlpha = alpha;
    }

    /**
     * @return The alpha of the toolbar.
     */
    public float getToolbarAlpha() {
        return mToolbarAlpha;
    }

    /**
     * @param inset Whether or not to inset the top vertical component of the tab border or not.
     */
    public void setInsetBorderVertical(boolean inset) {
        mInsetBorderVertical = inset;
    }

    /**
     * @return Whether or not to inset the top vertical component of the tab border or not.
     */
    public boolean insetBorderVertical() {
        return mInsetBorderVertical;
    }

    /**
     * @return The theoretical number of visible pixels. 0 if invisible.
     */
    public float computeVisibleArea() {
        return (mVisible && mAlpha > ALPHA_THRESHOLD ? 1.0f : 0.0f) * getFinalContentWidth()
                * getFinalContentHeight();
    }

    /**
     * @return The color of the background of the tab. Used as the best approximation to fill in.
     */
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    /**
     * @return The color of the background of the toolbar.
     */
    public int getToolbarBackgroundColor() {
        return mToolbarBackgroundColor;
    }

    /**
     * @return The color of the textbox in the toolbar. Used as the color for the anonymize rect.
     */
    public int getTextBoxBackgroundColor() {
        return mTextBoxBackgroundColor;
    }

    /**
     * @return The alpha value of the textbox in the toolbar.
     */
    public float getTextBoxAlpha() {
        return mTextBoxAlpha;
    }

    /**
     * Callback for
     * {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable}
     *
     * @param prop The property to set
     * @param val The value to set it to
     */
    @Override
    public void setProperty(Property prop, float val) {
        switch (prop) {
            case BORDER_ALPHA:
                setBorderAlpha(val);
                break;
            case BORDER_SCALE:
                setBorderScale(val);
                break;
            case ALPHA:
                setAlpha(val);
                break;
            case SATURATION:
                setSaturation(val);
                break;
            case STATIC_TO_VIEW_BLEND:
                setStaticToViewBlend(val);
                break;
            case SCALE:
                setScale(val);
                break;
            case TILTX:
                setTiltX(val, mTiltXPivotOffset);
                break;
            case TILTY:
                setTiltY(val, mTiltYPivotOffset);
                break;
            case X:
                setX(val);
                break;
            case Y:
                setY(val);
                break;
            case MAX_CONTENT_WIDTH:
                setMaxContentWidth(val);
                break;
            case MAX_CONTENT_HEIGHT:
                setMaxContentHeight(val);
                break;
            case TOOLBAR_ALPHA:
                setToolbarAlpha(val);
                break;
            case DECORATION_ALPHA:
                setDecorationAlpha(val);
                break;
            case TOOLBAR_Y_OFFSET:
                setToolbarYOffset(val);
                break;
            case SIDE_BORDER_SCALE:
                setSideBorderScale(val);
                break;
            case TOOLBAR_COLOR:
                if (!isVisible()) {
                    mCurrentAnimations.updateAndFinish();
                } else {
                    mToolbarBackgroundColor = ColorUtils.getColorWithOverlay(mInitialThemeColor,
                            mFinalThemeColor, val);
                }
                break;
        }
    }

    @Override
    public void onPropertyAnimationFinished(Property prop) {
        if (mCurrentAnimations != null && mCurrentAnimations.finished()) {
            mCurrentAnimations = null;
        }
    }
}
