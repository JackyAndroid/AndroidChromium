// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

/**
 * Controls the Search Peek Promo.
 */
public class ContextualSearchPeekPromoControl extends ContextualSearchInflater
        implements ChromeAnimation.Animatable<ContextualSearchPeekPromoControl.AnimationType> {

    /**
     * The initial width of the ripple for the appearance animation, in dps.
     */
    private static final float RIPPLE_MINIMUM_WIDTH_DP = 56.f;

    /**
     * Animation properties.
     */
    protected enum AnimationType {
        APPEARANCE
    }

    /**
     * Whether the Peek Promo is visible.
     */
    private boolean mIsVisible;

    /**
     * The height of the Peek Promo, in pixels.
     */
    private float mHeightPx;

    /**
     * The width of the Ripple resource in pixels.
     */
    private float mRippleWidthPx;

    /**
     * The opacity of the Ripple resource.
     */
    private float mRippleOpacity;

    /**
     * The opacity of the Promo Text View dynamic resource.
     */
    private float mTextOpacity;

    /**
     * The precomputed padding of the Peek Promo, in pixels.
     */
    private final float mPaddingPx;

    /**
     * The precomputed default height of the Peek Promo in pixels.
     */
    private final float mDefaultHeightPx;

    /**
     * The precomputed minimum width of the Ripple resource in pixels.
     */
    private final float mRippleMinimumWidthPx;

    /**
     * The precomputed maximum width of the Ripple resource in pixels.
     */
    private final float mRippleMaximumWidthPx;

    /**
     * @param panel             The panel.
     * @param context           The Android Context used to inflate the View.
     * @param container         The container View used to inflate the View.
     * @param resourceLoader    The resource loader that will handle the snapshot capturing.
     */
    public ContextualSearchPeekPromoControl(OverlayPanel panel,
                                            Context context,
                                            ViewGroup container,
                                            DynamicResourceLoader resourceLoader) {
        super(panel, R.layout.contextual_search_peek_promo_text_view,
                R.id.contextual_search_peek_promo_text_view, context, container, resourceLoader);

        final float dpToPx = context.getResources().getDisplayMetrics().density;

        mDefaultHeightPx = context.getResources().getDimensionPixelOffset(
                R.dimen.contextual_search_peek_promo_height);
        mPaddingPx = context.getResources().getDimensionPixelOffset(
                R.dimen.contextual_search_peek_promo_padding);

        mRippleMinimumWidthPx = RIPPLE_MINIMUM_WIDTH_DP * dpToPx;
        mRippleMaximumWidthPx = panel.getMaximumWidthPx();
    }

    /**
     * Shows the Peek Promo. This includes inflating the View and setting it to its initial state.
     * This also means a new cc::Layer will be created and added to the tree.
     */
    void show() {
        if (mIsVisible) return;

        mIsVisible = true;
        mHeightPx = Math.round(mDefaultHeightPx);

        invalidate();
    }

    /**
     * Hides the Peek Promo, returning the Control to its initial uninitialized state. In this
     * state, now View will be created and no Layer added to the tree (or removed if present).
     */
    void hide() {
        if (!mIsVisible) return;

        mIsVisible = false;
        mHeightPx = 0.f;
    }

    /**
     * @return The height of the Peek Promo when the Panel is the peeked state.
     */
    float getHeightPeekingPx() {
        return mIsVisible ? mDefaultHeightPx : 0.f;
    }

    // ============================================================================================
    // Public API
    // ============================================================================================

    /**
     * @return Whether the Peek Promo is visible.
     */
    public boolean isVisible() {
        return mIsVisible;
    }

    /**
     * @return The Peek Promo height in pixels.
     */
    public float getHeightPx() {
        return mHeightPx;
    }

    /**
     * @return The Peek Promo padding in pixels.
     */
    public float getPaddingPx() {
        return mPaddingPx;
    }

    /**
     * @return The width of the Ripple resource in pixels.
     */
    public float getRippleWidthPx() {
        return mRippleWidthPx;
    }

    /**
     * @return The opacity of the Ripple resource.
     */
    public float getRippleOpacity() {
        return mRippleOpacity;
    }

    /**
     * @return The opacity of the Promo Text View dynamic resource.
     */
    public float getTextOpacity() {
        return mTextOpacity;
    }

    // ============================================================================================
    // Panel Animation
    // ============================================================================================

    /**
     * Interpolates the UI from states Closed to Peeked.
     *
     * @param percentage The completion percentage.
     */
    public void onUpdateFromCloseToPeek(float percentage) {
        if (!isVisible()) return;

        mHeightPx = Math.round(mDefaultHeightPx);
    }

    /**
     * Interpolates the UI from states Peeked to Expanded.
     *
     * @param percentage The completion percentage.
     */
    public void onUpdateFromPeekToExpand(float percentage) {
        if (!isVisible()) return;

        mHeightPx = Math.round(MathUtils.interpolate(mDefaultHeightPx, 0.f, percentage));
        mTextOpacity = MathUtils.interpolate(1.f, 0.f, percentage);
    }

    /**
     * Interpolates the UI from states Expanded to Maximized.
     *
     * @param percentage The completion percentage.
     */
    public void onUpdateFromExpandToMaximize(float percentage) {
        if (!isVisible()) return;

        mHeightPx = 0.f;
        mTextOpacity = 0.f;
    }

    // ============================================================================================
    // Peek Promo Appearance Animation
    // ============================================================================================

    /**
     * Animates the Peek Promo appearance.
     */
    public void animateAppearance() {
        // TODO(pedrosimonetti): Find a generic way to tell when a specific animation finishes.
        mOverlayPanel.addToAnimation(this, AnimationType.APPEARANCE, 0.f, 1.f,
                ContextualSearchPanelAnimation.BASE_ANIMATION_DURATION_MS, 0);
    }

    @Override
    public void setProperty(AnimationType type, float value) {
        if (type == AnimationType.APPEARANCE) {
            updateForAppearanceAnimation(value);
        }
    }

    /**
     * Updates the UI for the appearance animation.
     *
     * @param percentage The completion percentage.
     */
    private void updateForAppearanceAnimation(float percentage) {
        mRippleWidthPx = Math.round(MathUtils.interpolate(
                mRippleMinimumWidthPx, mRippleMaximumWidthPx, percentage));

        mRippleOpacity = MathUtils.interpolate(0.f, 1.f, percentage);

        float textOpacityDelay = 0.5f;
        float textOpacityPercentage =
                Math.max(0, percentage - textOpacityDelay) / (1.f - textOpacityDelay);
        mTextOpacity = MathUtils.interpolate(0.f, 1.f, textOpacityPercentage);
    }
}
