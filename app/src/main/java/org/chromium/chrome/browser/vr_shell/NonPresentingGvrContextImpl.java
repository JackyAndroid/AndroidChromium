// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.os.StrictMode;

import com.google.vr.ndk.base.GvrLayout;

import org.chromium.base.Log;
import org.chromium.base.annotations.UsedByReflection;

/**
 * Creates an active GvrContext from a detached GvrLayout. This is used by magic window mode.
 */
@UsedByReflection("VrShellDelegate.java")
public class NonPresentingGvrContextImpl implements NonPresentingGvrContext {
    private static final String TAG = "NPGvrContextImpl";
    private GvrLayout mGvrLayout;

    @UsedByReflection("VrShellDelegate.java")
    public NonPresentingGvrContextImpl(Activity activity) {
        mGvrLayout = new GvrLayout(activity);
    }

    @Override
    public long getNativeGvrContext() {
        long nativeGvrContext = 0;
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            nativeGvrContext = mGvrLayout.getGvrApi().getNativeGvrContext();
        } catch (Exception ex) {
            Log.e(TAG, "Unable to instantiate GvrApi", ex);
            return 0;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
        return nativeGvrContext;
    }

    @Override
    public void resume() {
        mGvrLayout.getGvrApi().resumeTracking();
    }

    @Override
    public void pause() {
        // We can't pause/resume the GvrLayout, because doing so will force us to enter VR. However,
        // we should be safe not pausing it as we never add it to the view hierarchy, or give it a
        // presentation view, so there's nothing to pause but the tracking.
        mGvrLayout.getGvrApi().pauseTracking();
    }

    @Override
    public void shutdown() {
        mGvrLayout.shutdown();
    }
}
