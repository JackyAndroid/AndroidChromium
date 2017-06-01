// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * The purpose of this view is to store the system window insets (OSK, status bar) for
 * later use.
 */
public class InsetObserverView extends View {

    protected final Rect mWindowInsets;

    /**
     * Constructs a new {@link InsetObserverView} for the appropriate Android version.
     * @param context The Context the view is running in, through which it can access the current
     *            theme, resources, etc.
     * @return an instance of a InsetObserverView.
     */
    public static InsetObserverView create(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return new InsetObserverView(context);
        }
        return new InsetObserverViewApi21(context);
    }

    /**
     * Creates an instance of {@link InsetObserverView}.
     * @param context The Context to create this {@link InsetObserverView} in.
     */
    public InsetObserverView(Context context) {
        super(context);
        setVisibility(INVISIBLE);
        mWindowInsets = new Rect();
    }

    /**
     * Returns the left {@link WindowInsets} in pixels.
     */
    public int getSystemWindowInsetsLeft() {
        return mWindowInsets.left;
    }

    /**
     * Returns the top {@link WindowInsets} in pixels.
     */
    public int getSystemWindowInsetsTop() {
        return mWindowInsets.top;
    }

    /**
     * Returns the right {@link WindowInsets} in pixels.
     */
    public int getSystemWindowInsetsRight() {
        return mWindowInsets.right;
    }

    /**
     * Returns the bottom {@link WindowInsets} in pixels.
     */
    public int getSystemWindowInsetsBottom() {
        return mWindowInsets.bottom;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected boolean fitSystemWindows(Rect insets) {
        // For Lollipop and above, onApplyWindowInsets will set the insets.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mWindowInsets.set(insets.left, insets.top, insets.right, insets.bottom);
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static class InsetObserverViewApi21 extends InsetObserverView {
        /**
         * Creates an instance of {@link InsetObserverView} for Android versions L and above.
         * @param context The Context to create this {@link InsetObserverView} in.
         */
        InsetObserverViewApi21(Context context) {
            super(context);
        }

        @Override
        public WindowInsets onApplyWindowInsets(WindowInsets insets) {
            mWindowInsets.set(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        }
    }
}
