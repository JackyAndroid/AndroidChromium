// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.services;

import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.signin.SigninHelper;
import org.chromium.content.browser.BrowserStartupController;
import org.chromium.content.browser.BrowserStartupController.StartupCallback;

/**
 * This receiver is notified when accounts are added, accounts are removed, or
 * an account's credentials (saved password, etc) are changed.
 */
public class AccountsChangedReceiver extends BroadcastReceiver {
    private static final String TAG = "AccountsChangeReceiver";

    /**
     * Receives a callback whenever {@link AccountManager#LOGIN_ACCOUNTS_CHANGED_ACTION} is
     * broadcasted. Use {@link #addObserver} and {@link #removeObserver} to update registrations.
     *
     * The callback will only ever be called after the browser process has been initialized.
     */
    public interface AccountsChangedObserver {
        /**
         * Called when {@link AccountManager#LOGIN_ACCOUNTS_CHANGED_ACTION} is triggered.
         * @param context the application context.
         * @param intent the broadcasted intent.
         */
        void onAccountsChanged(Context context, Intent intent);
    }

    private static ObserverList<AccountsChangedObserver> sObservers = new ObserverList<>();

    /**
     * Adds an observer to the {@link AccountManager#LOGIN_ACCOUNTS_CHANGED_ACTION} broadcasts.
     * @param observer the observer to add.
     */
    public static void addObserver(AccountsChangedObserver observer) {
        sObservers.addObserver(observer);
    }

    /**
     * Removes an observer from the {@link AccountManager#LOGIN_ACCOUNTS_CHANGED_ACTION} broadcasts.
     * @param observer the observer to add.
     */
    public static void removeObserver(AccountsChangedObserver observer) {
        sObservers.removeObserver(observer);
    }

    @SuppressFBWarnings("DM_EXIT")
    @Override
    public void onReceive(Context context, final Intent intent) {
        final Context appContext = context.getApplicationContext();
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                SigninHelper.updateAccountRenameData(appContext);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                continueHandleAccountChangeIfNeeded(appContext, intent);
            }
        };
        task.execute();
    }

    private void continueHandleAccountChangeIfNeeded(final Context context, final Intent intent) {
        if (AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION.equals(intent.getAction())) {
            boolean isChromeVisible = ApplicationStatus.hasVisibleActivities();
            if (isChromeVisible) {
                StartupCallback startupCallback = new StartupCallback() {
                    @Override
                    public void onSuccess(boolean alreadyStarted) {
                        ThreadUtils.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                SigninHelper.get(context.getApplicationContext())
                                        .validateAccountSettings(true);
                            }
                        });
                    }
                    @Override
                    public void onFailure() {
                        // Startup failed. So notify SigninHelper of changed accounts via
                        // shared prefs.
                        SigninHelper.markAccountsChangedPref(context);
                    }
                };

                try {
                    ((ChromeApplication) context.getApplicationContext())
                            .startChromeBrowserProcessesAsync(startupCallback);
                } catch (ProcessInitException e) {
                    Log.e(TAG, "Unable to load native library.", e);
                    ChromeApplication.reportStartupErrorAndExit(e);
                }
            } else {
                // Notify SigninHelper of changed accounts (via shared prefs).
                SigninHelper.markAccountsChangedPref(context);
            }
            notifyObserver(context, intent);
        }
    }

    private static void notifyObserver(final Context context, final Intent intent) {
        StartupCallback chainedObserverCallback = new StartupCallback() {
            @Override
            public void onSuccess(boolean alreadyStarted) {
                for (AccountsChangedObserver observer : sObservers) {
                    observer.onAccountsChanged(context, intent);
                }
            }

            @Override
            public void onFailure() {
                // Startup failed, so ignore call.
            }
        };
        // If the browser process has already been loaded, a task will be posted immediately to
        // call the |chainedObserverCallback| passed in as a parameter.
        BrowserStartupController.get(context, LibraryProcessType.PROCESS_BROWSER)
                .addStartupCompletedObserver(chainedObserverCallback);
    }
}
