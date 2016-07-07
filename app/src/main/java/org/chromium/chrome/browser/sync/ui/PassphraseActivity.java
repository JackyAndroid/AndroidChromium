// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync.ui;

import android.accounts.Account;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.sync.SyncController;
import org.chromium.sync.signin.ChromeSigninController;

/**
 * This activity is used for requesting a sync passphrase from the user. Typically,
 * this will be the target of an Android notification.
 */
public class PassphraseActivity extends FragmentActivity implements
        PassphraseDialogFragment.Listener,
        FragmentManager.OnBackStackChangedListener {

    public static final String FRAGMENT_PASSPHRASE = "passphrase_fragment";
    public static final String FRAGMENT_SPINNER = "spinner_fragment";
    private static final String TAG = "PassphraseActivity";

    private ProfileSyncService.SyncStateChangedListener mSyncStateChangedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The Chrome browser process must be started here because this Activity
        // may be started explicitly from Android notifications.
        // During a normal user flow the ChromeTabbedActivity would start the Chrome browser
        // process and this wouldn't be necessary.
        try {
            ((ChromeApplication) getApplication())
                    .startBrowserProcessesAndLoadLibrariesSync(true);
        } catch (ProcessInitException e) {
            Log.e(TAG, "Failed to start browser process.", e);
            ChromeApplication.reportStartupErrorAndExit(e);
        }
        getFragmentManager().addOnBackStackChangedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Account account = ChromeSigninController.get(this).getSignedInUser();
        if (account == null) {
            finish();
            return;
        }

        if (!isShowingDialog(FRAGMENT_PASSPHRASE)) {
            if (ProfileSyncService.get().isBackendInitialized()) {
                displayPassphraseDialog();
            } else {
                addSyncStateChangedListener();
                displaySpinnerDialog();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Make sure we don't receive callbacks while in the background.
        // See http://crbug.com/469890.
        removeSyncStateChangedListener();
    }

    private void addSyncStateChangedListener() {
        if (mSyncStateChangedListener != null) {
            return;
        }
        mSyncStateChangedListener = new ProfileSyncService.SyncStateChangedListener() {
            @Override
            public void syncStateChanged() {
                if (ProfileSyncService.get().isBackendInitialized()) {
                    removeSyncStateChangedListener();
                    displayPassphraseDialog();
                }
            }
        };
        ProfileSyncService.get().addSyncStateChangedListener(mSyncStateChangedListener);
    }

    private void removeSyncStateChangedListener() {
        if (mSyncStateChangedListener != null) {
            ProfileSyncService.get().removeSyncStateChangedListener(mSyncStateChangedListener);
            mSyncStateChangedListener = null;
        }
    }

    private boolean isShowingDialog(String tag) {
        return getFragmentManager().findFragmentByTag(tag) != null;
    }

    private void displayPassphraseDialog() {
        assert ProfileSyncService.get().isBackendInitialized();
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.addToBackStack(null);
        PassphraseDialogFragment.newInstance(null).show(ft, FRAGMENT_PASSPHRASE);
    }

    private void displaySpinnerDialog() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.addToBackStack(null);
        SpinnerDialogFragment dialog = new SpinnerDialogFragment();
        dialog.show(ft, FRAGMENT_SPINNER);
    }

    /**
     * Callback for PassphraseDialogFragment.Listener
     */
    @Override
    public boolean onPassphraseEntered(String passphrase) {
        if (!passphrase.isEmpty() && ProfileSyncService.get().setDecryptionPassphrase(passphrase)) {
            // The passphrase was correct - close this activity.
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onPassphraseCanceled() {
        // Re add the notification.
        SyncController.get(this).getSyncNotificationController().syncStateChanged();
        finish();
    }

    @Override
    public void onBackStackChanged() {
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            finish();
        }
    }

    /**
     * Dialog shown while sync is loading.
     */
    public static class SpinnerDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setMessage(getResources().getString(R.string.sync_loading));
            return dialog;
        }
    }
}
