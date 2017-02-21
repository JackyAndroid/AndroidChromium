// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.upgrade;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.tabmodel.DocumentModeAssassin;
import org.chromium.chrome.browser.tabmodel.DocumentModeAssassin.DocumentModeAssassinObserver;
import org.chromium.chrome.browser.util.IntentUtils;

/**
 * Activity that interrupts launch and shows that users are being upgraded to a new version of
 * Chrome.
 *
 * TODO(dfalcantara): Do we need to worry about onNewIntent()?
 */
public class UpgradeActivity extends AppCompatActivity {
    public static final String EXTRA_INTENT_TO_REFIRE =
            "org.chromium.chrome.browser.upgrade.INTENT_TO_REFIRE";

    private static final long MIN_MS_TO_DISPLAY_ACTIVITY = 3000;
    private static final long INVALID_TIMESTAMP = -1;

    private final Handler mHandler;
    private final DocumentModeAssassinObserver mObserver;

    private Intent mIntentToFireAfterUpgrade;
    private long mStartTimestamp = INVALID_TIMESTAMP;
    private boolean mIsDestroyed;

    public static void launchInstance(Activity activity, Intent originalIntent) {
        Intent intent = new Intent();
        intent.setClass(activity, UpgradeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.putExtra(UpgradeActivity.EXTRA_INTENT_TO_REFIRE, originalIntent);
        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public UpgradeActivity() {
        mHandler = new Handler(Looper.getMainLooper());

        mObserver = new DocumentModeAssassinObserver() {
            @Override
            public void onStageChange(int newStage) {
                if (newStage != DocumentModeAssassin.STAGE_DONE) return;
                DocumentModeAssassin.getInstance().removeObserver(this);

                // Always post to avoid any issues that could arise from firing the Runnable
                // while other Observers are being alerted.
                long msElapsed = System.currentTimeMillis() - mStartTimestamp;
                long msRemaining = Math.max(0, MIN_MS_TO_DISPLAY_ACTIVITY - msElapsed);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        continueApplicationLaunch();
                    }
                }, msRemaining);
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIntentToFireAfterUpgrade = getIntentToFireAfterUpgrade(getIntent());
        setContentView(R.layout.upgrade_activity);

        DocumentModeAssassin assassin = DocumentModeAssassin.getInstance();
        if (assassin.isMigrationNecessary()) {
            // Kick off migration if it hasn't already started.
            assassin.addObserver(mObserver);
            assassin.migrateFromDocumentToTabbedMode();
        } else {
            // Migration finished in the background.
            continueApplicationLaunch();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set the timestamp after the Activity is visible to avoid shortening the timer.
        if (mStartTimestamp == INVALID_TIMESTAMP) mStartTimestamp = System.currentTimeMillis();
    }

    @Override
    protected void onDestroy() {
        mIsDestroyed = true;
        super.onDestroy();
    }

    private static Intent getIntentToFireAfterUpgrade(Intent activityIntent) {
        Intent intentToFire = null;

        // Retrieve the Intent that caused the user to end up on the upgrade pathway.
        if (activityIntent != null) {
            intentToFire = (Intent) IntentUtils.safeGetParcelableExtra(
                    activityIntent, EXTRA_INTENT_TO_REFIRE);
        }

        // If there's no Intent to refire, send them to the browser.
        if (intentToFire == null) {
            intentToFire = new Intent(Intent.ACTION_MAIN);
            intentToFire.setPackage(ContextUtils.getApplicationContext().getPackageName());
        }

        // Fire the Intent into a different task so that this one can go away.
        intentToFire.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);

        return intentToFire;
    }

    private void continueApplicationLaunch() {
        if (mIsDestroyed) return;

        ApiCompatibilityUtils.finishAndRemoveTask(this);
        if (mIntentToFireAfterUpgrade != null && ApplicationStatus.hasVisibleActivities()) {
            startActivity(mIntentToFireAfterUpgrade);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            mIntentToFireAfterUpgrade = null;
        }
    }
}
