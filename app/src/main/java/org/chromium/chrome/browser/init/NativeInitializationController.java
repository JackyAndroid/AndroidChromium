// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.init;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.chromium.base.ThreadUtils;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.content.browser.ChildProcessLauncher;

import java.util.ArrayList;
import java.util.List;

/**
 * This class controls the different asynchronous states during our initialization:
 * 1. During startBackgroundTasks(), we'll kick off loading the library and yield the call stack.
 * 2. We may receive a onStart() / onStop() call any point after that, whether or not
 *    the library has been loaded.
 */
class NativeInitializationController {
    private static final String TAG = "NativeInitializationController";

    private final Context mContext;
    private final ChromeActivityNativeDelegate mActivityDelegate;
    private final Handler mHandler;

    private boolean mOnStartPending;
    private boolean mOnResumePending;
    private List<Intent> mPendingNewIntents;
    private List<ActivityResult> mPendingActivityResults;
    private boolean mWaitingForFirstDraw;
    private boolean mHasDoneFirstDraw;
    private boolean mInitializationComplete;

    /**
     * This class encapsulates a call to onActivityResult that has to be deferred because the native
     * library is not yet loaded.
     */
    static class ActivityResult {
        public final int requestCode;
        public final int resultCode;
        public final Intent data;

        public ActivityResult(int requestCode, int resultCode, Intent data) {
            this.requestCode = requestCode;
            this.resultCode = resultCode;
            this.data = data;
        }
    }

    /**
     * Create the NativeInitializationController using the main loop and the application context.
     * It will be linked back to the activity via the given delegate.
     * @param context The context to pull the application context from.
     * @param activityDelegate The activity delegate for the owning activity.
     */
    public NativeInitializationController(Context context,
            ChromeActivityNativeDelegate activityDelegate) {
        mContext = context.getApplicationContext();
        mHandler = new Handler(Looper.getMainLooper());
        mActivityDelegate = activityDelegate;
    }

