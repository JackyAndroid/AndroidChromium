// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.messages.MessageFilter;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeOptions;

import org.chromium.base.Log;


/**
 * This class represents a connection to Google Play Services that performs subscriptions
 * and unsubscriptions to Nearby.
 */
abstract class NearbySubscription implements ConnectionCallbacks, OnConnectionFailedListener {
    public static final int UNSUBSCRIBE = 0;
    public static final int SUBSCRIBE = 1;
    private static final String TAG = "PhysicalWeb";
    private final GoogleApiClient mGoogleApiClient;

    protected static class SimpleResultCallback implements ResultCallback<Status> {
        private String mAction;

        SimpleResultCallback(String action) {
            mAction = action;
        }

        @Override
        public void onResult(final Status status) {
            if (status.isSuccess()) {
                Log.d(TAG, "Nearby " + mAction + " succeeded");
            } else {
                Log.d(TAG, "Nearby " + mAction + " failed: " + status.getStatusMessage());
            }
        }
    }

    NearbySubscription(Context context) {
        mGoogleApiClient = PhysicalWebBleClient.getInstance().modifyGoogleApiClientBuilder(
                new GoogleApiClient.Builder(context))
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    protected void connect() {
        mGoogleApiClient.connect();
    }

    protected void disconnect() {
        mGoogleApiClient.disconnect();
    }

    protected void onConnected() {
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        onConnected();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "Nearby connection suspended: " + cause);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "Nearby connection failed: " + result);
    }

    protected static SubscribeOptions createSubscribeOptions() {
        MessageFilter messageFilter = PhysicalWebBleClient.getInstance().modifyMessageFilterBuilder(
                new MessageFilter.Builder())
                .build();
        return new SubscribeOptions.Builder()
                .setStrategy(Strategy.BLE_ONLY)
                .setFilter(messageFilter)
                .build();
    }

    protected GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }
}
