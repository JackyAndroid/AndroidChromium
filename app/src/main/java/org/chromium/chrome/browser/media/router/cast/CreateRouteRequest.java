// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.chromium.base.Log;
import org.chromium.chrome.browser.media.router.ChromeMediaRouter;
import org.chromium.chrome.browser.media.router.MediaRoute;

/**
 * Establishes a {@link MediaRoute} by starting a Cast application represented by the given
 * presentation URL. Reports success or failure to {@link ChromeMediaRouter}.
 * Since there're numerous asynchronous calls involved in getting the application to launch
 * the class is implemented as a state machine.
 */
public class CreateRouteRequest implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<Cast.ApplicationConnectionResult> {
    private static final String TAG = "MediaRouter";

    private static final int STATE_IDLE = 0;
    private static final int STATE_CONNECTING_TO_API = 1;
    private static final int STATE_API_CONNECTION_SUSPENDED = 2;
    private static final int STATE_LAUNCHING_APPLICATION = 3;
    private static final int STATE_LAUNCH_SUCCEEDED = 4;
    private static final int STATE_TERMINATED = 5;

    private class CastListener extends Cast.Listener {
        private CastSession mSession;

        CastListener() {}

        void setSession(CastSession session) {
            mSession = session;
        }

        @Override
        public void onApplicationStatusChanged() {
            if (mSession == null) return;

            mSession.updateSessionStatus();
        }

        @Override
        public void onApplicationMetadataChanged(ApplicationMetadata metadata) {
            if (mSession == null) return;

            mSession.updateSessionStatus();
        }

        @Override
        // TODO(crbug.com/635567): Fix this properly.
        @SuppressLint("DefaultLocale")
        public void onApplicationDisconnected(int errorCode) {
            if (errorCode != CastStatusCodes.SUCCESS) {
                Log.e(TAG, String.format(
                        "Application disconnected with: %d", errorCode));
            }

            // This callback can be called more than once if the application is stopped from Chrome.
            if (mSession == null) return;

            mSession.stopApplication();
            mSession = null;
        }

        @Override
        public void onVolumeChanged() {
            if (mSession == null) return;

            mSession.onVolumeChanged();
        }
    }

    private final MediaSource mSource;
    private final MediaSink mSink;
    private final String mPresentationId;
    private final String mOrigin;
    private final int mTabId;
    private final boolean mIsIncognito;
    private final int mRequestId;
    private final CastMediaRouteProvider mRouteProvider;
    private final CastListener mCastListener = new CastListener();

    private GoogleApiClient mApiClient;
    private int mState = STATE_IDLE;

    /**
     * Initializes the request.
     * @param source The {@link MediaSource} defining the application to launch on the Cast device.
     * @param sink The {@link MediaSink} identifying the selected Cast device.
     * @param presentationId The presentation id assigned to the route by {@link ChromeMediaRouter}.
     * @param origin The origin of the frame requesting the route.
     * @param tabId The id of the tab containing the frame requesting the route.
     * @param isIncognito Whether the route is being requested from an Incognito profile.
     * @param requestId The id of the route creation request for tracking by
     * {@link ChromeMediaRouter}.
     * @param routeProvider The instance of {@link CastMediaRouteProvider} handling the request.
     */
    public CreateRouteRequest(
            MediaSource source,
            MediaSink sink,
            String presentationId,
            String origin,
            int tabId,
            boolean isIncognito,
            int requestId,
            CastMediaRouteProvider routeProvider) {
        assert source != null;
        assert sink != null;

        mSource = source;
        mSink = sink;
        mPresentationId = presentationId;
        mOrigin = origin;
        mTabId = tabId;
        mIsIncognito = isIncognito;
        mRequestId = requestId;
        mRouteProvider = routeProvider;
    }

    public MediaSource getSource() {
        return mSource;
    }

    public MediaSink getSink() {
        return mSink;
    }

    public String getPresentationId() {
        return mPresentationId;
    }

    public String getOrigin() {
        return mOrigin;
    }

    public int getTabId() {
        return mTabId;
    }

