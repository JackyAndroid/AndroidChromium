// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.chromium.base.CommandLine;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.content.app.ContentApplication;
import org.chromium.content.browser.BrowserStartupController;

/**
 * The Notification service receives intents fired as responses to user actions issued on Android
 * notifications displayed in the notification tray.
 */
public class NotificationService extends IntentService {
    private static final String TAG = NotificationService.class.getSimpleName();

    /**
     * The class which receives the intents from the Android framework. It initializes the
     * Notification service, and forward the intents there. Declared public as it needs to be
     * initialized by the Android framework.
     */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received a notification intent in the NotificationService's receiver.");

            // TODO(peter): Do we need to acquire a wake lock here?

            intent.setClass(context, NotificationService.class);
            context.startService(intent);
        }
    }

    public NotificationService() {
        super(TAG);
    }

    /**
     * Called when a Notification has been interacted with by the user. If we can verify that
     * the Intent has a notification Id, start Chrome (if needed) on the UI thread.
     *
     * @param intent The intent containing the specific information.
     */
    @Override
    public void onHandleIntent(final Intent intent) {
        if (!intent.hasExtra(NotificationConstants.EXTRA_PERSISTENT_NOTIFICATION_ID)
                || !intent.hasExtra(NotificationConstants.EXTRA_NOTIFICATION_INFO_ORIGIN)
                || !intent.hasExtra(NotificationConstants.EXTRA_NOTIFICATION_INFO_TAG)) {
            return;
        }

        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dispatchIntentOnUIThread(intent);
            }
        });
    }

    /**
     * Initializes Chrome and starts the browser process if it's not running as of yet, and
     * dispatch |intent| to the NotificationUIManager once this is done.
     *
     * @param intent The intent containing the notification's information.
     */
    @SuppressFBWarnings("DM_EXIT")
    private void dispatchIntentOnUIThread(Intent intent) {
        Context context = getApplicationContext();
        if (!CommandLine.isInitialized()) {
            ContentApplication.initCommandLine(context);
        }

        try {
            BrowserStartupController.get(this, LibraryProcessType.PROCESS_BROWSER)
                    .startBrowserProcessesSync(false);

            // Now that the browser process is initialized, we pass forward the call to the
            // Notification UI Manager which will take care of delivering the appropriate events.
            if (!NotificationUIManager.dispatchNotificationEvent(intent)) {
                Log.w(TAG, "Unable to dispatch the notification event to Chrome.");
            }

            // TODO(peter): Verify that the lifetime of the NotificationService is sufficient
            // when a notification event could be dispatched successfully.

            // TODO(peter): The native side needs to tell us when executing the event has
            // finished, so that we can forcefully stop the service.

        } catch (ProcessInitException e) {
            Log.e(TAG, "Unable to start the browser process.", e);
            System.exit(-1);
        }
    }
}
