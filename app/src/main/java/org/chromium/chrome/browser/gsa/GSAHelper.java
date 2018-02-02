// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.gsa;

import android.content.Context;
import android.os.Bundle;

import org.chromium.chrome.browser.ChromeActivity;

/**
 * Helper class that triggers integration methods with GSA.
 */
public class GSAHelper {

    /**
     * Returns A {@link ContextReporter} instance that handles reporting context to GSA. Might
     * return null.
     */
    @SuppressWarnings("unused")
    public ContextReporter getContextReporter(ChromeActivity activity) {
        return null;
    }

    /**
     * Starts syncing with local indexing service.
     */
    public void startSync() {}

    /**
     * Get a bundle that contains the required context information to register as a client to
     * GSA service.
     * @param context The context to use while constructing the bundle.
     * @return The bundle to use for registering a {@link GSAServiceClient}.
     */
    public Bundle getBundleForRegisteringGSAClient(Context context) {
        return null;
    }

    /**
     * Extracts the logged in account from a given gsaState.
     * @param gsaState The GSA state byte array that contains the account information.
     * @return The account that GSA is currently logged in to. Can be null.
     */
    public String getGSAAccountFromState(byte[] gsaState) {
        return null;
    }
}
