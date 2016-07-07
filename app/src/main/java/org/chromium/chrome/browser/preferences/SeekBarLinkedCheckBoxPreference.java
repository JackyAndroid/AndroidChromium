// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

/**
 * A CheckBoxPreference which can be checked via a SeekBarPreference. A normal CheckBoxPreference
 * cannot be used in this way as calling setChecked(...) calls notifyChanged() which causes the
 * drag on the seek bar to be cancelled.
 */
public class SeekBarLinkedCheckBoxPreference extends CheckBoxPreference {
    private Checkable mCheckable;
    private SeekBarPreference mLinkedSeekBarPreference;

    public SeekBarLinkedCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mCheckable = (Checkable) view.findViewById(android.R.id.checkbox);
        mCheckable.setChecked(isChecked());
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        if (mCheckable != null) mCheckable.setChecked(checked);
    }

    public void setLinkedSeekBarPreference(SeekBarPreference linkedSeekBarPreference) {
        mLinkedSeekBarPreference = linkedSeekBarPreference;
    }

    @Override
    protected void notifyChanged() {
        if (mLinkedSeekBarPreference == null || !mLinkedSeekBarPreference.isTrackingTouch()) {
            super.notifyChanged();
        }
    }
}
