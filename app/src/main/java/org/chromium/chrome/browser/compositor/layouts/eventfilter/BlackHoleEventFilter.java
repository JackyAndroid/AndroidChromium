// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.eventfilter;

import android.content.Context;
import android.view.MotionEvent;

/**
 * A {@link BlackHoleEventFilter} eats all the events coming its way with no side effects.
 */
public class BlackHoleEventFilter extends EventFilter {
    /**
     * Creates a {@link BlackHoleEventFilter}.
     * @param context A {@link Context} instance.
     * @param host    A {@link EventFilterHost} instance.
     */
    public BlackHoleEventFilter(Context context, EventFilterHost host) {
        super(context, host);
    }

    @Override
    public boolean onInterceptTouchEventInternal(MotionEvent e, boolean isKeyboardShowing) {
        return true;
    }

    @Override
    public boolean onTouchEventInternal(MotionEvent e) {
        return true;
    }
}