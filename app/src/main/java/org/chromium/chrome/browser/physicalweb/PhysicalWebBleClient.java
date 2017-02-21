// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.app.Activity;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageFilter;
import com.google.android.gms.nearby.messages.MessageListener;

import org.chromium.base.ContextUtils;
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

    // We don't actually listen to any of the onFound or onLost events in the foreground.
    // The background listener will get these.
    protected static class ForegroundMessageListener extends MessageListener {
        @Override
        public void onFound(Message message) {}
    }

    /**
     * Get a singleton instance of this class.
     * @return an instance of this class (or subclass).
     */
    public static PhysicalWebBleClient getInstance() {
        if (sInstance == null) {
            sInstance = ((ChromeApplication) ContextUtils.getApplicationContext())
                    .createPhysicalWebBleClient();
        }
        return sInstance;
    }

    /**
     * Begin a background subscription to URLs broadcasted from BLE beacons.
     * This currently does nothing and should be overridden by a subclass.
     * @param callback Callback to be run when subscription task is done, regardless of whether it
     *         is successful.
     */
    void backgroundSubscribe(Runnable callback) {
        Log.d(TAG, "background subscribing in empty client");
        if (callback != null) {
            callback.run();
        }
    }

    /**
     * Begin a background subscription to URLs broadcasted from BLE beacons.
     * This currently does nothing and should be overridden by a subclass.
     */
    void backgroundSubscribe() {
        backgroundSubscribe(null);
    }

    /**
     * Cancel a background subscription to URLs broadcasted from BLE beacons.
     * This currently does nothing and should be overridden by a subclass.
     * @param callback Callback to be run when subscription cancellation task is done, regardless of
     *         whether it is successful.
     */
    void backgroundUnsubscribe(Runnable callback) {
        Log.d(TAG, "background unsubscribing in empty client");
        if (callback != null) {
            callback.run();
        }
    }

    /**
     * Cancel a background subscription to URLs broadcasted from BLE beacons.
     * This currently does nothing and should be overridden by a subclass.
     */
    void backgroundUnsubscribe() {
        backgroundUnsubscribe(null);
    }

    /**
     * Begin a foreground subscription to URLs broadcasted from BLE beacons.
     * This currently does nothing and should be overridden by a subclass.
     * @param activity The Activity that is performing the scan.
     */
    void foregroundSubscribe(Activity activity) {
        Log.d(TAG, "foreground subscribing in empty client");
    }

    /**
     * Cancel a foreground subscription to URLs broadcasted from BLE beacons.
     * This currently does nothing and should be overridden by a subclass.
     */
    void foregroundUnsubscribe() {
        Log.d(TAG, "foreground unsubscribing in empty client");
    }

    /**
     * Create a MessageListener that listens during a foreground scan.
     * @return the MessageListener.
     */
    MessageListener createForegroundMessageListener() {
        return new ForegroundMessageListener();
    }

    /**
     * Get the URLs from a device within a message.
     * @param message The Nearby message.
     * @return The URL contained in the message.
     */
    String getUrlFromMessage(Message message) {
        return null;
    }

    /**
     * Modify a GoogleApiClient.Builder as necessary for doing Physical Web scanning.
     * @param builder The builder to be modified.
     * @return The Builder.
     */
    GoogleApiClient.Builder modifyGoogleApiClientBuilder(GoogleApiClient.Builder builder) {
        return builder.addApi(Nearby.MESSAGES_API);
    }

    /**
     * Modify a MessageFilter.Builder as necessary for doing Physical Web scanning.
     * @param builder The builder to be modified.
     * @return The Builder.
     */
    MessageFilter.Builder modifyMessageFilterBuilder(MessageFilter.Builder builder) {
        return builder.includeAllMyTypes();
    }
}
