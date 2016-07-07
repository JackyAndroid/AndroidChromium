// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.sync.ModelType;
import org.chromium.sync.PassphraseType;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JNI wrapper for the native ProfileSyncService.
 *
 * This class purely makes calls to native and contains absolutely  no business logic. It is only
 * usable from the UI thread as the native ProfileSyncService requires its access to be on the
 * UI thread. See chrome/browser/sync/profile_sync_service.h for more details.
 */
public class ProfileSyncService {

    /**
     * Listener for the underlying sync status.
     */
    public interface SyncStateChangedListener {
        // Invoked when the status has changed.
        public void syncStateChanged();
    }

    /**
     * Callback for getAllNodes.
     */
    public static class GetAllNodesCallback {
        private String mNodesString;

        // Invoked when getAllNodes completes.
        public void onResult(String nodesString) {
            mNodesString = nodesString;
        }

        // Returns the result of GetAllNodes as a JSONArray.
        @VisibleForTesting
        public JSONArray getNodesAsJsonArray() throws JSONException {
            return new JSONArray(mNodesString);
        }
    }

    private static final String TAG = "ProfileSyncService";

    private static final int[] ALL_SELECTABLE_TYPES = new int[] {
        ModelType.AUTOFILL,
        ModelType.BOOKMARKS,
        ModelType.PASSWORDS,
        ModelType.PREFERENCES,
        ModelType.PROXY_TABS,
        ModelType.TYPED_URLS
    };

    private static ProfileSyncService sProfileSyncService;

    // Sync state changes more often than listeners are added/removed, so using CopyOnWrite.
    private final List<SyncStateChangedListener> mListeners =
            new CopyOnWriteArrayList<SyncStateChangedListener>();

    /**
     * Native ProfileSyncServiceAndroid object. Cannot be final because it is initialized in
     * {@link init()}.
     */
    private long mNativeProfileSyncServiceAndroid;

    /**
     * Retrieves or creates the ProfileSyncService singleton instance.
     *
     * Can only be accessed on the main thread.
     */
    @SuppressFBWarnings("LI_LAZY_INIT")
    public static ProfileSyncService get() {
        ThreadUtils.assertOnUiThread();
        if (sProfileSyncService == null) {
            sProfileSyncService = new ProfileSyncService();
        }
        return sProfileSyncService;
    }

    @VisibleForTesting
    public static void overrideForTests(ProfileSyncService profileSyncService) {
        sProfileSyncService = profileSyncService;
    }

    protected ProfileSyncService() {
        init();
    }

    /**
     * This is called pretty early in our application. Avoid any blocking operations here. init()
     * is a separate function to enable a test subclass of ProfileSyncService to completely stub out
     * ProfileSyncService.
     */
    protected void init() {
        ThreadUtils.assertOnUiThread();

        // This may cause us to create ProfileSyncService even if sync has not
        // been set up, but ProfileSyncService::Startup() won't be called until
        // credentials are available.
        mNativeProfileSyncServiceAndroid = nativeInit();
    }

    @CalledByNative
    private static long getProfileSyncServiceAndroid() {
        return get().mNativeProfileSyncServiceAndroid;
    }

    public void signOut() {
        nativeSignOutSync(mNativeProfileSyncServiceAndroid);
    }

