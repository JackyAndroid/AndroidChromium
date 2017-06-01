// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;

import org.chromium.chrome.R;

/**
 * An EditText that shows an alert message when the content is empty.
 */
public class EmptyAlertEditText extends AppCompatEditText {

    private String mAlertMessage;

    /**
     * Constructor for inflating from XML.
     */
    public EmptyAlertEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EmptyAlertEditText);
        int resId = a.getResourceId(R.styleable.EmptyAlertEditText_alertMessage, 0);
        if (resId != 0) mAlertMessage = context.getResources().getString(resId);
        a.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validate();
            }
        });
    }

    /**
     * @return Trimmed text for validation.
     */
    public String getTrimmedText() {
        return getText().toString().trim();
    }

    /**
     * Sets the alert message to be shown when the text content is empty.
     */
    public void setAlertMessage(String message) {
        mAlertMessage = message;
    }

    /**
     * @return Whether the content is empty.
     */
    public boolean isEmpty() {
        return TextUtils.isEmpty(getTrimmedText());
    }

    /**
     * Check the text and show or hide error message if needed.
     */
    public void validate() {
        if (isEmpty()) {
            setError(mAlertMessage);
        } else {
            if (getError() != null) setError(null);
        }
    }
}
