// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.init;

import android.content.Intent;

/**
 * An activity level delegate to handle native initialization and activity lifecycle related tasks
 * that depend on native code. The lifecycle callbacks defined here are called synchronously with
 * their counterparts in Activity only if the native libraries have been loaded. If the libraries
 * have not been loaded yet, the calls in ChromeActivityNativeDelegate will be in the right order
 * among themselves, but can be deferrred and out of sync wrt to Activity calls.
 */
public interface ChromeActivityNativeDelegate {
    /**
     * Carry out native initialization related tasks and any other java tasks that can be done async
     * with native initialization.
     */
    void onCreateWithNative();

    /**
     * Carry out native code dependent tasks that should happen during on Activity.onStart().
     */
    void onStartWithNative();

    /**
     * Carry out native code dependent tasks that should happen during on Activity.onResume().
     */
    void onResumeWithNative();

    /**
     * Carry out native code dependent tasks that should happen during on Activity.onPause().
     */
    void onPauseWithNative();

    /**
     * Carry out native code dependent tasks that should happen during on Activity.onStop().
     */
    void onStopWithNative();

    /**
     * @return Whether the activity linked to the delegate has been destroyed.
     */
    boolean isActivityDestroyed();

    /**
     * Called when the first draw for the UI specific to the linked activity is complete.
     */
    void onFirstDrawComplete();

    /**
     * Carry out native code dependent tasks that relate to processing a new intent coming to
     * FragmentActivity.onNewIntent().
     */
    void onNewIntentWithNative(Intent intent);

    /**
     * @return The Intent that launched the activity.
     */
    Intent getInitialIntent();

    /**
     * Carry out native code dependent tasks that relate to processing an activity result coming to
     * Activity.onActivityResult().
     * @param requestCode The request code of the response.
     * @param resultCode  The result code of the response.
     * @param data        The intent data of the response.
     * @return            Whether or not the result was handled
     */
    boolean onActivityResultWithNative(int requestCode, int resultCode, Intent data);

    /**
     * Called when any failure about the initialization occurs.
     */
    void onStartupFailure();
}