    /**
     * Start loading the native library in the background. This kicks off the native initialization
     * process.
     */
    public void startBackgroundTasks() {
        // TODO(yusufo) : Investigate using an AsyncTask for this.
        new Thread() {
            @Override
            public void run() {
                try {
                    LibraryLoader libraryLoader =
                            LibraryLoader.get(LibraryProcessType.PROCESS_BROWSER);
                    libraryLoader.ensureInitialized(mContext.getApplicationContext());
                    // The prefetch is done after the library load for two reasons:
                    // - It is easier to know the library location after it has
                    //   been loaded.
                    // - Testing has shown that this gives the best compromise,
                    //   by avoiding performance regression on any tested
                    //   device, and providing performance improvement on
                    //   some. Doing it earlier delays UI inflation and more
                    //   generally startup on some devices, most likely by
                    //   competing for IO.
                    // For experimental results, see http://crbug.com/460438.
                    libraryLoader.asyncPrefetchLibrariesToMemory();
                } catch (ProcessInitException e) {
                    Log.e(TAG, "Unable to load native library.", e);
                    mActivityDelegate.onStartupFailure();
                    return;
                }
                ChildProcessLauncher.warmUp(mContext);
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onLibraryLoaded();
                    }
                });
            }
        }.start();
    }

    private void onLibraryLoaded() {
        if (mHasDoneFirstDraw) {
            // First draw is done
            onNativeLibraryLoaded();
        } else {
            mWaitingForFirstDraw = true;
        }
    }

    /**
     * Called when the current activity has finished its first draw pass. This and the library
     * load has to be completed to start the chromium browser process.
     */
    public void firstDrawComplete() {
        mHasDoneFirstDraw = true;

        if (mWaitingForFirstDraw) {
            mWaitingForFirstDraw = false;
            // Allow the UI thread to continue its initialization
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onNativeLibraryLoaded();
                }
            });
        }
    }

    private void onNativeLibraryLoaded() {
        // Callback from LibraryLoader on UI thread, when the load has completed.
        if (mActivityDelegate.isActivityDestroyed()) return;
        mActivityDelegate.onCreateWithNative();
    }

    /**
     * Called when native initialization for an activity has been finished.
     */
    public void onNativeInitializationComplete() {
        // Callback when we finished with ChromeActivityNativeDelegate.onCreateWithNative tasks
        mInitializationComplete = true;

        if (mOnStartPending) {
            mOnStartPending = false;
            startNowAndProcessPendingItems();
        }

        if (mOnResumePending) {
            mOnResumePending = false;
            onResume();
        }

        try {
            LibraryLoader.get(LibraryProcessType.PROCESS_BROWSER)
                    .onNativeInitializationComplete(mContext.getApplicationContext());
        } catch (ProcessInitException e) {
            Log.e(TAG, "Unable to load native library.", e);
            mActivityDelegate.onStartupFailure();
            return;
        }
    }

    /**
     * Called when an activity gets an onStart call and is done with java only tasks.
     */
    public void onStart() {
        if (mInitializationComplete) {
            startNowAndProcessPendingItems();
        } else {
            mOnStartPending = true;
        }
    }

    /**
     * Called when an activity gets an onResume call and is done with java only tasks.
     */
    public void onResume() {
        if (mInitializationComplete) {
            mActivityDelegate.onResumeWithNative();
        } else {
            mOnResumePending = true;
        }
    }

    /**
     * Called when an activity gets an onPause call and is done with java only tasks.
     */
    public void onPause() {
        mOnResumePending = false;  // Clear the delayed resume if a pause happens first.
        if (mInitializationComplete) mActivityDelegate.onPauseWithNative();
    }

    /**
     * Called when an activity gets an onStop call and is done with java only tasks.
     */
    public void onStop() {
        mOnStartPending = false;  // Clear the delayed start if a stop happens first.
        if (!mInitializationComplete) return;
        mActivityDelegate.onStopWithNative();
    }

    /**
     * Called when an activity gets an onNewIntent call and is done with java only tasks.
     * @param intent The intent that has arrived to the activity linked to the given delegate.
     */
    public void onNewIntent(Intent intent) {
        if (mInitializationComplete) {
            mActivityDelegate.onNewIntentWithNative(intent);
        } else {
            if (mPendingNewIntents == null) mPendingNewIntents = new ArrayList<Intent>(1);
            mPendingNewIntents.add(intent);
        }
    }

    /**
     * This is the Android onActivityResult callback deferred, if necessary,
     * to when the native library has loaded.
     * @param requestCode The request code for the ActivityResult.
     * @param resultCode The result code for the ActivityResult.
     * @param data The intent that has been sent with the ActivityResult.
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mInitializationComplete) {
            mActivityDelegate.onActivityResultWithNative(requestCode, resultCode, data);
        } else {
            if (mPendingActivityResults == null) {
                mPendingActivityResults = new ArrayList<ActivityResult>(1);
            }
            mPendingActivityResults.add(new ActivityResult(requestCode, resultCode, data));
        }
    }

    private void startNowAndProcessPendingItems() {
        // onNewIntent and onActivityResult are called only when the activity is paused.
        // To match the non-deferred behavior, onStart should be called before any processing
        // of pending intents and activity results.
        // Note that if we needed ChromeActivityNativeDelegate.onResumeWithNative(), the pending
        // intents and activity results processing should have happened in the corresponding
        // resumeNowAndProcessPendingItems, just before the call to
        // ChromeActivityNativeDelegate.onResumeWithNative().
        mActivityDelegate.onStartWithNative();

        if (mPendingNewIntents != null) {
            for (Intent intent : mPendingNewIntents) {
                mActivityDelegate.onNewIntentWithNative(intent);
            }
            mPendingNewIntents = null;
        }

        if (mPendingActivityResults != null) {
            ActivityResult activityResult;
            for (int i = 0; i < mPendingActivityResults.size(); i++) {
                activityResult = mPendingActivityResults.get(i);
                mActivityDelegate.onActivityResultWithNative(activityResult.requestCode,
                        activityResult.resultCode, activityResult.data);
            }
            mPendingActivityResults = null;
        }
    }
}
