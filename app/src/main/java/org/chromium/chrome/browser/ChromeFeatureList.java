// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.MainDex;

/**
 * Java accessor for base/feature_list.h state.
 */
@JNINamespace("chrome::android")
@MainDex
public abstract class ChromeFeatureList {
    // Prevent instantiation.
    private ChromeFeatureList() {}

    /**
     * Returns whether the specified feature is enabled or not.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to query.
     * @return Whether the feature is enabled or not.
     */
    public static boolean isEnabled(String featureName) {
        return nativeIsEnabled(featureName);
    }

    public static final String ANDROID_PAY_INTEGRATION_V1 = "AndroidPayIntegrationV1";
    public static final String AUTOFILL_SCAN_CARDHOLDER_NAME = "AutofillScanCardholderName";
    /** Whether we show an important sites dialog in the "Clear Browsing Data" flow. */
    public static final String IMPORTANT_SITES_IN_CBD = "ImportantSitesInCBD";
    public static final String NTP_FAKE_OMNIBOX_TEXT = "NTPFakeOmniboxText";
    public static final String NTP_MATERIAL_DESIGN = "NTPMaterialDesign";
    public static final String NTP_SNIPPETS = "NTPSnippets";
    public static final String NTP_SNIPPETS_SAVE_TO_OFFLINE = "NTPSaveToOffline";
    public static final String TAB_REPARENTING = "TabReparenting";
    public static final String WEB_PAYMENTS = "WebPayments";
    public static final String CCT_EXTERNAL_LINK_HANDLING = "CCTExternalLinkHandling";

    private static native boolean nativeIsEnabled(String featureName);
}
