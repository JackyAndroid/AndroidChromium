// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.profiles.ProfileAccountManagementMetrics;

/**
 * Shows the dialog that explains the user the consequences of signing out of Chrome.
 * Calls the listener callback if the user signs out.
 */
public class SignOutDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener {
    /**
     * The extra key used to specify the GAIA service that triggered this dialog.
     */
    public static final String SHOW_GAIA_SERVICE_TYPE_EXTRA = "ShowGAIAServiceType";

    /**
     * Receives updates when the user clicks "Sign out" or dismisses the dialog.
     */
    public interface SignOutDialogListener {
        /**
         * Called when the user clicks "Sign out".
         */
        public void onSignOutClicked();

        /**
         * Called when the dialog is dismissed.
         *
         * @param signOutClicked Whether the user clicked the "sign out" button before the dialog
         *                       was dismissed.
         */
        public void onSignOutDialogDismissed(boolean signOutClicked);
    }

    private boolean mSignOutClicked;

    /**
     * The GAIA service that's prompted this dialog. Values can be any constant in
     * signin::GAIAServiceType
     */
    private int mGaiaServiceType;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mGaiaServiceType = AccountManagementScreenHelper.GAIA_SERVICE_TYPE_NONE;
        if (getArguments() != null) {
            mGaiaServiceType = getArguments().getInt(
                    SHOW_GAIA_SERVICE_TYPE_EXTRA, mGaiaServiceType);
        }

        String managementDomain = SigninManager.get(getActivity()).getManagementDomain();
        String message;
        if (managementDomain == null) {
            message = getActivity().getResources().getString(R.string.signout_message);
        } else {
            message = getActivity().getResources().getString(
                    R.string.signout_managed_account_message, managementDomain);
        }

        return new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.signout_title)
                .setPositiveButton(R.string.signout_dialog_positive_button, this)
                .setNegativeButton(R.string.cancel, this)
                .setMessage(message)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            AccountManagementScreenHelper.logEvent(
                    ProfileAccountManagementMetrics.SIGNOUT_SIGNOUT, mGaiaServiceType);

            mSignOutClicked = true;
            SignOutDialogListener targetFragment = (SignOutDialogListener) getTargetFragment();
            targetFragment.onSignOutClicked();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        AccountManagementScreenHelper.logEvent(
                ProfileAccountManagementMetrics.SIGNOUT_CANCEL, mGaiaServiceType);

        SignOutDialogListener targetFragment = (SignOutDialogListener) getTargetFragment();
        targetFragment.onSignOutDialogDismissed(mSignOutClicked);
    }
}
