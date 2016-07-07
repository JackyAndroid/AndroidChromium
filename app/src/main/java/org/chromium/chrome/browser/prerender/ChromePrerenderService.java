// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.prerender;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;
import android.util.Log;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ApplicationInitialization;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.VerifiedHandler;
import org.chromium.content.browser.ChildProcessLauncher;

/**
 * A bound service that warms up Chrome and performs actions related with prerendering urls.
 */
public class ChromePrerenderService extends Service {
    public static final int MSG_PRERENDER_URL = 1;
    public static final int MSG_CANCEL_PRERENDER = 2;
    public static final String KEY_PRERENDERED_URL = "url_to_prerender";
    public static final String KEY_PRERENDER_WIDTH = "prerender_width";
    public static final String KEY_PRERENDER_HEIGHT = "prerender_height";
    public static final String KEY_REFERRER = "referrer";

    private static class LauncherWarmUpTask extends AsyncTask<Context, Void, Void> {
        @Override
        protected Void doInBackground(Context... args) {
            ChildProcessLauncher.warmUp(args[0]);
            return null;
        }
    }

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends VerifiedHandler {
        IncomingHandler(Context context) {
            super(context, ChromeVersionInfo.isLocalBuild()
                    ? 0 : ExternalAuthUtils.FLAG_SHOULD_BE_GOOGLE_SIGNED);
        }

        @Override
        public void handleMessage(Message msg) {
            ChromePrerenderService.this.handleMessage(msg);
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private Messenger mMessenger;

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @SuppressFBWarnings("DM_EXIT")
    @Override
    public IBinder onBind(Intent intent) {
        mMessenger = new Messenger(new IncomingHandler(getApplicationContext()));

        try {
            new LauncherWarmUpTask().execute(getApplicationContext());
            ((ChromeApplication) getApplication())
                    .startBrowserProcessesAndLoadLibrariesSync(true);

            ApplicationInitialization.enableFullscreenFlags(
                    getApplicationContext().getResources(),
                    getApplicationContext(), R.dimen.control_container_height);
        } catch (ProcessInitException e) {
            Log.e(this.getClass().toString(),
                    "ProcessInitException while starting the browser process");
            // Since the library failed to initialize nothing in the application
            // can work, so kill the whole application not just the activity
            System.exit(-1);
        }
        return mMessenger.getBinder();
    }

    /**
     * Handle and incoming message from the messenger. Child classes adding new messages
     * should extend this call and handle them separately.
     * @param msg The message to be handled.
     */
    protected void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_PRERENDER_URL:
                final String url = msg.getData().getString(KEY_PRERENDERED_URL);
                final String referrer = msg.getData().getString(KEY_REFERRER, "");
                final int width = msg.getData().getInt(KEY_PRERENDER_WIDTH, 0);
                final int height = msg.getData().getInt(KEY_PRERENDER_HEIGHT, 0);
                if (!TextUtils.isEmpty(url)) {
                    ThreadUtils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            prerenderUrl(url, referrer, width, height);
                        }
                    });
                }
                break;
            case MSG_CANCEL_PRERENDER:
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WarmupManager.getInstance().cancelCurrentPrerender();
                    }
                });
                break;
            default:
                break;
        }
    }

    private void prerenderUrl(String url, String referrer, int width, int height) {
        WarmupManager.getInstance().prerenderUrl(url, referrer, width, height);
    }
}