    public String querySyncStatus() {
        ThreadUtils.assertOnUiThread();
        return nativeQuerySyncStatusSummary(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Sets the the machine tag used by session sync.
     */
    public void setSessionsId(String sessionTag) {
        ThreadUtils.assertOnUiThread();
        nativeSetSyncSessionsId(mNativeProfileSyncServiceAndroid, sessionTag);
    }

    /**
     * Returns the actual passphrase type being used for encryption.
     * The sync backend must be running (isBackendInitialized() returns true) before
     * calling this function.
     * <p/>
     * This method should only be used if you want to know the raw value. For checking whether
     * we should ask the user for a passphrase, use isPassphraseRequiredForDecryption().
     */
    public PassphraseType getPassphraseType() {
        assert isBackendInitialized();
        int passphraseType = nativeGetPassphraseType(mNativeProfileSyncServiceAndroid);
        return PassphraseType.fromInternalValue(passphraseType);
    }

    /**
     * Returns true if the current explicit passphrase time is defined.
     */
    public boolean hasExplicitPassphraseTime() {
        assert isBackendInitialized();
        return nativeHasExplicitPassphraseTime(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Returns the current explicit passphrase time in milliseconds since epoch.
     */
    public long getExplicitPassphraseTime() {
        assert isBackendInitialized();
        return nativeGetExplicitPassphraseTime(mNativeProfileSyncServiceAndroid);
    }

    public String getSyncEnterGooglePassphraseBodyWithDateText() {
        assert isBackendInitialized();
        return nativeGetSyncEnterGooglePassphraseBodyWithDateText(mNativeProfileSyncServiceAndroid);
    }

    public String getSyncEnterCustomPassphraseBodyWithDateText() {
        assert isBackendInitialized();
        return nativeGetSyncEnterCustomPassphraseBodyWithDateText(mNativeProfileSyncServiceAndroid);
    }

    public String getCurrentSignedInAccountText() {
        assert isBackendInitialized();
        return nativeGetCurrentSignedInAccountText(mNativeProfileSyncServiceAndroid);
    }

    public String getSyncEnterCustomPassphraseBodyText() {
        return nativeGetSyncEnterCustomPassphraseBodyText(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Checks if sync is currently set to use a custom passphrase. The sync backend must be running
     * (isBackendInitialized() returns true) before calling this function.
     *
     * @return true if sync is using a custom passphrase.
     */
    public boolean isUsingSecondaryPassphrase() {
        assert isBackendInitialized();
        return nativeIsUsingSecondaryPassphrase(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Checks if we need a passphrase to decrypt a currently-enabled data type. This returns false
     * if a passphrase is needed for a type that is not currently enabled.
     *
     * @return true if we need a passphrase.
     */
    public boolean isPassphraseRequiredForDecryption() {
        assert isBackendInitialized();
        return nativeIsPassphraseRequiredForDecryption(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Checks if the sync backend is running.
     *
     * @return true if sync is initialized/running.
     */
    public boolean isBackendInitialized() {
        return nativeIsBackendInitialized(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Checks if the first sync setup is currently in progress.
     *
     * @return true if first sync setup is in progress
     */
    public boolean isFirstSetupInProgress() {
        return nativeIsFirstSetupInProgress(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Checks if encrypting all the data types is allowed.
     *
     * @return true if encrypting all data types is allowed, false if only passwords are allowed to
     * be encrypted.
     */
    public boolean isEncryptEverythingAllowed() {
        assert isBackendInitialized();
        return nativeIsEncryptEverythingAllowed(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Checks if the all the data types are encrypted.
     *
     * @return true if all data types are encrypted, false if only passwords are encrypted.
     */
    public boolean isEncryptEverythingEnabled() {
        assert isBackendInitialized();
        return nativeIsEncryptEverythingEnabled(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Turns on encryption of all data types. This only takes effect after sync configuration is
     * completed and setPreferredDataTypes() is invoked.
     */
    public void enableEncryptEverything() {
        assert isBackendInitialized();
        nativeEnableEncryptEverything(mNativeProfileSyncServiceAndroid);
    }

    public void setEncryptionPassphrase(String passphrase) {
        assert isBackendInitialized();
        nativeSetEncryptionPassphrase(mNativeProfileSyncServiceAndroid, passphrase);
    }

    public boolean isCryptographerReady() {
        assert isBackendInitialized();
        return nativeIsCryptographerReady(mNativeProfileSyncServiceAndroid);
    }

    public boolean setDecryptionPassphrase(String passphrase) {
        assert isBackendInitialized();
        return nativeSetDecryptionPassphrase(mNativeProfileSyncServiceAndroid, passphrase);
    }

    public GoogleServiceAuthError.State getAuthError() {
        int authErrorCode = nativeGetAuthError(mNativeProfileSyncServiceAndroid);
        return GoogleServiceAuthError.State.fromCode(authErrorCode);
    }

    /**
     * Gets the set of data types that are currently syncing.
     *
     * This is affected by whether sync is on.
     *
     * @return Set of active data types.
     */
    public Set<Integer> getActiveDataTypes() {
        int[] activeDataTypes = nativeGetActiveDataTypes(mNativeProfileSyncServiceAndroid);
        return modelTypeArrayToSet(activeDataTypes);
    }

    /**
     * Gets the set of data types that are enabled in sync.
     *
     * This is unaffected by whether sync is on.
     *
     * @return Set of preferred types.
     */
    public Set<Integer> getPreferredDataTypes() {
        int[] modelTypeArray = nativeGetPreferredDataTypes(mNativeProfileSyncServiceAndroid);
        return modelTypeArrayToSet(modelTypeArray);
    }

    private static Set<Integer> modelTypeArrayToSet(int[] modelTypeArray) {
        Set<Integer> modelTypeSet = new HashSet<Integer>();
        for (int i = 0; i < modelTypeArray.length; i++) {
            modelTypeSet.add(modelTypeArray[i]);
        }
        return modelTypeSet;
    }

    private static int[] modelTypeSetToArray(Set<Integer> modelTypeSet) {
        int[] modelTypeArray = new int[modelTypeSet.size()];
        int i = 0;
        for (int modelType : modelTypeSet) {
            modelTypeArray[i++] = modelType;
        }
        return modelTypeArray;
    }

    public boolean hasKeepEverythingSynced() {
        return nativeHasKeepEverythingSynced(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Enables syncing for the passed data types.
     *
     * @param syncEverything Set to true if the user wants to sync all data types
     *                       (including new data types we add in the future).
     * @param enabledTypes   The set of types to enable. Ignored (can be null) if
     *                       syncEverything is true.
     */
    public void setPreferredDataTypes(boolean syncEverything, Set<Integer> enabledTypes) {
        nativeSetPreferredDataTypes(mNativeProfileSyncServiceAndroid, syncEverything, syncEverything
                ? ALL_SELECTABLE_TYPES : modelTypeSetToArray(enabledTypes));
    }

    public void setSyncSetupCompleted() {
        nativeSetSyncSetupCompleted(mNativeProfileSyncServiceAndroid);
    }

    public boolean hasSyncSetupCompleted() {
        return nativeHasSyncSetupCompleted(mNativeProfileSyncServiceAndroid);
    }

    public boolean isSyncRequested() {
        return nativeIsSyncRequested(mNativeProfileSyncServiceAndroid);
    }

    // TODO(maxbogue): Remove this annotation once this method is used outside of tests.
    @VisibleForTesting
    public boolean isSyncActive() {
        return nativeIsSyncActive(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Notifies sync whether sync setup is in progress - this tells sync whether it should start
     * syncing data types when it starts up, or if it should just stay in "configuration mode".
     *
     * @param inProgress True to put sync in configuration mode, false to turn off configuration
     *                   and allow syncing.
     */
    public void setSetupInProgress(boolean inProgress) {
        nativeSetSetupInProgress(mNativeProfileSyncServiceAndroid, inProgress);
    }

    public void addSyncStateChangedListener(SyncStateChangedListener listener) {
        ThreadUtils.assertOnUiThread();
        mListeners.add(listener);
    }

    public void removeSyncStateChangedListener(SyncStateChangedListener listener) {
        ThreadUtils.assertOnUiThread();
        mListeners.remove(listener);
    }

    public boolean hasUnrecoverableError() {
        return nativeHasUnrecoverableError(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Called when the state of the native sync engine has changed, so various
     * UI elements can update themselves.
     */
    @CalledByNative
    public void syncStateChanged() {
        for (SyncStateChangedListener listener : mListeners) {
            listener.syncStateChanged();
        }
    }

    @VisibleForTesting
    public String getSyncInternalsInfoForTest() {
        ThreadUtils.assertOnUiThread();
        return nativeGetAboutInfoForTest(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Starts the sync engine.
     */
    public void requestStart() {
        nativeRequestStart(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Stops the sync engine.
     */
    public void requestStop() {
        nativeRequestStop(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Flushes the sync directory.
     */
    public void flushDirectory() {
        nativeFlushDirectory(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Returns the time when the last sync cycle was completed.
     *
     * @return The difference measured in microseconds, between last sync cycle completion time
     * and 1 January 1970 00:00:00 UTC.
     */
    @VisibleForTesting
    public long getLastSyncedTimeForTest() {
        return nativeGetLastSyncedTimeForTest(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Overrides the Sync engine's NetworkResources. This is used to set up the Sync FakeServer for
     * testing.
     *
     * @param networkResources the pointer to the NetworkResources created by the native code. It
     *                         is assumed that the Java caller has ownership of this pointer;
     *                         ownership is transferred as part of this call.
     */
    @VisibleForTesting
    public void overrideNetworkResourcesForTest(long networkResources) {
        nativeOverrideNetworkResourcesForTest(mNativeProfileSyncServiceAndroid, networkResources);
    }

    /**
     * Returns whether this client has previously prompted the user for a
     * passphrase error via the android system notifications.
     *
     * Can be called whether or not sync is initialized.
     *
     * @return Whether client has prompted for a passphrase error previously.
     */
    public boolean isPassphrasePrompted() {
        return nativeIsPassphrasePrompted(mNativeProfileSyncServiceAndroid);
    }

    /**
     * Sets whether this client has previously prompted the user for a
     * passphrase error via the android system notifications.
     *
     * Can be called whether or not sync is initialized.
     *
     * @param prompted whether the client has prompted the user previously.
     */
    public void setPassphrasePrompted(boolean prompted) {
        nativeSetPassphrasePrompted(mNativeProfileSyncServiceAndroid,
                                    prompted);
    }

    /**
     * Invokes the onResult method of the callback from native code.
     */
    @CalledByNative
    private static void onGetAllNodesResult(GetAllNodesCallback callback, String nodes) {
        callback.onResult(nodes);
    }

    /**
     * Retrieves a JSON version of local Sync data via the native GetAllNodes method.
     * This method is asynchronous; the result will be sent to the callback.
     */
    @VisibleForTesting
    public void getAllNodes(GetAllNodesCallback callback) {
        nativeGetAllNodes(mNativeProfileSyncServiceAndroid, callback);
    }

    // Native methods
    private native long nativeInit();
    private native void nativeRequestStart(long nativeProfileSyncServiceAndroid);
    private native void nativeRequestStop(long nativeProfileSyncServiceAndroid);
    private native void nativeFlushDirectory(long nativeProfileSyncServiceAndroid);
    private native void nativeSignOutSync(long nativeProfileSyncServiceAndroid);
    private native void nativeSetSyncSessionsId(long nativeProfileSyncServiceAndroid, String tag);
    private native String nativeQuerySyncStatusSummary(long nativeProfileSyncServiceAndroid);
    private native int nativeGetAuthError(long nativeProfileSyncServiceAndroid);
    private native boolean nativeIsBackendInitialized(long nativeProfileSyncServiceAndroid);
    private native boolean nativeIsFirstSetupInProgress(long nativeProfileSyncServiceAndroid);
    private native boolean nativeIsEncryptEverythingAllowed(long nativeProfileSyncServiceAndroid);
    private native boolean nativeIsEncryptEverythingEnabled(long nativeProfileSyncServiceAndroid);
    private native void nativeEnableEncryptEverything(long nativeProfileSyncServiceAndroid);
    private native boolean nativeIsPassphraseRequiredForDecryption(
            long nativeProfileSyncServiceAndroid);
    private native boolean nativeIsUsingSecondaryPassphrase(long nativeProfileSyncServiceAndroid);
    private native boolean nativeSetDecryptionPassphrase(
            long nativeProfileSyncServiceAndroid, String passphrase);
    private native void nativeSetEncryptionPassphrase(
            long nativeProfileSyncServiceAndroid, String passphrase);
    private native boolean nativeIsCryptographerReady(long nativeProfileSyncServiceAndroid);
    private native int nativeGetPassphraseType(long nativeProfileSyncServiceAndroid);
    private native boolean nativeHasExplicitPassphraseTime(long nativeProfileSyncServiceAndroid);
    private native long nativeGetExplicitPassphraseTime(long nativeProfileSyncServiceAndroid);
    private native String nativeGetSyncEnterGooglePassphraseBodyWithDateText(
            long nativeProfileSyncServiceAndroid);
    private native String nativeGetSyncEnterCustomPassphraseBodyWithDateText(
            long nativeProfileSyncServiceAndroid);
    private native String nativeGetCurrentSignedInAccountText(long nativeProfileSyncServiceAndroid);
    private native String nativeGetSyncEnterCustomPassphraseBodyText(
            long nativeProfileSyncServiceAndroid);
    private native int[] nativeGetActiveDataTypes(long nativeProfileSyncServiceAndroid);
    private native int[] nativeGetPreferredDataTypes(long nativeProfileSyncServiceAndroid);
    private native void nativeSetPreferredDataTypes(
            long nativeProfileSyncServiceAndroid, boolean syncEverything, int[] modelTypeArray);
    private native void nativeSetSetupInProgress(
            long nativeProfileSyncServiceAndroid, boolean inProgress);
    private native void nativeSetSyncSetupCompleted(long nativeProfileSyncServiceAndroid);
    private native boolean nativeHasSyncSetupCompleted(long nativeProfileSyncServiceAndroid);
    private native boolean nativeIsSyncRequested(long nativeProfileSyncServiceAndroid);
    private native boolean nativeIsSyncActive(long nativeProfileSyncServiceAndroid);
    private native boolean nativeHasKeepEverythingSynced(long nativeProfileSyncServiceAndroid);
    private native boolean nativeHasUnrecoverableError(long nativeProfileSyncServiceAndroid);
    private native boolean nativeIsPassphrasePrompted(long nativeProfileSyncServiceAndroid);
    private native void nativeSetPassphrasePrompted(long nativeProfileSyncServiceAndroid,
                                                    boolean prompted);
    private native String nativeGetAboutInfoForTest(long nativeProfileSyncServiceAndroid);
    private native long nativeGetLastSyncedTimeForTest(long nativeProfileSyncServiceAndroid);
    private native void nativeOverrideNetworkResourcesForTest(
            long nativeProfileSyncServiceAndroid, long networkResources);
    private native void nativeGetAllNodes(
            long nativeProfileSyncServiceAndroid, GetAllNodesCallback callback);
}
