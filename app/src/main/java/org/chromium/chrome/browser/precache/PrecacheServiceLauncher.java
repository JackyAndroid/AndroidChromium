// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.precache;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.components.precache.DeviceState;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Queue;

/**
 * BroadcastReceiver that determines when conditions are right for precaching, and starts the
 * {@link PrecacheService} if they are. Conditions are right for precaching when the device is
 * connected to power, Wi-Fi, interactivity (e.g., the screen) is off, and at least
 * |WAIT_UNTIL_NEXT_PRECACHE_MS| have passed since the last time precaching was done.
 */
public class PrecacheServiceLauncher extends BroadcastReceiver {
    private static final String TAG = "Precache";

    @VisibleForTesting
    static final String PREF_IS_PRECACHING_ENABLED = "precache.is_precaching_enabled";

    static final String PREF_LAST_ELAPSED_TIME = "precache.last_elapsed_time";

    @VisibleForTesting
    static final String PREF_PRECACHE_LAST_TIME = "precache.last_time";

    @VisibleForTesting
    static final String ACTION_ALARM =
            "org.chromium.chrome.browser.precache.PrecacheServiceLauncher.ALARM";

    private static final int INTERACTIVE_STATE_POLLING_PERIOD_MS = 15 * 60 * 1000;  // 15 minutes.
    static final int WAIT_UNTIL_NEXT_PRECACHE_MS = 4 * 60 * 60 * 1000;  // 4 hours.

    private static WakeLock sWakeLock = null;

    private DeviceState mDeviceState = DeviceState.getInstance();

    private PrecacheLauncher mPrecacheLauncher = PrecacheLauncher.get();

    private Queue<Integer> mFailureReasonsToRecord = new ArrayDeque<Integer>();

    @VisibleForTesting
    void setDeviceState(DeviceState deviceState) {
        mDeviceState = deviceState;
    }

    @VisibleForTesting
    void setPrecacheLauncher(PrecacheLauncher precacheLauncher) {
        mPrecacheLauncher = precacheLauncher;
    }

    /**
     * Set whether or not precaching is enabled. If precaching is enabled, this receiver will start
     * the PrecacheService when it receives an intent. If precaching is disabled, any running
     * PrecacheService will be stopped, and this receiver will do nothing when it receives an
     * intent.
     *
     * @param context the application context
     * @param enabled whether or not precaching is enabled
     */
    public static void setIsPrecachingEnabled(Context context, boolean enabled) {
        Log.v(TAG, "setIsPrecachingEnabled(%s)", enabled);
        // |context| needs to be the application context so that the following call gets a
        // reference to a consistent SharedPreferences object.
        Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(PREF_IS_PRECACHING_ENABLED, enabled);
        editor.apply();

        if (!enabled) {
            // Stop any running PrecacheService. If PrecacheService is not running, then this does
            // nothing.
            Intent serviceIntent = new Intent(null, null, context, PrecacheService.class);
            context.stopService(serviceIntent);
        }
    }

    /**
     * Handler for when precaching has finished. If precaching had successfully started, sets an
     * alarm for a subsequent run, and updates the last precache time. If not (i.e. the precache
     * attempt aborted), sets an alarm for another attempt.
     *
     * @param context the application context
     * @param tryAgainSoon whether precaching should be attempted again
     */
    public static void precachingFinished(Context context, boolean tryAgainSoon) {
        new PrecacheServiceLauncher().precachingFinishedInternal(context, tryAgainSoon);
    }

    private void precachingFinishedInternal(Context context, boolean tryAgainSoon) {
        Log.v(TAG, "precachingFinished(%s)", tryAgainSoon);
        if (tryAgainSoon) {
            setAlarm(context,
                    Math.max(INTERACTIVE_STATE_POLLING_PERIOD_MS,
                            WAIT_UNTIL_NEXT_PRECACHE_MS - timeSinceLastPrecacheMs(context)));
        } else {
            // Store a pref indicating that precaching finished just now.
            setAlarm(context,
                    Math.max(INTERACTIVE_STATE_POLLING_PERIOD_MS, WAIT_UNTIL_NEXT_PRECACHE_MS));

            Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
            editor.putLong(PREF_PRECACHE_LAST_TIME, getElapsedRealtimeOnSystem());
            editor.apply();
        }
    }

