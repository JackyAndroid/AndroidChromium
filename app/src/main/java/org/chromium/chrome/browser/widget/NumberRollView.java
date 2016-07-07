// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Property;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

import java.text.NumberFormat;

/**
 * View that shows an integer number. It provides a smooth roll animation on changing the
 * number.
 */
public class NumberRollView extends FrameLayout {
    private TextView mUpNumber;
    private TextView mDownNumber;
    private float mNumber;
    private Animator mLastRollAnimator;

    /**
     * A Property wrapper around the <code>number</code> functionality handled by the
     * {@link NumberRollView#setNumberRoll(float)} and {@link NumberRollView#getNumberRoll()}
     * methods.
     */
    public static final Property<NumberRollView, Float> NUMBER_PROPERTY =
            new Property<NumberRollView, Float>(Float.class, "") {
        @Override
        public void set(NumberRollView view, Float value) {
            view.setNumberRoll(value);
        }

        @Override
        public Float get(NumberRollView view) {
            return view.getNumberRoll();
        }
    };

    /**
     * Constructor for inflating from XML.
     */
    public NumberRollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mUpNumber = (TextView) findViewById(R.id.up);
        mDownNumber = (TextView) findViewById(R.id.down);

        assert mUpNumber != null;
        assert mDownNumber != null;

        setNumberRoll(mNumber);
    }

    /**
     * Sets a number to display.
     * @param animate Whether it should smoothly animate to the number.
     */
    public void setNumber(int number, boolean animate) {
        if (mLastRollAnimator != null) mLastRollAnimator.cancel();

        if (animate) {
            Animator rollAnimator = ObjectAnimator.ofFloat(this, NUMBER_PROPERTY, number);
            rollAnimator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
            rollAnimator.start();
            mLastRollAnimator = rollAnimator;
        } else {
            setNumberRoll(number);
        }
    }

    /**
     * Gets the current number roll position.
     */
    private float getNumberRoll() {
        return mNumber;
    }

    /**
     * Sets the number roll position.
     */
    private void setNumberRoll(float number) {
        mNumber = number;
        int downNumber = (int) number;
        int upNumber = downNumber + 1;

        NumberFormat numberFormatter = NumberFormat.getIntegerInstance();
        String newString = numberFormatter.format(upNumber);
        if (!newString.equals(mUpNumber.getText().toString())) mUpNumber.setText(newString);

        newString = numberFormatter.format(downNumber);
        if (!newString.equals(mDownNumber.getText().toString())) mDownNumber.setText(newString);

        float offset = number % 1.0f;

        mUpNumber.setTranslationY(mUpNumber.getHeight() * (offset - 1.0f));
        mDownNumber.setTranslationY(mDownNumber.getHeight() * offset);

        mUpNumber.setAlpha(offset);
        mDownNumber.setAlpha(1.0f - offset);
    }
}