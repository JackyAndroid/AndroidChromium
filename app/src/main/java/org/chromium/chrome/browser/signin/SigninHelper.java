// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.google.android.gms.auth.AccountChangeEvent;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.invalidation.InvalidationServiceFactory;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.SigninManager.SignInFlowObserver;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.sync.SyncController;
import org.chromium.sync.AndroidSyncSettings;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A helper for tasks like re-signin.
 *
 * This should be merged into SigninManager when it is upstreamed.
 */
public class SigninHelper {

    private static final String TAG = "SigninHelper";

    private static final Object LOCK = new Object();

    private static final String ACCOUNTS_CHANGED_PREFS_KEY = "prefs_sync_accounts_changed";

    // Key to the shared pref that holds the new account's name if the currently signed
    // in account has been renamed.
    private static final String ACCOUNT_RENAMED_PREFS_KEY = "prefs_sync_account_renamed";

    // Key to the shared pref that holds the last read index of all the account changed
    // events of the current signed in account.
    private static final String ACCOUNT_RENAME_EVENT_INDEX_PREFS_KEY =
            "prefs_sync_account_rename_event_index";

    private static final String ANDROID_ACCOUNTS_PREFS_KEY = "prefs_sync_android_accounts";

    private static SigninHelper sInstance;

    /**
     * Retrieve more detailed information from account changed intents.
     */
    public static interface AccountChangeEventChecker {
        public List<String> getAccountChangeEvents(
                Context context, int index, String accountName);
    }

    /**
     * Uses GoogleAuthUtil.getAccountChangeEvents to detect if account
     * renaming has occured.
     */
    public static final class SystemAccountChangeEventChecker
            implements SigninHelper.AccountChangeEventChecker {
        @Override
        public List<String> getAccountChangeEvents(
                Context context, int index, String accountName) {
            try {
                List<AccountChangeEvent> list = GoogleAuthUtil.getAccountChangeEvents(
                        context, index, accountName);
                List<String> result = new ArrayList<String>(list.size());
                for (AccountChangeEvent e : list) {
                    if (e.getChangeType() == GoogleAuthUtil.CHANGE_TYPE_ACCOUNT_RENAMED_TO) {
                        result.add(e.getChangeData());
                    } else {
                        result.add(null);
                    }
                }
                return result;
            } catch (IOException e) {
                Log.w(TAG, "Failed to get change events", e);
            } catch (GoogleAuthException e) {
                Log.w(TAG, "Failed to get change events", e);
            }
            return new ArrayList<String>(0);
        }
    }


    @VisibleForTesting
    protected final Context mContext;

    private final ChromeSigninController mChromeSigninController;

    private final ProfileSyncService mProfileSyncService;

    private final SigninManager mSigninManager;

    private final AccountTrackerService mAccountTrackerService;

    private final OAuth2TokenService mOAuth2TokenService;

    private final SyncController mSyncController;

