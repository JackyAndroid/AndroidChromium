// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import android.accounts.Account;
import android.app.Application;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.init.BrowserParts;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.init.EmptyBrowserParts;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.invalidation.PendingInvalidation;
import org.chromium.components.signin.ChromeSigninController;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A Sync adapter that receives invalidations from {@link InvalidationClientService} and dispatches
 * it to the native side with a caching layer in {@link DelayedInvalidationsController}.
 */
public class ChromeBrowserSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = "invalidation";

    private final Application mApplication;

    public ChromeBrowserSyncAdapter(Context context, Application application) {
        super(context, false);
        mApplication = application;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        if (extras.getBoolean(ContentResolver.SYNC_EXTRAS_INITIALIZE)) {
            Account signedInAccount = ChromeSigninController.get(getContext()).getSignedInUser();
            if (account.equals(signedInAccount)) {
                ContentResolver.setIsSyncable(account, authority, 1);
            } else {
                ContentResolver.setIsSyncable(account, authority, 0);
            }
            return;
        }
        PendingInvalidation invalidation = new PendingInvalidation(extras);

        DelayedInvalidationsController controller = DelayedInvalidationsController.getInstance();
        if (!controller.shouldNotifyInvalidation(extras)) {
            controller.addPendingInvalidation(getContext(), account.name, invalidation);
            return;
        }

        // Browser startup is asynchronous, so we will need to wait for startup to finish.
        Semaphore semaphore = new Semaphore(0);

        // Configure the BrowserParts with all the data it needs.
        BrowserParts parts =
                getBrowserParts(mApplication, account.name, invalidation, syncResult, semaphore);
        startBrowserProcess(parts, syncResult, semaphore);

        try {
            // This code is only synchronously calling a single native method
            // to trigger and asynchronous sync cycle, so 5 minutes is generous.
            if (!semaphore.tryAcquire(5, TimeUnit.MINUTES)) {
                Log.w(TAG, "Sync request timed out!");
                syncResult.stats.numIoExceptions++;
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Got InterruptedException when trying to request an invalidation.", e);
            // Using numIoExceptions so Android will treat this as a soft error.
            syncResult.stats.numIoExceptions++;
        }
    }

    private void startBrowserProcess(final BrowserParts parts, final SyncResult syncResult,
            Semaphore semaphore) {
        try {
            ThreadUtils.runOnUiThreadBlocking(new Runnable() {
                @Override
                @SuppressFBWarnings("DM_EXIT")
                public void run() {
                    ChromeBrowserInitializer.getInstance(getContext()).handlePreNativeStartup(
                            parts);
                    try {
                        ChromeBrowserInitializer.getInstance(getContext())
                                .handlePostNativeStartup(false, parts);
                    } catch (ProcessInitException e) {
                        Log.e(TAG, "Unable to load native library.", e);
                        System.exit(-1);
                    }
                }
            });
        } catch (RuntimeException e) {
            // It is still unknown why we ever experience this. See http://crbug.com/180044.
            Log.w(TAG, "Got exception when trying to notify the invalidation.", e);
            // Using numIoExceptions so Android will treat this as a soft error.
            syncResult.stats.numIoExceptions++;
            semaphore.release();
        }
    }

    private BrowserParts getBrowserParts(final Context context,
            final String account, final PendingInvalidation invalidation,
            final SyncResult syncResult, final Semaphore semaphore) {
        return new EmptyBrowserParts() {
            @Override
            public void finishNativeInitialization() {
                // Startup succeeded, so we can notify the invalidation.
                notifyInvalidation(invalidation.mObjectSource, invalidation.mObjectId,
                        invalidation.mVersion, invalidation.mPayload);
                semaphore.release();
            }

            @Override
            public void onStartupFailure() {
                // The startup failed, so we defer the invalidation.
                DelayedInvalidationsController.getInstance().addPendingInvalidation(
                        context, account, invalidation);
                // Using numIoExceptions so Android will treat this as a soft error.
                syncResult.stats.numIoExceptions++;
                semaphore.release();
            }
        };
    }

    @VisibleForTesting
    public void notifyInvalidation(
            int objectSource, String objectId, long version, String payload) {
        InvalidationServiceFactory.getForProfile(Profile.getLastUsedProfile())
                .notifyInvalidationToNativeChrome(objectSource, objectId, version, payload);
    }
}
