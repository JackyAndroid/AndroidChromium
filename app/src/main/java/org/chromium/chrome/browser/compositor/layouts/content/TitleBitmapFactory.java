// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.content;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.InflateException;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * A factory that creates text and favicon bitmaps.
 */
public class TitleBitmapFactory {
    private static final String TAG = "TitleBitmapFactory";

    private static final float TITLE_WIDTH_PERCENTAGE = 1.f;
    // Canvas#drawText() seems to fail when trying to draw 4100 or more characters.
    // See https://crbug.com/524390/ for more details.
    private static final int MAX_NUM_TITLE_CHAR = 1000;

    private final int mMaxWidth;
    private final int mNullFaviconResourceId;

    private final TextPaint mTextPaint;
    private int mFaviconDimension;
    private final int mViewHeight;
    private final float mTextHeight;
    private final float mTextYOffset;

    /**
     * @param context   The current Android's context.
     * @param incognito Whether the title are for incognito mode.
     * @param nullFaviconResourceId A drawable resource id of a default favicon.
     */
    public TitleBitmapFactory(Context context, boolean incognito, int nullFaviconResourceId) {
        mNullFaviconResourceId = nullFaviconResourceId;

        Resources res = context.getResources();
        int textColor = ApiCompatibilityUtils.getColor(res, incognito
                ? R.color.compositor_tab_title_bar_text_incognito
                : R.color.compositor_tab_title_bar_text);
        float textSize = res.getDimension(R.dimen.compositor_tab_title_text_size);

        boolean fakeBoldText = res.getBoolean(R.bool.compositor_tab_title_fake_bold_text);

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(textColor);

        mTextPaint.setTextSize(textSize);
        mTextPaint.setFakeBoldText(fakeBoldText);
        mTextPaint.density = res.getDisplayMetrics().density;

        FontMetrics textFontMetrics = mTextPaint.getFontMetrics();
        mTextHeight = (float) Math.ceil(textFontMetrics.bottom - textFontMetrics.top);
        mTextYOffset = -textFontMetrics.top;

        mFaviconDimension = res.getDimensionPixelSize(R.dimen.compositor_tab_title_favicon_size);
        mViewHeight = (int) Math.max(mFaviconDimension, mTextHeight);

        int width = res.getDisplayMetrics().widthPixels;
        int height = res.getDisplayMetrics().heightPixels;
        mMaxWidth = (int) (TITLE_WIDTH_PERCENTAGE * Math.max(width, height));

        // Set the favicon dimension here.
        mFaviconDimension = Math.min(mMaxWidth, mFaviconDimension);
    }

    /**
     * Generates the favicon bitmap.
     *
     * @param context   Android's UI context.
     * @param favicon   The favicon of the tab.
     * @return          The Bitmap with the favicon.
     */
    public Bitmap getFaviconBitmap(Context context, Bitmap favicon) {
        try {
            Bitmap b = Bitmap.createBitmap(
                    mFaviconDimension, mFaviconDimension, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (favicon == null) {
                Drawable drawable = ApiCompatibilityUtils.getDrawable(
                        context.getResources(), mNullFaviconResourceId);
                if (drawable instanceof BitmapDrawable) {
                    favicon = ((BitmapDrawable) drawable).getBitmap();
                }
            }
            if (favicon != null) {
                c.drawBitmap(favicon, Math.round((mFaviconDimension - favicon.getWidth()) / 2.0f),
                        Math.round((mFaviconDimension - favicon.getHeight()) / 2.0f), null);
            }
            return b;
        } catch (OutOfMemoryError ex) {
            Log.w(TAG, "OutOfMemoryError while building favicon texture.");
        } catch (InflateException ex) {
            Log.w(TAG, "InflateException while building favicon texture.");
        }

        return null;
    }

    /**
     * Generates the title bitmap.
     *
     * @param context   Android's UI context.
     * @param title     The title of the tab.
     * @return          The Bitmap with the title.
     */
    public Bitmap getTitleBitmap(Context context, String title) {
        try {
            boolean drawText = !TextUtils.isEmpty(title);
            int textWidth =
                    drawText ? (int) Math.ceil(Layout.getDesiredWidth(title, mTextPaint)) : 0;
            // Minimum 1 width bitmap to avoid createBitmap function's IllegalArgumentException,
            // when textWidth == 0.
            Bitmap b = Bitmap.createBitmap(Math.max(Math.min(mMaxWidth, textWidth), 1), mViewHeight,
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (drawText) {
                c.drawText(title, 0, Math.min(MAX_NUM_TITLE_CHAR, title.length()), 0,
                        Math.round((mViewHeight - mTextHeight) / 2.0f + mTextYOffset), mTextPaint);
            }
            return b;
        } catch (OutOfMemoryError ex) {
            Log.w(TAG, "OutOfMemoryError while building title texture.");
        } catch (InflateException ex) {
            Log.w(TAG, "InflateException while building title texture.");
        }

        return null;
    }
}
