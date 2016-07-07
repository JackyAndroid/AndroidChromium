// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.accounts.Account;
import android.content.Context;

import org.chromium.chrome.browser.services.AndroidEduAndChildAccountHelper;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

/**
 * A helper to perform all necessary steps for forced sign in.
 * The helper performs:
 * - necessary Android EDU and child account checks;
 * - automatic non-interactive forced sign in for Android EDU and child accounts; and
 * The helper calls the observer's onSignInComplete() if
 * - nothing needs to be done, or when
 * - the sign in is complete.
 *
 * Usage:
 * ForcedSigninProcessor.start(appContext).
 */
public final class ForcedSigninProcessor {
    /*
     * Only for static usage.
     */
    private ForcedSigninProcessor() {}

    /**
     * Check whether a forced automatic signin is required and process it if it is.
     * This is triggered once per Chrome Application lifetime and everytime the Account state
     * changes with early exit if an account has already been signed in.
     */
    public static void start(final Context appContext) {
        if (ChromeSigninController.get(appContext).isSignedIn()) return;
        new AndroidEduAndChildAccountHelper() {
            @Override
            public void onParametersReady() {
                boolean isAndroidEduDevice = isAndroidEduDevice();
                boolean hasChildAccount = hasChildAccount();
                // If neither a child account or and EDU device, we return.
                if (!isAndroidEduDevice && !hasChildAccount) return;
                // Child account and EDU device at the same time is not supported.
                assert !(isAndroidEduDevice && hasChildAccount);
                int signinType = hasChildAccount ? SigninManager.SIGNIN_TYPE_FORCED_CHILD_ACCOUNT
                                                 : SigninManager.SIGNIN_TYPE_FORCED_EDU;
                processAutomaticSignIn(appContext, signinType);
            }
        }.start(appContext);
    }

    /**
     * Processes the fully automatic non-FRE-related forced sign-in.
     * This is used to enforce the environment for Android EDU and child accounts.
     */
    private static void processAutomaticSignIn(Context appContext, int signinType) {
        final Account[] googleAccounts = AccountManagerHelper.get(appContext).getGoogleAccounts();
        SigninManager signinManager = SigninManager.get(appContext);
        if (!FeatureUtilities.canAllowSync(appContext) || !signinManager.isSignInAllowed()
                || googleAccounts.length != 1) {
            return;
        }
        signinManager.signInToSelectedAccount(null, googleAccounts[0], signinType,
                SigninManager.SIGNIN_SYNC_IMMEDIATELY, false, null);
    }
}
