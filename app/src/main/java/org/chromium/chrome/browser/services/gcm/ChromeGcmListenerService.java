// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.services.gcm;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.google.ipc.invalidation.ticl.android2.channel.AndroidGcmController;

import org.chromium.base.ThreadUtils;
import org.chromium.components.gcm_driver.GCMDriver;

/**
 * Receives Downstream messages and status of upstream messages from GCM.
 */
public class ChromeGcmListenerService extends GcmListenerService {
    private static final String TAG = "ChromeGcmListener";

    @Override
    public void onMessageReceived(String from, Bundle data) {
        String invalidationSenderId = AndroidGcmController.get(this).getSenderId();
        if (from.equals(invalidationSenderId)) {
            AndroidGcmController.get(this).onMessageReceived(data);
            return;
        }
        pushMessageReceived(data);
    }

    @Override
    public void onMessageSent(String msgId) {
        Log.d(TAG, "Message sent successfully. Message id: " + msgId);
        GcmUma.recordGcmUpstreamHistogram(getApplicationContext(), GcmUma.UMA_UPSTREAM_SUCCESS);
    }

    @Override
    public void onSendError(String msgId, String error) {
        Log.w(TAG, "Error in sending message. Message id: " + msgId + " Error: " + error);
        GcmUma.recordGcmUpstreamHistogram(getApplicationContext(), GcmUma.UMA_UPSTREAM_SEND_FAILED);
    }

    @Override
    public void onDeletedMessages() {
        // TODO(johnme): Ask GCM to include the subtype in this event.
        Log.w(TAG, "Push messages were deleted, but we can't tell the Service Worker as we don't"
                + "know what subtype (app ID) it occurred for.");
    }

    private void pushMessageReceived(final Bundle data) {
        final String bundleSubtype = "subtype";
        if (!data.containsKey(bundleSubtype)) {
            Log.w(TAG, "Received push message with no subtype");
            return;
        }
        final String appId = data.getString(bundleSubtype);
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GCMDriver.onMessageReceived(getApplicationContext(), appId, data);
            }
        });
    }
}
