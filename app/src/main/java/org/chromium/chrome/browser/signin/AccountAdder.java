// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.provider.Settings;

import org.chromium.base.VisibleForTesting;

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

    private static Intent createAddAccountIntent() {
        Intent createAccountIntent = new Intent(Settings.ACTION_ADD_ACCOUNT);
        // NOTE: the documentation says Settings.EXTRA_AUTHORITIES should be used,
        // but it doesn't work.
        createAccountIntent.putExtra(
                EXTRA_ACCOUNT_TYPES, new String[]{EXTRA_VALUE_GOOGLE_ACCOUNTS});
        return createAccountIntent;
    }

    /**
     * Triggers Android's account adding dialog from a fragment.
     * @param fragment A fragment
     * @param result An intent result code
     */
    public void addAccount(Fragment fragment, int result) {
        fragment.startActivityForResult(createAddAccountIntent(), result);
    }

    /**
     * Triggers Android's account adding dialog from an activity.
     * @param activity An activity
     * @param result An intent result code
     */
    public void addAccount(Activity activity, int result) {
        activity.startActivityForResult(createAddAccountIntent(), result);
    }
}
