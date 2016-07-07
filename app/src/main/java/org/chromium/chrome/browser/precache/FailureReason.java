// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.precache;

import java.util.EnumSet;

/** A reason why prefetching failed to start. */
enum FailureReason {
    /** PrecacheLauncher.updatePrecachingEnabled() has not yet been called. */
    UPDATE_PRECACHING_ENABLED_NEVER_CALLED(0),

    /** The sync backend is not yet initialized. */
    SYNC_NOT_INITIALIZED(1),

    /** PrivacyPreferencesManager#shouldPrerender() returns false. */
    PRERENDER_PRIVACY_PREFERENCE_NOT_ENABLED(2),

    /** PrecacheLauncher#nativeShouldRun() returns false. */
    NATIVE_SHOULD_RUN_IS_FALSE(3),

    /** DeviceState#isPowerConnected() returns false. */
    NO_POWER(4),

    /** DeviceState#isWifiAvailable() returns false. */
    NO_WIFI(5),

    // Deprecated: SCREEN_ON(6).

    /** PrecacheServiceLauncher#timeSinceLastPrecacheMs() is too recent. */
    NOT_ENOUGH_TIME_SINCE_LAST_PRECACHE(7),

    /** PrecacheService#isPrecaching() returns true. */
    CURRENTLY_PRECACHING(8);

    /** Returns the set of reasons as a bit vector. */
    static int bitValue(EnumSet<FailureReason> reasons) {
        int value = 0;
        for (FailureReason reason : reasons) value |= 1 << reason.mPosition;
        return value;
    }

    FailureReason(int position) {
        this.mPosition = position;
    }

    /** The bit position, to be set when computing the bit vector. */
    private final int mPosition;
}
