// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import org.chromium.chrome.R;

/**
 * A {@link Preference} that provides button functionality.
 *
 * Preference.getOnPreferenceClickListener().onPreferenceClick() is called when the button is
 * clicked.
 */
public class ButtonPreference extends Preference {

    /**
     * Constructor for inflating from XML
     */
    public ButtonPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_button);
        setSelectable(false);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        Button button = (Button) view.findViewById(R.id.button_preference);
        button.setText(this.getTitle());
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getOnPreferenceClickListener() != null) {
                    getOnPreferenceClickListener().onPreferenceClick(ButtonPreference.this);
                }
            }
        });
    }
}
