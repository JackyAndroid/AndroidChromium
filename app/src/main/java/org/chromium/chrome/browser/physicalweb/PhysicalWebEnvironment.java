// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import org.chromium.chrome.browser.ChromeApplication;

/**
 * Tool that reports information about conflicting clients.
 */
public class PhysicalWebEnvironment {
    private static final Object INSTANCE_LOCK = new Object();
    private static PhysicalWebEnvironment sInstance = null;

    /**
     * Get a singleton instance of this class.
     * @param chromeApplication An instance of {@link ChromeApplication}, used to get the
     * appropriate PhysicalWebEnvironment implementation.
     * @return an instance of this class (or subclass) as decided by the application parameter
     */
    public static PhysicalWebEnvironment getInstance(ChromeApplication chromeApplication) {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = chromeApplication.createPhysicalWebEnvironment();
            }
        }
        return sInstance;
    }

    /**
     * Reports whether the environment has another notification-based Physical Web client enabled.
     * @return true if there is another notification-based Physical Web client enabled.
     */
    public boolean hasNotificationBasedClient() {
        return false;
    }
}
