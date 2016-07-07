// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import org.chromium.chrome.browser.cookies.CookiesFetcher;
import org.chromium.content.browser.crypto.CipherFactory;
import org.chromium.content.browser.crypto.CipherFactory.CipherDataObserver;

/**
 *  {@link DocumentActivity} for incognito tabs.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class IncognitoDocumentActivity extends DocumentActivity {
    /**
     * Responsible for observing when cipher data generation is complete and saving
     * the new cipher data in the CipherKeyActivity.
     */
    private static class CipherKeySaver implements CipherDataObserver {
        private final Context mContext;

        public CipherKeySaver(Context context) {
            mContext = context;
            CipherFactory.getInstance().addCipherDataObserver(this);
        }

        @Override
        public void onCipherDataGenerated() {
            mContext.startActivity(
                    CipherKeyActivity.createIntent(mContext, null, null));
            CipherFactory.getInstance().removeCipherDataObserver(this);
        }
    }

    private static CipherKeySaver sCipherKeySaver;

    private static void maybeCreateCipherKeySaver(Context context) {
        if (sCipherKeySaver == null && !CipherFactory.getInstance().hasCipher()) {
            sCipherKeySaver = new CipherKeySaver(context);
        }
    }

    @Override
    protected boolean isIncognito() {
        return true;
    }

    @Override
    public void preInflationStartup() {
        CipherFactory.getInstance().restoreFromBundle(getSavedInstanceState());
        maybeCreateCipherKeySaver(this);
        super.preInflationStartup();
    }

    @Override
    public void onResume() {
        super.onResume();
        IncognitoNotificationManager.updateIncognitoNotification(
                ChromeLauncherActivity.getRemoveAllIncognitoTabsIntent(this));
    }

    @Override
    public void onResumeWithNative() {
        super.onResumeWithNative();
        CookiesFetcher.restoreCookies(this);
    }

    @Override
    public void onPauseWithNative() {
        CookiesFetcher.persistCookies(this);
        super.onPauseWithNative();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        CipherFactory.getInstance().saveToBundle(outState);

        // Save out the URL that was originally used to spawn this activity because we don't pass it
        // in through the Intent.
        String initialUrl = determineInitialUrl(determineTabId());
        outState.putString(KEY_INITIAL_URL, initialUrl);
    }

    @Override
    protected String determineInitialUrl(int tabId) {
        // Check if the URL was saved in the Bundle.
        if (getSavedInstanceState() != null) {
            String initialUrl = getSavedInstanceState().getString(KEY_INITIAL_URL);
            if (initialUrl != null) return initialUrl;
        }

        return super.determineInitialUrl(tabId);
    }
}
