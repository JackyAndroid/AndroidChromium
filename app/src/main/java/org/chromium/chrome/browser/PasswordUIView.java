// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.ObserverList;
import org.chromium.base.annotations.CalledByNative;

/**
 * Class for retrieving passwords and password exceptions (websites for which Chrome should not save
 * password) from native code.
 */
public final class PasswordUIView {

    /**
     * Class representing information about a saved password entry.
     */
    public static final class SavedPasswordEntry {
        private final String mUrl;
        private final String mName;

        private SavedPasswordEntry(String url, String name) {
            mUrl = url;
            mName = name;
        }

        public String getUrl() {
            return mUrl;
        }

        public String getUserName() {
            return mName;
        }
    }

    @CalledByNative
    private static SavedPasswordEntry createSavedPasswordEntry(String url, String name) {
        return new SavedPasswordEntry(url, name);
    }

    /**
     * Interface which client can use to listen to changes to password and password exception lists.
     * Clients can register and unregister themselves with addObserver and removeObserver.
     */
    public interface PasswordListObserver {
        /**
         * Called when passwords list is updated.
         * @param count Number of entries in the password list.
         */
        void passwordListAvailable(int count);

        /**
         * Called when password exceptions list is updated.
         * @param count Number of entries in the password exception list.
         */
        void passwordExceptionListAvailable(int count);
    }

    private ObserverList<PasswordListObserver> mObservers =
            new ObserverList<PasswordListObserver>();

    // Pointer to native implementation, set to 0 in destroy().
    private long mNativePasswordUIViewAndroid;

    /**
     * Constructor creates the native object as well. Callers should call destroy() after usage.
     */
    public PasswordUIView() {
        mNativePasswordUIViewAndroid = nativeInit();
    }

    @CalledByNative
    private void passwordListAvailable(int count) {
        for (PasswordListObserver observer : mObservers) {
            observer.passwordListAvailable(count);
        }
    }

    @CalledByNative
    private void passwordExceptionListAvailable(int count) {
        for (PasswordListObserver observer : mObservers) {
            observer.passwordExceptionListAvailable(count);
        }
    }

    public void addObserver(PasswordListObserver observer) {
        mObservers.addObserver(observer);
    }

    public void removeObserver(PasswordListObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Calls native to refresh password and exception lists. Observers are notified when fetch to
     * passwords is complete.
     */
    public void updatePasswordLists() {
        nativeUpdatePasswordLists(mNativePasswordUIViewAndroid);
    }

    /**
     * Get the saved password entry at index.
     *
     * @param index Index of Password.
     * @return SavedPasswordEntry at index.
     */
    public SavedPasswordEntry getSavedPasswordEntry(int index) {
        return nativeGetSavedPasswordEntry(mNativePasswordUIViewAndroid, index);
    }

    /**
     * Get saved password exception at index.
     *
     * @param index of exception
     * @return Origin of password exception.
     */
    public String getSavedPasswordException(int index) {
        return nativeGetSavedPasswordException(mNativePasswordUIViewAndroid, index);
    }

    /**
     * Remove saved password entry at index.
     *
     * @param index of password entry to remove.
     */
    public void removeSavedPasswordEntry(int index) {
        nativeHandleRemoveSavedPasswordEntry(mNativePasswordUIViewAndroid, index);
    }

    /**
     * Remove saved exception entry at index.
     *
     * @param index of exception entry.
     */
    public void removeSavedPasswordException(int index) {
        nativeHandleRemoveSavedPasswordException(mNativePasswordUIViewAndroid, index);
    }

    public static String getAccountDashboardURL() {
        return nativeGetAccountDashboardURL();
    }

    public static boolean shouldUseSmartLockBranding() {
        return nativeShouldUseSmartLockBranding();
    }

    /**
     * Destroy the native object.
     */
    public void destroy() {
        if (mNativePasswordUIViewAndroid != 0) {
            nativeDestroy(mNativePasswordUIViewAndroid);
            mNativePasswordUIViewAndroid = 0;
        }
        mObservers.clear();
    }

    private native long nativeInit();

    private native void nativeUpdatePasswordLists(long nativePasswordUIViewAndroid);

    private native SavedPasswordEntry nativeGetSavedPasswordEntry(
            long nativePasswordUIViewAndroid,
            int index);

    private native String nativeGetSavedPasswordException(long nativePasswordUIViewAndroid,
            int index);

    private native void nativeHandleRemoveSavedPasswordEntry(
            long nativePasswordUIViewAndroid,
            int index);

    private native void nativeHandleRemoveSavedPasswordException(
            long nativePasswordUIViewAndroid,
            int index);

    private static native String nativeGetAccountDashboardURL();

    private static native boolean nativeShouldUseSmartLockBranding();

    private native void nativeDestroy(long nativePasswordUIViewAndroid);

}
