// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.interfaces;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.offlinepages.DeviceConditions;

/**
 * Interface to allow mocking out the BackgroundSchedulerProcessor, which must call static
 * methods in BackgroundSchedulerBridge.
 */
public interface BackgroundSchedulerProcessor {
    /**
     * Starts processing of one or more queued background requests.  Returns whether processing was
     * started and that caller should expect a callback (once processing has completed or
     * terminated).  If processing was already active or not able to process for some other reason,
     * returns false and this calling instance will not receive a callback.
     */
    boolean startProcessing(DeviceConditions deviceConditions, Callback<Boolean> callback);
}
