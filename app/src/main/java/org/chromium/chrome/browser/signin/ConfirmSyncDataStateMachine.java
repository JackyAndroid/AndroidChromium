// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.annotation.SuppressLint;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.support.annotation.IntDef;
import android.text.TextUtils;

import org.chromium.base.Callback;
import org.chromium.base.Promise;
import org.chromium.chrome.browser.signin.ConfirmImportSyncDataDialog.ImportSyncType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class takes care of the various dialogs that must be shown when the user changes the
 * account they are syncing to (either directly, or by signing in to a new account). Most of the
 * complexity is due to many of the decisions getting answered through callbacks.
 *
 * This class progresses along the following state machine:
 *
 *       E----\  G--\
 *       ^    |  ^  |
 *       |    |  |  v
 * A->B->C->D-+->F->H
 *    |       |
 *    \-------/
 *
 * Where:
 * A - Start
 * B - Decision: progress to C if the user signed in previously to a different account, F otherwise.
 * C - Decision: progress to E if we are switching from a managed account, D otherwise.
 * D - Action: show Import Data Dialog.
 * E - Action: show Switching from Managed Account Dialog.
 * F - Decision: progress to G if we are switching to a managed account, H otherwise.
 * G - Action: show Switching to Managed Account Dialog.
 * H - End: perform {@link ConfirmImportSyncDataDialog.Listener#onConfirm} with the result of the
 *     Import Data Dialog, if displayed or true if switching from a managed account.
 *
 * At any dialog, the user can cancel the dialog and end the whole process (resulting in
 * {@link ConfirmImportSyncDataDialog.Listener#onCancel}).
 */
public class ConfirmSyncDataStateMachine
        implements ConfirmImportSyncDataDialog.Listener, ConfirmManagedSyncDataDialog.Listener {

    @IntDef({
        BEFORE_OLD_ACCOUNT_DIALOG, BEFORE_NEW_ACCOUNT_DIALOG,
        AFTER_NEW_ACCOUNT_DIALOG, DONE
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface State {}
    private static final int BEFORE_OLD_ACCOUNT_DIALOG = 0;  // Start of state B.
    private static final int BEFORE_NEW_ACCOUNT_DIALOG = 1;  // Start of state F.
    private static final int AFTER_NEW_ACCOUNT_DIALOG = 2;   // Start of state H.
    private static final int DONE = 4;

    private boolean mWipeData;
    @State private int mState = BEFORE_OLD_ACCOUNT_DIALOG;

    private final ConfirmImportSyncDataDialog.Listener mCallback;
    private final String mOldAccountName;
    private final String mNewAccountName;
    private final boolean mCurrentlyManaged;
    private final Promise<Boolean> mNewAccountManaged = new Promise<>();
    private final FragmentManager mFragmentManager;
    private final Context mContext;
    private final ImportSyncType mImportSyncType;

    /**
     * Run this state machine, displaying the appropriate dialogs.
     * @param callback One of the two functions of the {@link ConfirmImportSyncDataDialog.Listener}
     *         are guaranteed to be called.
     */
    public static void run(String oldAccountName, String newAccountName,
            ImportSyncType importSyncType, FragmentManager fragmentManager, Context context,
            ConfirmImportSyncDataDialog.Listener callback) {
        // Includes implicit not-null assertion.
        assert !newAccountName.equals("") : "New account name must be provided.";

        ConfirmSyncDataStateMachine stateMachine = new ConfirmSyncDataStateMachine(oldAccountName,
                newAccountName, importSyncType, fragmentManager, context, callback);
        stateMachine.progress();
    }

    /**
     * If any of the dialogs used by this state machine are shown, cancel them. If this state
     * machine is running and a dialog is being shown, the given
     * {@link ConfirmImportSyncDataDialog.Listener#onCancel())} is called.
     */
    public static void cancelAllDialogs(FragmentManager fragmentManager) {
        cancelDialog(fragmentManager,
                ConfirmImportSyncDataDialog.CONFIRM_IMPORT_SYNC_DATA_DIALOG_TAG);
        cancelDialog(fragmentManager,
                ConfirmManagedSyncDataDialog.CONFIRM_IMPORT_SYNC_DATA_DIALOG_TAG);
    }

    private static void cancelDialog(FragmentManager fragmentManager, String tag) {
        Fragment fragment = fragmentManager.findFragmentByTag(tag);

        if (fragment == null) return;
        DialogFragment dialogFragment = (DialogFragment) fragment;

        if (dialogFragment.getDialog() == null) return;
        dialogFragment.getDialog().cancel();
    }

    private ConfirmSyncDataStateMachine(String oldAccountName, String newAccountName,
            ImportSyncType importSyncType, FragmentManager fragmentManager, Context context,
            ConfirmImportSyncDataDialog.Listener callback) {
        mOldAccountName = oldAccountName;
        mNewAccountName = newAccountName;
        mImportSyncType = importSyncType;
        mFragmentManager = fragmentManager;
        mContext = context;
        mCallback = callback;

        mCurrentlyManaged = SigninManager.get(context).getManagementDomain() != null;

        // This check isn't needed right now, but can take a few seconds, so we kick it off early.
        SigninManager.isUserManaged(mNewAccountName, mNewAccountManaged.fulfillmentCallback());
    }

    /**
     * This will progress the state machine, by moving the state along and then by either calling
     * itself directly or creating a dialog. If the dialog is dismissed or answered negatively the
     * entire flow is over, if it is answered positively one of the onConfirm functions is called
     * and this function is called again.
     */
    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("SwitchIntDef")
    private void progress() {
        switch (mState) {
            case BEFORE_OLD_ACCOUNT_DIALOG:
                mState = BEFORE_NEW_ACCOUNT_DIALOG;

                if (TextUtils.isEmpty(mOldAccountName) || mNewAccountName.equals(mOldAccountName)) {
                    // If there is no old account or the user is just logging back into whatever
                    // they were previously logged in as, progress past the old account checks.
                    progress();
                } else if (mCurrentlyManaged
                        && mImportSyncType == ImportSyncType.SWITCHING_SYNC_ACCOUNTS) {
                    // We only care about the user's previous account being managed if they are
                    // switching accounts.

                    mWipeData = true;

                    // This will call back into onConfirm() on success.
                    ConfirmManagedSyncDataDialog.showSwitchFromManagedAccountDialog(this,
                            mFragmentManager, mContext.getResources(),
                            SigninManager.extractDomainName(mOldAccountName),
                            mOldAccountName, mNewAccountName);
                } else {
                    // This will call back into onConfirm(boolean wipeData) on success.
                    ConfirmImportSyncDataDialog.showNewInstance(mOldAccountName, mNewAccountName,
                            mImportSyncType, mFragmentManager, this);
                }

                break;
            case BEFORE_NEW_ACCOUNT_DIALOG:
                mState = AFTER_NEW_ACCOUNT_DIALOG;

                mNewAccountManaged.then(new Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean newAccountManaged) {
                        if (newAccountManaged) {
                            // Show 'logging into managed account' dialog
                            // This will call back into onConfirm on success.
                            ConfirmManagedSyncDataDialog.showSignInToManagedAccountDialog(
                                    ConfirmSyncDataStateMachine.this,
                                    mFragmentManager, mContext.getResources(),
                                    SigninManager.extractDomainName(mNewAccountName));
                        } else {
                            progress();
                        }
                    }
                });

                break;
            case AFTER_NEW_ACCOUNT_DIALOG:
                mState = DONE;
                mCallback.onConfirm(mWipeData);
                break;
            default:
                assert false : "Invalid state: " + mState;
        }
    }

    // ConfirmImportSyncDataDialog.Listener implementation.
    @Override
    public void onConfirm(boolean wipeData) {
        mWipeData = wipeData;
        progress();
    }

    // ConfirmManagedSyncDataDialog.Listener implementation.
    @Override
    public void onConfirm() {
        progress();
    }

    // ConfirmImportSyncDataDialog.Listener & ConfirmManagedSyncDataDialog.Listener implementation.
    @Override
    public void onCancel() {
        mState = DONE;
        mCallback.onCancel();
    }
}

