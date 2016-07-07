// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.banners;

import android.content.Context;
import android.text.TextUtils;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.WebContents;

/**
 * Manages an AppBannerInfoBar for a Tab.
 *
 * The AppBannerManager manages a single AppBannerInfoBar, creating a new one when it detects that
 * the current webpage is requesting a banner to be built. The actual observation of the WebContents
 * (which triggers the automatic creation and removal of banners, among other things) is done by the
 * native-side AppBannerManager.
 *
 * This Java-side class owns its native-side counterpart, which is basically used to grab resources
 * from the network.
 */
@JNINamespace("banners")
public class AppBannerManager extends EmptyTabObserver {
    private static final String TAG = "AppBannerManager";

    /** Retrieves information about a given package. */
    private static AppDetailsDelegate sAppDetailsDelegate;

    /** Whether the banners are enabled. */
    private static Boolean sIsEnabled;

    /** Pointer to the native side AppBannerManager. */
    private long mNativePointer;

    /** Tab that the AppBannerView/AppBannerManager is owned by. */
    private final Tab mTab;

    /**
     * Checks if app banners are enabled.
     * @return True if banners are enabled, false otherwise.
     */
    public static boolean isEnabled() {
        if (sIsEnabled == null) {
            Context context = ApplicationStatus.getApplicationContext();
            sIsEnabled = ShortcutHelper.isAddToHomeIntentSupported(context);
        }
        return sIsEnabled;
    }

    /**
     * Sets the delegate that provides information about a given package.
     * @param delegate Delegate to use.  Previously set ones are destroyed.
     */
    public static void setAppDetailsDelegate(AppDetailsDelegate delegate) {
        if (sAppDetailsDelegate != null) sAppDetailsDelegate.destroy();
        sAppDetailsDelegate = delegate;
    }

    /**
     * Constructs an AppBannerManager for the given tab.
     * @param tab Tab that the AppBannerManager will be attached to.
     */
    public AppBannerManager(Tab tab, Context context) {
        mNativePointer = nativeInit();
        mTab = tab;
        updatePointers();
    }

    @Override
    public void onWebContentsSwapped(Tab tab, boolean didStartLoad,
            boolean didFinishLoad) {
        updatePointers();
    }

    @Override
    public void onContentChanged(Tab tab) {
        updatePointers();
    }

    /**
     * Destroys the native AppBannerManager.
     */
    public void destroy() {
        nativeDestroy(mNativePointer);
        mNativePointer = 0;
    }

    /**
     * Updates which WebContents the native AppBannerManager is monitoring.
     */
    private void updatePointers() {
        nativeReplaceWebContents(mNativePointer, mTab.getWebContents());
    }

    /**
     * Grabs package information for the banner asynchronously.
     * @param url         URL for the page that is triggering the banner.
     * @param packageName Name of the package that is being advertised.
     */
    @CalledByNative
    private void fetchAppDetails(
            String url, String packageName, String referrer, int iconSizeInDp) {
        if (sAppDetailsDelegate == null) return;

        Context context = ApplicationStatus.getApplicationContext();
        int iconSizeInPx = Math.round(
                context.getResources().getDisplayMetrics().density * iconSizeInDp);
        sAppDetailsDelegate.getAppDetailsAsynchronously(
                createAppDetailsObserver(), url, packageName, referrer, iconSizeInPx);
    }

    private AppDetailsDelegate.Observer createAppDetailsObserver() {
        return new AppDetailsDelegate.Observer() {
            /**
             * Called when data about the package has been retrieved, which includes the url for the
             * app's icon but not the icon Bitmap itself.
             * @param data Data about the app.  Null if the task failed.
             */
            @Override
            public void onAppDetailsRetrieved(AppData data) {
                if (data == null || mNativePointer == 0) return;

                String imageUrl = data.imageUrl();
                if (TextUtils.isEmpty(imageUrl)) return;

                nativeOnAppDetailsRetrieved(
                        mNativePointer, data, data.title(), data.packageName(), data.imageUrl());
            }
        };
    }

    /** Enables or disables the app banners for testing. */
    @VisibleForTesting
    static void setIsEnabledForTesting(boolean state) {
        sIsEnabled = state;
    }

    /** Sets a constant (in days) that gets added to the time when the current time is requested. */
    @VisibleForTesting
    static void setTimeDeltaForTesting(int days) {
        nativeSetTimeDeltaForTesting(days);
    }

    /** Disables the HTTPS scheme requirement for testing. */
    @VisibleForTesting
    static void disableSecureSchemeCheckForTesting() {
        nativeDisableSecureSchemeCheckForTesting();
    }

    /** Sets the weights of direct and indirect page navigations for testing. */
    @VisibleForTesting
    static void setEngagementWeights(double directEngagement, double indirectEngagement) {
        nativeSetEngagementWeights(directEngagement, indirectEngagement);
    }

    /** Returns whether a AppBannerDataFetcher is actively retrieving data. */
    @VisibleForTesting
    public boolean isFetcherActiveForTesting() {
        return nativeIsFetcherActive(mNativePointer);
    }

    private native long nativeInit();
    private native void nativeDestroy(long nativeAppBannerManagerAndroid);
    private native void nativeReplaceWebContents(long nativeAppBannerManagerAndroid,
            WebContents webContents);
    private native boolean nativeOnAppDetailsRetrieved(long nativeAppBannerManagerAndroid,
            AppData data, String title, String packageName, String imageUrl);

    // Testing methods.
    private static native void nativeSetTimeDeltaForTesting(int days);
    private static native void nativeDisableSecureSchemeCheckForTesting();
    private static native void nativeSetEngagementWeights(double directEngagement,
            double indirectEngagement);
    private native boolean nativeIsFetcherActive(long nativeAppBannerManagerAndroid);
}
