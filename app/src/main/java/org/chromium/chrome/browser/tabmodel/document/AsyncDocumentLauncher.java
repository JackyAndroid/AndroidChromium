// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.document.IncognitoDocumentActivity;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModel.Entry;

import java.util.ArrayList;

/**
 * Fires an Intent to launch a new DocumentActivity.  Waits for the ActivityManager to acknowledge
 * that our task exists before firing the next Intent.
 */
public class AsyncDocumentLauncher {
    private static final String TAG = "document_AsyncLaunc";

    /**
     * Milliseconds to wait for Android to acknowledge that our Activity's task exists.
     * This value is empirically defined because Android doesn't let us know when it finishes
     * animating Activities, nor does it let us Observe the ActivityManager for when our task
     * exists (crbug.com/498920).
     */
    private static final int MAX_WAIT_MS = 3000;

    /** Milliseconds to wait between polls of the ActivityManager. */
    private static final int POLLING_DELAY_MS = 100;

    private static class LazyInitializer {
        private static final AsyncDocumentLauncher sInstance = new AsyncDocumentLauncher();
    }

    private class LaunchRunnable implements Runnable {
        private final boolean mIsIncognito;
        private final int mParentId;
        private final AsyncTabCreationParams mAsyncParams;
        private int mLaunchedId;
        private long mTimestampAtLaunch;
        private int mTabCountAtLaunch;

        public LaunchRunnable(boolean incognito, int parentId, AsyncTabCreationParams asyncParams) {
            mIsIncognito = incognito;
            mParentId = parentId;
            mAsyncParams = asyncParams;
            mLaunchedId = Tab.INVALID_TAB_ID;
        }

        /** Starts an Activity to with the stored parameters. */
        public void launch() {
            mTabCountAtLaunch = ChromeApplication.getDocumentTabModelSelector().getTotalTabCount();

            final Activity parentActivity = ActivityDelegate.getActivityForTabId(mParentId);
            mLaunchedId = ChromeLauncherActivity.launchDocumentInstance(
                    parentActivity, mIsIncognito, mAsyncParams);

            if (mLaunchedId == Tab.INVALID_TAB_ID) {
                Log.e(TAG, "Failed to launch document.");
                finishLaunch();
            } else {
                mTimestampAtLaunch = SystemClock.elapsedRealtime();
                run();
            }
        }

        @Override
        public void run() {
            // Check if the Activity was already launched.
            DocumentTabModelSelector selector = ChromeApplication.getDocumentTabModelSelector();
            for (Entry task : mActivityDelegate.getTasksFromRecents(mIsIncognito)) {
                if (task.tabId == mLaunchedId && selector.getTotalTabCount() > mTabCountAtLaunch) {
                    finishLaunch();
                    return;
                }
            }

            if (SystemClock.elapsedRealtime() - mTimestampAtLaunch > MAX_WAIT_MS) {
                // Check if the launch is taking excessively long.  This will likely make the
                // previous tab disappear, but it's better than making the user wait.
                finishLaunch();
            } else {
                // Wait a bit longer.
                mHandler.postDelayed(this, POLLING_DELAY_MS);
            }
        }

        /** Start up the next tab. */
        private void finishLaunch() {
            mCurrentRunnable = null;
            if (mQueue.size() != 0) {
                mCurrentRunnable = mQueue.remove(0);
                mCurrentRunnable.launch();
            }
        }
    }

    private final Handler mHandler;
    private final ActivityDelegate mActivityDelegate;
    private final ArrayList<LaunchRunnable> mQueue;
    private LaunchRunnable mCurrentRunnable;

    /** Returns the singleton instance. */
    public static AsyncDocumentLauncher getInstance() {
        return LazyInitializer.sInstance;
    }

    private AsyncDocumentLauncher() {
        mHandler = new Handler(Looper.getMainLooper());
        mActivityDelegate = new ActivityDelegateImpl(
                DocumentActivity.class, IncognitoDocumentActivity.class);
        mQueue = new ArrayList<LaunchRunnable>();
    }

    /** Enqueues a tab to be launched asynchronously. */
    public void enqueueLaunch(boolean incognito, int parentId, AsyncTabCreationParams asyncParams) {
        ThreadUtils.assertOnUiThread();
        LaunchRunnable runnable = new LaunchRunnable(incognito, parentId, asyncParams);
        if (mCurrentRunnable == null) {
            mCurrentRunnable = runnable;
            mCurrentRunnable.launch();
        } else {
            mQueue.add(runnable);
        }
    }
}
