// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.content.Context;
import android.support.annotation.ColorRes;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.third_party.android.swiperefresh.MaterialProgressDrawable;

/**
 * Displays an indeterminate circular spinner, similar to {@link android.widget.ProgressBar} in
 * Lollipop+. This class allows to backport the Material style to pre-L OS versions.
 *
 * It stays invisible for the first 500ms when shown, to avoid showing a spinner when content
 * can load very quickly.
 */
public class ProgressIndicatorView extends ImageView {
    private static final int SHOW_DELAY_MS = 500;
    private final Runnable mShowSpinnerRunnable;

    private final MaterialProgressDrawable mProgressDrawable;
    private boolean mPostedCallback = false;

    /**
     * Constructor for use in layout files.
     */
    public ProgressIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mShowSpinnerRunnable = new Runnable() {
            @Override
            public void run() {
                mPostedCallback = false;
                show();
            }
        };

        mProgressDrawable = new MaterialProgressDrawable(getContext(), this);

        mProgressDrawable.setBackgroundColor(getColorAsInt(R.color.ntp_bg));
        mProgressDrawable.setAlpha(255);
        mProgressDrawable.setColorSchemeColors(getColorAsInt(R.color.light_active_color));
        mProgressDrawable.updateSizes(MaterialProgressDrawable.LARGE);
        setImageDrawable(mProgressDrawable);

        hide();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // When the view is detached, the progress drawable would have been invalidated and stopped
        // animating. We restart the animation here in that case.
        if (getVisibility() == VISIBLE) show();
    }

    public void show() {
        if (mPostedCallback) return;

        // Stop first to reset the internal state of the drawable.
        mProgressDrawable.stop();

        setVisibility(View.VISIBLE);
        mProgressDrawable.start();
    }

    public void hide() {
        mProgressDrawable.stop();
        removeCallbacks(mShowSpinnerRunnable);
        mPostedCallback = false;
        setVisibility(View.GONE);
    }

    public void showDelayed() {
        // The indicator is already visible, just let it as it is to avoid jumps in the animation.
        if (getVisibility() == View.VISIBLE) return;

        mPostedCallback = true;
        // We don't want to show the spinner every time we load content if it loads quickly; instead
        // only start showing the spinner if loading the content has taken longer than 500ms
        postDelayed(mShowSpinnerRunnable, SHOW_DELAY_MS);
    }

    private int getColorAsInt(@ColorRes int colorId) {
        return ApiCompatibilityUtils.getColor(getResources(), colorId);
    }
}
