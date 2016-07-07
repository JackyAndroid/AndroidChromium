// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.Property;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.chromium.chrome.R;

import java.util.Arrays;

/**
 * Account chooser that displays profile images in a carousel and allows users to rotate it to
 * select an account.
 *
 * Internally it is implemented using four ImageViews that get translated along the X axis based
 * on the current carousel position.
 *
 *     |'''''|            |'''''|            |'''''|            |'''''|            |'''''|
 * |'''|     |'''|    |'''|     |'''|    |'''|     |'''|    |'''|     |'''|    |'''|     |'''|
 * |IM3| IM0 |IM1| -> |IM0| IM1 |IM2| -> |IM1| IM2 |IM3| -> |IM2| IM3 |IM0| -> |IM3| IM0 |IM1|
 * |,,,|     |,,,|    |,,,|     |,,,|    |,,,|     |,,,|    |,,,|     |,,,|    |,,,|     |,,,|
 *     |,,,,,|            |,,,,,|            |,,,,,|            |,,,,,|            |,,,,,|
 *
 *   mPosition=0        mPosition=1        mPosition=2        mPosition=3        mPosition=4
 *
 * IM0 is mViews[0]
 * IM1 is mViews[1]
 * IM2 is mViews[2]
 * IM3 is mViews[3]
 *
 * Each ImageView is displaying a profile image if there is one, however it is not necessarily true
 * that IM0 is showing mImages[0] and IM1 is showing mImages[1], and so on. This changes when there
 * are more than 4 accounts and ImageViews get reused for new accounts.
 */
public class ImageCarousel extends FrameLayout implements GestureDetector.OnGestureListener {

    /**
     * Constant used together image width to calculate how far should should each image move in
     * x axis. This value was tweaked until images did not overlap with each other when scrolling.
     */
    private static final float TRANSLATION_FACTOR = 0.64f;

    /**
     * Constant used together with carousel width to calculate how should fling velocity in x axis
     * be scaled when changing ImageCarousel position. It was tweaked for flings to look natural.
     */
    private static final float FLING_FACTOR = 20f * 0.92f / 2f;

    /**
     * Constant used together with carousel width to calculate how should scroll distance in x axis
     * be scaled when changing ImageCarousel position. It was tweaked for image to follow user's
     * finger when scrolling.
     */
    private static final float SCROLL_FACTOR = 0.92f / 2f;

    /**
     * Listener to ImageCarousel center position changes.
     */
    public interface ImageCarouselPositionChangeListener {
        /**
         * @param position The new center position of the ImageCarousel. It is a number in
         *                 range [0, mImages.length).
         */
        void onPositionChanged(int position);
    }

    private static final int SCROLL_ANIMATION_DURATION_MS = 200;
    private static final int ACCOUNT_SIGNED_IN_ANIMATION_DURATION_MS = 200;

    private static final float MINIMUM_POSITION_TWO_IMAGES = -0.1f;
    private static final float MAXIMUM_POSITION_TWO_IMAGES = 1.1f;

    /**
     * Number of ImageViews used in ImageCarousel.
     */
    private static final int VIEW_COUNT = 4;

    private static final int[] ORDER_OFFSETS = {2, 1, 3, 0};

    private static final int[] POSITION_OFFSETS = {0, -1, 2, 1};

    private static final int[] BITMAP_OFFSETS = {2, 1, -1, 0};

    /**
     * Property used to animate scrolling of the ImageCarousel.
     */
    private static final Property<ImageCarousel, Float> POSITION_PROPERTY =
            new Property<ImageCarousel, Float>(Float.class, "") {
        @Override
        public Float get(ImageCarousel object) {
            return object.mPosition;
        }

        @Override
        public void set(ImageCarousel object, Float value) {
            object.setPosition(value);
        }
    };

    /**
     * Property used to animate the alpha value of the images that are currently on the left and
     * the right of the center image.
     */
    private static final Property<ImageCarousel, Float> BACKGROUND_IMAGE_ALPHA =
            new Property<ImageCarousel, Float>(Float.class, "") {
        @Override
        public Float get(ImageCarousel object) {
            return object.mViews[object.getChildDrawingOrder(VIEW_COUNT, 1)].getAlpha();
        }

        @Override
        public void set(ImageCarousel object, Float value) {
            object.mViews[object.getChildDrawingOrder(VIEW_COUNT, 1)].setAlpha(value);
            object.mViews[object.getChildDrawingOrder(VIEW_COUNT, 2)].setAlpha(value);
        }
    };

