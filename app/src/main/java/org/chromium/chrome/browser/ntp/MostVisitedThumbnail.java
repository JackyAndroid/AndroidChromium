// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.widget.ImageView;

import org.chromium.chrome.R;

/**
 * Displays a thumbnail for a most visited item on the NTP.
 */
public class MostVisitedThumbnail extends ImageView {

    private final int mDesiredWidth;
    private final int mDesiredHeight;
    private Bitmap mThumbnail;
    private Matrix mImageMatrix;

    /**
     * Constructor for inflating from XML.
     */
    public MostVisitedThumbnail(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        mDesiredWidth = res.getDimensionPixelSize(R.dimen.most_visited_thumbnail_width);
        mDesiredHeight = res.getDimensionPixelSize(R.dimen.most_visited_thumbnail_height);
    }

    /**
     * Updates the thumbnail and trigger a redraw with the new thumbnail.
     */
    void setThumbnail(Bitmap thumbnail) {
        mThumbnail = thumbnail;
        if (thumbnail != null) {
            setImageBitmap(thumbnail);
            setScaleType(ImageView.ScaleType.MATRIX);
            updateThumbnailMatrix();
        } else {
            setBackgroundColor(Color.WHITE);
            setImageResource(R.drawable.most_visited_thumbnail_placeholder);
            setScaleType(ImageView.ScaleType.CENTER);
        }
    }

    /**
     * Updates the matrix used to scale the thumbnail when drawing it. This needs to be called
     * whenever the thumbnail changes or this view's size changes.
     *
     * This matrix ensures that the thumbnail is anchored at the top left corner of this view and
     * is scaled as small as possible while still covering the entire view. Surprisingly, there's
     * no way to get this behavior using the other ImageView.ScaleTypes.
     */
    private void updateThumbnailMatrix() {
        if (mThumbnail == null) return;

        if (mImageMatrix == null) mImageMatrix = new Matrix();
        float widthScale = (float) getMeasuredWidth() / mThumbnail.getWidth();
        float heightScale = (float) getMeasuredHeight() / mThumbnail.getHeight();
        float scale = Math.max(widthScale, heightScale);
        mImageMatrix.setScale(scale, scale);
        setImageMatrix(mImageMatrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateThumbnailMatrix();
    }

    /**
     * Maintains this view's aspect ratio, even when its width is constrained. We can't just use
     * android:adjustViewBounds, since that won't work when the source drawable isn't set or when
     * it's the "missing thumbnail" gray circle, which has a different aspect ratio than thumbnails.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(mDesiredWidth, widthMeasureSpec);
        int height;
        if (width == mDesiredWidth) {
            height = mDesiredHeight;
        } else {
            // The width is fixed. Find the height that keeps the proper aspect ratio.
            height = Math.round((float) mDesiredHeight / mDesiredWidth * width);
            height = resolveSize(height, heightMeasureSpec);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
