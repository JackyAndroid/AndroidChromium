// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.chrome.R;

/**
 * Used to group {@link Preference} objects and provide a disabled
 * title above the group with an icon inline with the title. Clicking on the icon starts
 * the PreferenceFragment associated with the Preference.
 */
public class PreferenceCategoryWithButton extends PreferenceGroup implements OnClickListener {

    private String mContentDescription;

    public PreferenceCategoryWithButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_category);
        setSelectable(false);

        TypedArray a = context.obtainStyledAttributes(attrs,
                new int[] {android.R.attr.contentDescription});
        mContentDescription = a.getString(0);
        a.recycle();
    }

    @Override
    protected void onBindView(final View view) {
        super.onBindView(view);
        // On pre-L devices, PreferenceCategoryWithButtonStyle is reused for PreferenceCategory,
        // which needs a top padding of 16dp; we don't want this top padding for
        // PreferenceCategoryWithButton views.
        view.setPadding(view.getPaddingLeft(), 0, view.getPaddingRight(), view.getPaddingBottom());
        View button = view.findViewById(android.R.id.icon);
        button.setOnClickListener(this);

        if (!TextUtils.isEmpty(mContentDescription)) {
            button.setContentDescription(mContentDescription);
        }
    }

    @Override
    public void onClick(View v) {
        ((Preferences) getContext()).startFragment(getFragment(), null);
    }

}
