// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.app.Activity;
import android.support.annotation.IntDef;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.metrics.RecordHistogram;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Tracks metrics caused by a particular Activity stopping.
 */
public class ActivityStopMetrics {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        STOP_REASON_UNKNOWN,
        STOP_REASON_BACK_BUTTON,
        STOP_REASON_OTHER_CHROME_ACTIVITY_IN_FOREGROUND,
        STOP_REASON_COUNT
    })
    public @interface StopReason{}

    /** Activity stopped for unknown reasons. */
    public static final int STOP_REASON_UNKNOWN = 0;

    /** Activity stopped after the user hit the back button. */
    public static final int STOP_REASON_BACK_BUTTON = 1;

    // Obsolete -- Activity stopped after the user hit the close/return UI button.
    // public static final int STOP_REASON_RETURN_BUTTON = 2;

    // Obsolete --  Activity stopped because it launched a {@link CustomTabActivity} on top of
    //              itself.
    // public static final int STOP_REASON_CUSTOM_TAB_STARTED = 3;

    // Obsolete -- Activity stopped because its child {@link CustomTabActivity} stopped itself.
    // public static final int STOP_REASON_CUSTOM_TAB_STOPPED = 4;

    /** Activity stopped because another of Chrome Activities came into focus. */
    public static final int STOP_REASON_OTHER_CHROME_ACTIVITY_IN_FOREGROUND = 5;

    /** Boundary.  Shouldn't ever be passed to the metrics service. */
    public static final int STOP_REASON_COUNT = 6;

    /** Name of the histogram that will be recorded. */
    private static final String HISTOGRAM_NAME = "Android.Activity.ChromeTabbedActivity.StopReason";

    /** Why the Activity is being stopped. */
    @StopReason private int mStopReason;

    /**
     * Constructs an {@link ActivityStopMetrics} instance.
     */
    public ActivityStopMetrics() {
        mStopReason = STOP_REASON_COUNT;
    }

    /**
     * Records the reason that the parent Activity was stopped.
     * @param parent Activity that owns this {@link ActivityStopMetrics} instance.
     */
    public void onStopWithNative(Activity parent) {
        if (mStopReason == STOP_REASON_COUNT) {
            if (parent != ApplicationStatus.getLastTrackedFocusedActivity()
                    && ApplicationStatus.hasVisibleActivities()) {
                mStopReason = STOP_REASON_OTHER_CHROME_ACTIVITY_IN_FOREGROUND;
            } else {
                mStopReason = STOP_REASON_UNKNOWN;
            }
        }
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_NAME, mStopReason, STOP_REASON_COUNT);
        mStopReason = STOP_REASON_COUNT;
    }

    /**
     * Tracks the reason that the parent Activity was stopped.
     * @param reason Reason the Activity was stopped (see {@link StopReason}).
     */
    public void setStopReason(@StopReason int reason) {
        if (mStopReason != STOP_REASON_COUNT) return;
        mStopReason = reason;
    }
}
