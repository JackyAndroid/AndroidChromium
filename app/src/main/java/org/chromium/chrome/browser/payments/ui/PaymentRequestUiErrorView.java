// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.BoundedLinearLayout;

/**
 * Displays the status of a payment request to the user.
 */
public class PaymentRequestUiErrorView extends BoundedLinearLayout {

    public PaymentRequestUiErrorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Initializes the view with the correct strings.
     *
     * @param title   Title of the webpage.
     * @param origin  Origin of the webpage.
     */
    public void initialize(String title, String origin) {
        ((TextView) findViewById(R.id.page_title)).setText(title);
        ((TextView) findViewById(R.id.hostname)).setText(origin);

        // Remove the close button, then expand the page information to take up the space formerly
        // occupied by the X.
        View toRemove = findViewById(R.id.close_button);
        ((ViewGroup) toRemove.getParent()).removeView(toRemove);

        int titleEndMargin = getContext().getResources().getDimensionPixelSize(
                R.dimen.payments_section_large_spacing);
        View pageInfoGroup = findViewById(R.id.page_info);
        ApiCompatibilityUtils.setMarginEnd(
                (MarginLayoutParams) pageInfoGroup.getLayoutParams(), titleEndMargin);
    }

    /**
     * Shows the dialog by attaching it to the given parent.
     *
     * @param parent   Parent to attach to.
     * @param callback Callback to run upon hitting the OK button.
     */
    public void show(ViewGroup parent, final Runnable callback) {
        int floatingDialogWidth = PaymentRequestUiErrorView.computeMaxWidth(parent.getContext(),
                parent.getMeasuredWidth(), parent.getMeasuredHeight());
        FrameLayout.LayoutParams overlayParams =
                new FrameLayout.LayoutParams(floatingDialogWidth, LayoutParams.WRAP_CONTENT);
        overlayParams.gravity = Gravity.CENTER;
        parent.addView(this, overlayParams);

        // Make the user explicitly click on the OK button to dismiss the dialog.
        View confirmButton = findViewById(R.id.ok_button);
        confirmButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.run();
            }
        });
    }

    /**
     * Sets what icon is displayed in the header.
     *
     * @param bitmap Icon to display.
     */
    public void setBitmap(Bitmap bitmap) {
        ((ImageView) findViewById(R.id.icon_view)).setImageBitmap(bitmap);
    }

    /**
     * Computes the maximum possible width for a dialog box.
     *
     * Follows https://www.google.com/design/spec/components/dialogs.html#dialogs-simple-dialogs
     *
     * @param context         Context to pull resources from.
     * @param availableWidth  Available width for the dialog.
     * @param availableHeight Available height for the dialog.
     * @return Maximum possible width for the dialog box.
     *
     * TODO(dfalcantara): Revisit this function when the new assets come in.
     * TODO(dfalcantara): The dialog should listen for configuration changes and resize accordingly.
     */
    public static int computeMaxWidth(Context context, int availableWidth, int availableHeight) {
        int baseUnit = context.getResources().getDimensionPixelSize(R.dimen.dialog_width_unit);
        int maxSize = Math.min(availableWidth, availableHeight);
        int multiplier = maxSize / baseUnit;
        int floatingDialogWidth = multiplier * baseUnit;
        return floatingDialogWidth;
    }
}
