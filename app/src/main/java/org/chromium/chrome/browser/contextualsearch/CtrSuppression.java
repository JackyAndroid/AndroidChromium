// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.content.Context;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;

/**
 * Provides a ContextualSearchHeuristic for CTR Recording, logging, and eventually suppression.
 * Records impressions and CTR when the Bar is dismissed.
 * TODO(donnd): add suppression logic.
 * Logs "impressions" and "CTR" per user in UMA for the previous week and 28-day period.
 * An impression is a view of our UX (the Bar) and CTR is the "click-through rate" (user opens of
 * the Bar).
 * This class also implements the device-based native integer storage required by the
 * native {@code CtrAggregator} class.
 */
public class CtrSuppression extends ContextualSearchHeuristic {
    private long mNativePointer;

    private static Integer sCurrentWeekNumberCache;

    private final ChromePreferenceManager mPreferenceManager;

    /**
     * Constructs an object that tracks impressions and clicks per user to produce CTR and
     * impression metrics.
     * @param context An Android Context.
     */
    CtrSuppression(Context context) {
        mPreferenceManager = ChromePreferenceManager.getInstance(context);

        // This needs to be done last in this constructor because the native code may call
        // into this object.
        mNativePointer = nativeInit();
    }

    /**
     * This method should be called to clean up storage when an instance of this class is
     * no longer in use.
     */
    public void destroy() {
        if (mNativePointer != 0L) {
            nativeDestroy(mNativePointer);
        }
    }

    // ============================================================================================
    // ContextualSearchHeuristic overrides.
    // ============================================================================================

    @Override
    protected boolean isConditionSatisfiedAndEnabled() {
        return false;
    }

    @Override
    protected void logConditionState() {
        // Since the CTR for previous time periods never changes, we only need to write to the UMA
        // log when we may have moved to a new week, or we have new persistent data.
        // We cache the current week in persistent storage so we can tell when it changes.
        boolean didWeekChange = didWeekChange(nativeGetCurrentWeekNumber(mNativePointer));
        if (didWeekChange) {
            if (nativeHasPreviousWeekData(mNativePointer)) {
                int previousWeekImpressions = nativeGetPreviousWeekImpressions(mNativePointer);
                int previousWeekCtr = (int) (100 * nativeGetPreviousWeekCtr(mNativePointer));
                ContextualSearchUma.logPreviousWeekCtr(previousWeekImpressions, previousWeekCtr);
            }

            if (nativeHasPrevious28DayData(mNativePointer)) {
                int previous28DayImpressions = nativeGetPrevious28DayImpressions(mNativePointer);
                int previous28DayCtr = (int) (100 * nativeGetPrevious28DayCtr(mNativePointer));
                ContextualSearchUma.logPrevious28DayCtr(previous28DayImpressions, previous28DayCtr);
            }
        }
    }

    @Override
    protected void logResultsSeen(boolean wasSearchContentViewSeen, boolean wasActivatedByTap) {
        if (wasActivatedByTap) {
            nativeRecordImpression(mNativePointer, wasSearchContentViewSeen);
        }
    }

    // ============================================================================================
    // Device integer storage.
    // ============================================================================================

    @CalledByNative
    void writeInt(String key, int value) {
        mPreferenceManager.writeInt(key, value);
    }

    @CalledByNative
    int readInt(String key) {
        return mPreferenceManager.readInt(key);
    }

    // ============================================================================================
    // Private helpers.
    // ============================================================================================

    /**
     * Updates the "current week number" preference and returns whether the week has changed.
     * @param currentWeekNumber The week number of the current week.
     * @return {@code true} if the current week number is different from the last time we checked,
     *         or we have never checked.
     */
    private boolean didWeekChange(int currentWeekNumber) {
        if (mPreferenceManager.getContextualSearchCurrentWeekNumber() == currentWeekNumber) {
            return false;
        } else {
            mPreferenceManager.setContextualSearchCurrentWeekNumber(currentWeekNumber);
            return true;
        }
    }

    // ============================================================================================
    // Native callback support.
    // ============================================================================================

    @CalledByNative
    private void clearNativePointer() {
        assert mNativePointer != 0;
        mNativePointer = 0;
    }

    // ============================================================================================
    // Native methods.
    // ============================================================================================
    private native long nativeInit();
    private native void nativeDestroy(long nativeCtrSuppression);

    private native void nativeRecordImpression(long nativeCtrSuppression, boolean wasSeen);
    private native int nativeGetCurrentWeekNumber(long nativeCtrSuppression);
    private native boolean nativeHasPreviousWeekData(long nativeCtrSuppression);
    private native int nativeGetPreviousWeekImpressions(long nativeCtrSuppression);
    private native float nativeGetPreviousWeekCtr(long nativeCtrSuppression);
    private native boolean nativeHasPrevious28DayData(long nativeCtrSuppression);
    private native int nativeGetPrevious28DayImpressions(long nativeCtrSuppression);
    private native float nativeGetPrevious28DayCtr(long nativeCtrSuppression);
}
