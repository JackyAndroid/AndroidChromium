// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.content.Context;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;

import java.io.IOException;

/**
 * Returns a stable id that can be used to identify a Google Account.  This
 * id does not change if the email address associated to the account changes,
 * nor does it change depending on whether the email has dots or varying
 * capitalization.
 */
public class AccountIdProvider {
    private static AccountIdProvider sProvider;

    protected AccountIdProvider() {
        // should not be initialized outside getInstance().
    }

    /**
     * Returns a stable id for the account associated with the given email address.
     * If an account with the given email address is not installed on the device
     * then null is returned.
     *
     * This method will throw IllegalStateException if called on the main thread.
     *
     * @param accountName The email address of a Google account.
     */
    public String getAccountId(Context ctx, String accountName) {
        try {
            return GoogleAuthUtil.getAccountId(ctx, accountName);
        } catch (IOException | GoogleAuthException ex) {
            Log.e("cr.AccountIdProvider", "AccountIdProvider.getAccountId", ex);
            return null;
        }
    }

    /**
     * Returns whether the AccountIdProvider can be used.
     * Since the AccountIdProvider queries Google Play services, this basically checks whether
     * Google Play services is available.
     */
    public boolean canBeUsed(Context ctx) {
        return ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                ctx, new UserRecoverableErrorHandler.Silent());
    }

    /**
     * Gets the global account Id provider.
     */
    public static AccountIdProvider getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sProvider == null) sProvider = new AccountIdProvider();
        return sProvider;
    }

    /**
     * For testing purposes only, allows to set the provider even if it has already been
     * initialized.
     */
    @VisibleForTesting
    public static void setInstanceForTest(AccountIdProvider provider) {
        ThreadUtils.assertOnUiThread();
        sProvider = provider;
    }
}

