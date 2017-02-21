// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.TintedDrawable;

@SuppressLint("Instantiatable")
class BookmarkDrawerListItemView extends AppCompatTextView {
    public BookmarkDrawerListItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setIcon(int iconDrawableId) {
        if (iconDrawableId == 0) {
            setCompoundDrawablePadding(0);
        } else {
            setCompoundDrawablePadding(getResources().getDimensionPixelSize(
                    R.dimen.bookmark_drawer_drawable_padding));
        }

        Drawable drawable = TintedDrawable.constructTintedDrawable(getResources(), iconDrawableId);
        ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                this, drawable, null, null, null);
    }
}
