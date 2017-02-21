// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.DisplayStyleObserver;
import org.chromium.chrome.browser.ntp.UiConfig;

/**
 * Adds lateral margins to the view when the display style is {@link UiConfig#DISPLAY_STYLE_WIDE}.
 */
public class MarginResizer implements DisplayStyleObserver {
    private int mDefaultMarginSizePixels;
    private final int mWideMarginSizePixels;
    private final View mView;

    @UiConfig.DisplayStyle
    private int mCurrentDisplayStyle;

    /**
     * Factory method that creates a {@link MarginResizer} and wraps it in a
     * {@link DisplayStyleObserverAdapter} that will take care of invoking it when appropriate.
     * @param view the view that will have its margins resized
     * @param config the UiConfig object to subscribe to
     * @return the newly created {@link MarginResizer}
     */
    public static MarginResizer createWithViewAdapter(View view, UiConfig config) {
        MarginResizer marginResizer = new MarginResizer(view);
        new DisplayStyleObserverAdapter(view, config, marginResizer);
        return marginResizer;
    }

    public MarginResizer(View view) {
        mView = view;
        mWideMarginSizePixels =
                view.getResources().getDimensionPixelSize(R.dimen.ntp_wide_card_lateral_margins);
    }

    @Override
    public void onDisplayStyleChanged(@UiConfig.DisplayStyle int newDisplayStyle) {
        mCurrentDisplayStyle = newDisplayStyle;
        updateMargins();
    }

    /**
     * Sets the lateral margins on the associated view, using the appropriate value depending on
     * the current display style.
     * @param marginPixels margin size to use in {@link UiConfig#DISPLAY_STYLE_REGULAR}. This value
     *                     will be used as new default value for the lateral margins.
     */
    public void setMargins(int marginPixels) {
        this.mDefaultMarginSizePixels = marginPixels;
        updateMargins();
    }

    private void updateMargins() {
        MarginLayoutParams layoutParams = (MarginLayoutParams) mView.getLayoutParams();
        if (mCurrentDisplayStyle == UiConfig.DISPLAY_STYLE_WIDE) {
            layoutParams.setMargins(mWideMarginSizePixels, layoutParams.topMargin,
                    mWideMarginSizePixels, layoutParams.bottomMargin);
        } else {
            layoutParams.setMargins(mDefaultMarginSizePixels, layoutParams.topMargin,
                    mDefaultMarginSizePixels, layoutParams.bottomMargin);
        }
    }
}
