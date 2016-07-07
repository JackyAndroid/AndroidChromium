// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.invalidation.DelayedInvalidationsController;
import org.chromium.chrome.browser.omaha.OmahaClient;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors the event that indicates the screen is turning on.  Used to run actions that shouldn't
 * occur while the phone's screen is off (i.e. when the user expects the phone to be "asleep").
 */
public class PowerBroadcastReceiver extends BroadcastReceiver {
    private final AtomicBoolean mNeedToRunActions = new AtomicBoolean(true);
    private final AtomicBoolean mIsRegistered = new AtomicBoolean(false);
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private PowerManagerHelper mPowerManagerHelper;
    private ServiceRunnable mServiceRunnable;

    /**
     * Stubs out interaction with the PowerManager.
     */
    @VisibleForTesting
    static class PowerManagerHelper {
        /** @return whether the screen is on or not. */
        public boolean isScreenOn(Context context) {
            return ApiCompatibilityUtils.isInteractive(context);
        }
    }

    /**
     * Defines a set of actions to perform when the conditions are correct.
     */
    @VisibleForTesting
    public static class ServiceRunnable implements Runnable {
        /**
         * ANRs are triggered if the app fails to respond to a touch event within 5 seconds. Posting
         * this runnable after 5 seconds lets ChromeTabbedActivity.onResume() perform whatever more
         * important tasks are necessary.
         */
        private static final long DELAY_TO_POST_MS = 5000;

        /**
         * @returns how long the runnable should be delayed before it is run.
         */
        public long delayToRun() {
            return DELAY_TO_POST_MS;
        }

        /**
         * Unless testing, do not override this function.
         */
        @Override
        public void run() {
            Context context = ApplicationStatus.getApplicationContext();

            // Resume communication with the Omaha Update Server.
            if (ChromeVersionInfo.isOfficialBuild()) {
                Intent omahaIntent = OmahaClient.createInitializeIntent(context);
                context.startService(omahaIntent);
            }

            DelayedInvalidationsController.getInstance().notifyPendingInvalidations(context);
        }
    }

    public PowerBroadcastReceiver() {
        mServiceRunnable = new ServiceRunnable();
        mPowerManagerHelper = new PowerManagerHelper();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)
                && ApplicationStatus.hasVisibleActivities()) {
            runActions(context, false);
        }
    }

    /**
     * @return Whether or not this is registered with a context.
     */
    @VisibleForTesting
    public boolean isRegistered() {
        return mIsRegistered.get();
    }

    /**
     * Unregisters this broadcast receiver so it no longer receives Intents.
     * Also cancels any Runnables waiting to be executed.
     * @param context Context to unregister the receiver from.
     */
    public void unregisterReceiver(Context context) {
        mHandler.removeCallbacks(mServiceRunnable);
        if (mIsRegistered.getAndSet(false)) {
            context.unregisterReceiver(this);
            mNeedToRunActions.set(false);
        }
    }

    /**
     * Registers this broadcast receiver so it receives Intents.
     * This should only be done by the owning Activity.
     * @param context Context to register the receiver with.
     */
    public void registerReceiver(Context context) {
        assert Looper.getMainLooper() == Looper.myLooper();
        if (!mIsRegistered.getAndSet(true)) {
            context.registerReceiver(this, new IntentFilter(Intent.ACTION_SCREEN_ON));
            mNeedToRunActions.set(true);
        }
    }

    /**
     * Posts a task to run the necessary actions.  The task is delayed to prevent spin-locking in
     * ChromeTabbedActivity.onResume(): http://b/issue?id=5864891&query=5864891
     * @param onlyIfScreenIsOn Whether or not the screen must be on for the actions to be run.
     */
    public void runActions(Context context, boolean onlyIfScreenIsOn) {
        assert mServiceRunnable != null;
        assert mPowerManagerHelper != null;
        if (!onlyIfScreenIsOn || mPowerManagerHelper.isScreenOn(context)) {
            if (mNeedToRunActions.getAndSet(false)) {
                unregisterReceiver(context);
                mHandler.postDelayed(mServiceRunnable, mServiceRunnable.delayToRun());
            }
        }
    }

    /**
     * Sets the runnable that contains the actions to do when the screen is on.
     */
    @VisibleForTesting
    void setServiceRunnableForTests(ServiceRunnable runnable) {
        assert mServiceRunnable != null;
        mHandler.removeCallbacks(mServiceRunnable);
        mServiceRunnable = runnable;
    }

    /**
     * Sets the PowerManagerHelper that will be used to check if the screen is on.
     */
    @VisibleForTesting
    void setPowerManagerHelperForTests(PowerManagerHelper helper) {
        mPowerManagerHelper = helper;
    }
}
