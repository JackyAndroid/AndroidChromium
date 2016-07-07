// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync.ui;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.chromium.chrome.R;

/**
 * Dialog to ask the user to enter a new custom passphrase.
 */
public class PassphraseCreationDialogFragment extends DialogFragment {
    interface Listener {
        void onPassphraseCreated(String passphrase);
    }

    private EditText mEnterPassphrase;
    private EditText mConfirmPassphrase;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.sync_custom_passphrase, null);
        mEnterPassphrase = (EditText) view.findViewById(R.id.passphrase);
        mConfirmPassphrase = (EditText) view.findViewById(R.id.confirm_passphrase);

        mConfirmPassphrase.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    tryToSubmitPassphrase();
                }
                return false;
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setView(view)
                .setTitle(R.string.sync_passphrase_type_custom)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.getDelegate().setHandleNativeActionModesEnabled(false);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog d = (AlertDialog) getDialog();
        // Override the button's onClick listener. The default gets set in the dialog's onCreate,
        // when it is shown (in super.onStart()), so we have to do this here. Otherwise the dialog
        // will close when the button is clicked regardless of what else we do.
        d.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tryToSubmitPassphrase();
            }
        });
    }

    private void tryToSubmitPassphrase() {
        String passphrase = mEnterPassphrase.getText().toString();
        String confirmPassphrase = mConfirmPassphrase.getText().toString();

        if (passphrase.isEmpty()) {
            mConfirmPassphrase.setError(getString(R.string.sync_passphrase_cannot_be_blank));
            return;
        } else if (!passphrase.equals(confirmPassphrase)) {
            mConfirmPassphrase.setError(getString(R.string.sync_passphrases_do_not_match));
            return;
        }

        // The passphrase is not empty and matches.
        ((Listener) getTargetFragment()).onPassphraseCreated(passphrase);
        getDialog().dismiss();
    }
}