    public boolean isIncognito() {
        return mIsIncognito;
    }

    public int getNativeRequestId() {
        return mRequestId;
    }

    /**
     * Starts the process of launching the application on the Cast device.
     * @param applicationContext application context
     * implementation provided by the caller.
     */
    public void start(Context applicationContext) {
        assert applicationContext != null;

        if (mState != STATE_IDLE) throwInvalidState();

        mApiClient = createApiClient(mCastListener, applicationContext);
        mApiClient.connect();
        mState = STATE_CONNECTING_TO_API;
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (mState != STATE_CONNECTING_TO_API && mState != STATE_API_CONNECTION_SUSPENDED) {
            throwInvalidState();
        }

        // TODO(avayvod): switch to using ConnectedTask class for GoogleApiClient operations.
        // See https://crbug.com/522478
        if (mState == STATE_API_CONNECTION_SUSPENDED) return;

        try {
            launchApplication(mApiClient, mSource.getApplicationId(), true)
                    .setResultCallback(this);
            mState = STATE_LAUNCHING_APPLICATION;
        } catch (Exception e) {
            Log.e(TAG, "Launch application failed: %s", mSource.getApplicationId(), e);
            reportError();
        }
    }

    // TODO(avayvod): switch to using ConnectedTask class for GoogleApiClient operations.
    // See https://crbug.com/522478
    @Override
    public void onConnectionSuspended(int cause) {
        mState = STATE_API_CONNECTION_SUSPENDED;
    }

    @Override
    public void onResult(Cast.ApplicationConnectionResult result) {
        if (mState != STATE_LAUNCHING_APPLICATION
                && mState != STATE_API_CONNECTION_SUSPENDED) {
            throwInvalidState();
        }

        Status status = result.getStatus();
        if (!status.isSuccess()) {
            Log.e(TAG, "Launch application failed with status: %s, %d, %s",
                    mSource.getApplicationId(), status.getStatusCode(), status.getStatusMessage());
            reportError();
        }

        mState = STATE_LAUNCH_SUCCEEDED;
        reportSuccess(result);
    }

    // TODO(avayvod): switch to using ConnectedTask class for GoogleApiClient operations.
    // See https://crbug.com/522478
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mState != STATE_CONNECTING_TO_API) throwInvalidState();

        Log.e(TAG, "GoogleApiClient connection failed: %d, %b",
                result.getErrorCode(), result.hasResolution());
        reportError();
    }

    private GoogleApiClient createApiClient(Cast.Listener listener, Context context) {
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                .builder(mSink.getDevice(), listener)
                // TODO(avayvod): hide this behind the flag or remove
                .setVerboseLoggingEnabled(true);

        return new GoogleApiClient.Builder(context)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    private PendingResult<Cast.ApplicationConnectionResult> launchApplication(
            GoogleApiClient apiClient,
            String appId,
            boolean relaunchIfRunning) {
        return Cast.CastApi.launchApplication(apiClient, appId, relaunchIfRunning);
    }

    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("DefaultLocale")
    private void throwInvalidState() {
        throw new RuntimeException(String.format("Invalid state: %d", mState));
    }

    private void reportSuccess(Cast.ApplicationConnectionResult result) {
        if (mState != STATE_LAUNCH_SUCCEEDED) throwInvalidState();

        CastSession session = new CastSessionImpl(
                mApiClient,
                result.getSessionId(),
                result.getApplicationMetadata(),
                result.getApplicationStatus(),
                mSink.getDevice(),
                mOrigin,
                mTabId,
                mIsIncognito,
                mSource,
                mRouteProvider);
        mCastListener.setSession(session);
        mRouteProvider.onSessionCreated(session);

        terminate();
    }

    private void reportError() {
        if (mState == STATE_TERMINATED) throwInvalidState();

        assert mRouteProvider != null;
        mRouteProvider.onLaunchError();

        terminate();
    }

    private void terminate() {
        mApiClient.unregisterConnectionCallbacks(this);
        mApiClient.unregisterConnectionFailedListener(this);
        mState = STATE_TERMINATED;
    }
}
