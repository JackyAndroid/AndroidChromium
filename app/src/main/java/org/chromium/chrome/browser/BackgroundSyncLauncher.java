// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.StrictMode;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;

/**
 * The {@link BackgroundSyncLauncher} singleton is created and owned by the C++ browser. It
 * registers interest in waking up the browser the next time the device goes online after the
 * browser closes via the {@link #setLaunchWhenNextOnline} method.
 *
 * Thread model: This class is to be run on the UI thread only.
 */
public class BackgroundSyncLauncher {
    private static final String TAG = "BgSyncLauncher";

    public static final String TASK_TAG = "BackgroundSync Event";

    static final String PREF_BACKGROUND_SYNC_LAUNCH_NEXT_ONLINE = "bgsync_launch_next_online";
    // The instance of BackgroundSyncLauncher currently owned by a C++
    // BackgroundSyncLauncherAndroid, if any. If it is non-null then the browser is running.
    private static BackgroundSyncLauncher sInstance;

    private GcmNetworkManager mScheduler;

    /**
     * Disables the automatic use of the GCMNetworkManager. When disabled, the methods which
     * interact with GCM can still be used, but will not be called automatically on creation, or by
     * {@link #launchBrowserIfStopped}.
     *
     * Automatic GCM use is disabled by tests, and also by this class if it is determined on
     * creation that the installed Play Services library is out of date.
     */
    private static boolean sGCMEnabled = true;

    /**
     * Create a BackgroundSyncLauncher object, which is owned by C++.
     * @param context The app context.
     */
    @VisibleForTesting
    @CalledByNative
    protected static BackgroundSyncLauncher create(Context context) {
        if (sInstance != null) {
            throw new IllegalStateException("Already instantiated");
        }

        sInstance = new BackgroundSyncLauncher(context);
        return sInstance;
    }

    /**
     * Called when the C++ counterpart is deleted.
     */
    @VisibleForTesting
    @CalledByNative
    protected void destroy() {
        assert sInstance == this;
        sInstance = null;
    }

    /**
     * Callback for {@link #shouldLaunchBrowserIfStopped}. The run method is invoked on the UI
     * thread.
     */
    public static interface ShouldLaunchCallback { public void run(Boolean shouldLaunch); }

