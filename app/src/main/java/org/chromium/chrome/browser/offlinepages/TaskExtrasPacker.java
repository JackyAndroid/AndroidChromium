// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.os.Bundle;

import org.chromium.chrome.browser.ChromeBackgroundService;

/**
 * Class to put our custom task information into the task bundle.
 */
public class TaskExtrasPacker {
    /** Bundle key for the timestamp in milliseconds when the request started. */
    public static final String SCHEDULED_TIME_TAG = "ScheduleTime";

    // Trigger condition tags.
    private static final String POWER_CONNECTED_TAG = "PowerConnected";
    private static final String BATTERY_PERCENTAGE_TAG = "BatteryPercentage";
    private static final String UNMETERED_NETWORK_TAG = "UnmeteredNetwork";

    /** Puts current time into the input bundle. */
    public static void packTimeInBundle(Bundle bundle) {
        bundle.putLong(SCHEDULED_TIME_TAG, System.currentTimeMillis());
        bundle.putBoolean(ChromeBackgroundService.HOLD_WAKELOCK, true);
    }

    /** Extracts the time we put into the bundle. */
    public static long unpackTimeFromBundle(Bundle bundle) {
        return bundle.getLong(SCHEDULED_TIME_TAG);
    }

    /** Puts trigger conditions into the input bundle. */
    public static void packTriggerConditionsInBundle(Bundle bundle, TriggerConditions conditions) {
        bundle.putBoolean(POWER_CONNECTED_TAG, conditions.requirePowerConnected());
        bundle.putInt(BATTERY_PERCENTAGE_TAG, conditions.getMinimumBatteryPercentage());
        bundle.putBoolean(UNMETERED_NETWORK_TAG, conditions.requireUnmeteredNetwork());
    }

    /** Extracts the trigger conditions we put into the bundle. */
    public static TriggerConditions unpackTriggerConditionsFromBundle(Bundle bundle) {
        boolean requirePowerConnected = bundle.getBoolean(POWER_CONNECTED_TAG, true);
        int minimumBatteryPercentage = bundle.getInt(BATTERY_PERCENTAGE_TAG, 100);
        boolean requireUnmeteredNetwork = bundle.getBoolean(UNMETERED_NETWORK_TAG, true);
        return new TriggerConditions(
                requirePowerConnected, minimumBatteryPercentage, requireUnmeteredNetwork);
    }
}
