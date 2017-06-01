// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.os.Build;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * Contains the basic functionality that should be shared by all ListPreference in Chrome.
 */
public class ChromeBaseListPreference extends ListPreference {

    private ManagedPreferenceDelegate mManagedPrefDelegate;

    /**
     * Constructor for inflating from XML.
     */
    public ChromeBaseListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Sets the ManagedPreferenceDelegate which will determine whether this preference is managed.
     */
    public void setManagedPreferenceDelegate(ManagedPreferenceDelegate delegate) {
        mManagedPrefDelegate = delegate;
        if (mManagedPrefDelegate != null) mManagedPrefDelegate.initPreference(this);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        ((TextView) view.findViewById(android.R.id.title)).setSingleLine(false);
        if (mManagedPrefDelegate != null) mManagedPrefDelegate.onBindViewToPreference(this, view);
    }

    @Override
    protected void onClick() {
        if (mManagedPrefDelegate != null && mManagedPrefDelegate.onClickPreference(this)) return;
        super.onClick();
    }

    @Override
    public void setValue(String value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            super.setValue(value);
            return;
        }

        // Work around an Android bug where notifyChanged() wasn't called on pre-KitKat devices,
        // causing the summary text not to be updated. http://crbug.com/446137
        String original = getValue();
        super.setValue(value);
        if (!TextUtils.equals(original, value)) {
            notifyChanged();
        }
    }
}
