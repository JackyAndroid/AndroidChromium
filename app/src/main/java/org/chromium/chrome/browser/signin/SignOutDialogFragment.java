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

/**
 * Shows the dialog that explains the user the consequences of signing out of Chrome.
 * Calls the listener callback if the user signs out.
 */
public class SignOutDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener {
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

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
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
                .setPositiveButton(R.string.ok, this)
                .setNegativeButton(R.string.cancel, this)
                .setMessage(message)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            mSignOutClicked = true;
            SignOutDialogListener targetFragment = (SignOutDialogListener) getTargetFragment();
            targetFragment.onSignOutClicked();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        SignOutDialogListener targetFragment = (SignOutDialogListener) getTargetFragment();
        targetFragment.onSignOutDialogDismissed(mSignOutClicked);
    }
}
