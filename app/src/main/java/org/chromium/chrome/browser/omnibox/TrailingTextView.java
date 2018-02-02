// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * A specialized TextView to be used for showing the trailing text in the URL container.
 */
public class TrailingTextView extends AppCompatTextView {

    public TrailingTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setLongClickable(false);
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally empty so this view is ignored by accessibility.
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally empty so this view is ignored by accessibility.
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        // Intentionally empty so this view is ignored by accessibility.
    }
}
