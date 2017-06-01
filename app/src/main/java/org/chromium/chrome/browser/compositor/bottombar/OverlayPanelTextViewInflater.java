// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.LayoutDirection;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

/**
 * Provides an {@link OverlayPanelInflater} that adjusts a text view to override the RTL/LTR
 * ordering when the initial text fragment is short.
 * Details in this issue: crbug.com/651389.
 */
public abstract class OverlayPanelTextViewInflater
        extends OverlayPanelInflater implements OnLayoutChangeListener {
    private static final float SHORTNESS_FACTOR = 0.5f;

    private boolean mDidAdjustViewDirection;

    /**
     * Constructs an instance similar to an {@link OverlayPanelInflater} that can adjust the RTL/LTR
     * ordering of text fragments whose initial values are considered short relative to the width
     * of the view.
     */
    public OverlayPanelTextViewInflater(OverlayPanel panel, int layoutId, int viewId,
            Context context, ViewGroup container, DynamicResourceLoader resourceLoader) {
        super(panel, layoutId, viewId, context, container, resourceLoader);
    }

    /**
     * Subclasses must override to return the {@link TextView} once it's inflated.
     * @return The {@link TextView} or {@code null} if not yet inflated.
     */
    protected abstract TextView getTextView();

    //========================================================================================
    // OverlayPanelInflater overrides
    //========================================================================================

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View view = getView();
        view.addOnLayoutChangeListener(this);
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        TextView textView = getTextView();
        if (!mDidAdjustViewDirection && textView != null) {
            // We only adjust the view once, based on the initial value set at layout time.
            mDidAdjustViewDirection = true;
            adjustViewDirection(textView);
        }
    }

    //========================================================================================
    // Private methods
    //========================================================================================

    /**
     * Adjusts the given {@code TextView} to have a layout direction that matches the UI direction
     * when the contents of the view is considered short (based on SHORTNESS_FACTOR).
     * @param textView The text view to adjust.
     */
    @SuppressLint("RtlHardcoded")
    private void adjustViewDirection(TextView textView) {
        float textWidth = textView.getPaint().measureText(textView.getText().toString());
        if (textWidth < SHORTNESS_FACTOR * textView.getWidth()) {
            int layoutDirection =
                    LocalizationUtils.isLayoutRtl() ? LayoutDirection.RTL : LayoutDirection.LTR;
            if (layoutDirection == LayoutDirection.LTR) textView.setGravity(Gravity.LEFT);
            if (layoutDirection == LayoutDirection.RTL) textView.setGravity(Gravity.RIGHT);
        }
    }
}
