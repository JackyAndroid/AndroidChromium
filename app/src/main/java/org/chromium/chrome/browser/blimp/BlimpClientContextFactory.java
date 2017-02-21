// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.blimp;

import org.chromium.blimp_public.BlimpClientContext;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * This factory creates BlimpClientContexts for the given {@link Profile}.
 */
public final class BlimpClientContextFactory {
    // Don't instantiate me.
    private BlimpClientContextFactory() {}

    /**
     * A factory method to build a {@link BlimpClientContext} object. Each Profile only ever has
     * a single {@link BlimpClientContext}, so the first this method is called (or from native),
     * the {@link BlimpClientContext} will be created, and later calls will return the already
     * created instance.
     * @return The {@link BlimpClientContext} for the given profile object.
     */
    public static BlimpClientContext getBlimpClientContextForProfile(Profile profile) {
        return nativeGetBlimpClientContextForProfile(profile);
    }

    private static native BlimpClientContext nativeGetBlimpClientContextForProfile(Profile profile);
}
