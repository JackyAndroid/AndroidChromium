// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import android.content.Context;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.invalidation.InvalidationService;

import java.util.HashMap;
import java.util.Map;

/**
 * InvalidationServiceFactory maps Profiles to instances of
 * {@link InvalidationService} instances. Each {@link Profile} will at most
 * have one instance of this service. If the service does not already exist,
 * it will be created on the first access.
 */
@JNINamespace("invalidation")
public final class InvalidationServiceFactory {

    private static final Map<Profile, InvalidationService> sServiceMap =
            new HashMap<Profile, InvalidationService>();

    private InvalidationServiceFactory() {}

    /**
     * Returns Java InvalidationService for the given Profile.
     */
    public static InvalidationService getForProfile(Profile profile) {
        ThreadUtils.assertOnUiThread();
        InvalidationService service = sServiceMap.get(profile);
        if (service == null) {
            service = nativeGetForProfile(profile);
            sServiceMap.put(profile, service);
        }
        return service;
    }

    @VisibleForTesting
    public static InvalidationService getForTest(Context context) {
        return nativeGetForTest(context);
    }

    private static native InvalidationService nativeGetForProfile(Profile profile);
    private static native InvalidationService nativeGetForTest(Context context);
}
