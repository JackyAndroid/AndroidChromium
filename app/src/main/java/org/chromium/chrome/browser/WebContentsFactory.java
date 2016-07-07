// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.content_public.browser.WebContents;

/**
 * This factory creates WebContents objects and the associated native counterpart.
 * TODO(dtrainor): Move this to the content/ layer if BrowserContext is ever supported in Java.
 */
public abstract class WebContentsFactory {
    // Don't instantiate me.
    private WebContentsFactory() {
    }

    /**
     * A factory method to build a {@link WebContents} object.
     * @param incognito       Whether or not the {@link WebContents} should be built with an
     *                        off-the-record profile or not.
     * @param initiallyHidden Whether or not the {@link WebContents} should be initially hidden.
     * @return                A newly created {@link WebContents} object.
     */
    public static WebContents createWebContents(boolean incognito, boolean initiallyHidden) {
        return nativeCreateWebContents(incognito, initiallyHidden);
    }

    private static native WebContents nativeCreateWebContents(boolean incognito,
            boolean initiallyHidden);
}
