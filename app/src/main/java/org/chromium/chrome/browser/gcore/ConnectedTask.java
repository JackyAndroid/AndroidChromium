// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.gcore;

import org.chromium.base.Log;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.RemovableInRelease;

import java.util.concurrent.TimeUnit;

/**
 * Base class for tasks which connects to Google Play Services using given GoogleApiClient,
 * performs action specified in doWhenConnected method, disconnects the client and cleans up
 * by invoking cleanUp method.
 *
 * <p>
 * Using the same client for tasks running in more than one thread is a serious error, as
 * the state can then be modified while other threads are still using the client. The
 * recommended way to use these tasks is with a {@link java.util.concurrent.ThreadPoolExecutor}
 * having a pool size of 1.
 * </p>
 * <p>
 * This class waits {@link #CONNECTION_TIMEOUT_MS} milliseconds for connection to be established.
 * If connection is unsuccessful then it will retry after {@link #CONNECTION_RETRY_TIME_MS}
 * milliseconds as long as Google Play Services is available. Number of retries is limited to
 * {@link #RETRY_NUMBER_LIMIT}.
 * </p>
 *
 * @param <T> type of {@link ChromeGoogleApiClient} to use for the tasks
 */
public abstract class ConnectedTask<T extends ChromeGoogleApiClient> implements Runnable {
    private static final String TAG = "GCore";

    public static final long CONNECTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
    public static final long CONNECTION_RETRY_TIME_MS = TimeUnit.SECONDS.toMillis(10);
    public static final int RETRY_NUMBER_LIMIT = 5;

    private final T mClient;
    private int mRetryNumber;

    /**
     * Used for logging and tracing.
     * <ul>
     * <li>Log format: "{logPrefix}| {{@link #getName()}} {message}"</li>
     * <li>Trace format: "ConnectedTask:{logPrefix}:{traceEventName}"</li>
     * </ul>
     */
    private final String mLogPrefix;

    /**
     * @param client
     * @param logPrefix used for logging and tracing.
     */
    public ConnectedTask(T client, String logPrefix) {
        assert logPrefix != null;
        mClient = client;
        mLogPrefix = logPrefix;
    }

    /** Creates a connected task with an empty log prefix. */
    @VisibleForTesting
    public ConnectedTask(T client) {
        this(client, "");
    }

    /**
     * Executed with client connected to Google Play Services.
     * This method is intended to be overridden by a subclass.
     */
    protected abstract void doWhenConnected(T client);

    /**
     * Returns a name of a task. Implementations should not have side effects
     * as we want to have the logging related calls removed.
     */
    @RemovableInRelease
    protected abstract String getName();

    /**
     * Executed after doWhenConnected was done and client was disconnected.
     * May also be executed when Google Play Services is no longer available, which means connection
     * was unsuccessful and won't be retried.
     * This method is intended to be overridden by a subclass.
     */
    protected void cleanUp() {}

    /**
     * Executed if the connection was unsuccessful.
     * This method is intended to be overridden by a subclass.
     */
    protected void connectionFailed() {}

    @Override
    @VisibleForTesting
    public final void run() {
        TraceEvent.begin("GCore:" + mLogPrefix + ":run");
        try {
            Log.d(TAG, "%s:%s started", mLogPrefix, getName());
            if (mClient.connectWithTimeout(CONNECTION_TIMEOUT_MS)) {
                try {
                    Log.d(TAG, "%s:%s connected", mLogPrefix, getName());
                    doWhenConnected(mClient);
                    Log.d(TAG, "%s:%s finished", mLogPrefix, getName());
                } finally {
                    mClient.disconnect();
                    Log.d(TAG, "%s:%s disconnected", mLogPrefix, getName());
                    cleanUp();
                    Log.d(TAG, "%s:%s cleaned up", mLogPrefix, getName());
                }
            } else {
                mRetryNumber++;
                if (mRetryNumber < RETRY_NUMBER_LIMIT && mClient.isGooglePlayServicesAvailable()) {
                    Log.d(TAG, "%s:%s calling retry", mLogPrefix, getName());
                    retry(this, CONNECTION_RETRY_TIME_MS);
                } else {
                    connectionFailed();
                    Log.d(TAG, "%s:%s number of retries exceeded", mLogPrefix, getName());
                    cleanUp();
                    Log.d(TAG, "%s:%s cleaned up", mLogPrefix, getName());
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "%s:%s runtime exception %s: %s", mLogPrefix, getName(),
                    e.getClass().getName(), e.getMessage());
            throw e;
        } finally {
            TraceEvent.end("GCore:" + mLogPrefix + ":run");
        }
    }

    /** Method to implement to determine how to run the retry task. */
    protected abstract void retry(Runnable task, long delayMs);
}