    /**
     * Gesture detector used to capture scrolls, flings and taps on the image carousel.
     */
    private GestureDetector mGestureDetector;

    /**
     * Array that holds four ImageViews that are used to display images in the carousel.
     */
    private ImageView[] mViews = new ImageView[VIEW_COUNT];

    /**
     * Images that shown in the image carousel.
     */
    private Bitmap[] mImages;

    private Animator mScrollAnimator;
    private Animator mFadeInOutAnimator;

    private float mPosition = 0f;

    private ImageCarouselPositionChangeListener mListener;
    private int mLastPosition = 0;
    private boolean mNeedsPositionUpdates = true;

    private int mCarouselWidth;
    private int mImageWidth;
    private float mScrollScalingFactor;
    private float mFlingScalingFactor;
    private float mTranslationFactor;

    private boolean mScrollingDisabled;
    private boolean mAccountSelected;

    public ImageCarousel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGestureDetector = new GestureDetector(getContext(), this);
    }

    /**
     * Scrolls ImageCarousel to the closest whole position for the desired position.
     * @param position Desired ImageCarousel position.
     * @param decelerate Whether animation should be decelerating.
     * @param needsPositionUpdates Whether this scroll should trigger position update calls to
     *                             mListener.
     */
    public void scrollTo(float position, boolean decelerate, boolean needsPositionUpdates) {
        mNeedsPositionUpdates = needsPositionUpdates;
        if (mScrollAnimator != null) mScrollAnimator.cancel();

        position = Math.round(position);
        if (mImages != null && mImages.length == 2) {
            if (position < 0) position = 0;
            if (position > 1) position = 1;
        }
        mScrollAnimator = ObjectAnimator.ofFloat(this, POSITION_PROPERTY, mPosition, position);
        mScrollAnimator.setDuration(SCROLL_ANIMATION_DURATION_MS);
        if (decelerate) mScrollAnimator.setInterpolator(new DecelerateInterpolator());
        mScrollAnimator.start();
    }

    /**
     * @param listener Listener that should be notified on ImageCarousel center position changes.
     */
    public void setListener(ImageCarouselPositionChangeListener listener) {
        mListener = listener;
    }

    /**
     * @param images Images that should be displayed in the ImageCarousel.
     */
    public void setImages(Bitmap[] images) {
        switch (images.length) {
            case 0:
                mImages = null;
                mScrollingDisabled = true;
                break;
            case 1:
                mScrollingDisabled = true;
                mImages = Arrays.copyOf(images, images.length);
                break;
            default:
                // Enable scrolling only if no account has already been selected.
                mScrollingDisabled = mAccountSelected;
                mImages = Arrays.copyOf(images, images.length);
                break;
        }

        updateImageViews();
    }

    /**
     * Sets the ImageCarousel to signed in mode that disables scrolling, animates away the
     * background images, and displays a checkmark next to the account image that was chosen.
     */
    public void setSignedInMode() {
        mScrollingDisabled = true;
        mAccountSelected = true;
        setPosition(getCenterPosition());

        ImageView checkmark = new ImageView(getContext());
        checkmark.setImageResource(R.drawable.verify_checkmark);
        setLayoutParamsForCheckmark(checkmark);
        addView(checkmark);

        if (mFadeInOutAnimator != null) mFadeInOutAnimator.cancel();
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, BACKGROUND_IMAGE_ALPHA, 0),
                ObjectAnimator.ofFloat(checkmark, View.ALPHA, 0.0f, 1.0f));
        mFadeInOutAnimator = animatorSet;
        mFadeInOutAnimator.setDuration(ACCOUNT_SIGNED_IN_ANIMATION_DURATION_MS);
        mFadeInOutAnimator.start();
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        mImageWidth = getResources().getDimensionPixelSize(R.dimen.fre_image_carousel_height);
        for (int i = 0; i < VIEW_COUNT; ++i) {
            ImageView view = new ImageView(getContext());
            FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams(mImageWidth, mImageWidth);
            params.gravity = Gravity.CENTER;
            view.setLayoutParams(params);
            mViews[i] = view;
            addView(view);
        }

        mCarouselWidth = getResources().getDimensionPixelSize(R.dimen.fre_image_carousel_width);
        mScrollScalingFactor = SCROLL_FACTOR * mCarouselWidth;
        mFlingScalingFactor = FLING_FACTOR * mCarouselWidth;
        mTranslationFactor = TRANSLATION_FACTOR * mImageWidth;

        setChildrenDrawingOrderEnabled(true);
        setPosition(0f);
    }

    /**
     * @return The index of the view that should be drawn on the given iteration.
     */
    @Override
    protected int getChildDrawingOrder(int childCount, int iteration) {
        // Draw the views that are not our 4 ImagesViews in their normal order.
        if (iteration >= VIEW_COUNT) return iteration;

        // Draw image views in the correct z order based on the current position.
        return (Math.round(mPosition) + ORDER_OFFSETS[iteration]) % VIEW_COUNT;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mScrollingDisabled) return false;
        if (mGestureDetector.onTouchEvent(event)) return true;

        if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            scrollTo(mPosition, false, true);
        }

        return false;
    }

    // Implementation of GestureDetector.OnGestureListener

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {}

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        mNeedsPositionUpdates = true;
        if (motionEvent.getX() < (mCarouselWidth - mImageWidth) / 2f) {
            scrollTo(mPosition - 1, false, true);
            return true;
        } else if (motionEvent.getX() > (mCarouselWidth + mImageWidth) / 2f) {
            scrollTo(mPosition + 1, false, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // Once the user has started scrolling, prevent the parent view from handling touch events.
        // This allows the ImageCarousel to be behave reasonably when nested inside a ScrollView.
        getParent().requestDisallowInterceptTouchEvent(true);

        mNeedsPositionUpdates = true;
        setPosition(mPosition + distanceX / mScrollScalingFactor);
        return true;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {}

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        mNeedsPositionUpdates = true;
        scrollTo(mPosition - velocityX / mFlingScalingFactor, true, true);
        return true;
    }

    // Internal methods

    /**
     * Updates the position, scale, alpha and image shown for all four ImageViews used by
     * the ImageCarousel.
     */
    private void updateImageViews() {
        if (mImages == null) return;

        for (int i = 0; i < VIEW_COUNT; i++) {
            if (mAccountSelected && i != getCenterPosition()) continue;

            ImageView image = mViews[i];

            updateBitmap(i);

            final float position = mPosition + POSITION_OFFSETS[i];

            // X translation is a sin function with a period of 4 and with range
            // [-mTranslationFactor, mTranslationFactor]
            image.setTranslationX(
                    -mTranslationFactor * ((float) Math.sin(position * Math.PI / 2f)));

            // scale is a cos function with a period of 4 and range [1/3, 1]
            // scale is 1 when the image is in the front and 1/3 when the image is behind other
            // images.
            final float scale = (float) Math.cos(position * Math.PI / 2f) / 3f + 2f / 3f;
            image.setScaleY(scale);
            image.setScaleX(scale);

            // alpha is a cos^2 function with a period of 2 and range [0, 1]
            // alpha is 1 when the image is in the center in the front and 0 when it is in the back.
            final float alpha = (float) Math.pow(Math.cos(position * Math.PI / 4f), 2);
            image.setAlpha(alpha);
        }
    }

    private void updateBitmap(int i) {
        int drawingOrder = getChildDrawingOrder(VIEW_COUNT, i);
        // Only draw one top bitmap for one image case.
        if (mImages.length == 1 && drawingOrder > 0) return;
        // Only draw two top bitmaps for two images case.
        if (mImages.length == 2 && drawingOrder > 1) return;
        ImageView image = mViews[drawingOrder];
        image.setImageBitmap(mImages[
                (mImages.length + Math.round(mPosition) + BITMAP_OFFSETS[i]) % mImages.length]);
    }

    private void setPosition(float position) {
        if (mImages != null) {
            if (mImages.length == 2) {
                position = Math.max(MINIMUM_POSITION_TWO_IMAGES, position);
                position = Math.min(MAXIMUM_POSITION_TWO_IMAGES, position);
                mPosition = position;
            } else {
                mPosition = ((position % mImages.length) + mImages.length) % mImages.length;
            }
        }

        int adjustedPosition = getCenterPosition();
        if (adjustedPosition != mLastPosition) {
            mLastPosition = adjustedPosition;
            if (mListener != null && mNeedsPositionUpdates) {
                mListener.onPositionChanged(adjustedPosition);
            }
        }

        // Need to call invalidate() for getChildDrawingOrder() to be called since the image
        // order has changed.
        updateImageViews();
        invalidate();
    }

    private int getCenterPosition() {
        if (mImages == null) return 0;
        return Math.round(mPosition) % mImages.length;
    }

    private void setLayoutParamsForCheckmark(View view) {
        int size = getResources().getDimensionPixelSize(R.dimen.fre_checkmark_size);
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER;
        view.setLayoutParams(params);
        view.setTranslationX((mImageWidth - size) / 2f);
        view.setTranslationY((mImageWidth - size) / 2f);
    }
}