    /**
     * Returns whether the browser should be launched when the device next goes online.
     * This is set by C++ and reset to false each time {@link BackgroundSyncLauncher}'s singleton is
     * created (the native browser is started). This call is asynchronous and will run the callback
     * on the UI thread when complete.
     * @param context The application context.
     * @param sharedPreferences The shared preferences.
     */
    protected static void shouldLaunchBrowserIfStopped(
            final Context context, final ShouldLaunchCallback callback) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
                return prefs.getBoolean(PREF_BACKGROUND_SYNC_LAUNCH_NEXT_ONLINE, false);
            }
            @Override
            protected void onPostExecute(Boolean shouldLaunch) {
                callback.run(shouldLaunch);
            }
        }.execute();
    }

    /**
     * Manages the scheduled tasks which re-launch the browser when the device next goes online
     * after at least {@code minDelayMs} milliseconds.
     * This method is called by C++ as background sync registrations are added and removed. When the
     * {@link BackgroundSyncLauncher} singleton is created (on browser start), this is called to
     * remove any pre-existing scheduled tasks.
     * @param context The application context.
     * @param shouldLaunch Whether or not to launch the browser in the background.
     * @param minDelayMs The minimum time to wait before checking on the browser process.
     */
    @VisibleForTesting
    @CalledByNative
    protected void launchBrowserIfStopped(
            final Context context, final boolean shouldLaunch, final long minDelayMs) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
                prefs.edit()
                        .putBoolean(PREF_BACKGROUND_SYNC_LAUNCH_NEXT_ONLINE, shouldLaunch)
                        .apply();
                return null;
            }
            @Override
            protected void onPostExecute(Void params) {
                if (sGCMEnabled) {
                    if (shouldLaunch) {
                        RecordHistogram.recordBooleanHistogram(
                                "BackgroundSync.LaunchTask.ScheduleSuccess",
                                scheduleLaunchTask(context, mScheduler, minDelayMs));
                    } else {
                        RecordHistogram.recordBooleanHistogram(
                                "BackgroundSync.LaunchTask.CancelSuccess",
                                removeScheduledTasks(mScheduler));
                    }
                }
            }
        }.execute();
    }

    /**
     * Returns true if the native browser has started and created an instance of {@link
     * BackgroundSyncLauncher}.
     */
    protected static boolean hasInstance() {
        return sInstance != null;
    }

    protected BackgroundSyncLauncher(Context context) {
        mScheduler = GcmNetworkManager.getInstance(context);
        launchBrowserIfStopped(context, false, 0);
    }

    private static boolean canUseGooglePlayServices(Context context) {
        return ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                context, new UserRecoverableErrorHandler.Silent());
    }

    /**
     * Returns true if the Background Sync Manager should be automatically disabled on startup.
     * This is currently only the case if Play Services is not up to date, since any sync attempts
     * which fail cannot be reregistered. Better to wait until Play Services is updated before
     * attempting them.
     *
     * @param context The application context.
     */
    @CalledByNative
    private static boolean shouldDisableBackgroundSync(Context context) {
        // Check to see if Play Services is up to date, and disable GCM if not.
        // This will not automatically set {@link sGCMEnabled} to true, in case it has been
        // disabled in tests.
        if (sGCMEnabled) {
            boolean isAvailable = true;
            if (!canUseGooglePlayServices(context)) {
                setGCMEnabled(false);
                Log.i(TAG, "Disabling Background Sync because Play Services is not up to date.");
                isAvailable = false;
            }
            RecordHistogram.recordBooleanHistogram(
                    "BackgroundSync.LaunchTask.PlayServicesAvailable", isAvailable);
        }
        return !sGCMEnabled;
    }

    private static boolean scheduleLaunchTask(
            Context context, GcmNetworkManager scheduler, long minDelayMs) {
        // Google Play Services may not be up to date, if the application was not installed through
        // the Play Store. In this case, scheduling the task will fail silently.
        final long minDelaySecs = minDelayMs / 1000;
        OneoffTask oneoff = new OneoffTask.Builder()
                                    .setService(ChromeBackgroundService.class)
                                    .setTag(TASK_TAG)
                                    // We have to set a non-zero execution window here
                                    .setExecutionWindow(minDelaySecs, minDelaySecs + 1)
                                    .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                                    .setPersisted(true)
                                    .setUpdateCurrent(true)
                                    .build();
        try {
            scheduler.schedule(oneoff);
        } catch (IllegalArgumentException e) {
            // Disable GCM for the remainder of this session.
            setGCMEnabled(false);
            // Return false so that the failure will be logged.
            return false;
        }
        return true;
    }

    private static boolean removeScheduledTasks(GcmNetworkManager scheduler) {
        // Third-party code causes broadcast to touch disk. http://crbug.com/614679
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            scheduler.cancelTask(TASK_TAG, ChromeBackgroundService.class);
        } catch (IllegalArgumentException e) {
            // This occurs when BackgroundSyncLauncherService is not found in the application
            // manifest. This should not happen in code that reaches here, but has been seen in
            // the past. See https://crbug.com/548314
            // Disable GCM for the remainder of this session.
            setGCMEnabled(false);
            // Return false so that the failure will be logged.
            return false;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
        return true;
    }

    /**
     * Reschedule any required background sync tasks, if they have been removed due to an
     * application upgrade.
     *
     * This method checks the saved preferences, and reschedules the sync tasks as appropriate
     * to match the preferences.
     * This method is static so that it can be run without actually instantiating a
     * BackgroundSyncLauncher.
     */
    protected static void rescheduleTasksOnUpgrade(final Context context) {
        final GcmNetworkManager scheduler = GcmNetworkManager.getInstance(context);
        BackgroundSyncLauncher.ShouldLaunchCallback callback =
                new BackgroundSyncLauncher.ShouldLaunchCallback() {
                    @Override
                    public void run(Boolean shouldLaunch) {
                        if (shouldLaunch) {
                            // It's unclear what time the sync event was supposed to fire, so fire
                            // without delay and let the browser reschedule if necessary.
                            // TODO(iclelland): If this fails, report the failure via UMA (not now,
                            // since the browser is not running, but on next startup.)
                            scheduleLaunchTask(context, scheduler, 0);
                        }
                    }
                };
        BackgroundSyncLauncher.shouldLaunchBrowserIfStopped(context, callback);
    }

    @VisibleForTesting
    static void setGCMEnabled(boolean enabled) {
        sGCMEnabled = enabled;
    }
}
