// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.os.Build;
import android.support.design.widget.TextInputLayout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import org.chromium.base.ApiCompatibilityUtils;

import javax.annotation.Nullable;

/**
 * Handles bugs with the Android Support library's {@link TextInputLayout} until Chrome can upgrade
 * to a newer version.
 *
 * TODO(dfalcantara): Remove this super gross dirty hack once Chrome can roll version 24:
 *                    https://crbug.com/603635
 */
public class CompatibilityTextInputLayout extends TextInputLayout {

    /** Whether or not the background has been mutated to work around the red line bug. */
    private boolean mIsBackgroundMutated;

    public CompatibilityTextInputLayout(Context context) {
        super(context);
    }

    public CompatibilityTextInputLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Super gross, dirty, awful hack for dealing with bugs in version 23 of the support library.
     *
     * Gleaned using dirty things from comments on the Android bug and support library source:
     * https://code.google.com/p/android/issues/detail?id=190829
     */
    @Override
    public void setError(@Nullable CharSequence error) {
        if (!mIsBackgroundMutated && getEditText() != null && getEditText().getBackground() != null
                && ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP))) {
            getEditText().setBackground(
                    getEditText().getBackground().getConstantState().newDrawable());
            getEditText().getBackground().mutate();
            mIsBackgroundMutated = true;
        }

        super.setError(error);
        if (TextUtils.isEmpty(error)) setErrorEnabled(false);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        if (getChildCount() == 1) {
            // If there is a child to this TextInputLayout, automatically set the hint.
            View child = getChildAt(0);
            if (child instanceof EditText && child.getId() > NO_ID) {
                ApiCompatibilityUtils.setLabelFor(this, child.getId());
            }
        }
    }
}
