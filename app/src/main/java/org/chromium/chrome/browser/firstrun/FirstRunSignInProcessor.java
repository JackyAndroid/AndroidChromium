// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInFlowObserver;
import org.chromium.chrome.browser.sync.ui.SyncCustomizationFragment;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

/**
 * A helper to perform all necessary steps for the automatic FRE sign in.
 * The helper performs any pending request to sign in from the First Run Experience.
 * The helper calls the observer's onSignInComplete() if
 * - nothing needs to be done, or when
 * - the sign in is complete.
 * If the sign in process fails or if an interactive FRE sequence is necessary,
 * the helper starts the FRE activity, finishes the current activity and calls
 * OnSignInCancelled.
 *
 * Usage:
 * FirstRunSignInProcessor.start(activity).
 */
public final class FirstRunSignInProcessor {
    private static final String TAG = "FirstRunSigninProc";
    /**
     * SharedPreferences preference names to keep the state of the First Run Experience.
     */
    private static final String FIRST_RUN_FLOW_SIGNIN_COMPLETE = "first_run_signin_complete";
    private static final String FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME =
            "first_run_signin_account_name";
    private static final String FIRST_RUN_FLOW_SIGNIN_SETUP_SYNC = "first_run_signin_setup_sync";

    /**
     * Initiates the automatic sign-in process in background.
     *
     * @param activity The context for the FRE parameters processor.
     */
    public static void start(final Activity activity) {
        SigninManager signinManager = SigninManager.get(activity.getApplicationContext());
        signinManager.onFirstRunCheckDone();

        boolean firstRunFlowComplete = FirstRunStatus.getFirstRunFlowComplete(activity);
        // We skip signin and the FRE only if
        // - FRE is disabled, or
        // - FRE hasn't been completed, but the user has already seen the ToS in the Setup Wizard.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)
                || (!firstRunFlowComplete && ToSAckedReceiver.checkAnyUserHasSeenToS(activity))) {
            return;
        }
        // Otherwise, force trigger the FRE.
        if (!firstRunFlowComplete) {
            requestToFireIntentAndFinish(activity);
            return;
        }

        // We are only processing signin from the FRE.
        if (getFirstRunFlowSignInComplete(activity)) {
            return;
        }
        final String accountName = getFirstRunFlowSignInAccountName(activity);
        if (!FeatureUtilities.canAllowSync(activity) || !signinManager.isSignInAllowed()
                || TextUtils.isEmpty(accountName)) {
            setFirstRunFlowSignInComplete(activity, true);
            return;
        }

        final Account account = AccountManagerHelper.get(activity).getAccountFromName(accountName);
        if (account == null) {
            // TODO(aruslan): handle the account being removed during the FRE.
            requestToFireIntentAndFinish(activity);
            return;
        }

        final boolean delaySync = getFirstRunFlowSignInSetupSync(activity);
        final int delaySyncType = delaySync
                ? SigninManager.SIGNIN_SYNC_SETUP_IN_PROGRESS
                : SigninManager.SIGNIN_SYNC_IMMEDIATELY;
        signinManager.signInToSelectedAccount(activity, account,
                SigninManager.SIGNIN_TYPE_INTERACTIVE, delaySyncType, false,
                new SignInFlowObserver() {
                    private void completeSignIn() {
                        // Show sync settings if user pressed the "Settings" button.
                        if (delaySync) {
                            openSyncSettings(activity);
                        }
                        setFirstRunFlowSignInComplete(activity, true);
                    }

                    @Override
                    public void onSigninComplete() {
                        completeSignIn();
                    }

                    @Override
                    public void onSigninCancelled() {
                        completeSignIn();
                    }
                });
    }

    /**
     * Opens Sync settings as requested in the FRE sign-in dialog.
     */
    private static void openSyncSettings(Activity activity) {
        String accountName = ChromeSigninController.get(activity).getSignedInAccountName();
        if (TextUtils.isEmpty(accountName)) return;
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                activity, SyncCustomizationFragment.class.getName());
        Bundle args = new Bundle();
        args.putString(SyncCustomizationFragment.ARGUMENT_ACCOUNT, accountName);
        intent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        activity.startActivity(intent);
    }

    /**
     * Starts the full FRE and finishes the current activity.
     */
    private static void requestToFireIntentAndFinish(Activity activity) {
        Log.e(TAG, "Attempt to pass-through without completed FRE");

        // Things went wrong -- we want the user to go through the full FRE.
        FirstRunStatus.setFirstRunFlowComplete(activity, false);
        setFirstRunFlowSignInComplete(activity, false);
        setFirstRunFlowSignInAccountName(activity, null);
        setFirstRunFlowSignInSetupSync(activity, false);
        activity.startActivity(FirstRunFlowSequencer.createGenericFirstRunIntent(activity, true));
    }

    /**
     * @return Whether there is no pending sign-in requests from the First Run Experience.
     * @param context A context
     */
    private static boolean getFirstRunFlowSignInComplete(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(FIRST_RUN_FLOW_SIGNIN_COMPLETE, false);
    }

    /**
     * Sets the "pending First Run Experience sign-in requests" preference.
     * @param context A context
     * @param isComplete Whether there is no pending sign-in requests from the First Run Experience.
     */
    @VisibleForTesting
    public static void setFirstRunFlowSignInComplete(Context context, boolean isComplete) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(FIRST_RUN_FLOW_SIGNIN_COMPLETE, isComplete)
                .apply();
    }

    /**
     * @return The account name selected during the First Run Experience, or null if none.
     * @param context A context
     */
    private static String getFirstRunFlowSignInAccountName(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME, null);
    }

    /**
     * Sets the account name for the pending sign-in First Run Experience request.
     * @param context A context
     * @param accountName The account name, or null.
     */
    private static void setFirstRunFlowSignInAccountName(Context context, String accountName) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME, accountName)
                .apply();
    }

    /**
     * @return Whether the user selected to see the Sync settings once signed in after FRE.
     * @param context A context
     */
    private static boolean getFirstRunFlowSignInSetupSync(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(FIRST_RUN_FLOW_SIGNIN_SETUP_SYNC, false);
    }

    /**
     * Sets the preference to see the Sync settings once signed in after FRE.
     * @param context A context
     * @param isComplete Whether the user selected to see the Sync settings once signed in.
     */
    private static void setFirstRunFlowSignInSetupSync(Context context, boolean isComplete) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(FIRST_RUN_FLOW_SIGNIN_SETUP_SYNC, isComplete)
                .apply();
    }

    /**
     * Finalize the state of the FRE flow (mark is as "complete" and finalize parameters).
     * @param context A context
     * @param data Resulting FRE properties bundle
     */
    public static void finalizeFirstRunFlowState(Context context, Bundle data) {
        FirstRunStatus.setFirstRunFlowComplete(context, true);
        setFirstRunFlowSignInAccountName(context,
                    data.getString(FirstRunActivity.RESULT_SIGNIN_ACCOUNT_NAME));
        setFirstRunFlowSignInSetupSync(context,
                    data.getBoolean(FirstRunActivity.RESULT_SHOW_SYNC_SETTINGS));
    }

    /**
     * Allows the user to sign-in if there are no pending FRE sign-in requests.
     * @param context A context
     */
    public static void updateSigninManagerFirstRunCheckDone(Context context) {
        SigninManager manager = SigninManager.get(context);
        if (manager.isSignInAllowed()) return;
        if (!FirstRunStatus.getFirstRunFlowComplete(context)) return;
        if (!getFirstRunFlowSignInComplete(context)) return;
        manager.onFirstRunCheckDone();
    }
}
