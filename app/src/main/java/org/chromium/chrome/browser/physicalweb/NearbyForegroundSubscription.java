// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.Context;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.MessageListener;


/**
 * This class represents a connection to Google Play Services that does foreground
 * subscription/unsubscription to Nearby Eddystone-URLs.
 * To use this class, one should:
 * 1. connect,
 * 2. subscribe,
 * 3. unsubscribe,
 * 4. repeat steps 2-3 as desired, and
 * 5. disconnect.
 */
class NearbyForegroundSubscription extends NearbySubscription {
    private static final String TAG = "PhysicalWeb";
    private final MessageListener mMessageListener;
    private boolean mShouldSubscribe;

    NearbyForegroundSubscription(Context context) {
        super(context);
        mMessageListener = PhysicalWebBleClient.getInstance().createForegroundMessageListener();
        mShouldSubscribe = false;
    }

    @Override
    protected void onConnected() {
        if (mShouldSubscribe) {
            subscribe();
        }
    }

    void subscribe() {
        if (!getGoogleApiClient().isConnected()) {
            mShouldSubscribe = true;
            return;
        }
        Nearby.Messages.subscribe(getGoogleApiClient(), mMessageListener, createSubscribeOptions())
                .setResultCallback(new SimpleResultCallback("foreground subscribe"));
    }

    void unsubscribe() {
        if (!getGoogleApiClient().isConnected()) {
            mShouldSubscribe = false;
            return;
        }
        Nearby.Messages.unsubscribe(getGoogleApiClient(), mMessageListener)
                .setResultCallback(new SimpleResultCallback("foreground unsubscribe"));
    }
}
