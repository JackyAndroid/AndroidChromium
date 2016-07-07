// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * Java instance for the native OAuth2TokenService.
 * <p/>
 * This class forwards calls to request or invalidate access tokens made by native code to
 * AccountManagerHelper and forwards callbacks to native code.
 * <p/>
 */
public final class OAuth2TokenService
        implements AccountTrackerService.OnSystemAccountsSeededListener {
    private static final String TAG = "OAuth2TokenService";

    @VisibleForTesting
    public static final String STORED_ACCOUNTS_KEY = "google.services.stored_accounts";

    /**
     * Classes that want to listen for refresh token availability should
     * implement this interface and register with {@link #addObserver}.
     */
    public interface OAuth2TokenServiceObserver {
        void onRefreshTokenAvailable(Account account);
        void onRefreshTokenRevoked(Account account);
        void onRefreshTokensLoaded();
    }

    private static final String OAUTH2_SCOPE_PREFIX = "oauth2:";

    private Context mPendingValidationContext = null;
    private boolean mPendingValidationForceNotifications = false;

    private final long mNativeOAuth2TokenServiceDelegateAndroid;
    private final ObserverList<OAuth2TokenServiceObserver> mObservers;

    private OAuth2TokenService(Context context, long nativeOAuth2Service) {
        mNativeOAuth2TokenServiceDelegateAndroid = nativeOAuth2Service;
        mObservers = new ObserverList<OAuth2TokenServiceObserver>();
        AccountTrackerService.get(context).addSystemAccountsSeededListener(this);
    }

    public static OAuth2TokenService getForProfile(Profile profile) {
        ThreadUtils.assertOnUiThread();
        return (OAuth2TokenService) nativeGetForProfile(profile);
    }

    @CalledByNative
    private static OAuth2TokenService create(Context context, long nativeOAuth2Service) {
        ThreadUtils.assertOnUiThread();
        return new OAuth2TokenService(context, nativeOAuth2Service);
    }

    @VisibleForTesting
    public void addObserver(OAuth2TokenServiceObserver observer) {
        ThreadUtils.assertOnUiThread();
        mObservers.addObserver(observer);
    }

    @VisibleForTesting
    public void removeObserver(OAuth2TokenServiceObserver observer) {
        ThreadUtils.assertOnUiThread();
        mObservers.removeObserver(observer);
    }

    private static Account getAccountOrNullFromUsername(Context context, String username) {
        if (username == null) {
            Log.e(TAG, "Username is null");
            return null;
        }

        AccountManagerHelper accountManagerHelper = AccountManagerHelper.get(context);
        Account account = accountManagerHelper.getAccountFromName(username);
        if (account == null) {
            Log.e(TAG, "Account not found for provided username.");
            return null;
        }
        return account;
    }

    /**
     * Called by native to list the activite account names in the OS.
     */
    @VisibleForTesting
    @CalledByNative
    public static String[] getSystemAccountNames(Context context) {
        AccountManagerHelper accountManagerHelper = AccountManagerHelper.get(context);
        java.util.List<String> accountNames = accountManagerHelper.getGoogleAccountNames();
        return accountNames.toArray(new String[accountNames.size()]);
    }

    /**
     * Called by native to list the accounts Id with OAuth2 refresh tokens.
     * This can differ from getSystemAccountNames as the user add/remove accounts
     * from the OS. validateAccounts should be called to keep these two
     * in sync.
     */
    @CalledByNative
    public static String[] getAccounts(Context context) {
        return getStoredAccounts(context);
    }

    /**
     * Called by native to retrieve OAuth2 tokens.
     *
     * @param username The native username (full address).
     * @param scope The scope to get an auth token for (without Android-style 'oauth2:' prefix).
     * @param nativeCallback The pointer to the native callback that should be run upon completion.
     */
    @CalledByNative
    public static void getOAuth2AuthToken(
            Context context, String username, String scope, final long nativeCallback) {
        Account account = getAccountOrNullFromUsername(context, username);
        if (account == null) {
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    nativeOAuth2TokenFetched(null, false, nativeCallback);
                }
            });
            return;
        }
        String oauth2Scope = OAUTH2_SCOPE_PREFIX + scope;

        AccountManagerHelper accountManagerHelper = AccountManagerHelper.get(context);
        accountManagerHelper.getAuthToken(
                account, oauth2Scope, new AccountManagerHelper.GetAuthTokenCallback() {
                    @Override
                    public void tokenAvailable(String token) {
                        nativeOAuth2TokenFetched(token, false, nativeCallback);
                    }

                    @Override
                    public void tokenUnavailable(boolean isTransientError) {
                        nativeOAuth2TokenFetched(null, isTransientError, nativeCallback);
                    }
                });
    }

    /**
     * Call this method to retrieve an OAuth2 access token for the given account and scope.
     *
     * @param activity the current activity. May be null.
     * @param account the account to get the access token for.
     * @param scope The scope to get an auth token for (without Android-style 'oauth2:' prefix).
     * @param callback called on successful and unsuccessful fetching of auth token.
     */
    public static void getOAuth2AccessToken(
            Context context, @Nullable Activity activity, Account account, String scope,
            AccountManagerHelper.GetAuthTokenCallback callback) {
        String oauth2Scope = OAUTH2_SCOPE_PREFIX + scope;
        AccountManagerHelper.get(context).getAuthToken(account, oauth2Scope, callback);
    }

    /**
     * Call this method to retrieve an OAuth2 access token for the given account and scope. This
     * method times out after the specified timeout, and will return null if that happens.
     *
     * Given that this is a blocking method call, this should never be called from the UI thread.
     *
     * @param activity the current activity. May be null.
     * @param account the account to get the access token for.
     * @param scope The scope to get an auth token for (without Android-style 'oauth2:' prefix).
     * @param timeout the timeout.
     * @param unit the unit for |timeout|.
     */
    @VisibleForTesting
    public static String getOAuth2AccessTokenWithTimeout(
            Context context, @Nullable Activity activity, Account account, String scope,
            long timeout, TimeUnit unit) {
        assert !ThreadUtils.runningOnUiThread();
        final AtomicReference<String> result = new AtomicReference<String>();
        final Semaphore semaphore = new Semaphore(0);
        getOAuth2AccessToken(
                context, activity, account, scope, new AccountManagerHelper.GetAuthTokenCallback() {
                    @Override
                    public void tokenAvailable(String token) {
                        result.set(token);
                        semaphore.release();
                    }

                    @Override
                    public void tokenUnavailable(boolean isTransientError) {
                        result.set(null);
                        semaphore.release();
                    }
                });
        try {
            if (semaphore.tryAcquire(timeout, unit)) {
                return result.get();
            } else {
                Log.d(TAG, "Failed to retrieve auth token within timeout ("
                        + timeout + " + " + unit.name() + ")");
                return null;
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Got interrupted while waiting for auth token");
            return null;
        }
    }

    /**
     * Called by native to check wether the account has an OAuth2 refresh token.
     */
    @CalledByNative
    public static boolean hasOAuth2RefreshToken(Context context, String accountName) {
        return AccountManagerHelper.get(context).hasAccountForName(accountName);
    }

    /**
    * Called by native to invalidate an OAuth2 token.
    */
    @CalledByNative
    public static void invalidateOAuth2AuthToken(Context context, String accessToken) {
        if (accessToken != null) {
            AccountManagerHelper.get(context).invalidateAuthToken(accessToken);
        }
    }

    /**
    * Continue pending accounts validation after system accounts have been seeded into
    * AccountTrackerService.
    */
    @Override
    public void onSystemAccountsSeedingComplete() {
        if (mPendingValidationContext != null) {
            validateAccountsWithSignedInAccountName(
                    mPendingValidationContext, mPendingValidationForceNotifications);
            mPendingValidationContext = null;
            mPendingValidationForceNotifications = false;
        }
    }

    /**
    * Clear pending accounts validation when system accounts in AccountTrackerService were
    * refreshed.
    */
    @Override
    public void onSystemAccountsForceRefreshed() {
        mPendingValidationContext = null;
        mPendingValidationForceNotifications = false;
    }

    @CalledByNative
    public void validateAccounts(Context context, boolean forceNotifications) {
        ThreadUtils.assertOnUiThread();
        if (!AccountTrackerService.get(context).isSystemAccountsSeeded()) {
            mPendingValidationContext = context;
            mPendingValidationForceNotifications = forceNotifications;
            return;
        }

        validateAccountsWithSignedInAccountName(context, forceNotifications);
    }

    private void validateAccountsWithSignedInAccountName(
            Context context, boolean forceNotifications) {
        String currentlySignedInAccount =
                ChromeSigninController.get(context).getSignedInAccountName();
        nativeValidateAccounts(mNativeOAuth2TokenServiceDelegateAndroid, currentlySignedInAccount,
                forceNotifications);
    }

    /**
     * Triggers a notification to all observers of the native and Java instance of the
     * OAuth2TokenService that a refresh token is now available. This may cause observers to retry
     * operations that require authentication.
     */
    @VisibleForTesting
    public void fireRefreshTokenAvailable(Account account) {
        ThreadUtils.assertOnUiThread();
        assert account != null;
        nativeFireRefreshTokenAvailableFromJava(
                mNativeOAuth2TokenServiceDelegateAndroid, account.name);
    }

    @CalledByNative
    private void notifyRefreshTokenAvailable(String accountName) {
        assert accountName != null;
        Account account = AccountManagerHelper.createAccountFromName(accountName);
        for (OAuth2TokenServiceObserver observer : mObservers) {
            observer.onRefreshTokenAvailable(account);
        }
    }

    /**
     * Triggers a notification to all observers of the native and Java instance of the
     * OAuth2TokenService that a refresh token is now revoked.
     */
    @VisibleForTesting
    public void fireRefreshTokenRevoked(Account account) {
        ThreadUtils.assertOnUiThread();
        assert account != null;
        nativeFireRefreshTokenRevokedFromJava(
                mNativeOAuth2TokenServiceDelegateAndroid, account.name);
    }

    @CalledByNative
    public void notifyRefreshTokenRevoked(String accountName) {
        assert accountName != null;
        Account account = AccountManagerHelper.createAccountFromName(accountName);
        for (OAuth2TokenServiceObserver observer : mObservers) {
            observer.onRefreshTokenRevoked(account);
        }
    }

    /**
     * Triggers a notification to all observers of the native and Java instance of the
     * OAuth2TokenService that all refresh tokens now have been loaded.
     */
    @VisibleForTesting
    public void fireRefreshTokensLoaded() {
        ThreadUtils.assertOnUiThread();
        nativeFireRefreshTokensLoadedFromJava(mNativeOAuth2TokenServiceDelegateAndroid);
    }

    @CalledByNative
    public void notifyRefreshTokensLoaded() {
        for (OAuth2TokenServiceObserver observer : mObservers) {
            observer.onRefreshTokensLoaded();
        }
    }

    private static String[] getStoredAccounts(Context context) {
        Set<String> accounts =
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getStringSet(STORED_ACCOUNTS_KEY, null);
        return accounts == null ? new String[]{} : accounts.toArray(new String[accounts.size()]);
    }

    @CalledByNative
    private static void saveStoredAccounts(Context context, String[] accounts) {
        Set<String> set = new HashSet<String>(Arrays.asList(accounts));
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putStringSet(STORED_ACCOUNTS_KEY, set).apply();
    }

    private static native Object nativeGetForProfile(Profile profile);
    private static native void nativeOAuth2TokenFetched(
            String authToken, boolean isTransientError, long nativeCallback);
    private native void nativeValidateAccounts(long nativeOAuth2TokenServiceDelegateAndroid,
            String currentlySignedInAccount, boolean forceNotifications);
    private native void nativeFireRefreshTokenAvailableFromJava(
            long nativeOAuth2TokenServiceDelegateAndroid, String accountName);
    private native void nativeFireRefreshTokenRevokedFromJava(
            long nativeOAuth2TokenServiceDelegateAndroid, String accountName);
    private native void nativeFireRefreshTokensLoadedFromJava(
            long nativeOAuth2TokenServiceDelegateAndroid);
}
