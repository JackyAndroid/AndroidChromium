// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync;

import org.chromium.base.VisibleForTesting;

/**
 * A listener that will share sync's custom passphrase encryption key with GmsCore.
 *
 * This is to prevent users from seeing the custom passphrase dialog pop up in GmsCore after they
 * have already entered it in Chrome.
 */
public abstract class GmsCoreSyncListener implements ProfileSyncService.SyncStateChangedListener {
    private boolean mGmsCoreInformed;

    /**
     * Inform GMSCore of a new custom passphrase encryption key.
     *
     * @param key The serialized NigoriKey proto.
     */
    public abstract void updateEncryptionKey(byte[] key);

    @Override
    @VisibleForTesting
    public void syncStateChanged() {
        ProfileSyncService syncService = ProfileSyncService.get();
        boolean passphraseSet = syncService.isBackendInitialized()
                && syncService.isUsingSecondaryPassphrase() && syncService.isCryptographerReady();
        if (passphraseSet && !mGmsCoreInformed) {
            byte[] key = syncService.getCustomPassphraseKey();
            if (key.length > 0) {
                updateEncryptionKey(key);
                mGmsCoreInformed = true;
            }
        } else if (!passphraseSet && mGmsCoreInformed) {
            // Prepare to inform GmsCore if a passphrase is set again.
            mGmsCoreInformed = false;
        }
    }
}
