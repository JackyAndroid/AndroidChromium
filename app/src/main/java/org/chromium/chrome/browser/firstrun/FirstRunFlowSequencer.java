// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.services.AndroidEduAndChildAccountHelper;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

/**
 * A helper to determine what should be the sequence of First Run Experience screens.
 * Usage:
 * new FirstRunFlowSequencer(activity, launcherProvidedProperties) {
 *     override onFlowIsKnown
 * }.start();
 */
public abstract class FirstRunFlowSequencer  {
    private final Activity mActivity;
    private final Bundle mLaunchProperties;

    private boolean mIsAndroidEduDevice;
    private boolean mHasChildAccount;

    /**
     * Callback that is called once the flow is determined.
     * If the properties is null, the First Run experience needs to finish and
     * restart the original intent if necessary.
     * @param activity An activity.
     * @param freProperties Properties to be used in the First Run activity, or null.
     */
    public abstract void onFlowIsKnown(Activity activity, Bundle freProperties);

    public FirstRunFlowSequencer(Activity activity, Bundle launcherProvidedProperties) {
        mActivity = activity;
        mLaunchProperties = launcherProvidedProperties;
    }

    /**
     * Starts determining parameters for the First Run.
     * Once finished, calls onFlowIsKnown().
     */
    public void start() {
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)) {
            onFlowIsKnown(mActivity, null);
            return;
        }

        if (!mLaunchProperties.getBoolean(FirstRunActivity.USE_FRE_FLOW_SEQUENCER)) {
            onFlowIsKnown(mActivity, mLaunchProperties);
            return;
        }

        new AndroidEduAndChildAccountHelper() {
            @Override
            public void onParametersReady() {
                mIsAndroidEduDevice = isAndroidEduDevice();
                mHasChildAccount = hasChildAccount();
                processFreEnvironment();
            }
        }.start(mActivity.getApplicationContext());
    }

    /**
     * @return Whether the sync could be turned on.
     */
    @VisibleForTesting
    boolean isSyncAllowed() {
        return FeatureUtilities.canAllowSync(mActivity)
                && !SigninManager.get(mActivity.getApplicationContext()).isSigninDisabledByPolicy();
    }

    /**
     * @return Whether Terms of Service could be assumed to be accepted.
     */
    @VisibleForTesting
    boolean didAcceptToS() {
        return ToSAckedReceiver.checkAnyUserHasSeenToS(mActivity)
                || PrefServiceBridge.getInstance().isFirstRunEulaAccepted();
    }

    private void processFreEnvironment() {
        final Context context = mActivity.getApplicationContext();

        if (FirstRunStatus.getFirstRunFlowComplete(mActivity)) {
            assert PrefServiceBridge.getInstance().isFirstRunEulaAccepted();
            // We do not need any interactive FRE.
            onFlowIsKnown(mActivity, null);
            return;
        }

        Bundle freProperties = new Bundle();
        freProperties.putAll(mLaunchProperties);
        freProperties.remove(FirstRunActivity.USE_FRE_FLOW_SEQUENCER);

        final Account[] googleAccounts = AccountManagerHelper.get(context).getGoogleAccounts();
        final boolean onlyOneAccount = googleAccounts.length == 1;

        // EDU devices should always have exactly 1 google account, which will be automatically
        // signed-in. All FRE screens are skipped in this case.
        final boolean forceEduSignIn = mIsAndroidEduDevice
                && onlyOneAccount
                && !ChromeSigninController.get(context).isSignedIn();

        final boolean shouldSkipFirstUseHints =
                ApiCompatibilityUtils.shouldSkipFirstUseHints(context.getContentResolver());

        if (!FirstRunStatus.getFirstRunFlowComplete(context)) {
            // In the full FRE we always show the Welcome page, except on EDU devices.
            final boolean showWelcomePage = !forceEduSignIn;
            freProperties.putBoolean(FirstRunActivity.SHOW_WELCOME_PAGE, showWelcomePage);

            // Enable reporting by default on non-Stable releases.
            // The user can turn it off on the Welcome page.
            // This is controlled by the administrator via a policy on EDU devices.
            if (!ChromeVersionInfo.isStableBuild()) {
                PrivacyPreferencesManager.getInstance(context).initCrashUploadPreference(true);
            }

            // We show the sign-in page if sync is allowed, and this is not an EDU device, and
            // - no "skip the first use hints" is set, or
            // - "skip the first use hints" is set, but there is at least one account.
            final boolean syncOk = isSyncAllowed();
            final boolean offerSignInOk = syncOk
                    && !forceEduSignIn
                    && (!shouldSkipFirstUseHints || googleAccounts.length > 0);
            freProperties.putBoolean(FirstRunActivity.SHOW_SIGNIN_PAGE, offerSignInOk);

            if (offerSignInOk || forceEduSignIn) {
                // If the user has accepted the ToS in the Setup Wizard and there is exactly
                // one account, or if the device has a child account, or if the device is an
                // Android EDU device and there is exactly one account, preselect the sign-in
                // account and force the selection if necessary.
                if ((ToSAckedReceiver.checkAnyUserHasSeenToS(mActivity) && onlyOneAccount)
                        || mHasChildAccount
                        || forceEduSignIn) {
                    freProperties.putString(AccountFirstRunFragment.FORCE_SIGNIN_ACCOUNT_TO,
                            googleAccounts[0].name);
                    freProperties.putBoolean(AccountFirstRunFragment.PRESELECT_BUT_ALLOW_TO_CHANGE,
                            !forceEduSignIn && !mHasChildAccount);
                }
            }
        } else {
            // If the full FRE has already been shown, don't show Welcome or Sign-In pages.
            freProperties.putBoolean(FirstRunActivity.SHOW_WELCOME_PAGE, false);
            freProperties.putBoolean(FirstRunActivity.SHOW_SIGNIN_PAGE, false);
        }

        freProperties.putBoolean(AccountFirstRunFragment.IS_CHILD_ACCOUNT, mHasChildAccount);

        onFlowIsKnown(mActivity, freProperties);
        if (mHasChildAccount || forceEduSignIn) {
            // Child and Edu forced signins are processed independently.
            FirstRunSignInProcessor.setFirstRunFlowSignInComplete(context, true);
        }
    }

    /**
     * Marks a given flow as completed.
     * @param activity An activity.
     * @param data Resulting FRE properties bundle.
     */
    public static void markFlowAsCompleted(Activity activity, Bundle data) {
        // When the user accepts ToS in the Setup Wizard (see ToSAckedReceiver), we do not
        // show the ToS page to the user because the user has already accepted one outside FRE.
        if (!PrefServiceBridge.getInstance().isFirstRunEulaAccepted()) {
            PrefServiceBridge.getInstance().setEulaAccepted();
        }

        // Mark the FRE flow as complete and set the sign-in flow preferences if necessary.
        FirstRunSignInProcessor.finalizeFirstRunFlowState(activity, data);
    }

    /**
     * Checks if the First Run needs to be launched.
     * @return The intent to launch the First Run Experience if necessary, or null.
     * @param context The context
     * @param fromChromeIcon Whether Chrome is opened via the Chrome icon
     */
    public static Intent checkIfFirstRunIsNecessary(Context context, boolean fromChromeIcon) {
        // If FRE is disabled (e.g. in tests), proceed directly to the intent handling.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)) {
            return null;
        }

        // If Chrome isn't opened via the Chrome icon, and the user accepted the ToS
        // in the Setup Wizard, skip any First Run Experience screens and proceed directly
        // to the intent handling.
        if (!fromChromeIcon && ToSAckedReceiver.checkAnyUserHasSeenToS(context)) return null;

        // If the user hasn't been through the First Run Activity -- it must be shown.
        final boolean baseFreComplete = FirstRunStatus.getFirstRunFlowComplete(context);
        if (!baseFreComplete) {
            return createGenericFirstRunIntent(context, fromChromeIcon);
        }

        // Promo pages are removed, so there is nothing else to show in FRE.
        return null;
    }

    /**
     * @return A generic intent to show the First Run Activity.
     * @param context The context
     * @param fromChromeIcon Whether Chrome is opened via the Chrome icon
    */
    public static Intent createGenericFirstRunIntent(Context context, boolean fromChromeIcon) {
        Intent intent = new Intent();
        intent.setClassName(context, FirstRunActivity.class.getName());
        intent.putExtra(FirstRunActivity.COMING_FROM_CHROME_ICON, fromChromeIcon);
        intent.putExtra(FirstRunActivity.USE_FRE_FLOW_SEQUENCER, true);
        return intent;
    }
}
