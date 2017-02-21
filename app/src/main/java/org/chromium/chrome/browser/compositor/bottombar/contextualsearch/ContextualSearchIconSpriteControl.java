// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;

import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;

/**
 * Controls the search provider icon sprite.
 */
public class ContextualSearchIconSpriteControl implements
        ChromeAnimation.Animatable<ContextualSearchIconSpriteControl.AnimationType> {

    /**
     * Animation properties.
     */
    protected enum AnimationType {
        APPEARANCE
    }

    /** Whether the search provider icon sprite is visible. */
    private boolean mIsVisible;

    /** Whether the appearance of the search provider icon sprite should be animated. */
    private boolean mShouldAnimateAppearance;

    /**
     * The completion percentage for the animation; used to calculate which sprite frame to display.
     */
    private float mCompletionPercentage;

    /** The OverlayPanelAnimation used to add animations. */
    private OverlayPanelAnimation mOverlayPanelAnimation;

    /**
     * @param overlayPanelAnimation The OverlayPanelAnimation used to add animations.
     * @param context The Android Context used to retrieve resources.
     */
    public ContextualSearchIconSpriteControl(OverlayPanelAnimation overlayPanelAnimation,
            Context context) {
        mOverlayPanelAnimation = overlayPanelAnimation;
    }

    /**
     * @return Whether the search provider icon sprite is visible.
     */
    public boolean isVisible() {
        return mIsVisible;
    }

    /**
     * @param isVisible Whether the search provider icon sprite should be visible.
     */
    public void setIsVisible(boolean isVisible) {
        mIsVisible = isVisible;
    }

    /**
     * @return The completion percentage for the animation; used to calculate which sprite frame
     *         to display.
     */
    public float getCompletionPercentage() {
        return mCompletionPercentage;
    }

    /**
     * @return Whether the appearance of the search provider icon sprite should be animated.
     */
    public boolean shouldAnimateAppearance() {
        return mShouldAnimateAppearance;
    }

    /**
     * @param shouldAnimateAppearance Whether the appearance of the search provider icon sprite
     *                                should be animated.
     */
    public void setShouldAnimateAppearance(boolean shouldAnimateAppearance) {
        if (shouldAnimateAppearance) {
            // The search provider icon sprite should be hidden until the animation starts.
            mIsVisible = false;
            mCompletionPercentage = 0.f;
        } else {
            mIsVisible = true;
            mCompletionPercentage = 1.f;
        }
        mShouldAnimateAppearance = shouldAnimateAppearance;
    }

     // ============================================================================================
     // Search Provider Icon Sprite Appearance Animation
     // ============================================================================================

    /**
     * Animates the appearance of the search provider icon sprite. This should be called after the
     * panel open animation has finished.
     */
    public void animateApperance() {
        // The search provider icon sprite should be visible once the animation starts.
        mIsVisible = true;
        mOverlayPanelAnimation.addToAnimation(this, AnimationType.APPEARANCE, 0.f, 1.f,
                OverlayPanelAnimation.MAXIMUM_ANIMATION_DURATION_MS, 0);
    }

    @Override
    public void setProperty(AnimationType type, float value) {
        if (type == AnimationType.APPEARANCE) {
            mCompletionPercentage = value;
        }
    }

    @Override
    public void onPropertyAnimationFinished(AnimationType prop) {}
}
