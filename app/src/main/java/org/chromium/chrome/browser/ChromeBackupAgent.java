// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.StreamUtil;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.firstrun.FirstRunSignInProcessor;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.components.signin.ChromeSigninController;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Backup agent for Chrome, filters the restored backup to remove preferences that should not have
 * been restored. Note: Nothing in this class can depend on the ChromeApplication instance having
 * been created. During restore Android creates a special instance of the Chrome application with
 * its own Android defined application class, which is not derived from ChromeApplication.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ChromeBackupAgent extends BackupAgent {

    private static final String TAG = "ChromeBackupAgent";

    // Lists of preferences that should be restored unchanged.

    private static final String[] RESTORED_ANDROID_PREFS = {
            FirstRunStatus.FIRST_RUN_FLOW_COMPLETE,
            FirstRunStatus.LIGHTWEIGHT_FIRST_RUN_FLOW_COMPLETE,
            FirstRunSignInProcessor.FIRST_RUN_FLOW_SIGNIN_SETUP,
            PrivacyPreferencesManager.PREF_METRICS_REPORTING,
    };

    // Sync preferences, all in C++ syncer::prefs namespace.
    //
    // TODO(aberent): These should ideally use the constants that are used to access the preferences
    // elsewhere, but those are currently only exist in C++, so doing so would require some
    // reorganization.
    private static final String[][] RESTORED_CHROME_PREFS = {
            // kSyncFirstSetupComplete
            {"sync", "has_setup_completed"},
            // kSyncKeepEverythingSynced
            {"sync", "keep_everything_synced"},
            // kSyncAutofillProfile
            {"sync", "autofill_profile"},
            // kSyncAutofillWallet
            {"sync", "autofill_wallet"},
            // kSyncAutofillWalletMetadata
            {"sync", "autofill_wallet_metadata"},
            // kSyncAutofill
            {"sync", "autofill"},
            // kSyncBookmarks
            {"sync", "bookmarks"},
            // kSyncDeviceInfo
            {"sync", "device_info"},
            // kSyncFaviconImages
            {"sync", "favicon_images"},
            // kSyncFaviconTracking
            {"sync", "favicon_tracking"},
            // kSyncHistoryDeleteDirectives
            {"sync", "history_delete_directives"},
            // kSyncPasswords
            {"sync", "passwords"},
            // kSyncPreferences
            {"sync", "preferences"},
            // kSyncPriorityPreferences
            {"sync", "priority_preferences"},
            // kSyncSessions
            {"sync", "sessions"},
            // kSyncSupervisedUserSettings
            {"sync", "managed_user_settings"},
            // kSyncSupervisedUserSharedSettings
            {"sync", "managed_user_shared_settings"},
            // kSyncSupervisedUserWhitelists
            {"sync", "managed_user_whitelists"},
            // kSyncTabs
            {"sync", "tabs"},
            // kSyncTypedUrls
            {"sync", "typed_urls"},
            // kSyncSuppressStart
            {"sync", "suppress_start"},
    };

    private static final String[] DEFAULT_JSON_PREFS_FILE = {
            // chrome::kInitialProfile
            "Default",
            // chrome::kPreferencesFilename
            "Preferences",
    };

    private static boolean sAllowChromeApplication = false;

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        // No implementation needed for Android 6.0 Auto Backup. Used only on older versions of
        // Android Backup
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        // No implementation needed for Android 6.0 Auto Backup. Used only on older versions of
        // Android Backup
    }

    // May be overriden by downstream products that access account information in a different way.
    protected Account[] getAccounts() {
        Log.d(TAG, "Getting accounts from AccountManager");
        AccountManager manager = (AccountManager) getSystemService(ACCOUNT_SERVICE);
        return manager.getAccounts();
    }

    private boolean accountExistsOnDevice(String userName) {
        // This cannot use AccountManagerHelper, since that depends on ChromeApplication.
        for (Account account : getAccounts()) {
            if (account.name.equals(userName)) return true;
        }
        return false;
    }

    @Override
    public void onRestoreFinished() {
        if (getApplicationContext() instanceof ChromeApplication && !sAllowChromeApplication) {
            // This should never happen in real use, but will happen during testing if Chrome is
            // already running (even in background, started to provide a service, for example).
            Log.w(TAG, "Running with wrong type of Application class");
            return;
        }
        // This is running without a ChromeApplication instance, so this has to be done here.
        ContextUtils.initApplicationContext(getApplicationContext());
        SharedPreferences sharedPrefs = ContextUtils.getAppSharedPreferences();
        // Save the user name for later restoration.
        String userName = sharedPrefs.getString(ChromeSigninController.SIGNED_IN_ACCOUNT_KEY, null);
        Log.d(TAG, "Previous signed in user name = " + userName);

        File prefsFile = this.getDir(ChromeBrowserInitializer.PRIVATE_DATA_DIRECTORY_SUFFIX,
                Context.MODE_PRIVATE);
        for (String name : DEFAULT_JSON_PREFS_FILE) {
            prefsFile = new File(prefsFile, name);
        }

        // If the user hasn't signed in, or can't sign in, then don't restore anything.
        if (userName == null || !accountExistsOnDevice(userName)) {
            clearAllPrefs(sharedPrefs, prefsFile);
            Log.d(TAG, "onRestoreFinished complete, nothing restored");
            return;
        }

        // Check that the file has been restored.
        if (!filterChromePrefs(prefsFile)) {
            // The preferences are corrupt, for safety delete all of them
            clearAllPrefs(sharedPrefs, prefsFile);
            Log.d(TAG, "onRestoreFinished failed");
            return;
        }
        restoreAndroidPrefs(sharedPrefs, userName);

        Log.d(TAG, "onRestoreFinished complete");
    }

    @SuppressLint("CommitPrefEdits")
    private void clearAllPrefs(SharedPreferences sharedPrefs, File prefsFile) {
        deleteFileIfPossible(prefsFile);
        // Android restore closes down the process immediately, so we want to make sure that the
        // prefs changes are committed to disk before exiting.
        sharedPrefs.edit().clear().commit();
    }

    @SuppressLint("CommitPrefEdits")
    private void restoreAndroidPrefs(SharedPreferences sharedPrefs, String userName) {
        Set<String> prefNames = sharedPrefs.getAll().keySet();
        SharedPreferences.Editor editor = sharedPrefs.edit();
        // Throw away prefs we don't want to restore.
        Set<String> restoredPrefs = new HashSet<>(Arrays.asList(RESTORED_ANDROID_PREFS));
        for (String pref : prefNames) {
            if (!restoredPrefs.contains(pref)) editor.remove(pref);
        }
        // Because FirstRunSignInProcessor.FIRST_RUN_FLOW_SIGNIN_COMPLETE is not restored Chrome
        // will sign in the user on first run to the account in FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME
        // if any. If the rest of FRE has been completed this will happen silently.
        editor.putString(FirstRunSignInProcessor.FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME, userName);
        // Android restore closes down the process immediately, so we want to make sure that the
        // prefs changes are committed to disk before exiting.
        editor.commit();
    }

    private boolean filterChromePrefs(File prefsFile) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = openInputStream(prefsFile);
            int fileLength = (int) getFileLength(prefsFile);
            byte[] buffer = new byte[fileLength];
            if (inputStream.read(buffer) != fileLength) return false;
            JSONObject jsonInput = new JSONObject(new String(buffer, "UTF-8"));
            JSONObject jsonOutput = new JSONObject();
            for (String[] pref : RESTORED_CHROME_PREFS) {
                Object prefValue = readChromePref(jsonInput, pref);
                if (prefValue != null) writeChromePref(jsonOutput, pref, prefValue);
            }
            byte[] outputBytes = jsonOutput.toString().getBytes("UTF-8");
            outputStream = openOutputStream(prefsFile);
            outputStream.write(outputBytes);
            return true;
        } catch (IOException | JSONException e) {
            Log.d(TAG, "Filtering preferences failed with %s", e.getMessage());
            return false;
        } finally {
            StreamUtil.closeQuietly(inputStream);
            StreamUtil.closeQuietly(outputStream);
        }
    }

    @VisibleForTesting
    protected long getFileLength(File prefsFile) {
        return prefsFile.length();
    }

    @VisibleForTesting
    protected InputStream openInputStream(File prefsFile) throws FileNotFoundException {
        return new FileInputStream(prefsFile);
    }

    @VisibleForTesting
    protected OutputStream openOutputStream(File prefsFile) throws FileNotFoundException {
        return new FileOutputStream(prefsFile);
    }

    private Object readChromePref(JSONObject json, String pref[]) {
        JSONObject finalParent = json;
        for (int i = 0; i < pref.length - 1; i++) {
            finalParent = finalParent.optJSONObject(pref[i]);
            if (finalParent == null) return null;
        }
        return finalParent.opt(pref[pref.length - 1]);
    }

    private void writeChromePref(JSONObject json, String[] prefPath, Object value)
            throws JSONException {
        JSONObject finalParent = json;
        for (int i = 0; i < prefPath.length - 1; i++) {
            JSONObject prevParent = finalParent;
            finalParent = prevParent.optJSONObject(prefPath[i]);
            if (finalParent == null) {
                finalParent = new JSONObject();
                prevParent.put(prefPath[i], finalParent);
            }
        }
        finalParent.put(prefPath[prefPath.length - 1], value);
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    private void deleteFileIfPossible(File file) {
        // Ignore result. There is nothing else we can do if the delete fails.
        file.delete();
    }

    @VisibleForTesting
    static void allowChromeApplicationForTesting() {
        sAllowChromeApplication = true;
    }
}
