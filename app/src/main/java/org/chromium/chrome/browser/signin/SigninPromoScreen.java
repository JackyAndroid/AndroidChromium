// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.firstrun.AccountFirstRunView;
import org.chromium.chrome.browser.firstrun.ProfileDataCache;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.SigninManager.SignInFlowObserver;
import org.chromium.chrome.browser.sync.ui.SyncCustomizationFragment;
import org.chromium.chrome.browser.widget.AlwaysDismissedDialog;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

/**
 * This class implements the dialog UI for the signin promo.
 */
public class SigninPromoScreen
        extends AlwaysDismissedDialog implements AccountFirstRunView.Listener {
    private AccountFirstRunView mAccountFirstRunView;
    private ProfileDataCache mProfileDataCache;

    /**
     * Launches the signin promo if it needs to be displayed.
     * @param activity The parent activity.
     * @return Whether the signin promo is shown.
     */
    public static boolean launchSigninPromoIfNeeded(final Activity activity) {
        // The promo is displayed if Chrome is launched directly (i.e., not with the intent to
        // navigate to and view a URL on startup), the instance is part of the field trial,
        // and the promo has been marked to display.
        ChromePreferenceManager preferenceManager = ChromePreferenceManager.getInstance(activity);
        if (MultiWindowUtils.getInstance().isMultiWindow(activity)) return false;
        if (!preferenceManager.getShowSigninPromo()) return false;
        preferenceManager.setShowSigninPromo(false);

        String lastSyncName = PrefServiceBridge.getInstance().getSyncLastAccountName();
        if (SigninManager.getAndroidSigninPromoExperimentGroup() < 0
                || ChromeSigninController.get(activity).isSignedIn()
                || !TextUtils.isEmpty(lastSyncName)) {
            return false;
        }

        SigninPromoScreen promoScreen = new SigninPromoScreen(activity);
        promoScreen.show();
        preferenceManager.setSigninPromoShown();
        return true;
    }

    /**
     * SigninPromoScreen constructor.
     *
     * @param activity An Android activity.
     */
    private SigninPromoScreen(Activity activity) {
        super(activity, R.style.SigninPromoDialog);
        setOwnerActivity(activity);

        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.fre_choose_account, null);
        mProfileDataCache = new ProfileDataCache(activity, Profile.getLastUsedProfile());
        mAccountFirstRunView = (AccountFirstRunView) view.findViewById(R.id.fre_account_layout);
        mAccountFirstRunView.init(mProfileDataCache);
        mAccountFirstRunView.configureForAddAccountPromo();
        mAccountFirstRunView.setListener(this);

        setContentView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SigninPromoUma.recordAction(SigninPromoUma.SIGNIN_PROMO_SHOWN);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        mProfileDataCache.destroy();
        mProfileDataCache = null;
    }

    @Override
    public void onAccountSelectionConfirmed(String accountName) {
        final Account account =
                AccountManagerHelper.get(getContext()).getAccountFromName(accountName);
        SignInFlowObserver signInCallback = new SignInFlowObserver() {
            @Override
            public void onSigninComplete() {
                mAccountFirstRunView.switchToSignedMode();
                SigninManager.get(getOwnerActivity()).logInSignedInUser();
                SigninPromoUma.recordAction(SigninPromoUma.SIGNIN_PROMO_ACCEPTED);
            }

            @Override
            public void onSigninCancelled() {
                SigninPromoUma.recordAction(SigninPromoUma.SIGNIN_PROMO_DECLINED);
                dismiss();
            }
        };
        SigninManager.get(getOwnerActivity().getApplicationContext()).signInToSelectedAccount(
                getOwnerActivity(), account, SigninManager.SIGNIN_TYPE_INTERACTIVE,
                SigninManager.SIGNIN_SYNC_IMMEDIATELY, false, signInCallback);
    }

    @Override
    public void onAccountSelectionCanceled() {
        SigninPromoUma.recordAction(SigninPromoUma.SIGNIN_PROMO_DECLINED);
        dismiss();
    }

    @Override
    public void onNewAccount() {
        AccountAdder.getInstance().addAccount(getOwnerActivity(), AccountAdder.ADD_ACCOUNT_RESULT);
    }

    @Override
    public void onSigningInCompleted(String accountName) {
        dismiss();
    }

    @Override
    public void onSettingsButtonClicked(String accountName) {
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(getContext(),
                SyncCustomizationFragment.class.getName());
        Bundle args = new Bundle();
        args.putString(SyncCustomizationFragment.ARGUMENT_ACCOUNT, accountName);
        intent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        getContext().startActivity(intent);

        SigninPromoUma.recordAction(SigninPromoUma.SIGNIN_PROMO_ACCEPTED_WITH_ADVANCED);
        dismiss();
    }

    @Override
    public void onFailedToSetForcedAccount(String forcedAccountName) {
        assert false : "No forced accounts in SigninPromoScreen";
    }
}
