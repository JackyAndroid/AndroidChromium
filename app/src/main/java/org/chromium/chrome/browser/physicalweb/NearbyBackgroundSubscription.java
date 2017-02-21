// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.app.PendingIntent;
import android.content.Intent;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;

import org.chromium.base.ContextUtils;


/**
 * This class represents a connection to Google Play Services that does background
 * subscription/unsubscription to Nearby Eddystone-URLs.
 */
class NearbyBackgroundSubscription extends NearbySubscription {
    private static final String TAG = "PhysicalWeb";
    private final int mAction;
    private final Runnable mCallback;

    NearbyBackgroundSubscription(int action, Runnable callback) {
        super(ContextUtils.getApplicationContext());
        mAction = action;
        mCallback = callback;
    }

    NearbyBackgroundSubscription(int action) {
        this(action, null);
    }

    private PendingIntent createNearbySubscribeIntent() {
        Intent intent =
                new Intent(ContextUtils.getApplicationContext(), NearbyMessageIntentService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                ContextUtils.getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent;
    }

    @Override
    protected void onConnected() {
        PendingResult<Status> pendingResult = null;
        String actionStr = null;
        if (mAction == SUBSCRIBE) {
            pendingResult = Nearby.Messages.subscribe(
                    getGoogleApiClient(), createNearbySubscribeIntent(), createSubscribeOptions());
            actionStr = "background subscribe";
        } else {
            pendingResult = Nearby.Messages.unsubscribe(
                    getGoogleApiClient(), createNearbySubscribeIntent());
            actionStr = "background unsubscribe";
        }
        pendingResult.setResultCallback(new SimpleResultCallback(actionStr) {
            @Override
            public void onResult(final Status status) {
                super.onResult(status);
                disconnect();
                if (mCallback != null) {
                    mCallback.run();
                }
            }
        });
    }

    void run() {
        connect();
    }
}
