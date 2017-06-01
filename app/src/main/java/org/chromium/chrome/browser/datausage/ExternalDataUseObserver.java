// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.datausage;

import android.content.Context;
import android.text.TextUtils;

import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.base.PackageUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.NativeClassQualifiedName;
import org.chromium.chrome.browser.ChromeApplication;

/**
 * This class provides a base class implementation of a data use observer that is external to
 * Chromium. This class should be accessed only on UI thread.
 */
@JNINamespace("chrome::android")
public class ExternalDataUseObserver {
    /**
     * Listens for application state changes and whenever Chromium state changes to running, checks
     * and notifies {@link #ExternalDataUseObserverBridge} if the control app gets installed or
     * uninstalled.
     */
    private class ControlAppManager implements ApplicationStatus.ApplicationStateListener {
        // Package name of the control app.
        private final String mControlAppPackageName;

        // True if the control app is installed.
        private boolean mInstalled;

        ControlAppManager(String controlAppPackageName) {
            mControlAppPackageName = controlAppPackageName;
            mInstalled = false;
            ApplicationStatus.registerApplicationStateListener(this);
            checkAndNotifyPackageInstallState();
            if (!mInstalled) {
                // Notify the state when the control app is not installed on startup.
                nativeOnControlAppInstallStateChange(mNativeExternalDataUseObserverBridge, false);
            }
        }

        @Override
        public void onApplicationStateChange(int newState) {
            if (newState == ApplicationState.HAS_RUNNING_ACTIVITIES) {
                checkAndNotifyPackageInstallState();
            }
        }

        /**
         * Checks if the control app is installed or uninstalled and notifies {@link
         * #ExternalDataUseObserverBridge} if there is change of installation state.
         */
        private void checkAndNotifyPackageInstallState() {
            // Check if native object is destroyed. This may happen at the time of Chromium
            // shutdown.
            if (mNativeExternalDataUseObserverBridge == 0) {
                return;
            }
            if (TextUtils.isEmpty(mControlAppPackageName)) {
                return;
            }
            boolean isControlAppInstalled =
                    PackageUtils.getPackageVersion(
                            ContextUtils.getApplicationContext(), mControlAppPackageName)
                    != -1;
            if (isControlAppInstalled != mInstalled) {
                mInstalled = isControlAppInstalled;
                nativeOnControlAppInstallStateChange(
                        mNativeExternalDataUseObserverBridge, mInstalled);
            }
        }
    }

    /**
     * Pointer to the native ExternalDataUseObserverBridge object.
     */
    private long mNativeExternalDataUseObserverBridge;

    /**
     * {@link #ControlAppManager} object that notifies when control app is installed.
     */
    private ControlAppManager mControlAppManager;

    @CalledByNative
    private static ExternalDataUseObserver create(Context context, long nativePtr) {
        return ((ChromeApplication) context).createExternalDataUseObserver(nativePtr);
    }

    /**
     * Creates an instance of {@link #ExternalDataUseObserver}.
     * @param nativePtr pointer to the native ExternalDataUseObserver object.
     */
    public ExternalDataUseObserver(long nativePtr) {
        mNativeExternalDataUseObserverBridge = nativePtr;
        assert mNativeExternalDataUseObserverBridge != 0;
    }

    /**
     * @return the default control app package name.
     */
    protected String getDefaultControlAppPackageName() {
        return "";
    }

    /**
     * @return the google variation id.
     * TODO(rajendrant): This function is unused, and should be removed.
     */
    protected int getGoogleVariationID() {
        return 0;
    }

    /**
     * Initializes the control app manager with package name of the control app.
     * @param controlAppPackageName package name of the control app. If this is empty the default
     * control app package name from {@link getDefaultControlAppPackageName} will be used.
     */
    @CalledByNative
    protected void initControlAppManager(String controlAppPackageName) {
        if (TextUtils.isEmpty(controlAppPackageName)) {
            controlAppPackageName = getDefaultControlAppPackageName();
        }
        mControlAppManager = new ControlAppManager(controlAppPackageName);
    }

