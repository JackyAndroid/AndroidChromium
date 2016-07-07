// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import org.chromium.base.metrics.RecordHistogram;

/**
 * Used for histograms that need to be recorded earlier than when the native
 * library is loaded.  Caches it until there is an opportunity to record it.
 */
public class LaunchHistogram {
    private final String mHistogramName;
    private boolean mIsHit;

    /**
     * @param histogramName Name of the histogram to record.
     */
    public LaunchHistogram(String histogramName) {
        mHistogramName = histogramName;
    }

    /** Records that the histogram condition occurred. */
    public void recordHit() {
        mIsHit = true;
    }

    /** Commits the histogram. Expects the native library to be loaded. */
    public void commitHistogram() {
        RecordHistogram.recordBooleanHistogram(mHistogramName, mIsHit);
        mIsHit = false;
    }
}