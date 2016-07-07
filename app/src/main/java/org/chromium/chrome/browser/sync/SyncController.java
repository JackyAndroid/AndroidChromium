// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.identity.UniqueIdentificationGenerator;
import org.chromium.chrome.browser.identity.UniqueIdentificationGeneratorFactory;
import org.chromium.chrome.browser.invalidation.InvalidationController;
import org.chromium.chrome.browser.signin.AccountManagementFragment;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInFlowObserver;
import org.chromium.chrome.browser.sync.ui.PassphraseActivity;
import org.chromium.sync.AndroidSyncSettings;
import org.chromium.sync.ModelType;
import org.chromium.sync.PassphraseType;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

/**
 * SyncController handles the coordination of sync state between the invalidation controller,
 * the Android sync settings, and the native sync code.
 *
 * It also handles initialization of some pieces of sync state on startup.
 *
 * Sync state can be changed from four places:
 *
 * - The Chrome UI, which will call SyncController directly.
 * - Native sync, which can disable it via a dashboard stop and clear.
 * - Android's Chrome sync setting.
 * - Android's master sync setting.
 *
 * SyncController implements listeners for the last three cases. When master sync is disabled, we
 * are careful to not change the Android Chrome sync setting so we know whether to turn sync back
 * on when it is re-enabled.
 */
public class SyncController implements ProfileSyncService.SyncStateChangedListener,
                                       AndroidSyncSettings.AndroidSyncSettingsObserver {
    private static final String TAG = "cr.SyncController";

    /**
     * An identifier for the generator in UniqueIdentificationGeneratorFactory to be used to
     * generate the sync sessions ID. The generator is registered in the Application's onCreate
     * method.
     */
    public static final String GENERATOR_ID = "SYNC";

    @VisibleForTesting
    public static final String SESSION_TAG_PREFIX = "session_sync";

    private static SyncController sInstance;

    private final Context mContext;
    private final ChromeSigninController mChromeSigninController;
    private final ProfileSyncService mProfileSyncService;
    private final SyncNotificationController mSyncNotificationController;

    private SyncController(Context context) {
        mContext = context;
        mChromeSigninController = ChromeSigninController.get(mContext);
        AndroidSyncSettings.registerObserver(context, this);
        mProfileSyncService = ProfileSyncService.get();
        mProfileSyncService.addSyncStateChangedListener(this);

        setSessionsId();

        // Create the SyncNotificationController.
        mSyncNotificationController = new SyncNotificationController(
                mContext, PassphraseActivity.class, AccountManagementFragment.class);
        mProfileSyncService.addSyncStateChangedListener(mSyncNotificationController);

        updateSyncStateFromAndroid();

        // When the application gets paused, tell sync to flush the directory to disk.
        ApplicationStatus.registerStateListenerForAllActivities(new ActivityStateListener() {
            @Override
            public void onActivityStateChange(Activity activity, int newState) {
                if (newState == ActivityState.PAUSED) {
                    mProfileSyncService.flushDirectory();
                }
            }
        });
    }

    /**
     * Retrieve the singleton instance of this class.
     *
     * @param context the current context.
     * @return the singleton instance.
     */
    public static SyncController get(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            sInstance = new SyncController(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Trigger Chromium sign in of the given account.
     *
     * This also ensure that sync setup is not in progress anymore, so sync will start after
     * sync initialization has happened.
     *
     * @param activity the current activity.
     * @param accountName the full account name.
     */
    @VisibleForTesting
    public void signIn(Activity activity, String accountName) {
        final Account account = AccountManagerHelper.createAccountFromName(accountName);

        // The SigninManager handles most of the sign-in flow, and doFinishSignIn handles the
        // ChromeShell specific details.
        SigninManager signinManager = SigninManager.get(mContext);
        signinManager.onFirstRunCheckDone();
        final boolean passive = false;
        signinManager.startSignIn(activity, account, passive, new SignInFlowObserver() {
            @Override
            public void onSigninComplete() {
                SigninManager.get(mContext).logInSignedInUser();
                mProfileSyncService.setSetupInProgress(false);
                start();
            }

            @Override
            public void onSigninCancelled() {
                stop();
            }
        });
    }

    /**
     * Updates sync to reflect the state of the Android sync settings.
     */
    public void updateSyncStateFromAndroid() {
        if (AndroidSyncSettings.isSyncEnabled(mContext)) {
            start();
        } else {
            stop();
        }
    }

    /**
     * Starts sync if the master sync flag is enabled.
     *
     * Affects native sync, the invalidation controller, and the Android sync settings.
     */
    public void start() {
        ThreadUtils.assertOnUiThread();
        if (AndroidSyncSettings.isMasterSyncEnabled(mContext)) {
            Log.d(TAG, "Enabling sync");
            InvalidationController.get(mContext).ensureStartedAndUpdateRegisteredTypes();
            mProfileSyncService.requestStart();
            AndroidSyncSettings.enableChromeSync(mContext);
        }
    }

    /**
     * Stops Sync if a user is currently signed in.
     *
     * Affects native sync, the invalidation controller, and the Android sync settings.
     */
    public void stop() {
        ThreadUtils.assertOnUiThread();
        Log.d(TAG, "Disabling sync");
        InvalidationController.get(mContext).stop();
        mProfileSyncService.requestStop();
        if (AndroidSyncSettings.isMasterSyncEnabled(mContext)) {
            // Only disable Android's Chrome sync setting if we weren't disabled
            // by the master sync setting. This way, when master sync is enabled
            // they will both be on and sync will start again.
            AndroidSyncSettings.disableChromeSync(mContext);
        }
    }

    /**
     * From {@link ProfileSyncService.SyncStateChangedListener}.
     *
     * Changes the invalidation controller and Android sync setting state to match
     * the new native sync state.
     */
    @Override
    public void syncStateChanged() {
        ThreadUtils.assertOnUiThread();
        // Make the Java state match the native state.
        if (mProfileSyncService.isSyncRequested()) {
            AndroidSyncSettings.enableChromeSync(mContext);
        } else {
            if (AndroidSyncSettings.isMasterSyncEnabled(mContext)) {
                // See comment in stop().
                AndroidSyncSettings.disableChromeSync(mContext);
            }
        }
    }

    /**
     * From {@link AndroidSyncSettings.AndroidSyncSettingsObserver}.
     */
    @Override
    public void androidSyncSettingsChanged() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateSyncStateFromAndroid();
            }
        });
    }

    /**
     * @return Whether sync is enabled to sync urls or open tabs with a non custom passphrase.
     */
    public boolean isSyncingUrlsWithKeystorePassphrase() {
        return mProfileSyncService.isBackendInitialized()
                && mProfileSyncService.getPreferredDataTypes().contains(ModelType.TYPED_URLS)
                && mProfileSyncService.getPassphraseType().equals(
                           PassphraseType.KEYSTORE_PASSPHRASE);
    }

    /**
     * Returns the SyncNotificationController.
     */
    public SyncNotificationController getSyncNotificationController() {
        return mSyncNotificationController;
    }

    /**
     * Set the sessions ID using the generator that was registered for GENERATOR_ID.
     */
    private void setSessionsId() {
        UniqueIdentificationGenerator generator =
                UniqueIdentificationGeneratorFactory.getInstance(GENERATOR_ID);
        String uniqueTag = generator.getUniqueId(null);
        if (uniqueTag.isEmpty()) {
            Log.e(TAG, "Unable to get unique tag for sync. "
                    + "This may lead to unexpected tab sync behavior.");
            return;
        }
        mProfileSyncService.setSessionsId(SESSION_TAG_PREFIX + uniqueTag);
    }
}
