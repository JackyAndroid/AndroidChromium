// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;

import java.util.concurrent.TimeUnit;

/**
 * Centralizes UMA data collection for the Physical Web feature.
 */
public final class PhysicalWebUma {
    public static void onNotificationPressed() {
        RecordUserAction.record("PhysicalWeb.NotificationPressed");
    }

    public static void onPwsResponse(long duration) {
        RecordHistogram.recordTimesHistogram("PhysicalWeb.RoundTripTimeMilliseconds", duration,
                                             TimeUnit.MILLISECONDS);
    }

    public static void onUrlsDisplayed(int numUrls) {
        RecordHistogram.recordCountHistogram("PhysicalWeb.TotalBeaconsDetected", numUrls);
    }

    public static void onUrlSelected() {
        RecordUserAction.record("PhysicalWeb.UrlSelected");
    }
}
