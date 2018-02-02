// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.accounts.Account;
import android.content.Context;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.chrome.browser.services.AndroidEduAndChildAccountHelper;
import org.chromium.chrome.browser.signin.AccountManagementFragment;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.components.signin.ChromeSigninController;

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
    private static final String TAG = "ForcedSignin";

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
                processForcedSignIn(appContext);
            }
        }.start(appContext);
    }

    /**
     * Processes the fully automatic non-FRE-related forced sign-in.
     * This is used to enforce the environment for Android EDU and child accounts.
     */
    private static void processForcedSignIn(final Context appContext) {
        final SigninManager signinManager = SigninManager.get(appContext);
        // By definition we have finished all the checks for first run.
        signinManager.onFirstRunCheckDone();
        if (!FeatureUtilities.canAllowSync(appContext) || !signinManager.isSignInAllowed()) {
            Log.d(TAG, "Sign in disallowed");
            return;
        }
        AccountManagerHelper.get(appContext).getGoogleAccounts(new Callback<Account[]>() {
            @Override
            public void onResult(Account[] accounts) {
                if (accounts.length != 1) {
                    Log.d(TAG, "Incorrect number of accounts (%d)", accounts.length);
                    return;
                }
                signinManager.signIn(accounts[0], null, new SigninManager.SignInCallback() {
                    @Override
                    public void onSignInComplete() {
                        // Since this is a forced signin, signout is not allowed.
                        AccountManagementFragment.setSignOutAllowedPreferenceValue(
                                appContext, false);
                    }

                    @Override
                    public void onSignInAborted() {}
                });
            }
        });
    }
}
