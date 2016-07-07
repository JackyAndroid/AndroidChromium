// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;

import org.chromium.sync.signin.AccountManagerHelper;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/**
 * Temporary wrapper class until all callers have moved to use {@link OAuth2TokenService}.
 * TODO(nyquist) Remove this class.
 */
public final class AndroidProfileOAuth2TokenServiceHelper {

    private AndroidProfileOAuth2TokenServiceHelper() {}

    /**
     * Use {@link OAuth2TokenService#getOAuth2AccessToken} instead.
     */
    @Deprecated
    public static void getOAuth2AccessToken(Context context, @Nullable Activity activity,
            Account account, String scope, AccountManagerHelper.GetAuthTokenCallback callback) {
        OAuth2TokenService.getOAuth2AccessToken(context, activity, account, scope, callback);
    }

    /**
     * Use {@link OAuth2TokenService#invalidateOAuth2AuthToken} instead.
     */
    @Deprecated
    public static void invalidateOAuth2AuthToken(Context context, String accessToken) {
        OAuth2TokenService.invalidateOAuth2AuthToken(context, accessToken);
    }

    /**
     * Use {@link OAuth2TokenService#getOAuth2AccessTokenWithTimeout} instead.
     */
    @Deprecated
    public static String getOAuth2AccessTokenWithTimeout(Context context,
            @Nullable Activity activity, Account account, String scope,
            long timeout, TimeUnit unit) {
        return OAuth2TokenService.getOAuth2AccessTokenWithTimeout(
                context, activity, account, scope, timeout, unit);
    }
}
