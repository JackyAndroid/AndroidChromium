// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.precache;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.components.precache.DeviceState;
import org.chromium.content.browser.BrowserStartupController;

/**
 * Background service that runs while Chrome is precaching resources. Precaching is only done while
 * the device is connected to power, Wi-Fi, and the device is not interactive (e.g., the screen is
 * off. This is a sticky service that is started when the {@link PrecacheServiceLauncher}
 * determines that conditions are right for precaching. Once started, this service runs until
 * either precaching finishes successfully, or the conditions are no longer met.
 */
public class PrecacheService extends Service {
    private static final String TAG = "Precache";

    public static final String ACTION_START_PRECACHE =
            "org.chromium.chrome.browser.precache.PrecacheService.START_PRECACHE";

    /** Wakelock that is held while precaching is in progress. */
    private WakeLock mPrecachingWakeLock;

    /** True if there is a precache in progress. */
    private static boolean sIsPrecaching = false;

    /** Returns true if there is a precache in progress. */
    public static boolean isPrecaching() {
        return sIsPrecaching;
    }

    @VisibleForTesting
    static void setIsPrecaching(boolean isPrecaching) {
        sIsPrecaching = isPrecaching;
    }

    private DeviceState mDeviceState = DeviceState.getInstance();

    @VisibleForTesting
    void setDeviceState(DeviceState deviceState) {
        mDeviceState = deviceState;
    }


    /** Receiver that will be notified when conditions become wrong for precaching. */
    private final BroadcastReceiver mDeviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (sIsPrecaching && (!mDeviceState.isPowerConnected(context)
                                         || !mDeviceState.isWifiAvailable(context))) {
                cancelPrecaching();
            }
        }
    };

    @VisibleForTesting
    BroadcastReceiver getDeviceStateReceiver() {
        return mDeviceStateReceiver;
    }

    @VisibleForTesting
    void handlePrecacheCompleted(boolean tryAgainSoon) {
        if (sIsPrecaching) finishPrecaching(tryAgainSoon);
    }

    /** PrecacheLauncher used to run precaching. */
    private PrecacheLauncher mPrecacheLauncher = new PrecacheLauncher() {
        @Override
        protected void onPrecacheCompleted(boolean tryAgainSoon) {
            handlePrecacheCompleted(tryAgainSoon);
        }
    };

    @VisibleForTesting
    void setPrecacheLauncher(PrecacheLauncher precacheLauncher) {
        mPrecacheLauncher = precacheLauncher;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerDeviceStateReceiver();
    }

    @Override
    public void onDestroy() {
        if (sIsPrecaching) cancelPrecaching();

        unregisterReceiver(mDeviceStateReceiver);
        mPrecacheLauncher.destroy();
        releasePrecachingWakeLock();

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null && ACTION_START_PRECACHE.equals(intent.getAction())
                    && !sIsPrecaching) {
                // Only start precaching if precaching is not already in progress.
                startPrecaching();
            }
        } finally {
            PrecacheServiceLauncher.releaseWakeLock();
        }
        // The PrecacheService should only be running while precaching is in progress. Return
        // {@link Service.START_STICKY} if precaching is in progress to keep the service running;
        // otherwise, return {@link Service.START_NOT_STICKY} to cause the service to stop once it
        // is done processing commands sent to it.
        return sIsPrecaching ? START_STICKY : START_NOT_STICKY;
    }

    /** PrecacheService does not support binding. */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Attempt to start up the browser processes and load the native libraries. */
    @SuppressFBWarnings("DM_EXIT")
    @VisibleForTesting
    void prepareNativeLibraries() {
        try {
            BrowserStartupController.get(getApplicationContext(),
                                             LibraryProcessType.PROCESS_BROWSER)
                    .startBrowserProcessesSync(false);
        } catch (ProcessInitException e) {
            Log.e(TAG, "ProcessInitException while starting the browser process");
            // Since the library failed to initialize nothing in the application
            // can work, so kill the whole application not just the activity.
            System.exit(-1);
        }
    }

    /** Begin a precache cycle. */
    private void startPrecaching() {
        Log.v(TAG, "Start precaching");
        prepareNativeLibraries();
        sIsPrecaching = true;
        acquirePrecachingWakeLock();

        // In certain cases, the PrecacheLauncher will skip precaching entirely and call
        // finishPrecaching() before this call to mPrecacheLauncher.start() returns, so the call to
        // mPrecacheLauncher.start() must happen after acquiring the wake lock to ensure that the
        // wake lock is released properly.
        mPrecacheLauncher.start();
    }

    /** End a precache cycle. */
    private void finishPrecaching(boolean tryAgainSoon) {
        Log.v(TAG, "Finish precaching");
        shutdownPrecaching(tryAgainSoon);
    }

    /** Cancel a precache cycle. */
    private void cancelPrecaching() {
        Log.v(TAG, "Cancel precaching");
        prepareNativeLibraries();
        mPrecacheLauncher.cancel();

        shutdownPrecaching(true);
    }

    /**
     * Update state to indicate that precaching is no longer in progress, and stop the service.
     */
    private void shutdownPrecaching(boolean tryAgainSoon) {
        sIsPrecaching = false;
        releasePrecachingWakeLock();
        PrecacheServiceLauncher.precachingFinished(getApplicationContext(), tryAgainSoon);
        stopSelf();
    }

    /** Register a BroadcastReceiver to detect when conditions become wrong for precaching. */
    private void registerDeviceStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mDeviceStateReceiver, filter);
    }

    /** Acquire the precaching WakeLock. */
    private void acquirePrecachingWakeLock() {
        if (mPrecachingWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mPrecachingWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        mPrecachingWakeLock.acquire();
    }

    /** Release the precaching WakeLock if it is held. */
    private void releasePrecachingWakeLock() {
        if (mPrecachingWakeLock != null && mPrecachingWakeLock.isHeld()) {
            mPrecachingWakeLock.release();
        }
    }
}
