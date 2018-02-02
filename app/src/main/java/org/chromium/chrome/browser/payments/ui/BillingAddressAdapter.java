// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.TintedDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Subclass of ArrayAdapter used to display a dropdown hint that won't appear in the expanded
 * dropdown options but will be used when no element is selected. The last shown element will have a
 * "+" icon on its left and a blue tint to indicate the option to add an element.
 *
 * @param <T> The type of element to be inserted into the adapter.
 *
 *
 * collapsed view:       --------          Expanded view:   ------------
 * (no item selected)   | hint   |                         | option 1   |
 *                       --------                          |------------|
 *                                                         | option 2   |
 * collapsed view:       ----------                        |------------|
 * (with selected item) | option X |                       | + option N | -> stylized and "+" icon
 *                       ----------                        .------------.
 *                                                         . hint       . -> hidden
 *                                                         ..............
 */
public class BillingAddressAdapter<T> extends ArrayAdapter<T> {

    /**
     * Creates an array adapter for which the last element is a hint that is not shown in the
     * expanded view and where the last shown element has a "+" icon on its left and has a blue
     * tint.
     *
     * @param context  The current context.
     * @param resource The resource ID for a layout file containing a layout to use when
     *                 instantiating views.
     * @param objects  The objects to represent in the ListView, the last of which will have a "+"
     *                 icon on its left and will have a blue tint.
     * @param hint     The element to be used as a hint when no element is selected. It is not taken
     *                 into account in the count function and thus will not be displayed when in the
     *                 expanded dropdown view.
     */
    public BillingAddressAdapter(Context context, int resource, List<T> objects, T hint) {
        // Make a copy of objects so the hint is not added to the original list.
        super(context, resource, new ArrayList<T>(objects));
        // The hint is added as the last element. It will not be shown when the dropdown is
        // expanded and not be taken into account in the getCount function.
        add(hint);
    }

    @Override
    public int getCount() {
        // Don't display last item, it is used as hint.
        int count = super.getCount();
        return count > 0 ? count - 1 : count;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view;

        // Add a "+" icon and a blue tint to the last element.
        if (position == getCount() - 1) {
            view = super.getDropDownView(position, convertView, parent);
            TextView tv = (TextView) view;
            Resources resources = getContext().getResources();

            // Create the "+" icon, put it left of the text and add appropriate padding.
            tv.setCompoundDrawablesWithIntrinsicBounds(
                    TintedDrawable.constructTintedDrawable(
                        resources, R.drawable.plus, R.color.light_active_color),
                    null, null, null);
            tv.setCompoundDrawablePadding(
                    resources.getDimensionPixelSize(R.dimen.payments_section_large_spacing));

            // Set the correct appearance, face and style for the text.
            ApiCompatibilityUtils.setTextAppearance(tv, R.style.PaymentsUiSectionAddButtonLabel);
            tv.setTypeface(Typeface.create(
                    resources.getString(R.string.roboto_medium_typeface),
                    R.integer.roboto_medium_textstyle));
        } else {
            // Don't use the recycled convertView, as it may have the style of the last element.
            view = super.getDropDownView(position, null, parent);
        }

        return view;
    }
}