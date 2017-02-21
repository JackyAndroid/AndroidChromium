// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.accounts.Account;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.FieldTrialList;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.services.AndroidEduAndChildAccountHelper;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.components.signin.ChromeSigninController;

/**
 * A helper to determine what should be the sequence of First Run Experience screens.
 * Usage:
 * new FirstRunFlowSequencer(activity, launcherProvidedProperties) {
 *     override onFlowIsKnown
 * }.start();
 */
public abstract class FirstRunFlowSequencer  {
    private static final int FIRST_RUN_EXPERIENCE_REQUEST_CODE = 101;

    private final Activity mActivity;
    private final Bundle mLaunchProperties;

    /**
     * Callback that is called once the flow is determined.
     * If the properties is null, the First Run experience needs to finish and
     * restart the original intent if necessary.
     * @param freProperties Properties to be used in the First Run activity, or null.
     */
    public abstract void onFlowIsKnown(Bundle freProperties);

    public FirstRunFlowSequencer(Activity activity, Bundle launcherProvidedProperties) {
        mActivity = activity;
        mLaunchProperties = launcherProvidedProperties;
    }

    /**
     * Starts determining parameters for the First Run.
     * Once finished, calls onFlowIsKnown().
     */
    public void start() {
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)
                || ApiCompatibilityUtils.isDemoUser(mActivity)) {
            onFlowIsKnown(null);
            return;
        }

        if (!mLaunchProperties.getBoolean(FirstRunActivity.EXTRA_USE_FRE_FLOW_SEQUENCER)) {
            onFlowIsKnown(mLaunchProperties);
            return;
        }

        new AndroidEduAndChildAccountHelper() {
            @Override
            public void onParametersReady() {
                processFreEnvironment(isAndroidEduDevice(), hasChildAccount());
            }
        }.start(mActivity.getApplicationContext());
    }

    @VisibleForTesting
    protected boolean isFirstRunFlowComplete() {
        return FirstRunStatus.getFirstRunFlowComplete(mActivity);
    }

    @VisibleForTesting
    protected boolean isSignedIn() {
        return ChromeSigninController.get(mActivity).isSignedIn();
    }

    @VisibleForTesting
    protected boolean isSyncAllowed() {
        SigninManager signinManager = SigninManager.get(mActivity.getApplicationContext());
        return FeatureUtilities.canAllowSync(mActivity) && !signinManager.isSigninDisabledByPolicy()
                && signinManager.isSigninSupported();
    }

    @VisibleForTesting
    protected Account[] getGoogleAccounts() {
        return AccountManagerHelper.get(mActivity).getGoogleAccounts();
    }

    @VisibleForTesting
    protected boolean hasAnyUserSeenToS() {
        return ToSAckedReceiver.checkAnyUserHasSeenToS(mActivity);
    }

    @VisibleForTesting
    protected boolean shouldSkipFirstUseHints() {
        return ApiCompatibilityUtils.shouldSkipFirstUseHints(mActivity.getContentResolver());
    }

    @VisibleForTesting
    protected boolean isFirstRunEulaAccepted() {
        return PrefServiceBridge.getInstance().isFirstRunEulaAccepted();
    }

    protected boolean shouldShowDataReductionPage() {
        return !DataReductionProxySettings.getInstance().isDataReductionProxyManaged()
                && FieldTrialList.findFullName("DataReductionProxyFREPromo").startsWith("Enabled");
    }

    @VisibleForTesting
    protected void setDefaultMetricsAndCrashReporting() {
        PrivacyPreferencesManager.getInstance().setUsageAndCrashReporting(
                FirstRunActivity.DEFAULT_METRICS_AND_CRASH_REPORTING);
    }

    @VisibleForTesting
    protected void setFirstRunFlowSignInComplete() {
        FirstRunSignInProcessor.setFirstRunFlowSignInComplete(
                mActivity.getApplicationContext(), true);
    }

    void processFreEnvironment(boolean androidEduDevice, boolean hasChildAccount) {
        if (isFirstRunFlowComplete()) {
            assert isFirstRunEulaAccepted();
            // We do not need any interactive FRE.
            onFlowIsKnown(null);
            return;
        }

        Bundle freProperties = new Bundle();
        freProperties.putAll(mLaunchProperties);
        freProperties.remove(FirstRunActivity.EXTRA_USE_FRE_FLOW_SEQUENCER);

        Account[] googleAccounts = getGoogleAccounts();
        boolean onlyOneAccount = googleAccounts.length == 1;

        // EDU devices should always have exactly 1 google account, which will be automatically
        // signed-in. All FRE screens are skipped in this case.
        boolean forceEduSignIn = androidEduDevice && onlyOneAccount && !isSignedIn();

        // In the full FRE we always show the Welcome page, except on EDU devices.
        boolean showWelcomePage = !forceEduSignIn;
        freProperties.putBoolean(FirstRunActivity.SHOW_WELCOME_PAGE, showWelcomePage);

        // Initialize usage and crash reporting according to the default value.
        // The user can explicitly enable or disable the reporting on the Welcome page.
        // This is controlled by the administrator via a policy on EDU devices.
        setDefaultMetricsAndCrashReporting();

        // We show the sign-in page if sync is allowed, and not signed in, and this is not an EDU
        // device, and
        // - no "skip the first use hints" is set, or
        // - "skip the first use hints" is set, but there is at least one account.
        final boolean offerSignInOk = isSyncAllowed()
                && !isSignedIn()
                && !forceEduSignIn
                && (!shouldSkipFirstUseHints() || googleAccounts.length > 0);
        freProperties.putBoolean(FirstRunActivity.SHOW_SIGNIN_PAGE, offerSignInOk);

        if (offerSignInOk || forceEduSignIn) {
            // If the user has accepted the ToS in the Setup Wizard and there is exactly
            // one account, or if the device has a child account, or if the device is an
            // Android EDU device and there is exactly one account, preselect the sign-in
            // account and force the selection if necessary.
            if ((hasAnyUserSeenToS() && onlyOneAccount) || hasChildAccount || forceEduSignIn) {
                freProperties.putString(AccountFirstRunFragment.FORCE_SIGNIN_ACCOUNT_TO,
                        googleAccounts[0].name);
                freProperties.putBoolean(AccountFirstRunFragment.PRESELECT_BUT_ALLOW_TO_CHANGE,
                        !forceEduSignIn && !hasChildAccount);
            }
        }

        freProperties.putBoolean(AccountFirstRunFragment.IS_CHILD_ACCOUNT, hasChildAccount);

        freProperties.putBoolean(FirstRunActivity.SHOW_DATA_REDUCTION_PAGE,
                shouldShowDataReductionPage());

        onFlowIsKnown(freProperties);
        if (hasChildAccount || forceEduSignIn) {
            // Child and Edu forced signins are processed independently.
            setFirstRunFlowSignInComplete();
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
     * @param context The context.
     * @param fromIntent The intent that was used to launch Chrome.
     * @param forLightweightFre Whether this is a check for the Lightweight First Run Experience.
     */
    public static Intent checkIfFirstRunIsNecessary(
            Context context, Intent fromIntent, boolean forLightweightFre) {
        // If FRE is disabled (e.g. in tests), proceed directly to the intent handling.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)
                || ApiCompatibilityUtils.isDemoUser(context)) {
            return null;
        }

        // If Chrome isn't opened via the Chrome icon, and the user accepted the ToS
        // in the Setup Wizard, skip any First Run Experience screens and proceed directly
        // to the intent handling.
        final boolean fromChromeIcon =
                fromIntent != null && TextUtils.equals(fromIntent.getAction(), Intent.ACTION_MAIN);
        if (!fromChromeIcon && ToSAckedReceiver.checkAnyUserHasSeenToS(context)) return null;

        final boolean baseFreComplete = FirstRunStatus.getFirstRunFlowComplete(context);
        if (!baseFreComplete) {
            if (forLightweightFre
                    && CommandLine.getInstance().hasSwitch(
                               ChromeSwitches.ENABLE_LIGHTWEIGHT_FIRST_RUN_EXPERIENCE)) {
                if (!FirstRunStatus.shouldSkipWelcomePage(context)
                        && !FirstRunStatus.getLightweightFirstRunFlowComplete(context)) {
                    return createLightweightFirstRunIntent(context, fromChromeIcon);
                }
            } else {
                return createGenericFirstRunIntent(context, fromChromeIcon);
            }
        }

        // Promo pages are removed, so there is nothing else to show in FRE.
        return null;
    }

    private static Intent createLightweightFirstRunIntent(Context context, boolean fromChromeIcon) {
        Intent intent = new Intent();
        intent.setClassName(context, LightweightFirstRunActivity.class.getName());
        intent.putExtra(FirstRunActivity.EXTRA_COMING_FROM_CHROME_ICON, fromChromeIcon);
        intent.putExtra(FirstRunActivity.EXTRA_START_LIGHTWEIGHT_FRE, true);
        return intent;
    }

    /**
     * @return A generic intent to show the First Run Activity.
     * @param context        The context.
     * @param fromChromeIcon Whether Chrome is opened via the Chrome icon.
    */
    public static Intent createGenericFirstRunIntent(Context context, boolean fromChromeIcon) {
        Intent intent = new Intent();
        intent.setClassName(context, FirstRunActivity.class.getName());
        intent.putExtra(FirstRunActivity.EXTRA_COMING_FROM_CHROME_ICON, fromChromeIcon);
        intent.putExtra(FirstRunActivity.EXTRA_USE_FRE_FLOW_SEQUENCER, true);
        return intent;
    }

    /**
     * Adds fromIntent as a PendingIntent to the firstRunIntent. This should be used to add a
     * PendingIntent that will be sent when first run is either completed or canceled.
     *
     * @param context        The context.
     * @param firstRunIntent The intent that will be used to start first run.
     * @param fromIntent     The intent that was used to launch Chrome.
     */
    public static void addPendingIntent(Context context, Intent firstRunIntent, Intent fromIntent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                FIRST_RUN_EXPERIENCE_REQUEST_CODE,
                fromIntent,
                fromIntent.getFlags());
        firstRunIntent.putExtra(FirstRunActivity.EXTRA_CHROME_LAUNCH_INTENT, pendingIntent);
    }
}
