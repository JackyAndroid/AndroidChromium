// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.childaccounts;

import android.accounts.Account;
import android.content.Context;

import org.chromium.base.Callback;
import org.chromium.base.ThreadUtils;
import org.chromium.sync.signin.AccountManagerHelper;

/**
 * This class serves as a simple interface for querying the child account information.
 * It has two methods namely, checkHasChildAccount(...) which is asynchronous and queries the
 * system directly for the information and the synchronous isChildAccount() which asks the native
 * side assuming it has been set correctly already.
 *
 * The former method is used by ForcedSigninProcessor and FirstRunFlowSequencer to detect child
 * accounts since the native side is only activated on signing in.
 * Once signed in by the ForcedSigninProcessor, the ChildAccountInfoFetcher will notify the native
 * side and also takes responsibility for monitoring changes and taking a suitable action.
 */
public class ChildAccountService {
    private ChildAccountService() {
        // Only for static usage.
    }

    /**
     * Checks for the presence of child accounts on the device.
     *
     * @param callback A callback which will be called with the result.
     */
    public static void checkHasChildAccount(Context context, final Callback<Boolean> callback) {
        ThreadUtils.assertOnUiThread();
        if (!nativeIsChildAccountDetectionEnabled()) {
            callback.onResult(false);
            return;
        }
        final AccountManagerHelper helper = AccountManagerHelper.get(context);
        helper.getGoogleAccounts(new Callback<Account[]>() {
            @Override
            public void onResult(Account[] accounts) {
                if (accounts.length != 1) {
                    callback.onResult(false);
                } else {
                    helper.checkChildAccount(accounts[0], callback);
                }
            }
        });
    }

    /**
     * Returns the previously determined value of whether there is a child account on the device.
     * Should only be called after the native library and profile have been loaded.
     *
     * @return The previously determined value of whether there is a child account on the device.
     */
    public static boolean isChildAccount() {
        return nativeIsChildAccount();
    }

    private static native boolean nativeIsChildAccount();

    /**
     * If this returns false, Chrome will assume there are no child accounts on the device,
     * and no further checks will be made, which has the effect of a kill switch.
     */
    private static native boolean nativeIsChildAccountDetectionEnabled();
}
