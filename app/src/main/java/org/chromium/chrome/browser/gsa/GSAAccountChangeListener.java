// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.gsa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;

/**
 * Listens to te account change notifications from GSA.
 *
 * Depending on GSA's version, the account change notifications are dispatched:
 * - Through SSB_SERVICE, or
 * - Through a Broadcast.
 *
 * We proceed the following way:
 * 1. Connect to the GSA service.
 * 2. If GSA supports the broadcast, disconnect from it, otherwise keep the old method.
 */
public class GSAAccountChangeListener {
    // These are GSA constants.
    private static final String GSA_PACKAGE_NAME = "com.google.android.googlequicksearchbox";
    private static final String ACCOUNT_UPDATE_BROADCAST_INTENT =
            "com.google.android.apps.now.account_update_broadcast";
    private static final String KEY_SSB_BROADCASTS_ACCOUNT_CHANGE_TO_CHROME =
            "ssb_service:ssb_broadcasts_account_change_to_chrome";
    private static final String BROADCAST_INTENT_ACCOUNT_NAME_EXTRA = "account_name";
    public static final String ACCOUNT_UPDATE_BROADCAST_PERMISSION =
            "com.google.android.apps.now.CURRENT_ACCOUNT_ACCESS";

    private static GSAAccountChangeListener sInstance;

    // Reference count for the connection.
    private int mUsersCount;
    private GSAServiceClient mClient;

    private boolean mAlreadyReportedHistogram;

    /** @return the instance of GSAAccountChangeListener. */
    public static GSAAccountChangeListener getInstance() {
        if (sInstance == null) {
            Context context = ContextUtils.getApplicationContext();
            sInstance = new GSAAccountChangeListener(context);
        }
        return sInstance;
    }

    private GSAAccountChangeListener(Context context) {
        Context applicationContext = context.getApplicationContext();
        BroadcastReceiver accountChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!ACCOUNT_UPDATE_BROADCAST_INTENT.equals(intent.getAction())) return;
                String accountName = intent.getStringExtra(BROADCAST_INTENT_ACCOUNT_NAME_EXTRA);
                GSAState.getInstance(context.getApplicationContext()).setGsaAccount(accountName);
            }
        };
        applicationContext.registerReceiver(accountChangeReceiver,
                new IntentFilter(ACCOUNT_UPDATE_BROADCAST_INTENT),
                ACCOUNT_UPDATE_BROADCAST_PERMISSION, null);

        createGsaClientAndConnect(applicationContext);

        // If some future version of GSA no longer broadcasts the account change
        // notification, need to fall back to the service.
        //
        // The states are: USE_SERVICE and USE_BROADCAST. The initial state (when Chrome starts) is
        // USE_SERVICE.
        // The state transitions are:
        // - USE_SERVICE -> USE_BROADCAST: When GSA sends a message (through the service) declaring
        //                                 it supports the broadcasts.
        // - USE_BROADCAST -> USE_SERVICE: When GSA is updated.
        BroadcastReceiver gsaUpdatedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Uri data = intent.getData();
                if (data == null) return;

                String packageName = data.getEncodedSchemeSpecificPart();
                if (GSA_PACKAGE_NAME.equals(packageName)) {
                    Context applicationContext = context.getApplicationContext();
                    // We no longer know the account, but GSA will tell us momentarily (through
                    // the service).
                    GSAState.getInstance(applicationContext).setGsaAccount(null);
                    // GSA has been updated, it might no longer support the broadcast. Reconnect to
                    // check.
                    mClient = null;
                    createGsaClientAndConnect(applicationContext);
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        context.registerReceiver(gsaUpdatedReceiver, filter);
    }

    private void createGsaClientAndConnect(Context context) {
        Callback<Bundle> onMessageReceived = new Callback<Bundle>() {
            @Override
            public void onResult(Bundle result) {
                boolean supportsBroadcast =
                        result.getBoolean(KEY_SSB_BROADCASTS_ACCOUNT_CHANGE_TO_CHROME);
                if (supportsBroadcast) notifyGsaBroadcastsAccountChanges();
                // If GSA doesn't support the broadcast, we connect several times to the service per
                // Chrome session (since there is a disconnect() call in
                // ChromeActivity#onStopWithNative()). Only record the histogram once per startup to
                // avoid skewing the results.
                if (!mAlreadyReportedHistogram) {
                    RecordHistogram.recordBooleanHistogram(
                            "Search.GsaBroadcastsAccountChanges", supportsBroadcast);
                    mAlreadyReportedHistogram = true;
                }
            }
        };
        mClient = new GSAServiceClient(context, onMessageReceived);
        mClient.connect();
    }

    /**
     * Connects to the GSA service if GSA doesn't support notifications.
     */
    public void connect() {
        if (mClient != null) mClient.connect();
        mUsersCount++;
    }

    /**
     * Disconnects from the GSA service if GSA doesn't support notifications.
     */
    public void disconnect() {
        mUsersCount--;
        if (mClient != null && mUsersCount == 0) mClient.disconnect();
    }

    private void notifyGsaBroadcastsAccountChanges() {
        if (mClient == null) return;
        mClient.disconnect();
        mClient = null;
    }
}