    /**
     * Returns true if precaching is able to run. Set by PrecacheLauncher#updatePrecachingEnabled.
     *
     * @param context the application context
     */
    @VisibleForTesting
    static boolean isPrecachingEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_IS_PRECACHING_ENABLED, false);
    }

    /**
     * Returns the set of reasons that the last prefetch attempt failed to start.
     *
     * @param context the context passed to onReceive()
     */
    @VisibleForTesting
    EnumSet<FailureReason> failureReasons(Context context) {
        EnumSet<FailureReason> reasons = EnumSet.noneOf(FailureReason.class);
        reasons.addAll(mPrecacheLauncher.failureReasons());
        if (!mDeviceState.isPowerConnected(context)) reasons.add(FailureReason.NO_POWER);
        if (!mDeviceState.isWifiAvailable(context)) reasons.add(FailureReason.NO_WIFI);
        if (timeSinceLastPrecacheMs(context) < WAIT_UNTIL_NEXT_PRECACHE_MS) {
            reasons.add(FailureReason.NOT_ENOUGH_TIME_SINCE_LAST_PRECACHE);
        }
        if (PrecacheService.isPrecaching()) reasons.add(FailureReason.CURRENTLY_PRECACHING);
        return reasons;
    }

    /**
     * Tries to record a histogram enumerating all of the return value of failureReasons().
     *
     * If the native libraries are not already loaded, no histogram is recorded.
     *
     * @param context the context passed to onReceive()
     */
    @VisibleForTesting
    void recordFailureReasons(Context context) {
        int reasons = FailureReason.bitValue(failureReasons(context));

        // Queue up this failure reason, for the next time we are able to record it in UMA.
        mFailureReasonsToRecord.add(reasons);

        // If native libraries are loaded, then we are able to flush our queue to UMA.
        if (LibraryLoader.isInitialized()) {
            Integer reasonsToRecord;
            while ((reasonsToRecord = mFailureReasonsToRecord.poll()) != null) {
                RecordHistogram.recordSparseSlowlyHistogram(
                        "Precache.Fetch.FailureReasons", reasonsToRecord);
                RecordUserAction.record("Precache.Fetch.IntentReceived");
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        resetLastPrecacheMsIfDeviceRebooted(context);

        // TODO(twifkak): Make these triggering conditions (power, screen, wifi, time) controllable
        // via variation parameters. Also change the cancel conditions in PrecacheService.
        boolean isPowerConnected = mDeviceState.isPowerConnected(context);
        boolean isWifiAvailable = mDeviceState.isWifiAvailable(context);
        boolean areConditionsGoodForPrecaching = isPowerConnected && isWifiAvailable;
        boolean hasEnoughTimePassedSinceLastPrecache =
                timeSinceLastPrecacheMs(context) >= WAIT_UNTIL_NEXT_PRECACHE_MS;

        // Do nothing if precaching is disabled.
        if (!isPrecachingEnabled(context.getApplicationContext())) {
            recordFailureReasons(context);
            return;
        }

        if (areConditionsGoodForPrecaching) {
            if (hasEnoughTimePassedSinceLastPrecache) {
                recordFailureReasons(context); // Record success.
                acquireWakeLockAndStartService(context);
            } else {
                // If we're just waiting for for enough time to pass after Wi-Fi or power has been
                // connected, then set an alarm for the next time to check the device state.
                // Don't call record failure reasons when setting an alarm to retry. These cases are
                // uninteresting.
                setAlarm(context,
                        Math.max(INTERACTIVE_STATE_POLLING_PERIOD_MS,
                                WAIT_UNTIL_NEXT_PRECACHE_MS - timeSinceLastPrecacheMs(context)));
            }
        } else {
            recordFailureReasons(context);
            // If the device doesn't have connected power or doesn't have Wi-Fi, then there's no
            // point in setting an alarm.
            cancelAlarm(context);
        }
    }

    /** Release the wakelock if it is held. */
    @VisibleForTesting
    protected static void releaseWakeLock() {
        ThreadUtils.assertOnUiThread();
        if (sWakeLock != null && sWakeLock.isHeld()) sWakeLock.release();
    }

    /**
     * Acquire the wakelock and start the PrecacheService.
     *
     * @param context the Context to use to start the service
     */
    private void acquireWakeLockAndStartService(Context context) {
        acquireWakeLock(context);
        startPrecacheService(context);
    }

    @VisibleForTesting
    protected void startPrecacheService(Context context) {
        Intent serviceIntent = new Intent(
                PrecacheService.ACTION_START_PRECACHE, null, context, PrecacheService.class);
        context.startService(serviceIntent);
    }

    @VisibleForTesting
    protected void acquireWakeLock(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sWakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        sWakeLock.acquire();
    }

    /**
     * Get a PendingIntent for setting an alarm to notify the PrecacheServiceLauncher.
     *
     * @param context the Context to use for the PendingIntent
     * @return The PendingIntent.
     */
    private static PendingIntent getPendingAlarmIntent(Context context) {
        return PendingIntent.getBroadcast(context, 0,
                new Intent(ACTION_ALARM, null, context, PrecacheServiceLauncher.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Set an alarm to notify the PrecacheServiceLauncher in the future.
     *
     * @param context the Context to use
     * @param delayMs delay in milliseconds before the alarm goes off
     */
    private void setAlarm(Context context, long delayMs) {
        setAlarmOnSystem(context, AlarmManager.ELAPSED_REALTIME_WAKEUP,
                getElapsedRealtimeOnSystem() + delayMs, getPendingAlarmIntent(context));
    }

    /**
     * Set the alarm on the system using the given parameters. This method can be overridden in
     * tests.
     */
    @VisibleForTesting
    protected void setAlarmOnSystem(Context context, int type, long triggerAtMillis,
            PendingIntent operation) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(type, triggerAtMillis, operation);
    }

    /**
     * Cancel a previously set alarm, if there is one. This method can be overridden in tests.
     *
     * @param context the Context to use
     */
    private void cancelAlarm(Context context) {
        cancelAlarmOnSystem(context, getPendingAlarmIntent(context));
    }

    /** Cancel a previously set alarm on the system. This method can be overridden in tests. */
    @VisibleForTesting
    protected void cancelAlarmOnSystem(Context context, PendingIntent operation) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(operation);
    }

    @VisibleForTesting
    protected long getElapsedRealtimeOnSystem() {
        return SystemClock.elapsedRealtime();
    }

    /** Returns the number of milliseconds since the last precache run completed. */
    private long timeSinceLastPrecacheMs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // If precache has never run, default to a negative number such that precache can run
        // immediately (if conditions are good).
        long lastPrecacheTimeMs =
                prefs.getLong(PREF_PRECACHE_LAST_TIME, -WAIT_UNTIL_NEXT_PRECACHE_MS);
        return getElapsedRealtimeOnSystem() - lastPrecacheTimeMs;
    }

    /** Reset PREF_PRECACHE_LAST_TIME if it appears the device has rebooted. */
    private void resetLastPrecacheMsIfDeviceRebooted(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long lastElapsedTime = prefs.getLong(PREF_LAST_ELAPSED_TIME, 0L);
        long elapsedTime = getElapsedRealtimeOnSystem();

        Editor editor = prefs.edit();
        if (elapsedTime < lastElapsedTime) {
            // If the elapsed-time clock has gone backwards, this means the system has rebooted.
            // Reset the last precache time as it is no longer valid.
            editor.remove(PREF_PRECACHE_LAST_TIME);
        }
        editor.putLong(PREF_LAST_ELAPSED_TIME, elapsedTime);
        editor.apply();
    }
}

