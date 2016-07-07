// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.datausage;

import android.content.Context;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeApplication;

/**
 * This class provides a base class implementation of a data use observer that is external to
 * Chromium. This class should be accessed only on IO thread.
 */
@JNINamespace("chrome::android")
public class ExternalDataUseObserver {
    /**
     * Pointer to the native ExternalDataUseObserver object.
     */
    private long mNativeExternalDataUseObserver;

    @CalledByNative
    private static ExternalDataUseObserver create(Context context, long nativePtr) {
        return ((ChromeApplication) context).createExternalDataUseObserver(nativePtr);
    }

    /**
     * Creates an instance of {@link #ExternalDataUseObserver}.
     * @param nativePtr pointer to the native ExternalDataUseObserver object.
     */
    public ExternalDataUseObserver(long nativePtr) {
        mNativeExternalDataUseObserver = nativePtr;
        assert mNativeExternalDataUseObserver != 0;
    }

    /**
     * Notification that the native object has been destroyed.
     */
    @CalledByNative
    private void onDestroy() {
        mNativeExternalDataUseObserver = 0;
    }

    /**
    * Fetches matching rules and returns them via {@link #fetchMatchingRulesCallback}. While the
    * fetch is underway, it is illegal to make calls to this method.
    */
    @CalledByNative
    protected void fetchMatchingRules() {
        fetchMatchingRulesCallback(null, null, null);
    }

    /*
     * {@link #fetchMatchingRulesCallback}  reports the result of {@link #fetchMatchingRules} to
     * the native.
     * @param appPackageName package name of the app that should be matched.
     * @domainPathRegex regex in RE2 syntax that is used for matching URLs.
     * @param label opaque label that must be applied to the data use reports, and must uniquely
     * identify the matching rule. Each element in {@link #label} must have non-zero length.
     * The three vectors are should have equal length. All three vectors may be empty which
     * implies that no matching rules are active.
     */
    protected void fetchMatchingRulesCallback(
            String[] appPackageName, String[] domainPathRegEx, String[] label) {
        // Check if native object is destroyed. This may happen at the time of Chromium shutdown.
        if (mNativeExternalDataUseObserver == 0) {
            return;
        }
        nativeFetchMatchingRulesCallback(
                mNativeExternalDataUseObserver, appPackageName, domainPathRegEx, label);
    }

    /**
     * Asynchronously reports data use to the external observer.
     * @param label the label provided by {@link #ExternalDataUseObserver} for the matching rule.
     * “ChromeTab” in case traffic was performed in a Chromium tab, and “ChromePlate” in case it was
     * performed within a ChromePlate.
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
     * {@link #nativeOnReportDataUseDone}. A new report should be submitted only after the
     * result has been returned via {@link #nativeOnReportDataUseDone}.
     */
    @CalledByNative
    protected void reportDataUse(String label, int networkType, String mccMnc,
            long startTimeInMillis, long endTimeInMillis, long bytesDownloaded,
            long bytesUploaded) {}

    /*
     * {@link #onReportDataUseDone}  reports the result of {@link #reportDataUse} to
     * the native.
     * @param success true if the data report was sucessfully submitted to the external observer.
     */
    protected void onReportDataUseDone(boolean success) {
        // Check if native object is destroyed.  This may happen at the time of Chromium shutdown.
        if (mNativeExternalDataUseObserver == 0) {
            return;
        }
        nativeOnReportDataUseDone(mNativeExternalDataUseObserver, success);
    }

    public native void nativeFetchMatchingRulesCallback(long nativeExternalDataUseObserver,
            String[] appPackageName, String[] domainPathRegEx, String[] label);

    public native void nativeOnReportDataUseDone(
            long nativeExternalDataUseObserver, boolean success);
}
