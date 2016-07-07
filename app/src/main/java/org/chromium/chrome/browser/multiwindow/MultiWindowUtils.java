// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.multiwindow;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Utilities for detecting multi-window/multi-instance support.
 *
 * Thread-safe: This class may be accessed from any thread.
 */
public class MultiWindowUtils {

    private static AtomicReference<MultiWindowUtils> sInstance =
            new AtomicReference<MultiWindowUtils>();

    /**
     * Returns the singleton instance of MultiWindowUtils, creating it if needed.
     */
    public static MultiWindowUtils getInstance() {
        if (sInstance.get() == null) {
            ChromeApplication application =
                    (ChromeApplication) ApplicationStatus.getApplicationContext();
            sInstance.compareAndSet(null, application.createMultiWindowUtils());
        }
        return sInstance.get();
    }

    /**
     * @param activity The {@link Activity} to check.
     * @return Whether or not {@code activity} is currently in multi-window mode.
     */
    public boolean isMultiWindow(Activity activity) {
        // This logic is overridden in a subclass.
        return false;
    }

    /**
     * @param activity The {@link Activity} to check.
     * @return Whether or not {@code activity} should run in multi-instance mode.
     */
    public boolean shouldRunInMultiInstanceMode(ChromeLauncherActivity activity) {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP
                && TextUtils.equals(activity.getIntent().getAction(), Intent.ACTION_MAIN)
                && isMultiWindow(activity)
                && activity.isChromeBrowserActivityRunning();
    }

    /**
     * Makes |intent| able to support multi-instance in multi-window mode.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void makeMultiInstanceIntent(Activity activity, Intent intent) {
        if (activity instanceof ChromeLauncherActivity && isMultiWindow(activity)) {
            if (TextUtils.equals(ChromeTabbedActivity.class.getName(),
                    intent.getComponent().getClassName())) {
                intent.setClassName(activity, MultiInstanceChromeTabbedActivity.class.getName());
            }
            intent.setFlags(intent.getFlags()
                    & ~(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT));
        }
    }
}
