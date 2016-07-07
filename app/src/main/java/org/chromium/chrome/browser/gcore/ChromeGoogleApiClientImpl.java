// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.gcore;

import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import org.chromium.base.Log;
import org.chromium.base.TraceEvent;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;

import java.util.concurrent.TimeUnit;

/**
 * Default implementation for {@link ChromeGoogleApiClient}.
 */
public class ChromeGoogleApiClientImpl implements ChromeGoogleApiClient {
    private static final String TAG = "cr.Icing";

    private final Context mApplicationContext;
    private final GoogleApiClient mClient;

    /**
     * @param context its application context will be exposed through
     *                {@link #getApplicationContext()}.
     * @param client will be exposed through {@link #getApiClient()}.
     */
    public ChromeGoogleApiClientImpl(Context context, GoogleApiClient client) {
        mApplicationContext = context.getApplicationContext();
        mClient = client;
    }

    @Override
    public void disconnect() {
        mClient.disconnect();
    }

    @Override
    public boolean isGooglePlayServicesAvailable() {
        TraceEvent.begin("ChromeGoogleApiClientImpl:isGooglePlayServicesAvailable");
        try {
            return ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                    mApplicationContext, new UserRecoverableErrorHandler.Silent());
        } finally {
            TraceEvent.end("ChromeGoogleApiClientImpl:isGooglePlayServicesAvailable");
        }
    }

    @Override
    public boolean connectWithTimeout(long timeout) {
        TraceEvent.begin("ChromeGoogleApiClientImpl:connectWithTimeout");
        try {
            ConnectionResult result = mClient.blockingConnect(timeout, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                Log.e(TAG, "Connection to GmsCore unsuccessful. Error %d", result.getErrorCode());
            } else {
                Log.d(TAG, "Connection to GmsCore successful.");
            }
            return result.isSuccess();
        } finally {
            TraceEvent.end("ChromeGoogleApiClientImpl:connectWithTimeout");
        }
    }

    public Context getApplicationContext() {
        return mApplicationContext;
    }

    public GoogleApiClient getApiClient() {
        return mClient;
    }
}
