// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.chromium.chrome.R;

/**
 * A custom preference for drawing Site Settings entries.
 */
public class SiteSettingsPreference extends Preference {
    /**
     * Constructor for inflating from XML.
     */
    public SiteSettingsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        int padding = getContext().getResources().getDimensionPixelSize(
                R.dimen.site_setttings_icon_padding);
        ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
        icon.setPadding(padding, icon.getPaddingTop(), 0, icon.getPaddingBottom());
    }
}
