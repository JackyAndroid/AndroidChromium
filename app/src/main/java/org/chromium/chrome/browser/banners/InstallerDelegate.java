// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.banners;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import org.chromium.base.VisibleForTesting;

import java.util.List;

/**
 * Monitors the PackageManager to see when an app has been installed.
 */
public class InstallerDelegate implements Runnable {
    /**
     * Callback for when the app install has completed.
     */
    public static interface Observer {
        /**
         * Called when the task has finished.
         * @param delegate Instance of the class that finished.
         * @param success  Whether or not the package was successfully installed.
         */
        public void onInstallFinished(InstallerDelegate delegate, boolean success);
    }

    private static final long DEFAULT_MS_BETWEEN_RUNS = 1000;
    private static final long DEFAULT_MS_MAXIMUM_WAITING_TIME = 3 * 60 * 1000;

    /** Message loop to post the Runnable to. */
    private final Handler mHandler;

    /** PackageManager that the Runnable is monitoring. */
    private final PackageManager mPackageManager;

    /** Object that is notified when the PackageManager has finished. */
    private final Observer mObserver;

    /** Name of the package that we need to see the PackageManager has finished installing. */
    private final String mPackageName;

    /** Whether or not the Runnable is currently looping. */
    private boolean mIsRunning;

    /** Number of milliseconds to wait between calls to run(). */
    private long mMsBetweenRuns;

    /** Maximum number of milliseconds to wait before giving up. */
    private long mMsMaximumWaitingTime;

    /** Timestamp of when we first started. */
    private long mTimestampStarted;

    /**
     * Constructs the InstallerDelegate.
     * @param looper         Thread to run the Runnable on.
     * @param packageManager Provides access to the list of installed apps.
     * @param observer       Alerted when the package has been completely installed.
     * @param packageName    Name of the package for the app to monitor.
     */
    public InstallerDelegate(
            Looper looper, PackageManager packageManager, Observer observer, String packageName) {
        mHandler = new Handler(looper);
        mPackageManager = packageManager;
        mObserver = observer;
        mPackageName = packageName;
        mMsBetweenRuns = DEFAULT_MS_BETWEEN_RUNS;
        mMsMaximumWaitingTime = DEFAULT_MS_MAXIMUM_WAITING_TIME;
    }

    /**
     * Begin monitoring the PackageManager to see if it completes installing the package.
     */
    public void start() {
        mTimestampStarted = SystemClock.elapsedRealtime();
        mIsRunning = true;
        mHandler.postDelayed(this, mMsBetweenRuns);
    }

    /**
     * Don't call this directly; instead, call {@link #start()}.
     */
    @Override
    public void run() {
        boolean isInstalled = isInstalled();
        boolean waitedTooLong =
                (SystemClock.elapsedRealtime() - mTimestampStarted) > mMsMaximumWaitingTime;
        if (isInstalled || !mIsRunning || waitedTooLong) {
            mObserver.onInstallFinished(this, isInstalled);
            mIsRunning = false;
        } else {
            mHandler.postDelayed(this, mMsBetweenRuns);
        }
    }

    /**
     * Checks if the app has been installed on the system.
     * @param packageManager PackageManager to use.
     * @param packageName Name of the package to check.
     * @return True if the PackageManager reports that the app is installed, false otherwise.
     */
    public static boolean isInstalled(PackageManager packageManager, String packageName) {
        List<PackageInfo> packs = packageManager.getInstalledPackages(0);
        for (int i = 0; i < packs.size(); i++) {
            if (TextUtils.equals(packs.get(i).packageName, packageName)) return true;
        }
        return false;
    }

    /**
     * Checks if the app has been installed on the system.
     * @return True if the PackageManager reports that the app is installed, false otherwise.
     */
    private boolean isInstalled() {
        return isInstalled(mPackageManager, mPackageName);
    }

    /**
     * Prevent rescheduling the Runnable.
     */
    public void cancel() {
        mIsRunning = false;
    }

    /**
     * Checks to see if the Runnable will continue scheduling itself.
     * @return True if the runnable is still being scheduled.
     */
    @VisibleForTesting
    boolean isRunning() {
        return mIsRunning;
    }

    /**
     * Set how often the handler will check the PackageManager.
     * @param msBetween How long to wait between executions of the Runnable.
     * @param msMax     How long to wait before giving up.
     */
    @VisibleForTesting
    void setTimingForTests(long msBetween, long msMax) {
        mMsBetweenRuns = msBetween;
        mMsMaximumWaitingTime = msMax;
    }
}
