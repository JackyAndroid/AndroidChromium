// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.firstrun.FirstRunGlueImpl;
import org.chromium.chrome.browser.firstrun.FirstRunSignInProcessor;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.components.signin.ChromeSigninController;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Backup agent for Chrome, using Android key/value backup.
 */

public class ChromeBackupAgent extends BackupAgent {
    private static final String ANDROID_DEFAULT_PREFIX = "AndroidDefault.";
    private static final String NATIVE_PREF_PREFIX = "native.";

    private static final String TAG = "ChromeBackupAgent";

    // Lists of preferences that should be restored unchanged.

    static final String[] BACKUP_ANDROID_BOOL_PREFS = {
            FirstRunGlueImpl.CACHED_TOS_ACCEPTED_PREF,
            FirstRunStatus.FIRST_RUN_FLOW_COMPLETE,
            FirstRunStatus.LIGHTWEIGHT_FIRST_RUN_FLOW_COMPLETE,
            FirstRunSignInProcessor.FIRST_RUN_FLOW_SIGNIN_SETUP,
            PrivacyPreferencesManager.PREF_METRICS_REPORTING,
    };

    /**
     * Class to save and restore the backup state, used to decide if backups are needed. Since the
     * backup data is small, and stored as private data by the backup service, this can simply store
     * and compare a copy of the data.
     */
    @SuppressFBWarnings(value = {"HE_EQUALS_USE_HASHCODE"},
            justification = "Only local use, hashcode never used")
    private static final class BackupState {
        private ArrayList<String> mNames;
        private ArrayList<byte[]> mValues;

        @SuppressFBWarnings(value = {"OS_OPEN_STREAM"}, justification = "Closed by backup system")
        @SuppressWarnings("unchecked")
        public BackupState(ParcelFileDescriptor parceledState) throws IOException {
            if (parceledState == null) return;
            try {
                FileInputStream instream = new FileInputStream(parceledState.getFileDescriptor());
                ObjectInputStream in = new ObjectInputStream(instream);
                mNames = (ArrayList<String>) in.readObject();
                mValues = (ArrayList<byte[]>) in.readObject();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        public BackupState(ArrayList<String> names, ArrayList<byte[]> values) {
            mNames = names;
            mValues = values;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BackupState)) return false;
            BackupState otherBackupState = (BackupState) other;
            return mNames.equals(otherBackupState.mNames)
                    && Arrays.deepEquals(mValues.toArray(), otherBackupState.mValues.toArray());
        }

        @SuppressFBWarnings(value = {"OS_OPEN_STREAM"}, justification = "Closed by backup system")
        public void save(ParcelFileDescriptor parceledState) throws IOException {
            FileOutputStream outstream = new FileOutputStream(parceledState.getFileDescriptor());
            ObjectOutputStream out = new ObjectOutputStream(outstream);
            out.writeObject(mNames);
            out.writeObject(mValues);
        }
    }

    @VisibleForTesting
    protected boolean accountExistsOnDevice(String userName) {
        return AccountManagerHelper.get(this).getAccountFromName(userName) != null;
    }

    @VisibleForTesting
    protected boolean initializeBrowser(Context context) {
        try {
            ChromeBrowserInitializer.getInstance(context).handleSynchronousStartup();
        } catch (ProcessInitException e) {
            Log.w(TAG, "Browser launch failed on restore: " + e);
            return false;
        }
        return true;
    }

    private static byte[] booleanToBytes(boolean value) {
        return value ? new byte[] {1} : new byte[] {0};
    }

