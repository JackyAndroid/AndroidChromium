// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import android.content.Context;

import org.chromium.chrome.browser.identity.UniqueIdentificationGenerator;
import org.chromium.chrome.browser.identity.UuidBasedUniqueIdentificationGenerator;
import org.chromium.sync.notifier.InvalidationClientNameGenerator;
import org.chromium.sync.notifier.InvalidationClientNameProvider;

/**
 * An InvalidationClientNameGenerator that wraps an UniqueIdentificationGenerator.
 *
 * If the right kind of UniqueIdentificationGenerator is provided, then this will produce IDs that
 * are unique and consistent across restarts.
 */
public class UniqueIdInvalidationClientNameGenerator implements InvalidationClientNameGenerator {
    // Pref key to use for UUID-based generator.
    private static final String INVALIDATIONS_UUID_PREF_KEY = "chromium.invalidations.uuid";

    /**
     * Called during early init to make this InvalidationClientNameGenerator the default.
     *
     * This should be called very early during initialization to setup the invalidaiton client name.
     */
    public static void doInitializeAndInstallGenerator(Context context) {
        UniqueIdentificationGenerator idGenerator =
                new UuidBasedUniqueIdentificationGenerator(context, INVALIDATIONS_UUID_PREF_KEY);
        InvalidationClientNameGenerator clientNameGenerator =
                new UniqueIdInvalidationClientNameGenerator(idGenerator);
        InvalidationClientNameProvider.get().setPreferredClientNameGenerator(clientNameGenerator);
    }

    private final UniqueIdentificationGenerator mGenerator;

    UniqueIdInvalidationClientNameGenerator(UniqueIdentificationGenerator generator) {
        mGenerator = generator;
    }

    @Override
    public byte[] generateInvalidatorClientName() {
        return mGenerator.getUniqueId(null).getBytes();
    }
}
