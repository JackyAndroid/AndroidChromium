// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.widget.ImageButton;

import org.chromium.chrome.R;

/**
 * Implementation of ImageButton that allows to tint the color of the image button for all
 * image button states using chrome:tint attribute in XML.
 */
public class TintedImageButton extends ImageButton {
    private ColorStateList mTint;

    public TintedImageButton(Context context) {
        super(context);
    }

    public TintedImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public TintedImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.TintedImage, defStyle, 0);
        setTintInternal(a.getColorStateList(R.styleable.TintedImage_tint));
        a.recycle();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        updateTintColor();
    }

    /**
     * Sets the tint color for the given ImageButton for all button states.
     * @param tint The set of colors to use to color the ImageButton.
     */
    public void setTint(ColorStateList tint) {
        if (mTint == tint) return;
        setTintInternal(tint);
        updateTintColor();
    }

    private void setTintInternal(ColorStateList tint) {
        mTint = tint;
    }

    private void updateTintColor() {
        if (mTint == null) return;
        setColorFilter(mTint.getColorForState(getDrawableState(), 0), PorterDuff.Mode.SRC_IN);
    }
}
