// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Fragment;
import android.content.Context;
import android.text.TextUtils;

import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.signin.AccountAdder;
import org.chromium.sync.signin.AccountManagerHelper;

import java.util.List;

/**
 * Provides preferences glue for FirstRunActivity.
 */
public class FirstRunGlueImpl implements FirstRunGlue {
    @Override
    public boolean didAcceptTermsOfService(Context appContext) {
        return ToSAckedReceiver.checkAnyUserHasSeenToS(appContext)
                || PrefServiceBridge.getInstance().isFirstRunEulaAccepted();
    }

    @Override
    public boolean isNeverUploadCrashDump(Context appContext) {
        return PrivacyPreferencesManager.getInstance(appContext).isNeverUploadCrashDump();
    }

    @Override
    public void acceptTermsOfService(Context appContext, boolean allowCrashUpload) {
        PrivacyPreferencesManager.getInstance(appContext)
                .initCrashUploadPreference(allowCrashUpload);
        PrefServiceBridge.getInstance().setEulaAccepted();
    }

    @Override
    public boolean isDefaultAccountName(Context appContext, String accountName) {
        List<String> accountNames = AccountManagerHelper.get(appContext).getGoogleAccountNames();
        return accountNames != null
                && accountNames.size() > 0
                && TextUtils.equals(accountNames.get(0), accountName);
    }

    @Override
    public int numberOfAccounts(Context appContext) {
        List<String> accountNames = AccountManagerHelper.get(appContext).getGoogleAccountNames();
        return accountNames == null ? 0 : accountNames.size();
    }

    @Override
    public void openAccountAdder(Fragment fragment) {
        AccountAdder.getInstance().addAccount(fragment, AccountAdder.ADD_ACCOUNT_RESULT);
    }
}