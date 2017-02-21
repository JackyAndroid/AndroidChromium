// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.net.qualityprovider;

import android.content.Context;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.util.NonThreadSafe;

/**
 * This class provides a base class implementation and may be overridden on operating systems that
 * provide more useful APIs. All method calls from native code will happen on the thread where
 * this object is constructed, but calls from subclasses (specifically,
 * {@link #notifyExternalEstimateProviderAndroidUpdate()} can happen on other threads.
 */
@JNINamespace("chrome::android")
public class ExternalEstimateProviderAndroid {
    /**
     * Value to return if a valid value is unavailable.
     */
    protected static final int NO_VALUE = -1;
    private NonThreadSafe mThreadCheck = new NonThreadSafe();
    private final Object mLock = new Object();

    private long mNativePtr;

    @CalledByNative
    private static ExternalEstimateProviderAndroid create(Context context, long nativePtr) {
        return ((ChromeApplication) context)
                .createExternalEstimateProviderAndroid(nativePtr);
    }

    /**
     * Creates an instance of {@link ExternalEstimateProviderAndroid}.
     */
    protected ExternalEstimateProviderAndroid(long nativePtr) {
        mNativePtr = nativePtr;
    }

    @CalledByNative
    private void destroy() {
        synchronized (mLock) {
            mNativePtr = 0;
        }
    }

    /**
     * Requests the provider to update the network quality estimate.
     */
    @CalledByNative
    protected void requestUpdate() {
        assert mThreadCheck.calledOnValidThread();
    }

    /**
     * @return Expected RTT duration in milliseconds or {@link #NO_VALUE} if the estimate is
     *         unavailable.
     */
    @CalledByNative
    protected int getRTTMilliseconds() {
        assert mThreadCheck.calledOnValidThread();
        return NO_VALUE;
    }

    /**
     * @return The expected downstream throughput in Kbps (Kilobits per second) or
     *         {@link #NO_VALUE} if the estimate is unavailable.
     */
    @CalledByNative
    protected long getDownstreamThroughputKbps() {
        assert mThreadCheck.calledOnValidThread();
        return NO_VALUE;
    }

    /**
     * @return The expected upstream throughput in Kbps (Kilobits per second) or
     *         {@link #NO_VALUE} if the estimate is unavailable.
     */
    @CalledByNative
    protected long getUpstreamThroughputKbps() {
        assert mThreadCheck.calledOnValidThread();
        return NO_VALUE;
    }

    /**
     * @return Time (in seconds) since the network quality estimate was last updated.
     */
    @CalledByNative
    protected long getTimeSinceLastUpdateSeconds() {
        assert mThreadCheck.calledOnValidThread();
        return NO_VALUE;
    }

    @CalledByNative
    private static int getNoValue() {
        return NO_VALUE;
    }

    protected final void notifyExternalEstimateProviderAndroidUpdate() {
        synchronized (mLock) {
            if (mNativePtr == 0) return;

            // It's important to call this inside the critical section, to ensure the native object
            // isn't destroyed on its origin thread in the meantime.
            nativeNotifyExternalEstimateProviderAndroidUpdate(mNativePtr);
        }
    }

    private native void nativeNotifyExternalEstimateProviderAndroidUpdate(
            long nativeExternalEstimateProviderAndroid);
}
