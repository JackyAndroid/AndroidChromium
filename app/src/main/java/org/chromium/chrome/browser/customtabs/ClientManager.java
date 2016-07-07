// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.customtabs.ICustomTabsCallback;
import android.text.TextUtils;
import android.util.SparseBooleanArray;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.IntentHandler;
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
    private static final int NO_PREDICTION = 0;
    private static final int GOOD_PREDICTION = 1;
    private static final int BAD_PREDICTION = 2;
    private static final int PREDICTION_STATUS_COUNT = 3;
    // Values for the "CustomTabs.CalledWarmup" UMA histogram. Append-only.
    @VisibleForTesting static final int NO_SESSION_NO_WARMUP = 0;
    @VisibleForTesting static final int NO_SESSION_WARMUP = 1;
    @VisibleForTesting static final int SESSION_NO_WARMUP_ALREADY_CALLED = 2;
    @VisibleForTesting static final int SESSION_NO_WARMUP_NOT_CALLED = 3;
    @VisibleForTesting static final int SESSION_WARMUP = 4;
    @VisibleForTesting static final int SESSION_WARMUP_COUNT = 5;

    /** Per-session values. */
    private static class SessionParams {
        public final int uid;
        public final String packageName;
        public final ICustomTabsCallback callback;
        public final IBinder.DeathRecipient deathRecipient;
        private ServiceConnection mKeepAliveConnection = null;
        private String mPredictedUrl = null;
        private long mLastMayLaunchUrlTimestamp = 0;

        public SessionParams(Context context, int uid, ICustomTabsCallback callback,
                IBinder.DeathRecipient deathRecipient) {
            this.uid = uid;
            packageName = getPackageName(context, uid);
            this.callback = callback;
            this.deathRecipient = deathRecipient;
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
    }

    /** To be called when a client gets disconnected. */
    public interface DisconnectCallback { public void run(IBinder session); }

    private final Context mContext;
    private final Map<IBinder, SessionParams> mSessionParams = new HashMap<>();
    private final SparseBooleanArray mUidHasCalledWarmup = new SparseBooleanArray();
    private boolean mWarmupHasBeenCalled = false;

    public ClientManager(Context context) {
        mContext = context.getApplicationContext();
        RequestThrottler.loadInBackground(mContext);
    }

    /** Creates a new session.
     *
     * @param cb Callback provided by the client.
     * @param uid Client UID, as returned by Binder.getCallingUid(),
     * @param onDisconnect To be called on the UI thread when a client gets disconnected.
     * @return true for success.
     */
    public boolean newSession(
            ICustomTabsCallback cb, int uid, final DisconnectCallback onDisconnect) {
        if (cb == null) return false;
        final IBinder session = cb.asBinder();
        IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                ThreadUtils.postOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cleanupSession(session);
                        onDisconnect.run(session);
                    }
                });
            }
        };
        SessionParams params = new SessionParams(mContext, uid, cb, deathRecipient);
        synchronized (this) {
            if (mSessionParams.containsKey(session)) return false;
            try {
                session.linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                // The return code doesn't matter, because this executes when
                // the caller has died.
                return false;
            }
            mSessionParams.put(session, params);
        }
        return true;
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
            IBinder session, int uid, String url) {
        SessionParams params = mSessionParams.get(session);
        if (params == null || params.uid != uid) return false;
        params.setPredictionMetrics(url, SystemClock.elapsedRealtime());
        RequestThrottler throttler = RequestThrottler.getForUid(mContext, uid);
        return throttler.updateStatsAndReturnWhetherAllowed();
    }

    @VisibleForTesting
    synchronized int getWarmupState(IBinder session) {
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

    /**
     * Registers that a client has launched a URL inside a Custom Tab.
     */
    public synchronized void registerLaunch(IBinder session, String url) {
        int outcome = NO_PREDICTION;
        long elapsedTimeMs = -1;
        SessionParams params = mSessionParams.get(session);
        if (params != null) {
            String predictedUrl = params.getPredictedUrl();
            outcome = predictedUrl == null ? NO_PREDICTION : predictedUrl.equals(url)
                            ? GOOD_PREDICTION
                            : BAD_PREDICTION;
            long now = SystemClock.elapsedRealtime();
            elapsedTimeMs = now - params.getLastMayLaunchUrlTimestamp();
            params.setPredictionMetrics(null, 0);
            if (outcome == GOOD_PREDICTION) {
                RequestThrottler.getForUid(mContext, params.uid).registerSuccess(url);
            }
        }
        RecordHistogram.recordEnumeratedHistogram(
                "CustomTabs.PredictionStatus", outcome, PREDICTION_STATUS_COUNT);
        if (outcome == GOOD_PREDICTION) {
            RecordHistogram.recordCustomTimesHistogram("CustomTabs.PredictionToLaunch",
                    elapsedTimeMs, 1, TimeUnit.MINUTES.toMillis(3), TimeUnit.MILLISECONDS, 100);
        }
        RecordHistogram.recordEnumeratedHistogram(
                "CustomTabs.WarmupStateOnLaunch", getWarmupState(session), SESSION_WARMUP_COUNT);
    }

    /**
     * @return The referrer that is associated with the client owning given session.
     */
    public synchronized Referrer getReferrerForSession(IBinder session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return null;
        final String packageName = params.packageName;
        return IntentHandler.constructValidReferrerForAuthority(packageName);
    }

    /**
     * @return The package name associated with the client owning the given session.
     */
    public synchronized String getClientPackageNameForSession(IBinder session) {
        SessionParams params = mSessionParams.get(session);
        return params == null ? null : params.packageName;
    }

    /**
     * @return The callback {@link IBinder} for the given session.
     */
    public synchronized ICustomTabsCallback getCallbackForSession(IBinder session) {
        SessionParams params = mSessionParams.get(session);
        return params != null ? params.callback : null;
    }

    /** Tries to bind to a client to keep it alive, and returns true for success. */
    public synchronized boolean keepAliveForSession(IBinder session, Intent intent) {
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
    public synchronized void dontKeepAliveForSession(IBinder session) {
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

    /**
     * Cleans up all data associated with all sessions.
     */
    public synchronized void cleanupAll() {
        List<IBinder> sessions = new ArrayList<>(mSessionParams.keySet());
        for (IBinder session : sessions) cleanupSession(session);
    }

    private synchronized void cleanupSession(IBinder session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return;
        mSessionParams.remove(session);
        mUidHasCalledWarmup.delete(params.uid);
        IBinder binder = params.callback.asBinder();
        binder.unlinkToDeath(params.deathRecipient, 0);
    }
}
