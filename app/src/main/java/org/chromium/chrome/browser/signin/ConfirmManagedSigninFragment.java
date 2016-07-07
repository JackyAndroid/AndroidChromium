// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * This is an AlertDialog asking the user to confirm that he wants to sign in to a managed account.
 */
public class ConfirmManagedSigninFragment extends DialogFragment {

    private  String mManagementDomain = null;
    private  OnClickListener mListener = null;

    public ConfirmManagedSigninFragment() {
    }

    public ConfirmManagedSigninFragment(String managementDomain, OnClickListener listener) {
        mManagementDomain = managementDomain;
        mListener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setCancelable(false);

        Activity activity = getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        builder.setTitle(R.string.policy_dialog_title);
        builder.setMessage(activity.getResources().getString(R.string.policy_dialog_message,
                                                             mManagementDomain));
        builder.setPositiveButton(R.string.policy_dialog_proceed, mListener);
        builder.setNegativeButton(R.string.policy_dialog_cancel, mListener);
        return builder.create();
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        super.onDismiss(dialogInterface);
        // This makes dismissing the dialog equivalent to cancelling sign-in, and
        // allows the listener to clean up any pending state.
        mListener.onClick(dialogInterface, AlertDialog.BUTTON_NEGATIVE);
    }
}
