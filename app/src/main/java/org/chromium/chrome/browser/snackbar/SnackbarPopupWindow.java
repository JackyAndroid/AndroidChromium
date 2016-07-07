// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * Visual representation of a snackbar. On phone it fills the width of the activity; on tablet it
 * has a fixed width and is anchored at the start-bottom corner of the current window.
 */
class SnackbarPopupWindow extends PopupWindow {
    private final TemplatePreservingTextView mMessageView;
    private final TextView mActionButtonView;
    private final int mAnimationDuration;

    /**
     * Creates an instance of the {@link SnackbarPopupWindow}.
     * @param parent Parent View the popup window anchors to
     * @param listener An {@link OnClickListener} that will be called when the action button is
     *                 clicked.
     * @param snackbar The snackbar to be displayed.
     */
    SnackbarPopupWindow(View parent, OnClickListener listener, Snackbar snackbar) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.snackbar, null);
        setContentView(view);
        mMessageView = (TemplatePreservingTextView) view.findViewById(R.id.snackbar_message);
        mActionButtonView = (TextView) view.findViewById(R.id.snackbar_button);
        mAnimationDuration = view.getResources().getInteger(
                android.R.integer.config_mediumAnimTime);
        mActionButtonView.setOnClickListener(listener);

        // Set width and height of popup window
        boolean isTablet = DeviceFormFactor.isTablet(parent.getContext());
        setWidth(isTablet
                ? parent.getResources().getDimensionPixelSize(R.dimen.snackbar_tablet_width)
                : parent.getWidth());

        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        update(snackbar, false);
    }

    @Override
    public void dismiss() {
        // Disable action button during animation.
        mActionButtonView.setEnabled(false);
        super.dismiss();
    }

    /**
     * Updates the view to display data from the given snackbar.
     *
     * @param snackbar The snackbar to display
     * @param animate Whether or not to animate the text in or set it immediately
     */
    void update(Snackbar snackbar, boolean animate) {
        mMessageView.setMaxLines(snackbar.getSingleLine() ? 1 : Integer.MAX_VALUE);
        mMessageView.setTemplate(snackbar.getTemplateText());
        setViewText(mMessageView, snackbar.getText(), animate);
        String actionText = snackbar.getActionText();

        View view = getContentView();
        int backgroundColor = snackbar.getBackgroundColor();
        if (backgroundColor == 0) {
            backgroundColor = ApiCompatibilityUtils.getColor(view.getResources(),
                    R.color.snackbar_background_color);
        }

        if (DeviceFormFactor.isTablet(view.getContext())) {
            // On tablet, snackbar popups have rounded corners.
            view.setBackgroundResource(R.drawable.snackbar_background);
            ((GradientDrawable) view.getBackground()).setColor(backgroundColor);
        } else {
            view.setBackgroundColor(backgroundColor);
        }

        if (snackbar.getBackgroundColor() != 0) {
            view.setBackgroundColor(snackbar.getBackgroundColor());
        }
        if (actionText != null) {
            mActionButtonView.setVisibility(View.VISIBLE);
            setViewText(mActionButtonView, snackbar.getActionText(), animate);
        } else {
            mActionButtonView.setVisibility(View.GONE);
        }
    }

    private void setViewText(TextView view, CharSequence text, boolean animate) {
        if (view.getText().toString().equals(text)) return;
        view.animate().cancel();
        if (animate) {
            view.setAlpha(0.0f);
            view.setText(text);
            view.animate().alpha(1.f).setDuration(mAnimationDuration).setListener(null);
        } else {
            view.setText(text);
        }
    }

    /**
     * Sends an accessibility event to mMessageView announcing that this window was added so that
     * the mMessageView content description is read aloud if accessibility is enabled.
     */
    void announceforAccessibility() {
        mMessageView.announceForAccessibility(mMessageView.getContentDescription());
    }
}
