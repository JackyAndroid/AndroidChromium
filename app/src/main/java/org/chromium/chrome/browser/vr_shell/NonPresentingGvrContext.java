// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

/**
 * Abstracts away the NonPresentingGvrContext class, which may or may not be present at runtime
 * depending on compile flags.
 */
public interface NonPresentingGvrContext {
    /**
     * Returns the native gvr context.
     */
    long getNativeGvrContext();

    /**
     * Must be called when activity resumes.
     */
    void resume();

    /**
     * Must be called when activity pauses.
     */
    void pause();

    /**
     * Shutdown the native gvr context.
     */
    void shutdown();
}
