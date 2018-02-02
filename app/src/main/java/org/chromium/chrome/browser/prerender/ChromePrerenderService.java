// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.prerender;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.VerifiedHandler;

/**
 * A bound service that does nothing. Kept here to prevent old clients relying on it being
 * available from crashing.
 */
public class ChromePrerenderService extends Service {
    /**
     * Handler of incoming messages from clients.
     */
    static class IncomingHandler extends VerifiedHandler {
        IncomingHandler(Context context) {
            super(context, ChromeVersionInfo.isLocalBuild()
                    ? 0 : ExternalAuthUtils.FLAG_SHOULD_BE_GOOGLE_SIGNED);
        }

        @Override
        public void handleMessage(Message msg) {}
    }

    private Messenger mMessenger;

    @Override
    public IBinder onBind(Intent intent) {
        mMessenger = new Messenger(new IncomingHandler(getApplicationContext()));
        return mMessenger.getBinder();
    }
}
