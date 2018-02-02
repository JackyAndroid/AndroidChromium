// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Activity;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;

/**
 * Triggers Android's account adding dialog.
 */
public class AccountAdder {

    public static final int ADD_ACCOUNT_RESULT = 102;
    private static final String EXTRA_ACCOUNT_TYPES = "account_types";
    private static final String EXTRA_VALUE_GOOGLE_ACCOUNTS = "com.google";

    private static AccountAdder sInstance = new AccountAdder();

    protected AccountAdder() {}

    /**
     * Returns the singleton instance of AccountAdder.
     */
    public static AccountAdder getInstance() {
        return sInstance;
    }

    /**
     * Overrides the singleton instance of AccountAdder with the specified instance, for use in
     * tests.
     */
    @VisibleForTesting
    public static void overrideAccountAdderForTests(AccountAdder adder) {
        sInstance = adder;
    }

    private static Intent createAddGoogleAccountIntent() {
        Intent createAccountIntent = new Intent(Settings.ACTION_ADD_ACCOUNT);
        // NOTE: the documentation says Settings.EXTRA_AUTHORITIES should be used,
        // but it doesn't work.
        createAccountIntent.putExtra(
                EXTRA_ACCOUNT_TYPES, new String[]{EXTRA_VALUE_GOOGLE_ACCOUNTS});
        return createAccountIntent;
    }

    private void onOpenAddGoogleAccountPageFailed(final Activity activity, final int result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        builder.setMessage(R.string.signin_open_add_google_account_page_failed);
        builder.setPositiveButton(
                R.string.signin_open_settings_accounts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Open Accounts page in device's Settings app.
                        Intent openSettingsAccounts = new Intent(Settings.ACTION_SYNC_SETTINGS);
                        if (openSettingsAccounts.resolveActivity(activity.getPackageManager())
                                != null) {
                            activity.startActivityForResult(openSettingsAccounts, result);
                        }
                    }
                });
        builder.create().show();
    }

    /**
     * Triggers Android's account adding dialog from a fragment.
     * @param fragment A fragment
     * @param result An intent result code
     */
    public void addAccount(Fragment fragment, int result) {
        Intent addGoogleAccount = createAddGoogleAccountIntent();
        if (addGoogleAccount.resolveActivity(fragment.getActivity().getPackageManager()) != null) {
            fragment.startActivityForResult(addGoogleAccount, result);
        } else {
            onOpenAddGoogleAccountPageFailed(fragment.getActivity(), result);
        }
    }

    /**
     * Triggers Android's account adding dialog from an activity.
     * @param activity An activity
     * @param result An intent result code
     */
    public void addAccount(Activity activity, int result) {
        Intent addGoogleAccount = createAddGoogleAccountIntent();
        if (addGoogleAccount.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivityForResult(addGoogleAccount, result);
        } else {
            onOpenAddGoogleAccountPageFailed(activity, result);
        }
    }
}
