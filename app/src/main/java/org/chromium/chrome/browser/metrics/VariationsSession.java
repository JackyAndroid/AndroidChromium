// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.content.Context;

import org.chromium.base.Callback;

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
            getRestrictMode(context, new Callback<String>() {
                @Override
                public void onResult(String restrictMode) {
                    assert restrictMode != null;
                    mRestrictMode = restrictMode;
                    nativeStartVariationsSession(mRestrictMode);
                }
            });
        // If |mRestrictMode| is null, async initialization is in progress and
        // nativeStartVariationsSession will be called when it completes.
        } else if (mRestrictMode != null) {
            nativeStartVariationsSession(mRestrictMode);
        }
    }

    /**
     * Asynchronously returns the value of the "restrict" URL param that the variations service
     * should use for variation seed requests.
     */
    protected void getRestrictMode(Context context, Callback<String> callback) {
        callback.onResult("");
    }

    private native void nativeStartVariationsSession(String restrictMode);
}
