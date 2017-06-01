// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

/**
 * Provides Java scheduling support from native offlining code as
 * well as JNI interface to tell native code to start processing
 * queued requests.
 */
@JNINamespace("offline_pages::android")
public class BackgroundSchedulerBridge {
    // Starts processing of one or more queued background requests.
    // Returns whether processing was started and that caller should
    // expect a callback (once processing has completed or terminated).
    // If processing was already active or not able to process for
    // some other reason, returns false and this calling instance will
    // not receive a callback.
    // TODO(dougarnett): consider adding policy check api to let caller
    //     separately determine if not allowed by policy.
    public static boolean startProcessing(
            DeviceConditions deviceConditions, Callback<Boolean> callback) {
        return nativeStartProcessing(deviceConditions.isPowerConnected(),
                deviceConditions.getBatteryPercentage(), deviceConditions.getNetConnectionType(),
                callback);
    }

    @CalledByNative
    private static void schedule(TriggerConditions triggerConditions) {
        BackgroundScheduler.schedule(ContextUtils.getApplicationContext(), triggerConditions);
    }

    @CalledByNative
    private static void backupSchedule(TriggerConditions triggerConditions, long delayInSeconds) {
        BackgroundScheduler.backupSchedule(ContextUtils.getApplicationContext(), triggerConditions,
                                           delayInSeconds);
    }

    @CalledByNative
    private static void unschedule() {
        BackgroundScheduler.unschedule(ContextUtils.getApplicationContext());
    }

    /**
     * Used by native code to create and pass up Java object encapsulating the
     * trigger conditions.
     */
    @CalledByNative
    private static TriggerConditions createTriggerConditions(boolean requirePowerConnected,
            int minimumBatteryPercentage, boolean requireUnmeteredNetwork) {
        return new TriggerConditions(
                requirePowerConnected, minimumBatteryPercentage, requireUnmeteredNetwork);
    }

    /** Instructs the native RequestCoordinator to start processing. */
    private static native boolean nativeStartProcessing(boolean powerConnected,
            int batteryPercentage, int netConnectionType, Callback<Boolean> callback);
}