    public static SigninHelper get(Context context) {
        synchronized (LOCK) {
            if (sInstance == null) {
                sInstance = new SigninHelper(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    private SigninHelper(Context context) {
        mContext = context;
        mProfileSyncService = ProfileSyncService.get();
        mSigninManager = SigninManager.get(mContext);
        mAccountTrackerService = AccountTrackerService.get(mContext);
        mOAuth2TokenService = OAuth2TokenService.getForProfile(Profile.getLastUsedProfile());
        mSyncController = SyncController.get(context);
        mChromeSigninController = ChromeSigninController.get(mContext);
    }

    public void validateAccountSettings(boolean accountsChanged) {
        if (accountsChanged) {
            // Account details have changed so inform AccountTrackerService refresh itself.
            mAccountTrackerService.forceRefresh();
        }

        Account syncAccount = mChromeSigninController.getSignedInUser();
        if (syncAccount == null) {
            if (SigninManager.getAndroidSigninPromoExperimentGroup() < 0) return;

            // Never shows a signin promo if user has manually disconnected.
            String lastSyncAccountName =
                    PrefServiceBridge.getInstance().getSyncLastAccountName();
            if (lastSyncAccountName != null && !lastSyncAccountName.isEmpty()) return;

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            boolean hasKnownAccountKeys = sharedPrefs.contains(ANDROID_ACCOUNTS_PREFS_KEY);
            // Nothing to do if Android accounts are not changed and already known to Chrome.
            if (hasKnownAccountKeys && !accountsChanged) return;

            List<String> currentAccountNames =
                    AccountManagerHelper.get(mContext).getGoogleAccountNames();
            if (hasKnownAccountKeys) {
                ChromePreferenceManager chromePreferenceManager =
                        ChromePreferenceManager.getInstance(mContext);
                if (!chromePreferenceManager.getSigninPromoShown()) {
                    Set<String> lastKnownAccountNames = sharedPrefs.getStringSet(
                            ANDROID_ACCOUNTS_PREFS_KEY, new HashSet<String>());
                    Set<String> newAccountNames = new HashSet<String>(currentAccountNames);
                    newAccountNames.removeAll(lastKnownAccountNames);
                    if (!newAccountNames.isEmpty()) {
                        chromePreferenceManager.setShowSigninPromo(true);
                    }
                }
            }

            sharedPrefs.edit().putStringSet(
                    ANDROID_ACCOUNTS_PREFS_KEY, new HashSet<String>(currentAccountNames)).apply();
            return;
        }

        String renamedAccount = getNewSignedInAccountName(mContext);
        if (accountsChanged && renamedAccount != null) {
            handleAccountRename(ChromeSigninController.get(mContext).getSignedInAccountName(),
                    renamedAccount);
            return;
        }

        // Always check for account deleted.
        if (!accountExists(syncAccount)) {
            // It is possible that Chrome got to this point without account
            // rename notification. Let us signout before doing a rename.
            // updateAccountRenameData(mContext, new SystemAccountChangeEventChecker());
            AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    updateAccountRenameData(mContext, new SystemAccountChangeEventChecker());
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    String renamedAccount = getNewSignedInAccountName(mContext);
                    if (renamedAccount == null) {
                        mSigninManager.signOut(null, null);
                    } else {
                        validateAccountSettings(true);
                    }
                }
            };
            task.execute();
            return;
        }

        if (accountsChanged) {
            // Account details have changed so inform the token service that credentials
            // should now be available.
            mOAuth2TokenService.validateAccounts(mContext, false);
        }

        if (AndroidSyncSettings.isSyncEnabled(mContext)) {
            if (mProfileSyncService.hasSyncSetupCompleted()) {
                if (accountsChanged) {
                    // Nudge the syncer to ensure it does a full sync.
                    InvalidationServiceFactory.getForProfile(Profile.getLastUsedProfile())
                                        .requestSyncFromNativeChromeForAllTypes();
                }
            } else {
                // We should have set up sync but for some reason it's not enabled. Tell the sync
                // engine to start.
                mProfileSyncService.requestStart();
            }
        }
    }

    /**
     * Deal with account rename. The current approach is to sign out and then sign back in.
     * In the (near) future, we should just be clearing all the cached email address here
     * and have the UI re-fetch the emailing address based on the ID.
     */
    private void handleAccountRename(final String oldName, final String newName) {
        Log.i(TAG, "handleAccountRename from: " + oldName + " to " + newName);

        // TODO(acleung): I think most of the operations need to run on the main
        // thread. May be we should have a progress Dialog?

        // Before signing out, remember the current sync state and data types.
        final boolean isSyncWanted = AndroidSyncSettings.isChromeSyncEnabled(mContext);
        final Set<Integer> dataTypes = mProfileSyncService.getPreferredDataTypes();

        // TODO(acleung): Deal with passphrase or just prompt user to re-enter it?

        // Perform a sign-out with a callback to sign-in again.
        mSigninManager.signOut(null, new Runnable() {
            @Override
            public void run() {
                // Clear the shared perf only after signOut is successful.
                // If Chrome dies, we can try it again on next run.
                // Otherwise, if re-sign-in fails, we'll just leave chrome
                // signed-out.
                clearNewSignedInAccountName(mContext);
                performResignin(newName, isSyncWanted, dataTypes);
            }
        });
    }

    private void performResignin(String newName,
                                 final boolean isSyncWanted,
                                 final Set<Integer> dataTypes) {
        // This is the correct account now.
        final Account account = AccountManagerHelper.createAccountFromName(newName);

        mSigninManager.startSignIn(null, account, true, new SignInFlowObserver() {
            @Override
            public void onSigninComplete() {
                mProfileSyncService.setSetupInProgress(false);

                if (isSyncWanted) {
                    mSyncController.start();
                } else {
                    mSyncController.stop();
                }

                validateAccountSettings(true);
            }

            @Override
            public void onSigninCancelled() {
            }
        });
    }

    private boolean accountExists(Account account) {
        Account[] accounts = AccountManagerHelper.get(mContext).getGoogleAccounts();
        for (Account a : accounts) {
            if (a.equals(account)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the ACCOUNTS_CHANGED_PREFS_KEY to true.
     */
    public static void markAccountsChangedPref(Context context) {
        // The process may go away as soon as we return from onReceive but Android makes sure
        // that in-flight disk writes from apply() complete before changing component states.
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean(ACCOUNTS_CHANGED_PREFS_KEY, true).apply();
    }

    /**
     * @return The new account name of the current user. Null if it wasn't renamed.
     */
    public static String getNewSignedInAccountName(Context context) {
        return (PreferenceManager.getDefaultSharedPreferences(context)
                .getString(ACCOUNT_RENAMED_PREFS_KEY, null));
    }

    private static void clearNewSignedInAccountName(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(ACCOUNT_RENAMED_PREFS_KEY, null)
                .apply();
    }

    private static String getLastKnownAccountName(Context context) {
        // This is the last known name of the currently signed in user.
        // It can be:
        //  1. The signed in account name known to the ChromeSigninController.
        //  2. A pending newly choosen name that is differed from the one known to
        //     ChromeSigninController but is stored in ACCOUNT_RENAMED_PREFS_KEY.
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(
                ACCOUNT_RENAMED_PREFS_KEY, null);

        // If there is no pending rename, take the name known to ChromeSigninController.
        return name == null ? ChromeSigninController.get(context).getSignedInAccountName() : name;
    }

    public static void updateAccountRenameData(Context context) {
        updateAccountRenameData(context, new SystemAccountChangeEventChecker());
    }

    @VisibleForTesting
    public static void updateAccountRenameData(Context context, AccountChangeEventChecker checker) {
        String curName = getLastKnownAccountName(context);

        // Skip the search if there is no signed in account.
        if (curName == null) return;

        String newName = curName;

        // This is the last read index of all the account change event.
        int eventIndex = PreferenceManager.getDefaultSharedPreferences(context).getInt(
                ACCOUNT_RENAME_EVENT_INDEX_PREFS_KEY, 0);

        int newIndex = eventIndex;

        try {
            outerLoop: while (true) {
                List<String> nameChanges = checker.getAccountChangeEvents(context,
                        newIndex, newName);

                for (String name : nameChanges) {
                    if (name != null) {
                        // We have found a rename event of the current account.
                        // We need to check if that account is further renamed.
                        newName = name;
                        newIndex = 0; // Start from the beginning of the new account.
                        continue outerLoop;
                    }
                }

                // If there is no rename event pending. Update the last read index to avoid
                // re-reading them in the future.
                newIndex = nameChanges.size();
                break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error while looking for rename events.", e);
        }

        if (!curName.equals(newName)) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putString(ACCOUNT_RENAMED_PREFS_KEY, newName).apply();
        }

        if (newIndex != eventIndex) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putInt(ACCOUNT_RENAME_EVENT_INDEX_PREFS_KEY, newIndex).apply();
        }
    }

    public static boolean checkAndClearAccountsChangedPref(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(ACCOUNTS_CHANGED_PREFS_KEY, false)) {
            // Clear the value in prefs.
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putBoolean(ACCOUNTS_CHANGED_PREFS_KEY, false).apply();
            return true;
        } else {
            return false;
        }
    }
}
