// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * Helper class for creating a horizontal list of icons with a title.
 */
class EditorIconsField {
    private final View mLayout;

    /**
     * Builds a horizontal list of icons.
     *
     * @param context    The application context to use when creating widgets.
     * @param root       The object that provides a set of LayoutParams values for the view.
     * @param fieldModel The data model of the icon list.
     */
    public EditorIconsField(Context context, ViewGroup root, EditorFieldModel fieldModel) {
        assert fieldModel.getInputTypeHint() == EditorFieldModel.INPUT_TYPE_HINT_ICONS;

        mLayout = LayoutInflater.from(context).inflate(
                R.layout.payment_request_editor_icons, root, false);

        ((TextView) mLayout.findViewById(R.id.label)).setText(fieldModel.getLabel());

        LinearLayout container = (LinearLayout) mLayout.findViewById(R.id.icons_container);
        int size = context.getResources().getDimensionPixelSize(
                R.dimen.payments_editor_icon_list_size);
        int margin = context.getResources().getDimensionPixelSize(
                R.dimen.payments_section_small_spacing);
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        ApiCompatibilityUtils.setMarginEnd(layoutParams, margin);
        for (int i = 0; i < fieldModel.getIconResourceIds().size(); i++) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(fieldModel.getIconResourceIds().get(i));
            icon.setBackgroundResource(R.drawable.payments_ui_logo_bg);
            icon.setContentDescription(context.getString(
                    fieldModel.getIconDescriptionsForAccessibility().get(i)));
            icon.setAdjustViewBounds(true);
            icon.setMaxWidth(size);
            icon.setMaxHeight(size);
            container.addView(icon, layoutParams);
        }
    }

    /** @return The View containing everything. */
    public View getLayout() {
        return mLayout;
    }
}
