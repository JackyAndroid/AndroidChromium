// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.signin.AccountSigninView;
import org.chromium.chrome.browser.signin.SigninAccessPoint;
import org.chromium.chrome.browser.signin.SigninManager;

/**
 * A {@link Fragment} meant to handle sync setup for the first run experience.
 */
public class AccountFirstRunFragment extends FirstRunPage implements AccountSigninView.Delegate {
    // Per-page parameters:
    public static final String FORCE_SIGNIN_ACCOUNT_TO = "ForceSigninAccountTo";
    public static final String PRESELECT_BUT_ALLOW_TO_CHANGE = "PreselectButAllowToChange";
    public static final String IS_CHILD_ACCOUNT = "IsChildAccount";

    private AccountSigninView mView;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = (AccountSigninView) inflater.inflate(
                R.layout.account_signin_view, container, false);
        return mView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mView.init(getPageDelegate().getProfileDataCache(), this, new AccountSigninView.Listener() {
            @Override
            public void onAccountSelectionCanceled() {
                getPageDelegate().refuseSignIn();
                advanceToNextPage();
            }

            @Override
            public void onNewAccount() {
                getPageDelegate().openAccountAdder(AccountFirstRunFragment.this);
            }

            @Override
            public void onAccountSelected(String accountName, boolean settingsClicked) {
                getPageDelegate().acceptSignIn(accountName);
                if (settingsClicked) {
                    getPageDelegate().askToOpenSignInSettings();
                }
                advanceToNextPage();
            }

            @Override
            public void onFailedToSetForcedAccount(String forcedAccountName) {
                // Somehow the forced account disappeared while we were in the FRE.
                // The user would have to go through the FRE again.
                getPageDelegate().abortFirstRunExperience();
            }
        });

        mView.setIsChildAccount(getProperties().getBoolean(IS_CHILD_ACCOUNT));

        String forcedAccountName =
                getProperties().getString(FORCE_SIGNIN_ACCOUNT_TO);
        if (!TextUtils.isEmpty(forcedAccountName)) {
            mView.switchToForcedAccountMode(forcedAccountName);
        }

        RecordUserAction.record("MobileFre.SignInShown");
        RecordUserAction.record("Signin_Signin_FromStartPage");
        SigninManager.logSigninStartAccessPoint(SigninAccessPoint.START_PAGE);
    }

    // FirstRunPage:

    @Override
    public boolean interceptBackPressed() {
        if (!mView.isSignedIn()
                || (mView.isInForcedAccountMode()
                            && !getProperties().getBoolean(PRESELECT_BUT_ALLOW_TO_CHANGE))) {
            return super.interceptBackPressed();
        }

        if (mView.isInForcedAccountMode()
                && getProperties().getBoolean(PRESELECT_BUT_ALLOW_TO_CHANGE)) {
            // Allow the user to choose the account or refuse to sign in,
            // and re-create this fragment.
            getProperties().remove(FORCE_SIGNIN_ACCOUNT_TO);
        }

        // Re-create the fragment if the user presses the back button when in signed in mode.
        // The fragment is re-created in the normal (signed out) mode.
        getPageDelegate().recreateCurrentPage();
        return true;
    }
}
