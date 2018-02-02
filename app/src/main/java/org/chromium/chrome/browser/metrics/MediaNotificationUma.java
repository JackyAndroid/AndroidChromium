// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import static org.chromium.base.metrics.CachedMetrics.EnumeratedHistogramSample;

import android.content.Intent;

/**
 * Helper class to record which kind of media notifications does the user click to go back to
 * Chrome.
 */
public class MediaNotificationUma {
    public static final int SOURCE_INVALID = -1;
    public static final int SOURCE_MEDIA = 0;
    public static final int SOURCE_PRESENTATION = 1;
    public static final int SOURCE_MEDIA_FLING = 2;
    public static final int SOURCE_MAX = 3;

    public static final String INTENT_EXTRA_NAME =
            "org.chromium.chrome.browser.metrics.MediaNotificationUma.EXTRA_CLICK_SOURCE";

    private static final EnumeratedHistogramSample sClickSourceHistogram =
            new EnumeratedHistogramSample("Media.Notification.Click", SOURCE_MAX);

    /**
     * Record the UMA as specified by {@link intent}. The {@link intent} should contain intent extra
     * of name {@link INTENT_EXTRA_NAME} indicating the type.
     * @param intent The intent starting the activity.
     */
    public static void recordClickSource(Intent intent) {
        if (intent == null) return;
        int source = intent.getIntExtra(INTENT_EXTRA_NAME, SOURCE_INVALID);
        if (source == SOURCE_INVALID || source >= SOURCE_MAX) return;
        sClickSourceHistogram.record(source);
    }
}