    private static boolean bytesToBoolean(byte[] bytes) {
        return bytes[0] != 0;
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        final ChromeBackupAgent backupAgent = this;

        final ArrayList<String> backupNames = new ArrayList<>();
        final ArrayList<byte[]> backupValues = new ArrayList<>();

        // The native preferences can only be read on the UI thread.
        if (!ThreadUtils.runOnUiThreadBlockingNoException(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    // Start the browser if necessary, so that Chrome can access the native
                    // preferences. Although Chrome requests the backup, it doesn't happen
                    // immediately, so by the time it does Chrome may not be running.
                    if (!initializeBrowser(backupAgent)) return false;

                    String[] nativeBackupNames = nativeGetBoolBackupNames();
                    boolean[] nativeBackupValues = nativeGetBoolBackupValues();
                    assert nativeBackupNames.length == nativeBackupValues.length;

                    for (String name : nativeBackupNames) {
                        backupNames.add(NATIVE_PREF_PREFIX + name);
                    }
                    for (boolean val : nativeBackupValues) {
                        backupValues.add(booleanToBytes(val));
                    }
                    return true;
                }
            })) {
            // Something went wrong reading the native preferences, skip the backup.
            return;
        }
        // Add the Android boolean prefs.
        SharedPreferences sharedPrefs = ContextUtils.getAppSharedPreferences();
        for (String prefName : BACKUP_ANDROID_BOOL_PREFS) {
            if (sharedPrefs.contains(prefName)) {
                backupNames.add(ANDROID_DEFAULT_PREFIX + prefName);
                backupValues.add(booleanToBytes(sharedPrefs.getBoolean(prefName, false)));
            }
        }

        // Finally add the user id.
        backupNames.add(ANDROID_DEFAULT_PREFIX + ChromeSigninController.SIGNED_IN_ACCOUNT_KEY);
        backupValues.add(
                sharedPrefs.getString(ChromeSigninController.SIGNED_IN_ACCOUNT_KEY, "").getBytes());

        BackupState newBackupState = new BackupState(backupNames, backupValues);

        // Check if a backup is actually needed.
        try {
            BackupState oldBackupState = new BackupState(oldState);
            if (newBackupState.equals(oldBackupState)) {
                Log.i(TAG, "Nothing has changed since the last backup. Backup skipped.");
                newBackupState.save(newState);
                return;
            }
        } catch (IOException e) {
            // This will happen if Chrome has never written backup data, or if the backup status is
            // corrupt. Create a new backup in either case.
            Log.i(TAG, "Can't read backup status file");
        }
        // Write the backup data
        for (int i = 0; i < backupNames.size(); i++) {
            data.writeEntityHeader(backupNames.get(i), backupValues.get(i).length);
            data.writeEntityData(backupValues.get(i), backupValues.get(i).length);
        }
        // Remember the backup state.
        newBackupState.save(newState);

        Log.i(TAG, "Backup complete");
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        // Check that the user hasn't already seen FRE (not sure if this can ever happen, but if it
        // does then restoring the backup will overwrite the user's choices).
        SharedPreferences sharedPrefs = ContextUtils.getAppSharedPreferences();
        if (sharedPrefs.getBoolean(FirstRunStatus.FIRST_RUN_FLOW_COMPLETE, false)
                || sharedPrefs.getBoolean(
                           FirstRunStatus.LIGHTWEIGHT_FIRST_RUN_FLOW_COMPLETE, false)) {
            Log.w(TAG, "Restore attempted after first run");
            return;
        }

        final ArrayList<String> backupNames = new ArrayList<>();
        final ArrayList<byte[]> backupValues = new ArrayList<>();

        String restoredUserName = null;
        while (data.readNextHeader()) {
            String key = data.getKey();
            int dataSize = data.getDataSize();
            byte[] buffer = new byte[dataSize];
            data.readEntityData(buffer, 0, dataSize);
            if (key.equals(ANDROID_DEFAULT_PREFIX + ChromeSigninController.SIGNED_IN_ACCOUNT_KEY)) {
                restoredUserName = new String(buffer);
            } else {
                backupNames.add(key);
                backupValues.add(buffer);
            }
        }

        // Chrome has to be running before it can check if the account exists.
        final ChromeBackupAgent backupAgent = this;
        if (!ThreadUtils.runOnUiThreadBlockingNoException(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    // Start the browser if necessary.
                    return initializeBrowser(backupAgent);
                }
            })) {
            // Something went wrong starting Chrome, skip the restore.
            return;
        }
        // If the user hasn't signed in, or can't sign in, then don't restore anything.
        if (restoredUserName == null || !accountExistsOnDevice(restoredUserName)) {
            Log.i(TAG, "Chrome was not signed in with a known account name, not restoring");
            return;
        }
        // Restore the native preferences on the UI thread
        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> nativeBackupNames = new ArrayList<>();
                boolean[] nativeBackupValues = new boolean[backupNames.size()];
                int count = 0;
                int prefixLength = NATIVE_PREF_PREFIX.length();
                for (int i = 0; i < backupNames.size(); i++) {
                    String name = backupNames.get(i);
                    if (name.startsWith(NATIVE_PREF_PREFIX)) {
                        nativeBackupNames.add(name.substring(prefixLength));
                        nativeBackupValues[count] = bytesToBoolean(backupValues.get(i));
                        count++;
                    }
                }
                nativeSetBoolBackupPrefs(nativeBackupNames.toArray(new String[count]),
                        Arrays.copyOf(nativeBackupValues, count));
            }
        });

        // Now that everything looks good so restore the Android preferences.
        SharedPreferences.Editor editor = sharedPrefs.edit();
        // Only restore preferences that we know about.
        int prefixLength = ANDROID_DEFAULT_PREFIX.length();
        for (int i = 0; i < backupNames.size(); i++) {
            String name = backupNames.get(i);
            if (name.startsWith(ANDROID_DEFAULT_PREFIX)
                    && Arrays.asList(BACKUP_ANDROID_BOOL_PREFS)
                               .contains(name.substring(prefixLength))) {
                editor.putBoolean(
                        name.substring(prefixLength), bytesToBoolean(backupValues.get(i)));
            }
        }

        // Because FirstRunSignInProcessor.FIRST_RUN_FLOW_SIGNIN_COMPLETE is not restored Chrome
        // will sign in the user on first run to the account in FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME
        // if any. If the rest of FRE has been completed this will happen silently.
        editor.putString(
                FirstRunSignInProcessor.FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME, restoredUserName);
        editor.apply();

        // The silent first run will change things, so there is no point in trying to prevent
        // additional backups at this stage. Don't write anything to |newState|.
        Log.i(TAG, "Restore complete");
    }

    @VisibleForTesting
    protected native String[] nativeGetBoolBackupNames();

    @VisibleForTesting
    protected native boolean[] nativeGetBoolBackupValues();

    @VisibleForTesting
    protected native void nativeSetBoolBackupPrefs(String[] name, boolean[] value);
}
