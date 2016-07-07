// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * Implementation of BitmapDrawable that allows to tint the color of the drawable for all
 * bitmap drawable states using chrome:tint attribute in XML.
 */
public class TintedDrawable extends BitmapDrawable {
    /**
     * The set of colors that just be used for tinting this bitmap drawable.
     */
    protected ColorStateList mTint;

    public TintedDrawable(Resources res, Bitmap bitmap) {
        super(res, bitmap);
        mTint = ApiCompatibilityUtils.getColorStateList(res, R.color.dark_mode_tint);
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean ret = updateTintColor();
        super.onStateChange(state);
        return ret;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    /**
     * Sets the tint color for the given Drawable for all button states.
     * @param tint The set of colors to use to color the ImageButton.
     */
    public void setTint(ColorStateList tint) {
        if (mTint == tint) return;
        mTint = tint;
        updateTintColor();
    }

    /**
     * Factory method for creating a {@link TintedDrawable} with a resource id.
     */
    public static TintedDrawable constructTintedDrawable(Resources res, int drawableId) {
        Bitmap icon = BitmapFactory.decodeResource(res, drawableId);
        return new TintedDrawable(res, icon);
    }

    /**
     * Factory method for creating a {@link TintedDrawable} with a {@link Bitmap} icon.
     */
    public static TintedDrawable constructTintedDrawable(Resources res, Bitmap icon) {
        return new TintedDrawable(res, icon);
    }

    private boolean updateTintColor() {
        if (mTint == null) return false;
        setColorFilter(mTint.getColorForState(getState(), 0), PorterDuff.Mode.SRC_IN);
        return true;
    }
}
