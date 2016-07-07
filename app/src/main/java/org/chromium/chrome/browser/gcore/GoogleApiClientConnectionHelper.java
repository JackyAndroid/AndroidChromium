// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.gcore;

import android.os.Bundle;
import android.os.Handler;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

/**
 * Helper that allows to manage connection failures when using {@link GoogleApiClient}.
 *
 * When the connection fails, it will try to reconnect {@link ConnectedTask#RETRY_NUMBER_LIMIT}
 * times in the background, waiting {@link ConnectedTask#CONNECTION_RETRY_TIME_MS} milliseconds
 * between each attempt.
 */
public class GoogleApiClientConnectionHelper
        implements OnConnectionFailedListener, ConnectionCallbacks {
    private int mResolutionAttempts = 0;
    private Handler mHandler;
    private final GoogleApiClient mClient;

    public GoogleApiClientConnectionHelper(GoogleApiClient client) {
        mClient = client;
        mClient.registerConnectionCallbacks(this);
        mClient.registerConnectionFailedListener(this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolutionAttempts++ < ConnectedTask.RETRY_NUMBER_LIMIT) {
            if (mHandler == null) mHandler = new Handler();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mClient.connect();
                }
            }, ConnectedTask.CONNECTION_RETRY_TIME_MS);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        mResolutionAttempts = 0;
    }

    @Override
    public void onConnectionSuspended(int cause) {}
}