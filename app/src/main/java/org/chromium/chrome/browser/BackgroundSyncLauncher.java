// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
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
    static final String PREF_BACKGROUND_SYNC_LAUNCH_NEXT_ONLINE = "bgsync_launch_next_online";
    // The instance of BackgroundSyncLauncher currently owned by a C++
    // BackgroundSyncLauncherAndroid, if any. If it is non-null then the browser is running.
    private static BackgroundSyncLauncher sInstance;

    private GcmNetworkManager mScheduler;

    /**
     * Disables the automatic use of the GCMNetworkManager. When disabled, the methods which
     * interact with GCM can still be used, but will not be called automatically on creation, or by
     * {@link #launchBrowserWhenNextOnlineIfStopped}.
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
     * Callback for {@link #shouldLaunchWhenNextOnline}. The run method is invoked on the UI thread.
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
    protected static void shouldLaunchWhenNextOnline(
            final Context context, final ShouldLaunchCallback callback) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                return prefs.getBoolean(PREF_BACKGROUND_SYNC_LAUNCH_NEXT_ONLINE, false);
            }
            @Override
            protected void onPostExecute(Boolean shouldLaunch) {
                callback.run(shouldLaunch);
            }
        }.execute();
    }

    /**
     * Manages the scheduled tasks which re-launch the browser when the device next goes online.
     * This method is called by C++ as background sync registrations are added and removed. When the
     * {@link BackgroundSyncLauncher} singleton is created (on browser start), this is called to
     * remove any pre-existing scheduled tasks.
     */
    @VisibleForTesting
    @CalledByNative
    protected void launchBrowserWhenNextOnlineIfStopped(
            final Context context, final boolean shouldLaunch) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                prefs.edit()
                        .putBoolean(PREF_BACKGROUND_SYNC_LAUNCH_NEXT_ONLINE, shouldLaunch)
                        .apply();
                return null;
            }
            @Override
            protected void onPostExecute(Void params) {
                if (sGCMEnabled) {
                    if (shouldLaunch) {
                        scheduleLaunchTask(context, mScheduler);
                    } else {
                        removeScheduledTasks(mScheduler);
                    }
                }
            }
        }.execute();
    }

    /**
     * True if the native browser has started and created an instance of {@link
     * BackgroundSyncLauncher}.
     */
    protected static boolean hasInstance() {
        return sInstance != null;
    }

    protected BackgroundSyncLauncher(Context context) {
        // Check to see if Play Services is up to date, and disable GCM if not.
        // This will not automatically set {@link sGCMEnabled} to true, in case it has been disabled
        // in tests.
        if (sGCMEnabled && !canUseGooglePlayServices(context)) {
            setGCMEnabled(false);
        }
        mScheduler = GcmNetworkManager.getInstance(context);
        launchBrowserWhenNextOnlineIfStopped(context, false);
    }

    private boolean canUseGooglePlayServices(Context context) {
        return ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                context, new UserRecoverableErrorHandler.Silent());
    }

    private static void scheduleLaunchTask(Context context, GcmNetworkManager scheduler) {
        // Google Play Services may not be up to date, if the application was not installed through
        // the Play Store. In this case, scheduling the task will fail silently.
        OneoffTask oneoff = new OneoffTask.Builder()
                                    .setService(BackgroundSyncLauncherService.class)
                                    .setTag("BackgroundSync Event")
                                    // We have to set a non-zero execution window here
                                    .setExecutionWindow(0, 1)
                                    .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                                    .setPersisted(true)
                                    .setUpdateCurrent(true)
                                    .build();
        scheduler.schedule(oneoff);
    }

    private static void removeScheduledTasks(GcmNetworkManager scheduler) {
        scheduler.cancelAllTasks(BackgroundSyncLauncherService.class);
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
                            scheduleLaunchTask(context, scheduler);
                        }
                    }
                };
        BackgroundSyncLauncher.shouldLaunchWhenNextOnline(context, callback);
    }

    @VisibleForTesting
    static void setGCMEnabled(boolean enabled) {
        sGCMEnabled = enabled;
    }
}
