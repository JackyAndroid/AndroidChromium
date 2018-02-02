// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.TintedDrawable;

import java.util.Locale;

/**
 * A drawable for the tab switcher icon.
 */
public class TabSwitcherDrawable extends TintedDrawable {
    private final float mSingleDigitTextSize;
    private final float mDoubleDigitTextSize;

    private final Rect mTextBounds = new Rect();
    private final TextPaint mTextPaint;

    // Tab Count Label
    private int mTabCount;
    private boolean mIncognito;

    /**
     * Creates a {@link TabSwitcherDrawable}.
     * @param resources A {@link Resources} instance.
     * @param useLight  Whether or not to use light or dark textures and text colors.
     * @return          A {@link TabSwitcherDrawable} instance.
     */
    public static TabSwitcherDrawable createTabSwitcherDrawable(
            Resources resources, boolean useLight) {
        Bitmap icon = BitmapFactory.decodeResource(resources, R.drawable.btn_tabswitcher);
        return new TabSwitcherDrawable(resources, useLight, icon);
    }

    private TabSwitcherDrawable(Resources resources, boolean useLight, Bitmap bitmap) {
        super(resources, bitmap);
        setTint(ApiCompatibilityUtils.getColorStateList(resources,
                useLight ? R.color.light_mode_tint : R.color.dark_mode_tint));
        mSingleDigitTextSize =
                resources.getDimension(R.dimen.toolbar_tab_count_text_size_1_digit);
        mDoubleDigitTextSize =
                resources.getDimension(R.dimen.toolbar_tab_count_text_size_2_digit);

        mTextPaint = new TextPaint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextAlign(Align.CENTER);
        mTextPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        mTextPaint.setColor(getColorForState());
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean retVal = super.onStateChange(state);
        if (retVal) mTextPaint.setColor(getColorForState());
        return retVal;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        String textString = getTabCountString();
        if (!textString.isEmpty()) {
            mTextPaint.getTextBounds(textString, 0, textString.length(), mTextBounds);

            Rect drawableBounds = getBounds();
            int textX = drawableBounds.width() / 2;
            int textY = drawableBounds.height() / 2 + (mTextBounds.bottom - mTextBounds.top) / 2
                    - mTextBounds.bottom;

            canvas.drawText(textString, textX, textY, mTextPaint);
        }
    }

    /**
     * @return The current tab count this drawable is displaying.
     */
    @VisibleForTesting
    public int getTabCount() {
        return mTabCount;
    }

    /**
     * Update the visual state based on the number of tabs present.
     * @param tabCount The number of tabs.
     */
    public void updateForTabCount(int tabCount, boolean incognito) {
        if (tabCount == mTabCount && incognito == mIncognito) return;
        mTabCount = tabCount;
        mIncognito = incognito;
        float textSizePx = mTabCount > 9 ? mDoubleDigitTextSize : mSingleDigitTextSize;
        mTextPaint.setTextSize(textSizePx);
        invalidateSelf();
    }

    private String getTabCountString() {
        if (mTabCount <= 0) {
            return "";
        } else if (mTabCount > 99) {
            return mIncognito ? ";)" : ":D";
        } else {
            return String.format(Locale.getDefault(), "%d", mTabCount);
        }
    }

    private int getColorForState() {
        return mTint.getColorForState(getState(), 0);
    }

    @Override
    public void setTint(ColorStateList tint) {
        super.setTint(tint);
        if (mTextPaint != null) mTextPaint.setColor(getColorForState());
    }
}
