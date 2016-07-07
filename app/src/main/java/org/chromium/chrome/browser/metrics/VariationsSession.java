// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.content.Context;

/**
 * Sets up communication with the VariationsService. This is primarily used for
 * triggering seed fetches on application startup.
 */
public class VariationsSession {
    private boolean mInitialized;
    private String mRestrictMode;

    /**
     * Triggers to the native VariationsService that the application has entered the foreground.
     */
    public void start(Context context) {
        if (!mInitialized) {
            mInitialized = true;
            // Check the restrict mode only once initially to avoid doing extra work each time the
            // app enters foreground.
            mRestrictMode = getRestrictMode(context);
        }
        nativeStartVariationsSession(mRestrictMode);
    }

    /**
     * Returns the value of the "restrict" URL param that the variations service should use for
     * variation seed requests.
     */
    protected String getRestrictMode(Context context) {
        return "";
    }

    private native void nativeStartVariationsSession(String restrictMode);
}
