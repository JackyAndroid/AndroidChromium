// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.safebrowsing;

import android.text.TextUtils;

import org.chromium.components.variations.VariationsAssociatedData;

/**
 *  Field Trial support for Safe Browsing on Chrome for Android.
 */
public final class SafeBrowsingFieldTrial {
    private static final String FIELD_TRIAL_NAME = "SafeBrowsingAndroid";
    private static final String ENABLED_PARAM = "enabled";
    private static final String ENABLED_VALUE = "true";

    /*
     * @return whether the SafeBrowsingAndroid field trial is marked as "enabled"
     */
    public static boolean isEnabled() {
        return TextUtils.equals(ENABLED_VALUE,
                VariationsAssociatedData.getVariationParamValue(FIELD_TRIAL_NAME, ENABLED_PARAM));
    }

    private SafeBrowsingFieldTrial() {}
}
