// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;

/**
 * A class to display the dialogs the user may encounter when switching to/from or signing into/out
 * of a managed account.
 */
public class ConfirmManagedSyncDataDialog extends DialogFragment
        implements DialogInterface.OnClickListener {
    /**
     * A listener to allow the Dialog to report on the action taken. Either
     * {@link Listener#onConfirm} or {@link Listener#onCancel} will be called once.
     */
    public interface Listener {
        /**
         * The user has accepted the dialog.
         */
        public void onConfirm();

        /**
         * The user has cancelled the dialog either through a negative response or by dismissing it.
         */
        public void onCancel();
    }

    @VisibleForTesting
    public static final String CONFIRM_IMPORT_SYNC_DATA_DIALOG_TAG =
            "sync_managed_data_tag";

    private static final String KEY_TITLE = "title";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_POSITIVE_BUTTON = "positiveButton";
    private static final String KEY_NEGATIVE_BUTTON = "negativeButton";

    private Listener mListener;
    private boolean mListenerCalled;

    /**
     * Create the dialog to show when signing in to a managed account (either through sign in or
     * when switching accounts).
     * @param callback Callback for result.
     * @param fragmentManager FragmentManaged to display the dialog.
     * @param resources Resources to load the strings.
     * @param domain The domain of the managed account.
     */
    public static void showSignInToManagedAccountDialog(Listener callback,
            FragmentManager fragmentManager, Resources resources, String domain) {
        String title = resources.getString(R.string.sign_in_managed_account);
        String positive = resources.getString(R.string.policy_dialog_proceed);
        String negative = resources.getString(R.string.cancel);
        String desc = resources.getString(R.string.sign_in_managed_account_description, domain);
        showNewInstance(title, desc, positive, negative, fragmentManager, callback);
    }
    /**
     * Create the dialog to show when signing out of a managed account (but not switching from a
     * managed account).
     * @param callback Callback for result.
     * @param fragmentManager FragmentManaged to display the dialog.
     * @param resources Resources to load the strings.
     * @param domain The domain of the managed account.
     */
    public static void showSignOutFromManagedAccountDialog(Listener callback,
            FragmentManager fragmentManager, Resources resources, String domain) {
        String title = resources.getString(R.string.sign_out_managed_account);
        String positive = resources.getString(R.string.accept_and_sign_out);
        String negative = resources.getString(R.string.cancel);
        String desc = resources.getString(R.string.sign_out_managed_account_description, domain);
        showNewInstance(title, desc, positive, negative, fragmentManager, callback);
    }

    /**
     * Create the dialog to show when switching from a managed account.
     * @param callback Callback for result.
     * @param fragmentManager FragmentManaged to display the dialog.
     * @param resources Resources to load the strings.
     * @param domain The domain of the managed account.
     * @param oldAccount The old account email address.
     * @param newAccount The new account email address.
     */
    public static void showSwitchFromManagedAccountDialog(Listener callback,
            FragmentManager fragmentManager, Resources resources, String domain, String oldAccount,
            String newAccount) {
        String title = resources.getString(R.string.sign_out_managed_account);
        String positive = resources.getString(R.string.accept_and_switch_accounts);
        String negative = resources.getString(R.string.cancel);
        String desc = resources.getString(R.string.switch_from_managed_account_description,
                oldAccount, newAccount, domain);
        showNewInstance(title, desc, positive, negative, fragmentManager, callback);
    }

    private static void showNewInstance(String title, String description, String positiveButton,
            String negativeButton, FragmentManager fragmentManager, Listener callback) {
        ConfirmManagedSyncDataDialog confirmSync =
                newInstance(title, description, positiveButton, negativeButton);

        confirmSync.setListener(callback);
        confirmSync.show(fragmentManager, CONFIRM_IMPORT_SYNC_DATA_DIALOG_TAG);
    }

    private static ConfirmManagedSyncDataDialog newInstance(String title, String description,
            String positiveButton, String negativeButton) {
        ConfirmManagedSyncDataDialog fragment = new ConfirmManagedSyncDataDialog();
        Bundle args = new Bundle();
        args.putString(KEY_TITLE, title);
        args.putString(KEY_DESCRIPTION, description);
        args.putString(KEY_POSITIVE_BUTTON, positiveButton);
        args.putString(KEY_NEGATIVE_BUTTON, negativeButton);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String title = getArguments().getString(KEY_TITLE);
        String description = getArguments().getString(KEY_DESCRIPTION);
        String positiveButton = getArguments().getString(KEY_POSITIVE_BUTTON);
        String negativeButton = getArguments().getString(KEY_NEGATIVE_BUTTON);

        return new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(title)
                .setMessage(description)
                .setPositiveButton(positiveButton, this)
                .setNegativeButton(negativeButton, this)
                .create();
    }

    private void setListener(Listener listener) {
        assert mListener == null;
        mListener = listener;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            mListener.onConfirm();
        } else {
            assert which == AlertDialog.BUTTON_NEGATIVE;
            mListener.onCancel();
        }
        mListenerCalled = true;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (!mListenerCalled) {
            mListener.onCancel();
        }
    }
}

