// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.notifications.GoogleServicesNotificationController;
import org.chromium.chrome.browser.notifications.NotificationConstants;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.sync.AndroidSyncSettings;

/**
 * {@link SyncNotificationController} provides functionality for displaying Android notifications
 * regarding the user sync status.
 */
public class SyncNotificationController implements ProfileSyncService.SyncStateChangedListener {
    private static final String TAG = "SyncNotificationController";
    private final Context mApplicationContext;
    private final GoogleServicesNotificationController mNotificationController;
    private final Class<? extends Activity> mPassphraseRequestActivity;
    private final Class<? extends Fragment> mAccountManagementFragment;
    private final ProfileSyncService mProfileSyncService;

    public SyncNotificationController(Context context,
            Class<? extends Activity> passphraseRequestActivity,
            Class<? extends Fragment> accountManagementFragment) {
        mApplicationContext = context.getApplicationContext();
        mNotificationController = GoogleServicesNotificationController.get(context);
        mProfileSyncService = ProfileSyncService.get();
        mPassphraseRequestActivity = passphraseRequestActivity;
        mAccountManagementFragment = accountManagementFragment;
    }

    public void displayAndroidMasterSyncDisabledNotification() {
        String masterSyncDisabled =
                GoogleServicesNotificationController.formatMessageParts(mApplicationContext,
                        R.string.sign_in_sync, R.string.sync_android_master_sync_disabled);
        mNotificationController.showNotification(masterSyncDisabled.hashCode(), masterSyncDisabled,
                masterSyncDisabled, new Intent(Settings.ACTION_SYNC_SETTINGS));
    }

    /**
     * Callback for {@link ProfileSyncService.SyncStateChangedListener}.
     */
    @Override
    public void syncStateChanged() {
        ThreadUtils.assertOnUiThread();

        int message;
        Intent intent;

        // Auth errors take precedence over passphrase errors.
        if (!AndroidSyncSettings.isSyncEnabled(mApplicationContext)) {
            mNotificationController.cancelNotification(NotificationConstants.NOTIFICATION_ID_SYNC);
            return;
        }
        if (shouldSyncAuthErrorBeShown()) {
            message = mProfileSyncService.getAuthError().getMessage();
            intent = createSettingsIntent();
        } else if (mProfileSyncService.isBackendInitialized()
                && mProfileSyncService.isPassphraseRequiredForDecryption()) {
            if (mProfileSyncService.isPassphrasePrompted()) {
                return;
            }
            switch (mProfileSyncService.getPassphraseType()) {
                case IMPLICIT_PASSPHRASE: // Falling through intentionally.
                case FROZEN_IMPLICIT_PASSPHRASE: // Falling through intentionally.
                case CUSTOM_PASSPHRASE:
                    message = R.string.sync_need_passphrase;
                    intent = createPasswordIntent();
                    break;
                case KEYSTORE_PASSPHRASE: // Falling through intentionally.
                default:
                    mNotificationController.cancelNotification(
                            NotificationConstants.NOTIFICATION_ID_SYNC);
                    return;
            }
        } else {
            mNotificationController.cancelNotification(NotificationConstants.NOTIFICATION_ID_SYNC);
            return;
        }

        mNotificationController.updateSingleNotification(NotificationConstants.NOTIFICATION_ID_SYNC,
                GoogleServicesNotificationController.formatMessageParts(
                        mApplicationContext, R.string.sign_in_sync, message),
                intent);
    }

    private boolean shouldSyncAuthErrorBeShown() {
        switch (mProfileSyncService.getAuthError()) {
            case NONE:
            case CONNECTION_FAILED:
            case SERVICE_UNAVAILABLE:
            case REQUEST_CANCELED:
            case INVALID_GAIA_CREDENTIALS:
                return false;
            case USER_NOT_SIGNED_UP:
            case CAPTCHA_REQUIRED:
            case ACCOUNT_DELETED:
            case ACCOUNT_DISABLED:
            case TWO_FACTOR:
            case HOSTED_NOT_ALLOWED:
                return true;
            default:
                Log.w(TAG, "Not showing unknown Auth Error: " + mProfileSyncService.getAuthError());
                return false;
        }
    }

    /**
     * Creates an intent that launches the Chrome settings, and automatically opens the fragment
     * for signed in users.
     *
     * @return the intent for opening the settings
     */
    private Intent createSettingsIntent() {
        return PreferencesLauncher.createIntentForSettingsPage(
                mApplicationContext, mAccountManagementFragment.getCanonicalName());
    }

    /**
     * Creates an intent that launches an activity that requests the users password/passphrase.
     *
     * @return the intent for opening the password/passphrase activity
     */
    private Intent createPasswordIntent() {
        // Make sure we don't prompt too many times.
        mProfileSyncService.setPassphrasePrompted(true);

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(mApplicationContext, mPassphraseRequestActivity));
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        // This activity will become the start of a new task on this history stack.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Clears the task stack above this activity if it already exists.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }
}
