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
 * Fragment that allows user to add their Google account.
 */
public class AddGoogleAccountDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener {

    /**
     * Listener for new account additions.
     */
    public interface AddGoogleAccountListener {
        /**
         * Called when the user chooses to add an account to their device.
         */
        public void onAddAccountClicked();
    }

    private AddGoogleAccountListener mListener;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.add_account_title)
                .setPositiveButton(R.string.add_account_continue, this)
                .setNegativeButton(R.string.cancel, null)
                .setMessage(R.string.add_account_message)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            if (mListener != null) mListener.onAddAccountClicked();
        }
    }

    /**
     * @param listener Listener that should be notified when user chooses to add an account.
     */
    public void setListener(AddGoogleAccountListener listener) {
        mListener = listener;
    }
}
