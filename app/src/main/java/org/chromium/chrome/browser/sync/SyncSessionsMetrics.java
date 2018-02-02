// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync;

/**
 * A bridge to call sync sessions metrics logic.
 */
public final class SyncSessionsMetrics {
    private SyncSessionsMetrics() {}

    /*
     * Records a metric based on the youngest foreign tab. Should be called
     * exactly once per NTP load.
     */
    public static void recordYoungestForeignTabAgeOnNTP() {
        nativeRecordYoungestForeignTabAgeOnNTP();
    }

    // Native methods
    private static native void nativeRecordYoungestForeignTabAgeOnNTP();
}
