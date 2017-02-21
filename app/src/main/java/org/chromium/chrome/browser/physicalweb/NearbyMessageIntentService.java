// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.app.IntentService;
import android.content.Intent;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;

/**
 * Service that handles intents from Nearby.
 */
public class NearbyMessageIntentService extends IntentService {
    private static final MessageListener MESSAGE_LISTENER = new MessageListener() {
        @Override
        public void onFound(Message message) {
            String url = PhysicalWebBleClient.getInstance().getUrlFromMessage(message);
            if (url != null) {
                UrlManager.getInstance().addUrl(new UrlInfo(url));
            }
        }

        @Override
        public void onLost(Message message) {
            String url = PhysicalWebBleClient.getInstance().getUrlFromMessage(message);
            if (url != null) {
                UrlManager.getInstance().removeUrl(new UrlInfo(url));
            }
        }
    };

    public NearbyMessageIntentService() {
        super(NearbyMessageIntentService.class.getSimpleName());
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Nearby.Messages.handleIntent(intent, MESSAGE_LISTENER);
    }
}
