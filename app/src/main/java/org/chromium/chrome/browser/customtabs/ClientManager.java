// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsService;
import android.support.customtabs.CustomTabsSessionToken;
import android.text.TextUtils;
import android.util.SparseBooleanArray;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.Referrer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Manages the clients' state for Custom Tabs. This class is threadsafe. */
@SuppressFBWarnings("CHROMIUM_SYNCHRONIZED_METHOD")
class ClientManager {
    // Values for the "CustomTabs.PredictionStatus" UMA histogram. Append-only.
    @VisibleForTesting static final int NO_PREDICTION = 0;
    @VisibleForTesting static final int GOOD_PREDICTION = 1;
    @VisibleForTesting static final int BAD_PREDICTION = 2;
    private static final int PREDICTION_STATUS_COUNT = 3;
    // Values for the "CustomTabs.CalledWarmup" UMA histogram. Append-only.
    @VisibleForTesting static final int NO_SESSION_NO_WARMUP = 0;
    @VisibleForTesting static final int NO_SESSION_WARMUP = 1;
    @VisibleForTesting static final int SESSION_NO_WARMUP_ALREADY_CALLED = 2;
    @VisibleForTesting static final int SESSION_NO_WARMUP_NOT_CALLED = 3;
    @VisibleForTesting static final int SESSION_WARMUP = 4;
    @VisibleForTesting static final int SESSION_WARMUP_COUNT = 5;

    /** To be called when a client gets disconnected. */
    public interface DisconnectCallback { public void run(CustomTabsSessionToken session); }

    /** Per-session values. */
    private static class SessionParams {
        public final int uid;
        public final DisconnectCallback disconnectCallback;
        public final String packageName;
        public final PostMessageHandler postMessageHandler;
        public boolean mIgnoreFragments;
        private boolean mShouldHideDomain;
        private boolean mShouldPrerenderOnCellular;
        private boolean mShouldSendNavigationInfo;
        private ServiceConnection mKeepAliveConnection;
        private String mPredictedUrl;
        private long mLastMayLaunchUrlTimestamp;

        public SessionParams(Context context, int uid, DisconnectCallback callback,
                PostMessageHandler postMessageHandler) {
            this.uid = uid;
            packageName = getPackageName(context, uid);
            disconnectCallback = callback;
            this.postMessageHandler = postMessageHandler;
        }

        private static String getPackageName(Context context, int uid) {
            PackageManager packageManager = context.getPackageManager();
            String[] packageList = packageManager.getPackagesForUid(uid);
            if (packageList.length != 1 || TextUtils.isEmpty(packageList[0])) return null;
            return packageList[0];
        }

        public ServiceConnection getKeepAliveConnection() {
            return mKeepAliveConnection;
        }

        public void setKeepAliveConnection(ServiceConnection serviceConnection) {
            mKeepAliveConnection = serviceConnection;
        }

        public void setPredictionMetrics(String predictedUrl, long lastMayLaunchUrlTimestamp) {
            mPredictedUrl = predictedUrl;
            mLastMayLaunchUrlTimestamp = lastMayLaunchUrlTimestamp;
        }

        public String getPredictedUrl() {
            return mPredictedUrl;
        }

        public long getLastMayLaunchUrlTimestamp() {
            return mLastMayLaunchUrlTimestamp;
        }

        /**
         * @return Whether the default parameters are used for this session.
         */
        public boolean isDefault() {
            return !mIgnoreFragments && !mShouldPrerenderOnCellular;
        }
    }

    private final Context mContext;
    private final Map<CustomTabsSessionToken, SessionParams> mSessionParams = new HashMap<>();
    private final SparseBooleanArray mUidHasCalledWarmup = new SparseBooleanArray();
    private boolean mWarmupHasBeenCalled = false;

    public ClientManager(Context context) {
        mContext = context.getApplicationContext();
        RequestThrottler.loadInBackground(mContext);
    }

    /** Creates a new session.
     *
     * @param session Session provided by the client.
     * @param uid Client UID, as returned by Binder.getCallingUid(),
     * @param onDisconnect To be called on the UI thread when a client gets disconnected.
     * @param postMessageHandler The handler to be used for postMessage related operations.
     * @return true for success.
     */
    public boolean newSession(CustomTabsSessionToken session, int uid,
            DisconnectCallback onDisconnect, PostMessageHandler postMessageHandler) {
        if (session == null) return false;
        SessionParams params = new SessionParams(mContext, uid, onDisconnect, postMessageHandler);
        synchronized (this) {
            if (mSessionParams.containsKey(session)) return false;
            mSessionParams.put(session, params);
        }
        return true;
    }

