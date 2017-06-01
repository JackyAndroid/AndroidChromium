// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.IntentHandler.ExternalAppId;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.signin.AccountManagementFragment;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInCallback;
import org.chromium.chrome.browser.util.FeatureUtilities;

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

    // Needed by ChromeBackupAgent
    public static final String FIRST_RUN_FLOW_SIGNIN_SETUP = "first_run_signin_setup";
    public static final String FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME =
            "first_run_signin_account_name";

    /**
     * Initiates the automatic sign-in process in background.
     *
     * @param activity The context for the FRE parameters processor.
     */
    public static void start(final Activity activity) {
        SigninManager signinManager = SigninManager.get(activity.getApplicationContext());
        signinManager.onFirstRunCheckDone();

        boolean firstRunFlowComplete = FirstRunStatus.getFirstRunFlowComplete();
        // We skip signin and the FRE if
        // - FRE is disabled, or
        // - FRE hasn't been completed, but the user has already seen the ToS in the Setup Wizard.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)
                || ApiCompatibilityUtils.isDemoUser(activity)
                || (!firstRunFlowComplete && ToSAckedReceiver.checkAnyUserHasSeenToS(activity))) {
            return;
        }

        // Force trigger the FRE if the Lightweight FRE is disabled or Chrome is started via Chrome
        // icon or via intent from GSA. Otherwise, skip signin.
        if (!firstRunFlowComplete) {
            if (!CommandLine.getInstance().hasSwitch(
                        ChromeSwitches.ENABLE_LIGHTWEIGHT_FIRST_RUN_EXPERIENCE)
                    || TextUtils.equals(activity.getIntent().getAction(), Intent.ACTION_MAIN)
                    || IntentHandler.determineExternalIntentSource(
                               activity.getPackageName(), activity.getIntent())
                            == ExternalAppId.GSA) {
                requestToFireIntentAndFinish(activity);
            }
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

        final boolean setUp = getFirstRunFlowSignInSetup(activity);
        signinManager.signIn(accountName, activity, new SignInCallback() {
            @Override
            public void onSignInComplete() {
                // Show sync settings if user pressed the "Settings" button.
                if (setUp) {
                    openSignInSettings(activity);
                }
                setFirstRunFlowSignInComplete(activity, true);
            }

            @Override
            public void onSignInAborted() {
                // Set FRE as complete even if signin fails because the user has already seen and
                // accepted the terms of service.
                setFirstRunFlowSignInComplete(activity, true);
            }
        });
    }

    /**
     * Opens sign in settings as requested in the FRE sign-in dialog.
     */
    private static void openSignInSettings(Activity activity) {
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                activity, AccountManagementFragment.class.getName());
        activity.startActivity(intent);
    }

    /**
     * Starts the full FRE and finishes the current activity.
     */
    private static void requestToFireIntentAndFinish(Activity activity) {
        Log.e(TAG, "Attempt to pass-through without completed FRE");

        // Things went wrong -- we want the user to go through the full FRE.
        FirstRunStatus.setFirstRunFlowComplete(false);
        setFirstRunFlowSignInComplete(activity, false);
        setFirstRunFlowSignInAccountName(activity, null);
        setFirstRunFlowSignInSetup(activity, false);
        activity.startActivity(FirstRunFlowSequencer.createGenericFirstRunIntent(activity, true));
    }

    /**
     * @return Whether there is no pending sign-in requests from the First Run Experience.
     * @param context A context
     */
    @VisibleForTesting
    public static boolean getFirstRunFlowSignInComplete(Context context) {
        return ContextUtils.getAppSharedPreferences()
                .getBoolean(FIRST_RUN_FLOW_SIGNIN_COMPLETE, false);
    }

    /**
     * Sets the "pending First Run Experience sign-in requests" preference.
     * @param context A context
     * @param isComplete Whether there is no pending sign-in requests from the First Run Experience.
     */
    @VisibleForTesting
    public static void setFirstRunFlowSignInComplete(Context context, boolean isComplete) {
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(FIRST_RUN_FLOW_SIGNIN_COMPLETE, isComplete)
                .apply();
    }

    /**
     * @return The account name selected during the First Run Experience, or null if none.
     * @param context A context
     */
    private static String getFirstRunFlowSignInAccountName(Context context) {
        return ContextUtils.getAppSharedPreferences()
                .getString(FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME, null);
    }

    /**
     * Sets the account name for the pending sign-in First Run Experience request.
     * @param context A context
     * @param accountName The account name, or null.
     */
    private static void setFirstRunFlowSignInAccountName(Context context, String accountName) {
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putString(FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME, accountName)
                .apply();
    }

    /**
     * @return Whether the user selected to see the settings once signed in after FRE.
     * @param context A context
     */
    private static boolean getFirstRunFlowSignInSetup(Context context) {
        return ContextUtils.getAppSharedPreferences().getBoolean(
                FIRST_RUN_FLOW_SIGNIN_SETUP, false);
    }

    /**
     * Sets the preference to see the settings once signed in after FRE.
     * @param context A context
     * @param isComplete Whether the user selected to see the settings once signed in.
     */
    private static void setFirstRunFlowSignInSetup(Context context, boolean isComplete) {
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(FIRST_RUN_FLOW_SIGNIN_SETUP, isComplete)
                .apply();
    }

    /**
     * Finalize the state of the FRE flow (mark is as "complete" and finalize parameters).
     * @param context A context
     * @param data Resulting FRE properties bundle
     */
    public static void finalizeFirstRunFlowState(Context context, Bundle data) {
        FirstRunStatus.setFirstRunFlowComplete(true);
        setFirstRunFlowSignInAccountName(context,
                    data.getString(FirstRunActivity.RESULT_SIGNIN_ACCOUNT_NAME));
        setFirstRunFlowSignInSetup(
                context, data.getBoolean(FirstRunActivity.RESULT_SHOW_SIGNIN_SETTINGS));
    }

    /**
     * Allows the user to sign-in if there are no pending FRE sign-in requests.
     * @param context A context
     */
    public static void updateSigninManagerFirstRunCheckDone(Context context) {
        SigninManager manager = SigninManager.get(context);
        if (manager.isSignInAllowed()) return;
        if (!FirstRunStatus.getFirstRunFlowComplete()) return;
        if (!getFirstRunFlowSignInComplete(context)) return;
        manager.onFirstRunCheckDone();
    }
}
