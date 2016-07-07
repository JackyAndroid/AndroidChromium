// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.gsa;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeApplication;

/**
 * A simple client that connects and talks to the GSAService using Messages.
 */
public class GSAServiceClient {
    private static final String TAG = "GSAServiceClient";

    /**
     * Constants for gsa communication. These should not change without corresponding changes on the
     * service side in GSA.
     */
    private static final String GSA_SERVICE =
            "com.google.android.ssb.action.SSB_SERVICE";
    public static final int REQUEST_REGISTER_CLIENT = 2;
    public static final int RESPONSE_UPDATE_SSB = 3;

    public static final String KEY_GSA_STATE = "ssb_service:ssb_state";
    public static final String KEY_GSA_CONTEXT = "ssb_service:ssb_context";
    public static final String KEY_GSA_PACKAGE_NAME = "ssb_service:ssb_package_name";

    /** Messenger to handle incoming messages from the service */
    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    private final GSAServiceConnection mConnection;
    private final GSAHelper mGsaHelper;
    private Context mContext;

    /** Messenger for communicating with service. */
    private Messenger mService;


    /**
     * Handler of incoming messages from service.
     */
    @SuppressFBWarnings("BC_IMPOSSIBLE_CAST")
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == RESPONSE_UPDATE_SSB) {
                if (mService == null) return;
                Bundle bundle = (Bundle) msg.obj;
                String account =
                        mGsaHelper.getGSAAccountFromState(bundle.getByteArray(KEY_GSA_STATE));
                GSAState.getInstance(mContext.getApplicationContext()).setGsaAccount(account);
            } else {
                super.handleMessage(msg);
            }
        }
    }

    /**
     * Constructs an instance of this class.
     */
    public GSAServiceClient(Context context) {
        mContext = context;
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
    public boolean connect() {
        if (mService != null) Log.e(TAG, "Already connected.");
        Intent intent = new Intent(GSA_SERVICE).setPackage(GSAState.SEARCH_INTENT_PACKAGE);
        return mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Disconnects from the service and resets the client's state.
     */
    public void disconnect() {
        if (mService == null) return;

        mContext.unbindService(mConnection);
        mContext = null;
        mService = null;
    }

    /**
     * Indicates whether or not the client is currently connected to the service.
     * @return true if connected, false otherwise.
     */
    public boolean isConnected() {
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
