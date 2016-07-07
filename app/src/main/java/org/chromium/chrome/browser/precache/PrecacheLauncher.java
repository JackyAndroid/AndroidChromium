// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.precache;

import android.content.Context;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.sync.ProfileSyncService;

import java.util.EnumSet;

/** Class that interacts with the PrecacheManager to control precache cycles. */
public abstract class PrecacheLauncher {
    private static final String TAG = "Precache";

    private static final PrecacheLauncher sInstance = new PrecacheLauncher() {
        /** A null implementation, as it is not needed by clients of sInstance. */
        @Override
        protected void onPrecacheCompleted(boolean tryAgainSoon) {}
    };

    /** Returns the singleton instance of PrecacheLauncher. */
    public static PrecacheLauncher get() {
        return sInstance;
    }

    /** Pointer to the native PrecacheLauncher object. Set to 0 when uninitialized. */
    private long mNativePrecacheLauncher;

    /**
     * Initialized by updateEnabled to call updateEnabledSync when the sync backend is initialized.
     * Only accessed on the UI thread.
     */
    private ProfileSyncService.SyncStateChangedListener mListener = null;

    /**
     * Boolean failure indicators, reflecting the state of the last call to updatePrecachingEnabled.
     * Access must occur on the UI thread. Values default to false -- so if mCalled is false, the
     * value of the other booleans is not necessarily valid.
     */
    private boolean mCalled = false;
    private boolean mSyncInitialized = false;
    private boolean mNetworkPredictionsAllowed = false;
    private boolean mShouldRun = false;

    /** Destroy the native PrecacheLauncher, releasing the memory that it was using. */
    public void destroy() {
        if (mNativePrecacheLauncher != 0) {
            nativeDestroy(mNativePrecacheLauncher);
            mNativePrecacheLauncher = 0;
        }
    }

    /** Starts a precache cycle. */
    public void start() {
        // Lazily initialize the native PrecacheLauncher.
        if (mNativePrecacheLauncher == 0) {
            mNativePrecacheLauncher = nativeInit();
        }
        nativeStart(mNativePrecacheLauncher);
    }

    /** Cancel the precache cycle if one is ongoing. */
    public void cancel() {
        // Lazily initialize the native PrecacheLauncher.
        if (mNativePrecacheLauncher == 0) {
            mNativePrecacheLauncher = nativeInit();
        }
        nativeCancel(mNativePrecacheLauncher);
    }

    /**
     * Called when a precache cycle completes.
     *
     * @param tryAgainSoon true iff the precache failed to start due to a transient error and should
     * be attempted again soon
     */
    protected abstract void onPrecacheCompleted(boolean tryAgainSoon);

    /**
     * Called by native code when the precache cycle completes. This method exists because an
     * abstract method cannot be directly called from native.
     *
     * @param tryAgainSoon true iff the precache failed to start due to a transient error and should
     * be attempted again soon
     */
    @CalledByNative
    private void onPrecacheCompletedCallback(boolean tryAgainSoon) {
        onPrecacheCompleted(tryAgainSoon);
    }

    /**
     * Updates the PrecacheServiceLauncher with whether conditions are right for precaching. All of
     * the following must be true:
     *
     * <ul>
     *   <li>The predictive network actions preference is enabled.</li>
     *   <li>The current network type is suitable for predictive network actions.</li>
     *   <li>Sync is enabled for sessions and it is not encrypted with a secondary passphrase.</li>
     *   <li>Either the Precache field trial or the precache commandline flag is enabled.</li>
     * </ul>
     *
     * This should be called only after the sync backend has been initialized. Must be called on the
     * UI thread.
     *
     * @param context any context within the application
     */
    private void updateEnabledSync(Context context) {
        // PrefServiceBridge.getInstance() and nativeShouldRun() can only be executed on the UI
        // thread.
        ThreadUtils.assertOnUiThread();

        boolean networkPredictionsAllowed =
                PrefServiceBridge.getInstance().canPredictNetworkActions();
        boolean shouldRun = nativeShouldRun();

        mNetworkPredictionsAllowed = networkPredictionsAllowed;
        mShouldRun = shouldRun;

        PrecacheServiceLauncher.setIsPrecachingEnabled(
                context.getApplicationContext(), networkPredictionsAllowed && shouldRun);
        Log.v(TAG, "updateEnabledSync complete");
    }

    /**
     * If precaching is enabled, then allow the PrecacheService to be launched and signal Chrome
     * when conditions are right to start precaching. If precaching is disabled, prevent the
     * PrecacheService from ever starting.
     *
     * @param context any context within the application
     */
    @VisibleForTesting
    void updateEnabled(final Context context) {
        Log.v(TAG, "updateEnabled starting");
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCalled = true;
                final ProfileSyncService sync = ProfileSyncService.get();

                if (mListener == null) {
                    mListener = new ProfileSyncService.SyncStateChangedListener() {
                        public void syncStateChanged() {
                            if (sync.isBackendInitialized()) {
                                mSyncInitialized = true;
                                updateEnabledSync(context);
                            }
                        }
                    };
                    sync.addSyncStateChangedListener(mListener);
                }

                // Call the listener once, in case the sync backend is already initialized.
                mListener.syncStateChanged();
                Log.v(TAG, "updateEnabled complete");
            }
        });
    }

    /**
     * If precaching is enabled, then allow the PrecacheService to be launched and signal Chrome
     * when conditions are right to start precaching. If precaching is disabled, prevent the
     * PrecacheService from ever starting.
     *
     * @param context any context within the application
     */
    public static void updatePrecachingEnabled(final Context context) {
        sInstance.updateEnabled(context);
    }

    /** Returns the set of reasons that the "precache.is_precaching_enabled" pref is false. */
    public EnumSet<FailureReason> failureReasons() {
        ThreadUtils.assertOnUiThread();
        EnumSet<FailureReason> reasons = EnumSet.noneOf(FailureReason.class);
        if (!mCalled) reasons.add(FailureReason.UPDATE_PRECACHING_ENABLED_NEVER_CALLED);
        if (!mSyncInitialized) reasons.add(FailureReason.SYNC_NOT_INITIALIZED);
        if (!mNetworkPredictionsAllowed) {
            reasons.add(FailureReason.PRERENDER_PRIVACY_PREFERENCE_NOT_ENABLED);
        }
        if (!mShouldRun) reasons.add(FailureReason.NATIVE_SHOULD_RUN_IS_FALSE);
        return reasons;
    }

    private native long nativeInit();
    private native void nativeDestroy(long nativePrecacheLauncher);
    private native void nativeStart(long nativePrecacheLauncher);
    private native void nativeCancel(long nativePrecacheLauncher);

    @VisibleForTesting native boolean nativeShouldRun();
}
