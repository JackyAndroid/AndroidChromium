// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Looper;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.banners.AppData;
import org.chromium.chrome.browser.banners.InstallerDelegate;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.ui.base.WindowAndroid;

/**
 * Handles the promotion and installation of an app specified by the current web page.  This Java
 * object is created by and owned by the native AppBannerInfoBarDelegateAndroid.
 */
@JNINamespace("banners")
public class AppBannerInfoBarDelegateAndroid {
    /** PackageManager to use in place of the real one. */
    private static PackageManager sPackageManagerForTests;

    /** Weak pointer to the native AppBannerInfoBarDelegateAndroid. */
    private long mNativePointer;

    /** Monitors an installation in progress. */
    private InstallerDelegate mInstallTask;

    /** Monitors for application state changes. */
    private final ApplicationStatus.ApplicationStateListener mListener;

    /** Overrides the PackageManager for testing. */
    @VisibleForTesting
    public static void setPackageManagerForTesting(PackageManager manager) {
        sPackageManagerForTests = manager;
    }

    private AppBannerInfoBarDelegateAndroid(long nativePtr) {
        mNativePointer = nativePtr;
        mListener = createApplicationStateListener();
        ApplicationStatus.registerApplicationStateListener(mListener);
    }

    private ApplicationStatus.ApplicationStateListener createApplicationStateListener() {
        return new ApplicationStatus.ApplicationStateListener() {
            @Override
            public void onApplicationStateChange(int newState) {
                if (!ApplicationStatus.hasVisibleActivities()) return;
                nativeUpdateInstallState(mNativePointer);
            }
        };
    }

    @CalledByNative
    private void destroy() {
        if (mInstallTask != null) {
            mInstallTask.cancel();
            mInstallTask = null;
        }
        ApplicationStatus.unregisterApplicationStateListener(mListener);
        mNativePointer = 0;
    }

    @CalledByNative
    private boolean installOrOpenNativeApp(Tab tab, AppData appData, String referrer) {
        Context context = ApplicationStatus.getApplicationContext();
        String packageName = appData.packageName();
        PackageManager packageManager = getPackageManager(context);

        if (InstallerDelegate.isInstalled(packageManager, packageName)) {
            // Open the app.
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
            if (launchIntent == null) return true;
            context.startActivity(launchIntent);
            return true;
        } else {
            // Try installing the app.  If the installation was kicked off, return false to prevent
            // the infobar from disappearing.
            // The supplied referrer is the URL of the page requesting the native app banner. It
            // may be empty depending on that page's referrer policy. If it is non-empty, attach it
            // to the installation intent as Intent.EXTRA_REFERRER.
            Intent installIntent = appData.installIntent();
            if (referrer.length() > 0) installIntent.putExtra(Intent.EXTRA_REFERRER, referrer);
            return !tab.getWindowAndroid().showIntent(
                    installIntent, createIntentCallback(appData), null);
        }
    }

    private WindowAndroid.IntentCallback createIntentCallback(final AppData appData) {
        return new WindowAndroid.IntentCallback() {
            @Override
            public void onIntentCompleted(WindowAndroid window, int resultCode,
                    ContentResolver contentResolver, Intent data) {
                boolean isInstalling = resultCode == Activity.RESULT_OK;
                if (isInstalling) {
                    // Start monitoring the install.
                    PackageManager pm =
                            getPackageManager(ApplicationStatus.getApplicationContext());
                    mInstallTask = new InstallerDelegate(
                            Looper.getMainLooper(), pm, createInstallerDelegateObserver(),
                            appData.packageName());
                    mInstallTask.start();
                }

                nativeOnInstallIntentReturned(mNativePointer, isInstalling);
            }
        };
    }

    private InstallerDelegate.Observer createInstallerDelegateObserver() {
        return new InstallerDelegate.Observer() {
            @Override
            public void onInstallFinished(InstallerDelegate task, boolean success) {
                if (mInstallTask != task) return;
                mInstallTask = null;
                nativeOnInstallFinished(mNativePointer, success);
            }
        };
    }

    @CalledByNative
    private void showAppDetails(Tab tab, AppData appData) {
        tab.getWindowAndroid().showIntent(appData.detailsIntent(), null, null);
    }

    @CalledByNative
    private int determineInstallState(AppData data) {
        if (mInstallTask != null) return AppBannerInfoBarAndroid.INSTALL_STATE_INSTALLING;

        PackageManager pm = getPackageManager(ApplicationStatus.getApplicationContext());
        boolean isInstalled = InstallerDelegate.isInstalled(pm, data.packageName());
        return isInstalled ? AppBannerInfoBarAndroid.INSTALL_STATE_INSTALLED
                : AppBannerInfoBarAndroid.INSTALL_STATE_NOT_INSTALLED;
    }

    private PackageManager getPackageManager(Context context) {
        if (sPackageManagerForTests != null) return sPackageManagerForTests;
        return context.getPackageManager();
    }

    @CalledByNative
    private static AppBannerInfoBarDelegateAndroid create(long nativePtr) {
        return new AppBannerInfoBarDelegateAndroid(nativePtr);
    }

    private native void nativeOnInstallIntentReturned(
            long nativeAppBannerInfoBarDelegateAndroid, boolean isInstalling);
    private native void nativeOnInstallFinished(
            long nativeAppBannerInfoBarDelegateAndroid, boolean success);
    private native void nativeUpdateInstallState(long nativeAppBannerInfoBarDelegateAndroid);
}
