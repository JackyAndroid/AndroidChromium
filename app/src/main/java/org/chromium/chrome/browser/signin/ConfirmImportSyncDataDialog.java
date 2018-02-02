// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ManagedPreferencesUtils;
import org.chromium.chrome.browser.widget.RadioButtonWithDescription;

import java.util.Arrays;
import java.util.List;

/**
 * A dialog that is displayed when the user switches the account they are syncing to. It gives the
 * option to merge the data of the two accounts or to keep them separate.
 */
public class ConfirmImportSyncDataDialog extends DialogFragment
        implements DialogInterface.OnClickListener {

    /**
     * Callback for completion of the dialog. Only one of {@link Listener#onConfirm} or
     * {@link Listener#onCancel} will be called and it will only be called once.
     */
    public interface Listener {
        /**
         * The user has completed the dialog using the positive button.
         * @param wipeData Whether the user requested that existing data should be wiped.
         */
        public void onConfirm(boolean wipeData);

        /**
         * The user dismisses the dialog through any means other than the positive button.
         */
        public void onCancel();
    }

    /**
     * The situation ConfirmImportSyncDataDialog is created for - whether the user had previously
     * been signed into another account, had signed out then signed into a different one, or
     * if they directly switched accounts. This changes the strings displayed.
     */
    public enum ImportSyncType { SWITCHING_SYNC_ACCOUNTS, PREVIOUS_DATA_FOUND }

    @VisibleForTesting
    public static final String CONFIRM_IMPORT_SYNC_DATA_DIALOG_TAG =
            "sync_account_switch_import_data_tag";

    private static final String KEY_OLD_ACCOUNT_NAME = "lastAccountName";
    private static final String KEY_NEW_ACCOUNT_NAME = "newAccountName";
    private static final String KEY_IMPORT_SYNC_TYPE = "importSyncType";

    private RadioButtonWithDescription mConfirmImportOption;
    private RadioButtonWithDescription mKeepSeparateOption;

    private Listener mListener;
    private boolean mListenerCalled;

    private static ConfirmImportSyncDataDialog newInstance(
            String oldAccountName, String newAccountName, ImportSyncType importSyncType) {

        ConfirmImportSyncDataDialog fragment = new ConfirmImportSyncDataDialog();
        Bundle args = new Bundle();
        args.putString(KEY_OLD_ACCOUNT_NAME, oldAccountName);
        args.putString(KEY_NEW_ACCOUNT_NAME, newAccountName);
        args.putSerializable(KEY_IMPORT_SYNC_TYPE, importSyncType);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Creates and shows a new instance of ConfirmImportSyncDataFragment, a dialog that gives the
     * user the option to merge data between the account they are attempting to sign in to and the
     * account they were previously signed into, or to keep the data separate.
     * @param oldAccountName  The previous sync account name.
     * @param newAccountName  The potential next sync account name.
     * @param importSyncType  The situation the dialog is created in - either when directly changing
     *                        the sync account or signing in after being signed out (this changes
     *                        displayed strings).
     * @param fragmentManager FragmentManager to attach the dialog to.
     * @param callback        Callback to be called if the user completes the dialog (as opposed to
     *                        hitting cancel).
     */
    public static void showNewInstance(String oldAccountName, String newAccountName,
            ImportSyncType importSyncType, FragmentManager fragmentManager, Listener callback) {
        ConfirmImportSyncDataDialog confirmSync =
                newInstance(oldAccountName, newAccountName, importSyncType);

        confirmSync.setListener(callback);
        confirmSync.show(fragmentManager, CONFIRM_IMPORT_SYNC_DATA_DIALOG_TAG);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // If the dialog is being recreated it won't have the listener set and so won't be
        // functional. Therefore we dismiss, and the user will need to open the dialog again.
        if (savedInstanceState != null) {
            dismiss();
        }
        String oldAccountName = getArguments().getString(KEY_OLD_ACCOUNT_NAME);
        String newAccountName = getArguments().getString(KEY_NEW_ACCOUNT_NAME);
        ImportSyncType importSyncType =
                (ImportSyncType) getArguments().getSerializable(KEY_IMPORT_SYNC_TYPE);

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.confirm_import_sync_data, null);

        TextView prompt = (TextView) v.findViewById(R.id.sync_import_data_prompt);

        if (importSyncType == ImportSyncType.SWITCHING_SYNC_ACCOUNTS) {
            prompt.setText(getActivity().getString(
                    R.string.sync_import_data_prompt_switching_accounts,
                    newAccountName, oldAccountName));
        } else {
            prompt.setText(getActivity().getString(
                    R.string.sync_import_data_prompt_existing_data,
                    newAccountName, oldAccountName));
        }

        mConfirmImportOption = (RadioButtonWithDescription)
                v.findViewById(R.id.sync_confirm_import_choice);
        mKeepSeparateOption = (RadioButtonWithDescription)
                v.findViewById(R.id.sync_keep_separate_choice);

        mConfirmImportOption.setDescriptionText(getActivity().getString(
                R.string.sync_import_existing_data_subtext, newAccountName));
        mKeepSeparateOption.setDescriptionText(getActivity().getString(
                (importSyncType == ImportSyncType.SWITCHING_SYNC_ACCOUNTS
                        ? R.string.sync_keep_existing_data_separate_subtext_switching_accounts
                        : R.string.sync_keep_existing_data_separate_subtext_existing_data),
                newAccountName, oldAccountName));

        List<RadioButtonWithDescription> radioGroup =
                Arrays.asList(mConfirmImportOption, mKeepSeparateOption);
        mConfirmImportOption.setRadioButtonGroup(radioGroup);
        mKeepSeparateOption.setRadioButtonGroup(radioGroup);

        // If the account is managed, disallow merging information.
        if (SigninManager.get(getActivity()).getManagementDomain() != null) {
            mKeepSeparateOption.setChecked(true);
            mConfirmImportOption.setOnClickListener(new OnClickListener(){
                @Override
                public void onClick(View v) {
                    ManagedPreferencesUtils.showManagedByAdministratorToast(getActivity());
                }
            });
        } else {
            if (importSyncType == ImportSyncType.SWITCHING_SYNC_ACCOUNTS) {
                mKeepSeparateOption.setChecked(true);
            } else {
                mConfirmImportOption.setChecked(true);
            }
        }

        return new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.sync_import_data_title)
                .setPositiveButton(R.string.continue_button, this)
                .setNegativeButton(R.string.cancel, this)
                .setView(v)
                .create();
    }

    private void setListener(Listener listener) {
        assert mListener == null;
        mListener = listener;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (mListener == null) return;

        if (which == AlertDialog.BUTTON_POSITIVE) {
            assert mConfirmImportOption.isChecked() ^ mKeepSeparateOption.isChecked();

            RecordUserAction.record(mKeepSeparateOption.isChecked()
                    ? "Signin_ImportDataPrompt_DontImport"
                    : "Signin_ImportDataPrompt_ImportData");
            mListener.onConfirm(mKeepSeparateOption.isChecked());
        } else {
            assert which == AlertDialog.BUTTON_NEGATIVE;

            RecordUserAction.record("Signin_ImportDataPrompt_Cancel");
            mListener.onCancel();
        }
        mListenerCalled = true;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mListener != null && !mListenerCalled) {
            mListener.onCancel();
        }
    }
}

