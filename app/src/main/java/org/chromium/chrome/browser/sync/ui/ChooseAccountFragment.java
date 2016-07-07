// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync.ui;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;
import org.chromium.sync.signin.AccountManagerHelper;

import java.util.List;

/**
 * A dialog that lets the user choose which Google account to use when signing in to Chrome.
 */
public class ChooseAccountFragment extends DialogFragment implements OnClickListener {

    protected String[] mAccounts;

    protected int mSelectedAccount;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        List<String> accountsList = AccountManagerHelper.get(getActivity()).getGoogleAccountNames();
        mAccounts = accountsList.toArray(new String[accountsList.size()]);
        return new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setSingleChoiceItems(mAccounts, mSelectedAccount, this)
                .setPositiveButton(R.string.choose_account_sign_in, this)
                .setNegativeButton(R.string.cancel, this)
                .setTitle(R.string.sign_in_google_account)
                .create();
    }

    protected void selectAccount(final String account) {
        ConfirmAccountChangeFragment.confirmSyncAccount(account, getActivity());
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case AlertDialog.BUTTON_POSITIVE: {
                selectAccount(mAccounts[mSelectedAccount]);
                break;
            }
            default: {
                mSelectedAccount = which;
                break;
            }
        }
    }
}
