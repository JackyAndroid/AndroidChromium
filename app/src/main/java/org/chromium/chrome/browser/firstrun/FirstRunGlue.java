// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Fragment;
import android.content.Context;

/**
 * Interface to Glue the FirstRunActivity with the actual preferences and other details.
 */
public interface FirstRunGlue {
    /**
     * @return Whether the user has accepted Chrome Terms of Service.
     * @param appContext An application context.
     */
    boolean didAcceptTermsOfService(Context appContext);

    /**
     * @return Whether the "upload crash dump" setting is set to "NEVER".
     * @param appContext An application context.
     */
    boolean isNeverUploadCrashDump(Context appContext);

    /**
     * Sets the EULA/Terms of Services state as "ACCEPTED".
     * @param appContext An application context.
     * @param allowCrashUpload True if the user allows to upload crash dumps and collect stats.
     */
    void acceptTermsOfService(Context appContext, boolean allowCrashUpload);

    /**
     * @return Whether a given account name is the default (first) Android account name.
     * @param appContext An application context.
     * @param accountName An account name.
     */
    boolean isDefaultAccountName(Context appContext, String accountName);

    /**
     * @return Number of available accounts on the device.
     * @param appContext An application context.
     */
    int numberOfAccounts(Context appContext);

    /**
     * Opens the Android account adder UI.
     * @param fragment A fragment that requested the service.
     */
    void openAccountAdder(Fragment fragment);
}