    /**
     * Notification that the native object has been destroyed.
     */
    @CalledByNative
    private void onDestroy() {
        mNativeExternalDataUseObserverBridge = 0;
    }

    /**
     * Fetches matching rules and returns them via {@link #fetchMatchingRulesDone}. subsequent
     * calls to this method While the fetch is underway, may cause the {@link
     * #fetchMatchingRulesDone} callback to be missed for the subsequent call.
     */
    @CalledByNative
    protected void fetchMatchingRules() {
        fetchMatchingRulesDone(null, null, null);
    }

    /*
     * {@link #fetchMatchingRulesDone}  reports the result of {@link #fetchMatchingRules} to
     * the native.
     * @param appPackageName package name of the app that should be matched.
     * @domainPathRegex regex in RE2 syntax that is used for matching URLs.
     * @param label opaque label that must be applied to the data use reports, and must uniquely
     * identify the matching rule. Each element in {@link #label} must have non-zero length.
     * The three vectors are should have equal length. All three vectors may be empty which
     * implies that no matching rules are active.
     */
    protected void fetchMatchingRulesDone(
            String[] appPackageName, String[] domainPathRegEx, String[] label) {
        // Check if native object is destroyed. This may happen at the time of Chromium shutdown.
        if (mNativeExternalDataUseObserverBridge == 0) {
            return;
        }
        nativeFetchMatchingRulesDone(
                mNativeExternalDataUseObserverBridge, appPackageName, domainPathRegEx, label);
    }

    /**
     * Asynchronously reports data use to the external observer.
     * @param label the label provided by {@link #ExternalDataUseObserver} for the matching rule.
     * @param tag “ChromeCustomTab” for Chrome custom tab, or "ChromeTab" for a default tab.
     * @param networkType type of the network on which the traffic was exchanged. This integer value
     * must map to NetworkChangeNotifier.ConnectionType.
     * @param mccMnc MCCMNC of the network on which the traffic was exchanged.
     * @param startTimeInMillis start time of the report in milliseconds since the Epoch (January
     * 1st 1970, 00:00:00.000).
     * @param endTimeInMillis end time of the report in milliseconds since the Epoch (January 1st
     * 1970, 00:00:00.000).
     * @param bytesDownloaded number of bytes downloaded by Chromium.
     * @param bytesUploaded number of bytes uploaded by Chromium.
     * The result of this request is returned asynchronously via
     * {@link #nativeOnReportDataUseDone}. A new report should preferably be submitted only after
     * the result of the previous report has been returned via {@link #nativeOnReportDataUseDone}.
     * Submitting another data use report while the previous is pending may cause the previous
     * report to be lost.
     */
    @CalledByNative
    protected void reportDataUse(String label, String tag, int networkType, String mccMnc,
            long startTimeInMillis, long endTimeInMillis, long bytesDownloaded,
            long bytesUploaded) {}

    /*
     * {@link #onReportDataUseDone}  reports the result of {@link #reportDataUse} to
     * the native.
     * @param success true if the data report was successfully submitted to the external observer.
     */
    protected void onReportDataUseDone(boolean success) {
        // Check if native object is destroyed.  This may happen at the time of Chromium shutdown.
        if (mNativeExternalDataUseObserverBridge == 0) {
            return;
        }
        nativeOnReportDataUseDone(mNativeExternalDataUseObserverBridge, success);
    }

    @NativeClassQualifiedName("ExternalDataUseObserverBridge")
    private native void nativeFetchMatchingRulesDone(long nativeExternalDataUseObserver,
            String[] appPackageName, String[] domainPathRegEx, String[] label);

    @NativeClassQualifiedName("ExternalDataUseObserverBridge")
    private native void nativeOnReportDataUseDone(
            long nativeExternalDataUseObserver, boolean success);

    @NativeClassQualifiedName("ExternalDataUseObserverBridge")
    private native void nativeOnControlAppInstallStateChange(
            long nativeExternalDataUseObserver, boolean isControlAppInstalled);
}
