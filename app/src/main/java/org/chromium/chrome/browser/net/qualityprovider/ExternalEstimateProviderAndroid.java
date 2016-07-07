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
 * provide more useful APIs. This class is not thread safe.
 */
@JNINamespace("chrome::android")
public class ExternalEstimateProviderAndroid {
    /**
     * Value to return if a valid value is unavailable.
     */
    protected static final int NO_VALUE = -1;
    private static final Object LOCK = new Object();
    private static NonThreadSafe sThreadCheck = null;
    private static ExternalEstimateProviderAndroid sExternalEstimateProviderAndroid;

    @CalledByNative
    private static ExternalEstimateProviderAndroid create(Context context, long nativePtr) {
        synchronized (LOCK) {
            if (sExternalEstimateProviderAndroid == null) {
                assert sThreadCheck == null;
                assert sExternalEstimateProviderAndroid == null;
                sThreadCheck = new NonThreadSafe();
                sExternalEstimateProviderAndroid =
                        ((ChromeApplication) context)
                                .createExternalEstimateProviderAndroid(nativePtr);
            }
        }
        return sExternalEstimateProviderAndroid;
    }

    /**
     * Creates an instance of |@link #ExternalEstimateProviderAndroid}.
     */
    public ExternalEstimateProviderAndroid() {
        assert sThreadCheck.calledOnValidThread();
    }

    /**
     * Requests the provider to update the network quality estimate.
     */
    @CalledByNative
    protected void requestUpdate() {
        assert sThreadCheck.calledOnValidThread();
    }

    /**
     * @return Expected RTT duration in milliseconds or {@link #NO_VALUE} if the estimate is
     *         unavailable.
     */
    @CalledByNative
    protected int getRTTMilliseconds() {
        assert sThreadCheck.calledOnValidThread();
        return NO_VALUE;
    }

    /**
     * @return The expected downstream throughput in Kbps (Kilobits per second) or
     *         {@link #NO_VALUE} if the estimate is unavailable.
     */
    @CalledByNative
    protected long getDownstreamThroughputKbps() {
        assert sThreadCheck.calledOnValidThread();
        return NO_VALUE;
    }

    /**
     * @return The expected upstream throughput in Kbps (Kilobits per second) or
     *         {@link #NO_VALUE} if the estimate is unavailable.
     */
    @CalledByNative
    protected long getUpstreamThroughputKbps() {
        assert sThreadCheck.calledOnValidThread();
        return NO_VALUE;
    }

    /**
     * @return Time (in seconds) since the network quality estimate was last updated.
     */
    @CalledByNative
    protected long getTimeSinceLastUpdateSeconds() {
        assert sThreadCheck.calledOnValidThread();
        return NO_VALUE;
    }

    @CalledByNative
    private static int getNoValue() {
        assert sThreadCheck.calledOnValidThread();
        return NO_VALUE;
    }

    public native void nativeNotifyExternalEstimateProviderAndroidUpdate(
            long nativeExternalEstimateProviderAndroid);
}
