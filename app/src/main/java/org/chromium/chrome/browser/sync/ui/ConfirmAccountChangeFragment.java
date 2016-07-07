// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync.ui;

import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 * The fragment shown when the user was previously signed in, then disconnected their account,
 * and is now attempting to sign in to a new account. This dialog warns the user that they should
 * clear their browser data, or else their bookmarks etc from their old account will be merged with
 * the new account when they sign in.
 */
public class ConfirmAccountChangeFragment extends DialogFragment
        implements DialogInterface.OnClickListener {
    private static final String KEY_OLD_ACCOUNT_NAME = "lastAccountName";
    private static final String KEY_NEW_ACCOUNT_NAME = "newAccountName";

    public static void confirmSyncAccount(String syncAccountName, Activity activity) {
        String lastSyncAccountName = PrefServiceBridge.getInstance().getSyncLastAccountName();
        if (lastSyncAccountName != null && !lastSyncAccountName.isEmpty()
                && !lastSyncAccountName.equals(syncAccountName)) {
            ConfirmAccountChangeFragment dialog = newInstance(syncAccountName, lastSyncAccountName);
            dialog.show(activity.getFragmentManager(), null);
        } else {
            // Do not display dialog, just sign-in.
            signIn(activity, syncAccountName);
        }
    }

    public static ConfirmAccountChangeFragment newInstance(
            String syncAccountName, String lastSyncAccountName) {
        ConfirmAccountChangeFragment dialogFragment = new ConfirmAccountChangeFragment();
        Bundle args = new Bundle();
        args.putString(KEY_OLD_ACCOUNT_NAME, lastSyncAccountName);
        args.putString(KEY_NEW_ACCOUNT_NAME, syncAccountName);
        dialogFragment.setArguments(args);
        return dialogFragment;
    }

    private String mAccountName;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        String lastSyncAccountName = getArguments().getString(KEY_OLD_ACCOUNT_NAME);
        mAccountName = getArguments().getString(KEY_NEW_ACCOUNT_NAME);

        LayoutInflater inflater = activity.getLayoutInflater();
        View v = inflater.inflate(R.layout.confirm_sync_account_change_account, null);
        final TextView textView = (TextView) v.findViewById(R.id.confirmMessage);
        String message = activity.getString(
                R.string.confirm_account_change_dialog_message, lastSyncAccountName, mAccountName);

        // Show clear sync data dialog when the user clicks the "settings" link.
        SpannableString messageWithLink = SpanApplier.applySpans(message,
                new SpanInfo("<link>", "</link>", new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        showClearSyncDataDialogFragment();
                    }
                }));

        textView.setText(messageWithLink);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        return new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.confirm_account_change_dialog_title)
                .setPositiveButton(R.string.confirm_account_change_dialog_signin, this)
                .setNegativeButton(R.string.cancel, this).setView(v)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            signIn(getActivity(), mAccountName);
        }
    }

    private void showClearSyncDataDialogFragment() {
        ClearSyncDataDialogFragment dialogFragment = new ClearSyncDataDialogFragment();
        dialogFragment.show(getFragmentManager(), null);
        // Dismiss the confirmation dialog.
        dismiss();
    }

    private static void signIn(Activity activity, String accountName) {
        if (activity == null) return;
        Account account = AccountManagerHelper.get(activity).getAccountFromName(accountName);
        if (account == null) return;
        SigninManager.get(activity).signInToSelectedAccount(activity, account,
                SigninManager.SIGNIN_TYPE_INTERACTIVE, SigninManager.SIGNIN_SYNC_IMMEDIATELY,
                false, null);
    }
}
