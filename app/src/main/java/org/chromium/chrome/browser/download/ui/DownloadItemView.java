// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.util.AttributeSet;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.TintedImageView;
import org.chromium.chrome.browser.widget.selection.SelectableItemView;

import javax.annotation.Nullable;

/**
 * The view for a downloaded item displayed in the Downloads list.
 */
public class DownloadItemView extends SelectableItemView<DownloadHistoryItemWrapper> {
    private DownloadHistoryItemWrapper mItem;
    private TintedImageView mIconView;
    private int mIconBackgroundColor;
    private int mIconBackgroundColorSelected;
    private int mIconResId;
    private Bitmap mThumbnailBitmap;
    private ColorStateList mWhiteTint;

    /**
     * Constructor for inflating from XML.
     */
    public DownloadItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIconBackgroundColor =
                ApiCompatibilityUtils.getColor(context.getResources(), R.color.light_active_color);
        mIconBackgroundColorSelected =
                ApiCompatibilityUtils.getColor(context.getResources(), R.color.google_grey_600);
        mWhiteTint =
                ApiCompatibilityUtils.getColorStateList(getResources(), R.color.white_mode_tint);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = (TintedImageView) findViewById(R.id.icon_view);
    }

    /**
     * Initialize the DownloadItemView. Must be called before the item can respond to click events.
     *
     * @param item      The item represented by this DownloadItemView.
     * @param iconResId The drawable resource ID to use for the icon ImageView.
     * @param thumbnail The Bitmap to use for the thumbnail or null.
     */
    public void initialize(DownloadHistoryItemWrapper item, int iconResId,
            @Nullable Bitmap thumbnail) {
        mItem = item;
        setItem(item);

        mIconResId = iconResId;
        mThumbnailBitmap = thumbnail;
        updateIconView();
    }

    /**
     * @param thumbnail The Bitmap to use for the icon ImageView.
     */
    public void setThumbnailBitmap(Bitmap thumbnail) {
        mThumbnailBitmap = thumbnail;
        updateIconView();
    }

    @Override
    public void onClick() {
        if (mItem != null) mItem.open();
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        updateIconView();
    }

    private void updateIconView() {
        if (isChecked()) {
            mIconView.setBackgroundColor(mIconBackgroundColorSelected);
            mIconView.setImageResource(R.drawable.ic_check_googblue_24dp);
            mIconView.setTint(mWhiteTint);
        } else if (mThumbnailBitmap != null) {
            mIconView.setBackground(null);
            mIconView.setImageBitmap(mThumbnailBitmap);
            mIconView.setTint(null);
        } else {
            mIconView.setBackgroundColor(mIconBackgroundColor);
            mIconView.setImageResource(mIconResId);
            mIconView.setTint(mWhiteTint);
        }
    }
}
