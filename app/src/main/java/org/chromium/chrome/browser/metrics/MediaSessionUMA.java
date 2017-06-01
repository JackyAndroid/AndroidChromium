// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import org.chromium.base.metrics.RecordHistogram;

/**
 * Centralizes UMA data collection for Android-specific MediaSession features.
 */
public class MediaSessionUMA {
    // MediaSessionAction defined in tools/metrics/histograms/histograms.xml.
    public static final int MEDIA_SESSION_ACTION_SOURCE_MEDIA_NOTIFICATION = 0;
    public static final int MEDIA_SESSION_ACTION_SOURCE_MEDIA_SESSION = 1;
    public static final int MEDIA_SESSION_ACTION_SOURCE_HEADSET_UNPLUG = 2;
    public static final int MEDIA_SESSION_ACTION_SOURCE_MAX = 3;

    public static void recordPlay(int action) {
        assert action >= 0 && action < MEDIA_SESSION_ACTION_SOURCE_MAX;
        RecordHistogram.recordEnumeratedHistogram("Media.Session.Play", action,
                MEDIA_SESSION_ACTION_SOURCE_MAX);
    }

    public static void recordPause(int action) {
        assert action >= 0 && action < MEDIA_SESSION_ACTION_SOURCE_MAX;
        RecordHistogram.recordEnumeratedHistogram("Media.Session.Pause", action,
                MEDIA_SESSION_ACTION_SOURCE_MAX);
    }

    public static void recordStop(int action) {
        assert action >= 0 && action < MEDIA_SESSION_ACTION_SOURCE_MAX;
        RecordHistogram.recordEnumeratedHistogram("Media.Session.Stop", action,
                MEDIA_SESSION_ACTION_SOURCE_MAX);
    }
}