    public synchronized int postMessage(CustomTabsSessionToken session, String message) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return CustomTabsService.RESULT_FAILURE_MESSAGING_ERROR;
        return params.postMessageHandler.postMessage(message);
    }

    /**
     * Records that {@link CustomTabsConnection#warmup(long)} has been called from the given uid.
     */
    public synchronized void recordUidHasCalledWarmup(int uid) {
        mWarmupHasBeenCalled = true;
        mUidHasCalledWarmup.put(uid, true);
    }

    /** Updates the client behavior stats and returns whether speculation is allowed.
     *
     * @param session Client session.
     * @param uid As returned by Binder.getCallingUid().
     * @param url Predicted URL.
     * @return true if speculation is allowed.
     */
    public synchronized boolean updateStatsAndReturnWhetherAllowed(
            CustomTabsSessionToken session, int uid, String url) {
        SessionParams params = mSessionParams.get(session);
        if (params == null || params.uid != uid) return false;
        params.setPredictionMetrics(url, SystemClock.elapsedRealtime());
        RequestThrottler throttler = RequestThrottler.getForUid(mContext, uid);
        return throttler.updateStatsAndReturnWhetherAllowed();
    }

    @VisibleForTesting
    synchronized int getWarmupState(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        boolean hasValidSession = params != null;
        boolean hasUidCalledWarmup = hasValidSession && mUidHasCalledWarmup.get(params.uid);
        int result = mWarmupHasBeenCalled ? NO_SESSION_WARMUP : NO_SESSION_NO_WARMUP;
        if (hasValidSession) {
            if (hasUidCalledWarmup) {
                result = SESSION_WARMUP;
            } else {
                result = mWarmupHasBeenCalled ? SESSION_NO_WARMUP_ALREADY_CALLED
                                              : SESSION_NO_WARMUP_NOT_CALLED;
            }
        }
        return result;
    }

    @VisibleForTesting
    synchronized int getPredictionOutcome(CustomTabsSessionToken session, String url) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return NO_PREDICTION;

        String predictedUrl = params.getPredictedUrl();
        if (predictedUrl == null) return NO_PREDICTION;

        boolean urlsMatch = TextUtils.equals(predictedUrl, url)
                || (params.mIgnoreFragments
                        && UrlUtilities.urlsMatchIgnoringFragments(predictedUrl, url));
        return urlsMatch ? GOOD_PREDICTION : BAD_PREDICTION;
    }

    /**
     * Registers that a client has launched a URL inside a Custom Tab.
     */
    public synchronized void registerLaunch(CustomTabsSessionToken session, String url) {
        int outcome = getPredictionOutcome(session, url);
        RecordHistogram.recordEnumeratedHistogram(
                "CustomTabs.PredictionStatus", outcome, PREDICTION_STATUS_COUNT);

        SessionParams params = mSessionParams.get(session);
        if (outcome == GOOD_PREDICTION) {
            long elapsedTimeMs = SystemClock.elapsedRealtime()
                    - params.getLastMayLaunchUrlTimestamp();
            RequestThrottler.getForUid(mContext, params.uid).registerSuccess(
                    params.mPredictedUrl);
            RecordHistogram.recordCustomTimesHistogram("CustomTabs.PredictionToLaunch",
                    elapsedTimeMs, 1, TimeUnit.MINUTES.toMillis(3), TimeUnit.MILLISECONDS, 100);
        }
        RecordHistogram.recordEnumeratedHistogram(
                "CustomTabs.WarmupStateOnLaunch", getWarmupState(session), SESSION_WARMUP_COUNT);
        if (params != null) params.setPredictionMetrics(null, 0);
    }

    /**
     * See {@link PostMessageHandler#setPostMessageOrigin(Uri)}.
     */
    public synchronized void setPostMessageOriginForSession(
            CustomTabsSessionToken session, Uri origin) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return;
        params.postMessageHandler.setPostMessageOrigin(origin);
    }

    /**
     * See {@link PostMessageHandler#reset(WebContents)}.
     */
    public synchronized void resetPostMessageHandlerForSession(
            CustomTabsSessionToken session, WebContents webContents) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return;
        params.postMessageHandler.reset(webContents);
    }

    /**
     * @return The referrer that is associated with the client owning given session.
     */
    public synchronized Referrer getReferrerForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return null;
        final String packageName = params.packageName;
        return IntentHandler.constructValidReferrerForAuthority(packageName);
    }

    /**
     * @return The package name associated with the client owning the given session.
     */
    public synchronized String getClientPackageNameForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params == null ? null : params.packageName;
    }

    /**
     * @return The callback {@link CustomTabsSessionToken} for the given session.
     */
    public synchronized CustomTabsCallback getCallbackForSession(CustomTabsSessionToken session) {
        return session != null ? session.getCallback() : null;
    }

    /**
     * @return Whether the urlbar should be hidden for the session on first page load. Urls are
     *         foced to show up after the user navigates away.
     */
    public synchronized boolean shouldHideDomainForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params != null ? params.mShouldHideDomain : false;
    }

    /**
     * Sets whether the urlbar should be hidden for a given session.
     */
    public synchronized void setHideDomainForSession(CustomTabsSessionToken session, boolean hide) {
        SessionParams params = mSessionParams.get(session);
        if (params != null) params.mShouldHideDomain = hide;
    }

    /**
     * @return Whether navigation info should be recorded and shared for the session.
     */
    public synchronized boolean shouldSendNavigationInfoForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params != null ? params.mShouldSendNavigationInfo : false;
    }

    /**
     * Sets whether navigation info should be recorded and shared for the current navigation in this
     * session.
     */
    public synchronized void setSendNavigationInfoForSession(
            CustomTabsSessionToken session, boolean send) {
        SessionParams params = mSessionParams.get(session);
        if (params != null) params.mShouldSendNavigationInfo = send;
    }

    /**
     * @return Whether the fragment should be ignored for prerender matching.
     */
    public synchronized boolean getIgnoreFragmentsForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params == null ? false : params.mIgnoreFragments;
    }

    /** Sets whether the fragment should be ignored for prerender matching. */
    public synchronized void setIgnoreFragmentsForSession(
            CustomTabsSessionToken session, boolean value) {
        SessionParams params = mSessionParams.get(session);
        if (params != null) params.mIgnoreFragments = value;
    }

    /**
     * @return Whether prerender should be turned on for cellular networks for given session.
     */
    public synchronized boolean shouldPrerenderOnCellularForSession(
            CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params != null ? params.mShouldPrerenderOnCellular : false;
    }

    /**
     * @return Whether the session is using the default parameters (that is,
     *         don't ignore fragments and don't prerender on cellular connections).
     */
    public synchronized boolean usesDefaultSessionParameters(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params != null ? params.isDefault() : true;
    }

    /**
     * Sets whether prerender should be turned on for mobile networks for given session.
     */
    public synchronized void setPrerenderCellularForSession(
            CustomTabsSessionToken session, boolean prerender) {
        SessionParams params = mSessionParams.get(session);
        if (params != null) params.mShouldPrerenderOnCellular = prerender;
    }

    /** Tries to bind to a client to keep it alive, and returns true for success. */
    public synchronized boolean keepAliveForSession(CustomTabsSessionToken session, Intent intent) {
        // When an application is bound to a service, its priority is raised to
        // be at least equal to the application's one. This binds to a dummy
        // service (no calls to this service are made).
        if (intent == null || intent.getComponent() == null) return false;
        SessionParams params = mSessionParams.get(session);
        if (params == null) return false;

        String packageName = intent.getComponent().getPackageName();
        PackageManager pm = mContext.getApplicationContext().getPackageManager();
        // Only binds to the application associated to this session.
        if (!Arrays.asList(pm.getPackagesForUid(params.uid)).contains(packageName)) return false;
        Intent serviceIntent = new Intent().setComponent(intent.getComponent());
        // This ServiceConnection doesn't handle disconnects. This is on
        // purpose, as it occurs when the remote process has died. Since the
        // only use of this connection is to keep the application alive,
        // re-connecting would just re-create the process, but the application
        // state has been lost at that point, the callbacks invalidated, etc.
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {}
            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        boolean ok;
        try {
            ok = mContext.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            return false;
        }
        if (ok) params.setKeepAliveConnection(connection);
        return ok;
    }

    /** Unbind from the KeepAlive service for a client. */
    public synchronized void dontKeepAliveForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null || params.getKeepAliveConnection() == null) return;
        ServiceConnection connection = params.getKeepAliveConnection();
        params.setKeepAliveConnection(null);
        mContext.unbindService(connection);
    }

    /** See {@link RequestThrottler#isPrerenderingAllowed()} */
    public synchronized boolean isPrerenderingAllowed(int uid) {
        return RequestThrottler.getForUid(mContext, uid).isPrerenderingAllowed();
    }

    /** See {@link RequestThrottler#registerPrerenderRequest(String)} */
    public synchronized void registerPrerenderRequest(int uid, String url) {
        RequestThrottler.getForUid(mContext, uid).registerPrerenderRequest(url);
    }

    /** See {@link RequestThrottler#reset()} */
    public synchronized void resetThrottling(int uid) {
        RequestThrottler.getForUid(mContext, uid).reset();
    }

    /** See {@link RequestThrottler#ban()} */
    public synchronized void ban(int uid) {
        RequestThrottler.getForUid(mContext, uid).ban();
    }

    /**
     * Cleans up all data associated with all sessions.
     */
    public synchronized void cleanupAll() {
        List<CustomTabsSessionToken> sessions = new ArrayList<>(mSessionParams.keySet());
        for (CustomTabsSessionToken session : sessions) cleanupSession(session);
    }

    /**
     * Handle any clean up left after a session is destroyed.
     * @param session The session that has been destroyed.
     */
    public synchronized void cleanupSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return;
        mSessionParams.remove(session);
        if (params.disconnectCallback != null) params.disconnectCallback.run(session);
        mUidHasCalledWarmup.delete(params.uid);
    }
}
