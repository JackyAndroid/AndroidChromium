// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import org.chromium.base.Log;
import org.chromium.chrome.browser.ChromeApplication;

/**
 * The Client that harvests URLs from BLE signals.
 * This class is designed to scan URSs from Bluetooth Low Energy beacons.
 * This class is currently an empty implementation and must be extended by a
 * subclass.
 */
public class PhysicalWebBleClient {
    private static PhysicalWebBleClient sInstance = null;
    private static final String TAG = "PhysicalWeb";

    /**
     * Get a singleton instance of this class.
     * @param chromeApplication An instance of {@link ChromeApplication}, used to get the
     * appropriate PhysicalWebBleClient implementation.
     * @return an instance of this class (or subclass) as decided by the
     * application parameter
     */
    public static PhysicalWebBleClient getInstance(ChromeApplication chromeApplication) {
        if (sInstance == null) {
            sInstance = chromeApplication.createPhysicalWebBleClient();
        }
        return sInstance;
    }

    /**
     * Begin subscribing to URLs broadcasted from BLE beacons.
     * This currently does nothing and should be overridden by a subclass.
     */
    void subscribe() {
        Log.d(TAG, "subscribing in empty client");
    }
}
