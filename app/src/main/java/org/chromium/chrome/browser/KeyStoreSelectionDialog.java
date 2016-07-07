// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * Client certificate KeyStore selection dialog. When smart card (CAC) support is enabled, this
 * dialog allows choosing between (the default) system certificate store and the smart card for
 * client authentication.
 */
class KeyStoreSelectionDialog extends DialogFragment {
    private static final CharSequence SYSTEM_STORE = "Android";

    private final Runnable mSystemCallback;
    private final Runnable mSmartCardCallback;
    private final Runnable mCancelCallback;

    private Runnable mSelectedChoice;

    /**
     * Constructor for KeyStore selection dialog.
     *
     * @param systemCallback Runnable that's invoked when the user selects to use the Android
     *     system store for authentication
     * @param smartCardCallback Runnable that's invoked when the user selects to use the SmartCard
     *     store for authentication
     * @param cancelCallback Runnable that's invoked when the user cancels or closes the dialog.
     */
    public KeyStoreSelectionDialog(Runnable systemCallback, Runnable smartCardCallback,
            Runnable cancelCallback) {
        mSystemCallback = systemCallback;
        mSmartCardCallback = smartCardCallback;
        mCancelCallback = cancelCallback;

        // Default to SmartCard
        mSelectedChoice = mSmartCardCallback;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final CharSequence[] choices = {
            getString(R.string.smartcard_certificate_option),
            SYSTEM_STORE
        };
        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.smartcard_dialog_title)
                .setSingleChoiceItems(choices, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (choices[id] == SYSTEM_STORE) {
                            mSelectedChoice = mSystemCallback;
                        } else {
                            mSelectedChoice = mSmartCardCallback;
                        }
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mSelectedChoice.run();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mCancelCallback.run();
                    }
                });

        return builder.create();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
    }
}
