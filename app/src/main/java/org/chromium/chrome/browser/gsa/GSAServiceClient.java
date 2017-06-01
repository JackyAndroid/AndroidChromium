// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.gsa;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeApplication;

import java.util.List;

/**
 * A simple client that connects and talks to the GSAService using Messages.
 */
public class GSAServiceClient {
    private static final String TAG = "GSAServiceClient";

    /**
     * Constants for gsa communication. These should not change without corresponding changes on the
     * service side in GSA.
     */
    @VisibleForTesting
    static final String GSA_SERVICE = "com.google.android.ssb.action.SSB_SERVICE";
    public static final int REQUEST_REGISTER_CLIENT = 2;
    public static final int RESPONSE_UPDATE_SSB = 3;

    public static final String KEY_GSA_STATE = "ssb_service:ssb_state";
    public static final String KEY_GSA_CONTEXT = "ssb_service:ssb_context";
    public static final String KEY_GSA_PACKAGE_NAME = "ssb_service:ssb_package_name";

    @VisibleForTesting
    static final int INVALID_PSS = -1;

    private static boolean sHasRecordedPss;
    /** Messenger to handle incoming messages from the service */
    private final Messenger mMessenger;
    private final IncomingHandler mHandler;
    private final GSAServiceConnection mConnection;
    private final GSAHelper mGsaHelper;
    private Context mContext;
    private Callback<Bundle> mOnMessageReceived;

    /** Messenger for communicating with service. */
    private Messenger mService;
    private ComponentName mComponentName;

    /**
     * Handler of incoming messages from service.
     */
    @SuppressFBWarnings("BC_IMPOSSIBLE_CAST")
    @SuppressLint("HandlerLeak")
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what != RESPONSE_UPDATE_SSB) {
                super.handleMessage(msg);
                return;
            }

            if (mService == null) return;
            final Bundle bundle = (Bundle) msg.obj;
            String account = mGsaHelper.getGSAAccountFromState(bundle.getByteArray(KEY_GSA_STATE));
            GSAState.getInstance(mContext.getApplicationContext()).setGsaAccount(account);
            if (sHasRecordedPss) {
                if (mOnMessageReceived != null) mOnMessageReceived.onResult(bundle);
                return;
            }

            // Getting the PSS for the GSA service process can be long, don't block the UI thread on
            // that. Also, don't process the callback before the PSS is known, since the callback
            // can lead to a service disconnect, which can lead to the framework killing the
            // process. Hence an AsyncTask (long operation), and processing the callback in
            // onPostExecute() (don't disconnect before).
            sHasRecordedPss = true;
            new AsyncTask<Void, Void, Integer>() {
                @Override
                protected Integer doInBackground(Void... params) {
                    TraceEvent.begin("GSAServiceClient.getPssForservice");
                    try {
                        // Looking for the service process is done by component name, which is
                        // inefficient. We really want the PID, which is only accessible from within
                        // a Binder transaction. Since the service connection is Messenger-based,
                        // the calls are not processed from a Binder thread. The alternatives are:
                        // 1. Override methods in the framework to append the calling PID to the
                        //    Message.
                        // 2. Usse msg.callingUid to narrow down the search.
                        //
                        // (1) is dirty (and brittle), and (2) only works on L+, and still requires
                        // to get the full list of services from ActivityManager.
                        return getPssForService(mComponentName);
                    } finally {
                        TraceEvent.end("GSAServiceClient.getPssForservice");
                    }
                }

                @Override
                protected void onPostExecute(Integer pssInKB) {
                    if (pssInKB != INVALID_PSS) {
                        RecordHistogram.recordMemoryKBHistogram(
                                "Search.GsaProcessMemoryPss", pssInKB);
                    }
                    if (mOnMessageReceived != null) mOnMessageReceived.onResult(bundle);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * Get the PSS used by the process hosting a service.
     *
     * @param packageName Package name of the service to search for.
     * @return the PSS in kB of the process hosting a service, or INVALID_PSS.
     */
    @VisibleForTesting
    static int getPssForService(ComponentName componentName) {
        if (componentName == null) return INVALID_PSS;
        Context context = ContextUtils.getApplicationContext();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services =
                activityManager.getRunningServices(1000);
        if (services == null) return INVALID_PSS;
        int pid = -1;
        for (ActivityManager.RunningServiceInfo info : services) {
            if (componentName.equals(info.service)) {
                pid = info.pid;
                break;
            }
        }
        if (pid == -1) return INVALID_PSS;
        Debug.MemoryInfo infos[] = activityManager.getProcessMemoryInfo(new int[] {pid});
        if (infos == null || infos.length == 0) return INVALID_PSS;
        return infos[0].getTotalPss();
    }

    /**
     * Constructs an instance of this class.
     *
     * @param context Appliation context.
     * @param onMessageReceived optional callback when a message is received.
     */
    GSAServiceClient(Context context, Callback<Bundle> onMessageReceived) {
        mContext = context.getApplicationContext();
        mOnMessageReceived = onMessageReceived;
        mHandler = new IncomingHandler();
        mMessenger = new Messenger(mHandler);
        mConnection = new GSAServiceConnection();
        mGsaHelper = ((ChromeApplication) mContext.getApplicationContext())
                .createGsaHelper();
    }

    /**
     * Establishes a connection to the service. Call this method once the callback passed here is
     * ready to handle calls.
     * If you pass in an GSA context, it will be sent up the service as soon as the connection is
     * established.
     * @return Whether or not the connection to the service was established successfully.
     */
    boolean connect() {
        if (mService != null) Log.e(TAG, "Already connected.");
        Intent intent = new Intent(GSA_SERVICE).setPackage(GSAState.SEARCH_INTENT_PACKAGE);
        return mContext.bindService(
                intent, mConnection, Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND);
    }

    /**
     * Disconnects from the service and resets the client's state.
     */
    void disconnect() {
        if (mService == null) return;
        mContext.unbindService(mConnection);
        mService = null;

        // Remove pending handler actions to prevent memory leaks.
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Indicates whether or not the client is currently connected to the service.
     * @return true if connected, false otherwise.
     */
    boolean isConnected() {
        return mService != null;
    }

    private class GSAServiceConnection implements ServiceConnection {
        private static final String SERVICE_CONNECTION_TAG = "GSAServiceConnection";

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Ignore this call if we disconnected in the meantime.
            if (mContext == null) return;

            mService = new Messenger(service);
            mComponentName = name;
            try {
                Message registerClientMessage = Message.obtain(
                        null, REQUEST_REGISTER_CLIENT);
                registerClientMessage.replyTo = mMessenger;
                Bundle b = mGsaHelper.getBundleForRegisteringGSAClient(mContext);
                registerClientMessage.setData(b);
                registerClientMessage.getData().putString(
                        KEY_GSA_PACKAGE_NAME, mContext.getPackageName());
                mService.send(registerClientMessage);
                // Send prepare overlay message if there is a pending GSA context.
            } catch (RemoteException e) {
                Log.w(SERVICE_CONNECTION_TAG, "GSAServiceConnection - remote call failed", e);
            }
        }
    }
}
