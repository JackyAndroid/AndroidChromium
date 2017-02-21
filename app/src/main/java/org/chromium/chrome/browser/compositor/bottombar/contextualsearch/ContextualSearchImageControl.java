// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;
import android.support.v4.view.animation.PathInterpolatorCompat;
import android.text.TextUtils;
import android.view.animation.Interpolator;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;

/**
 * Controls the image shown in the Bar. Owns the {@link ContextualSearchIconSpriteControl} and
 * details about the thumbnail, and handles animating between the two.
 */
public class ContextualSearchImageControl
        implements ChromeAnimation.Animatable<ContextualSearchImageControl.AnimationType> {
    /**
      * Animation properties.
      */
    protected enum AnimationType {
        THUMBNAIL_VISIBILITY
    }

    /** The current context. */
    private final Context mContext;

    /** The OverlayPanelAnimation used to add animations. */
    private final OverlayPanelAnimation mOverlayPanelAnimation;

    public ContextualSearchImageControl(OverlayPanelAnimation overlayPanelAnimation,
            Context context) {
        mContext = context;
        mOverlayPanelAnimation = overlayPanelAnimation;
    }

    // ============================================================================================
    // Search Provider Icon Sprite
    // ============================================================================================

    private ContextualSearchIconSpriteControl mIconSpriteControl;

    /**
     * @return The {@link ContextualSearchIconSpriteControl} for the panel.
     */
    public ContextualSearchIconSpriteControl getIconSpriteControl() {
        if (mIconSpriteControl == null) {
            mIconSpriteControl =
                    new ContextualSearchIconSpriteControl(mOverlayPanelAnimation, mContext);
        }
        return mIconSpriteControl;
    }

    /**
     * @param shouldAnimateIconSprite Whether the search provider icon sprite should be animated.
     */
    public void setShouldAnimateIconSprite(boolean shouldAnimateIconSprite) {
        getIconSpriteControl().setShouldAnimateAppearance(shouldAnimateIconSprite);
    }

    // ============================================================================================
    // Thumbnail
    // ============================================================================================

    /**
     * The URL of the thumbnail to display.
     */
    private String mThumbnailUrl;

    /**
     * The height and width of the thumbnail.
     */
    private int mThumbnailSize;

    /**
     * Whether the thumbnail is visible.
     */
    private boolean mThumbnailVisible;

    /**
     * The thumbnail visibility percentage, which dictates how and where to draw the thumbnail.
     * The thumbnail is not visible at all at 0.f and completely visible at 1.f.
     */
    private float mThumbnailVisibilityPercentage = 0.f;

    /**
     * @param thumbnailUrl The URL of the thumbnail to display
     */
    public void setThumbnailUrl(String thumbnailUrl) {
        mThumbnailUrl = thumbnailUrl;
    }

    /**
     * @return The URL used to fetch a thumbnail to display in the Bar. Will return an empty string
     *         if no thumbnail is available.
     */
    public String getThumbnailUrl() {
        return mThumbnailUrl != null ? mThumbnailUrl : "";
    }

    /**
     * Hides the thumbnail if it is visible and makes the icon sprite visible. Also resets the
     * thumbnail URL.
     * @param animate Whether hiding the thumbnail should be animated.
     */
    public void hideThumbnail(boolean animate) {
        getIconSpriteControl().setIsVisible(true);
        if (mThumbnailVisible && animate) {
            animateThumbnailVisibility(false);
        } else {
            mOverlayPanelAnimation.cancelAnimation(this, AnimationType.THUMBNAIL_VISIBILITY);
            onThumbnailHidden();
        }
    }

    /**
     * @return The height and width of the thumbnail in px.
     */
    public int getThumbnailSize() {
        if (mThumbnailSize == 0) {
            mThumbnailSize = mContext.getResources().getDimensionPixelSize(
                    R.dimen.contextual_search_thumbnail_size);
        }
        return mThumbnailSize;
    }

    /**
     * @return Whether the thumbnail is visible.
     */
    public boolean getThumbnailVisible() {
        return mThumbnailVisible;
    }

    /**
     * @return The thumbnail visibility percentage, which dictates how and where to draw the
     *         thumbnail. The thumbnail is not visible at all at 0.f and completely visible at 1.f.
     */
    public float getThumbnailVisibilityPercentage() {
        return mThumbnailVisibilityPercentage;
    }

    /**
     * Called when the thumbnail has finished being fetched.
     * @param success Whether fetching the thumbnail was successful.
     */
    public void onThumbnailFetched(boolean success) {
        // Check if the thumbnail URL was cleared before the thumbnail fetch completed. This may
        // occur if the user taps to refine the search.
        mThumbnailVisible = success && !TextUtils.isEmpty(mThumbnailUrl);
        if (!mThumbnailVisible) return;

        // TODO(twellington): if the icon sprite is animating wait to start the thumbnail visibility
        //                    animation.
        animateThumbnailVisibility(true);
    }

    private void onThumbnailHidden() {
        mThumbnailUrl = "";
        mThumbnailVisible = false;
        getIconSpriteControl().setIsVisible(true);
        mThumbnailVisibilityPercentage = 0.f;
    }

    // ============================================================================================
    // Thumbnail Animation
    // ============================================================================================

    private Interpolator mThumbnailVisibilityInterpolator;

    private void animateThumbnailVisibility(boolean visible) {
        if (mThumbnailVisibilityInterpolator == null) {
            mThumbnailVisibilityInterpolator = PathInterpolatorCompat.create(0.4f, 0.f, 0.6f, 1.f);
        }

        mOverlayPanelAnimation.cancelAnimation(this, AnimationType.THUMBNAIL_VISIBILITY);

        float endValue = visible ? 1.f : 0.f;
        mOverlayPanelAnimation.addToAnimation(this, AnimationType.THUMBNAIL_VISIBILITY,
                mThumbnailVisibilityPercentage, endValue,
                OverlayPanelAnimation.BASE_ANIMATION_DURATION_MS, 0, false,
                mThumbnailVisibilityInterpolator);
    }

    @Override
    public void setProperty(AnimationType prop, float val) {
        if (prop == AnimationType.THUMBNAIL_VISIBILITY) {
            mThumbnailVisibilityPercentage = val;
        }
    }

    @Override
    public void onPropertyAnimationFinished(AnimationType prop) {
        if (prop == AnimationType.THUMBNAIL_VISIBILITY) {
            if (mThumbnailVisibilityPercentage == 0.f) {
                onThumbnailHidden();
            } else {
                getIconSpriteControl().setIsVisible(false);
            }
        }
    }

}
