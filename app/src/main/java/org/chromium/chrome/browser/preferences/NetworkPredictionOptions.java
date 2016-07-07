// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import org.chromium.chrome.R;

import java.util.Locale;

/**
 * Bandwidth options available based on network.
 */
public enum NetworkPredictionOptions {
    NETWORK_PREDICTION_ALWAYS,
    NETWORK_PREDICTION_WIFI_ONLY,
    NETWORK_PREDICTION_NEVER;

    public static final NetworkPredictionOptions DEFAULT = NETWORK_PREDICTION_WIFI_ONLY;

    /**
     * @return The number of choices offered for the user.
     */
    public static int choiceCount() {
        return values().length;
    }

    /**
     * Fetch the display title for the preference.
     * @return resource for the title.
     */
    public int getDisplayTitle() {
        switch(this) {
            case NETWORK_PREDICTION_ALWAYS:
                return R.string.always_prefetch_bandwidth_entry;
            case NETWORK_PREDICTION_WIFI_ONLY :
                return R.string.wifi_prefetch_bandwidth_entry;
            case NETWORK_PREDICTION_NEVER:
                return R.string.never_prefetch_bandwidth_entry;
            default:
                assert false;
                return 0;
        }
    }

    /**
     * Convert an integer to enum NetworkPredictionOptions.
     * @return NetworkPredictionOptions instance.
     */
    public static NetworkPredictionOptions intToEnum(int index) {
        return NetworkPredictionOptions.values()[index];
    }

    /**
     * Convert an enum NetworkPredictionOptions to integer.
     * @return Integer corresponding to NetworkPredictionOptions instance.
     */
    public int enumToInt() {
        return ordinal();
    }

    /**
     * Convert an string to enum NetworkPredictionOptions.
     * @return NetworkPredictionOptions instance.
     */
    public static NetworkPredictionOptions stringToEnum(String name) {
        return valueOf(name.toUpperCase(Locale.US));
    }

    /**
     * Convert an enum NetworkPredictionOptions to String.
     * @return String corresponding to NetworkPredictionOptions instance.
     */
    public String enumToString() {
        return name().toLowerCase(Locale.US);
    }
}
