// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;

import org.chromium.chrome.R;

/**
 * A {@link Preference} that provides label text plus button functionality.
 *
 * Preference.getOnPreferenceClickListener().onPreferenceClick() is called when the button is
 * clicked. The button is defined by the widgetLayout attribute.
 */
public class TextAndButtonPreference extends Preference {
    /**
     * Constructor for inflating from XML
     */
    public TextAndButtonPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSelectable(false);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        View button = view.findViewById(R.id.preference_click_target);
        button.setClickable(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getOnPreferenceClickListener() != null) {
                    getOnPreferenceClickListener().onPreferenceClick(TextAndButtonPreference.this);
                }
            }
        });
    }
}
