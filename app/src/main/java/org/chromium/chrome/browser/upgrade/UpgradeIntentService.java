// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.upgrade;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.tabmodel.DocumentModeAssassin;
import org.chromium.chrome.browser.tabmodel.DocumentModeAssassin.DocumentModeAssassinObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Migrates users back from document mode into tabbed mode without using the native library on an
 * upgrade.  The timeout length is arbitrary but exists to avoid keeping the service alive forever.
 */
public class UpgradeIntentService extends IntentService {

    private static final String TAG = "UpgradeIntentService";
    private static final long TIMEOUT_MS = 10000;

    public static void startMigrationIfNecessary(Context context) {
        if (!DocumentModeAssassin.getInstance().isMigrationNecessary()) return;

        Intent migrationIntent = new Intent();
        migrationIntent.setClass(context, UpgradeIntentService.class);
        context.startService(migrationIntent);
    }

    public UpgradeIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final DocumentModeAssassin assassin = DocumentModeAssassin.getInstance();
        if (!assassin.isMigrationNecessary()) return;

        final CountDownLatch finishSignal = new CountDownLatch(1);
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (assassin.isMigrationNecessary()) {
                    // Kick off migration if it hasn't already started.
                    DocumentModeAssassinObserver observer = new DocumentModeAssassinObserver() {
                        @Override
                        public void onStageChange(int newStage) {
                            if (newStage != DocumentModeAssassin.STAGE_DONE) return;
                            assassin.removeObserver(this);
                            finishSignal.countDown();
                        }
                    };
                    assassin.addObserver(observer);
                    assassin.migrateFromDocumentToTabbedMode();
                } else {
                    // Migration finished in the background.
                    finishSignal.countDown();
                }
            }
        });

        try {
            boolean success = finishSignal.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Log.d(TAG, "Migration completed.  Status: " + success);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to migrate user on time.");
        }
    }
}
