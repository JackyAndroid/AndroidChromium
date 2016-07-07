// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.newtab;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Property;
import android.widget.Button;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Button for creating new tabs.
 */
public class NewTabButton extends Button implements Drawable.Callback {

    private static final Property<Drawable, Integer> DRAWABLE_ALPHA_PROPERTY =
            new Property<Drawable, Integer>(Integer.class, "alpha") {
        @Override
        public Integer get(Drawable d) {
            // getAlpha() is only exposed on drawable in API 19+, so we rely on animations
            // always setting the starting and ending values instead of relying on this
            // property.
            return 0;
        }

        @Override
        public void set(Drawable d, Integer alpha) {
            d.setAlpha(alpha);
        }
    };

    private final Drawable mNormalDrawable;
    private final Drawable mIncognitoDrawable;
    private boolean mIsIncognito;
    private AnimatorSet mTransitionAnimation;

    /**
     * Constructor for inflating from XML.
     */
    public NewTabButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mNormalDrawable = ApiCompatibilityUtils.getDrawable(
                getResources(), R.drawable.btn_new_tab_white);
        mNormalDrawable.setBounds(
                0, 0, mNormalDrawable.getIntrinsicWidth(), mNormalDrawable.getIntrinsicHeight());
        mNormalDrawable.setCallback(this);
        mIncognitoDrawable = ApiCompatibilityUtils.getDrawable(
                getResources(), R.drawable.btn_new_tab_incognito);
        mIncognitoDrawable.setBounds(
                0, 0,
                mIncognitoDrawable.getIntrinsicWidth(), mIncognitoDrawable.getIntrinsicHeight());
        mIncognitoDrawable.setCallback(this);
        mIsIncognito = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.max(
                mIncognitoDrawable.getIntrinsicWidth(), mNormalDrawable.getIntrinsicWidth());
        desiredWidth += getPaddingLeft() + getPaddingRight();
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(this);
        int paddingStart = ApiCompatibilityUtils.getPaddingStart(this);
        int widthWithoutPadding = getWidth() - paddingStart;

        canvas.save();
        if (!isRtl) canvas.translate(paddingStart, 0);

        canvas.save();
        canvas.translate(0, (getHeight() - mNormalDrawable.getIntrinsicHeight()) / 2.f);
        if (isRtl) {
            canvas.translate(widthWithoutPadding - mNormalDrawable.getIntrinsicWidth(), 0);
        }
        mNormalDrawable.draw(canvas);
        canvas.restore();

        if (mIsIncognito || (mTransitionAnimation != null && mTransitionAnimation.isRunning())) {
            canvas.save();
            canvas.translate(0, (getHeight() - mIncognitoDrawable.getIntrinsicHeight()) / 2.f);
            if (isRtl) {
                canvas.translate(widthWithoutPadding - mIncognitoDrawable.getIntrinsicWidth(), 0);
            }
            mIncognitoDrawable.draw(canvas);
            canvas.restore();
        }

        canvas.restore();
    }

    @Override
    public void invalidateDrawable(Drawable dr) {
        if (dr == mIncognitoDrawable || dr == mNormalDrawable) {
            invalidate();
        } else {
            super.invalidateDrawable(dr);
        }
    }

    /**
     * Updates the visual state based on whether incognito or normal tabs are being created.
     * @param incognito Whether the button is now used for creating incognito tabs.
     */
    public void setIsIncognito(boolean incognito) {
        if (mIsIncognito == incognito) return;
        mIsIncognito = incognito;

        if (mTransitionAnimation != null) {
            mTransitionAnimation.cancel();
            mTransitionAnimation = null;
        }

        Drawable fadeOutDrawable = incognito ? mNormalDrawable : mIncognitoDrawable;
        Drawable fadeInDrawable = incognito ? mIncognitoDrawable : mNormalDrawable;

        if (getVisibility() != VISIBLE) {
            fadeOutDrawable.setAlpha(0);
            fadeInDrawable.setAlpha(255);
            return;
        }

        List<Animator> animations = new ArrayList<Animator>();
        Animator animation = ObjectAnimator.ofInt(
                fadeOutDrawable, DRAWABLE_ALPHA_PROPERTY, 255, 0);
        animation.setDuration(100);
        animations.add(animation);

        animation = ObjectAnimator.ofInt(
                fadeInDrawable, DRAWABLE_ALPHA_PROPERTY, 0, 255);
        animation.setStartDelay(150);
        animation.setDuration(100);
        animations.add(animation);

        mTransitionAnimation = new AnimatorSet();
        mTransitionAnimation.playTogether(animations);
        mTransitionAnimation.start();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        mNormalDrawable.setState(getDrawableState());
        mIncognitoDrawable.setState(getDrawableState());
    }
}