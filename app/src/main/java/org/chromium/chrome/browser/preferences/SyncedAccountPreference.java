// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.accounts.Account;
import android.content.Context;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;

import org.chromium.chrome.R;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.components.signin.ChromeSigninController;
import org.chromium.components.sync.AndroidSyncSettings;

/**
 * A preference that displays the account currently being synced and allows the user to choose a new
 * account to use for syncing. The values used are the account names.
 */
public class SyncedAccountPreference extends ListPreference {
    private static final String TAG = "SyncedAccountPreference";

    /**
     * Constructor for inflating from XML
     */
    public SyncedAccountPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTitle(context.getResources().getString(R.string.sync_to_account_header));
        updateAccountsList();
    }

    /**
     * Updates the list of accounts to those currently signed in and sets the display to the
     * current sync account.
     */
    public void update() {
        updateAccountsList();
    }

    private void updateAccountsList() {
        boolean syncEnabled = AndroidSyncSettings.isSyncEnabled(getContext());
        if (!syncEnabled) {
            setEnabled(false);
            // Don't return at this point, we still want the preference to display the currently
            // signed in account
        }

        Account[] accounts = AccountManagerHelper.get(getContext()).getGoogleAccounts();
        String[] accountNames = new String[accounts.length];
        String[] accountValues = new String[accounts.length];

        String signedInAccountName =
                ChromeSigninController.get(getContext()).getSignedInAccountName();
        String signedInSettingsKey = "";

        for (int i = 0; i < accounts.length; ++i) {
            Account account = accounts[i];
            accountNames[i] = account.name;
            accountValues[i] = account.name;
            boolean isPrimaryAccount = TextUtils.equals(account.name, signedInAccountName);
            if (isPrimaryAccount) {
                signedInSettingsKey = accountValues[i];
            }
        }

        setEntries(accountNames);
        setEntryValues(accountValues);
        setValue(signedInSettingsKey);
        setSummary(signedInAccountName);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
    }
}
