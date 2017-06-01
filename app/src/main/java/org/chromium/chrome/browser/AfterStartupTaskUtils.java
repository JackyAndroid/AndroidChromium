// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

/**
 * JNI call glue for AfterStartupTaskUtils in C++.
 */
public final class AfterStartupTaskUtils {
    private AfterStartupTaskUtils() {}

    /**
     * Informs the C++ side that startup is complete. Tasks that
     * have been deferred until after startup will be scheduled
     * to run and newly posted tasks will no longer be deferred.
     */
    public static void setStartupComplete() {
        nativeSetStartupComplete();
    }

    private static native void nativeSetStartupComplete();
}
