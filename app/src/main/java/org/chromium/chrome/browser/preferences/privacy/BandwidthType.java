// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

/**
 * Bandwidth options available based on network.
 */
public enum BandwidthType {

    // Still using "prerender" in the strings for historical reasons: we don't want to break
    // existing users' settings.
    NEVER_PRERENDER      ("never_prefetch"),
    PRERENDER_ON_WIFI    ("prefetch_on_wifi"),
    ALWAYS_PRERENDER     ("always_prefetch");

    public static final BandwidthType DEFAULT = PRERENDER_ON_WIFI;

    private final String mTitle;

    BandwidthType(String title) {
        mTitle = title;
    }

    /**
     * Returns the title of the bandwidthType.
     * @return title
     */
    public String title() {
        return mTitle;
    }

    /**
     * Get the BandwidthType from the title.
     * @param title
     * @return BandwidthType
     */
    public static BandwidthType getBandwidthFromTitle(String title) {
        for (BandwidthType b : BandwidthType.values()) {
            if (b.mTitle.equals(title)) return b;
        }
        assert false;
        return DEFAULT;
    }
}
