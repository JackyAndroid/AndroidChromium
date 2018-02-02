// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import org.chromium.base.annotations.CalledByNative;

/**
 * Communicates between BrowsingDataCounter (C++ backend) and ClearBrowsingDataFragment (Java UI).
 */
public class BrowsingDataCounterBridge {
    /**
     * Can receive a callback from a BrowsingDataCounter.
     */
    public interface BrowsingDataCounterCallback {
        /**
         * The callback to be called when a BrowsingDataCounter is finished.
         * @param result A string describing how much storage space will be reclaimed by clearing
         *      this data type.
         */
        public void onCounterFinished(String result);
    }

    private long mNativeBrowsingDataCounterBridge;
    private BrowsingDataCounterCallback mCallback;

    /**
     * Initializes BrowsingDataCounterBridge.
     * @param callback A callback to call with the result when the counter finishes.
     * @param dataType The browsing data type to be counted (from the shared enum
     *      {@link org.chromium.chrome.browser.browsing_data.BrowsingDataType}).
     */
    public BrowsingDataCounterBridge(BrowsingDataCounterCallback callback, int dataType) {
        mCallback = callback;
        mNativeBrowsingDataCounterBridge = nativeInit(dataType);
    }

    /**
     * Destroys the native counterpart of this class.
     */
    public void destroy() {
        if (mNativeBrowsingDataCounterBridge != 0) {
            nativeDestroy(mNativeBrowsingDataCounterBridge);
            mNativeBrowsingDataCounterBridge = 0;
        }
    }

    @CalledByNative
    private void onBrowsingDataCounterFinished(String result) {
        mCallback.onCounterFinished(result);
    }

    private native long nativeInit(int dataType);
    private native void nativeDestroy(long nativeBrowsingDataCounterBridge);
}
