// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.MainDex;

import java.util.Set;

/**
 * Java accessor for base/feature_list.h state.
 */
@JNINamespace("chrome::android")
@MainDex
public abstract class ChromeFeatureList {
    /** Map that stores substitution feature flags for tests. */
    private static Set<String> sTestEnabledFeatures;

    // Prevent instantiation.
    private ChromeFeatureList() {}

    /**
     * Sets the feature flags to use in JUnit tests, since native calls are not available there.
     * Do not use directly, prefer using the {@link EnableFeatures} annotation.
     *
     * @see EnableFeatures
     * @see EnableFeatures.Processor
     */
    @VisibleForTesting
    public static void setTestEnabledFeatures(Set<String> featureList) {
        sTestEnabledFeatures = featureList;
    }

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
        if (sTestEnabledFeatures == null) return nativeIsEnabled(featureName);
        return sTestEnabledFeatures.contains(featureName);
    }

    // Alphabetical:
    public static final String ANDROID_PAY_INTEGRATION_V1 = "AndroidPayIntegrationV1";
    public static final String AUTOFILL_SCAN_CARDHOLDER_NAME = "AutofillScanCardholderName";
    public static final String CCT_EXTERNAL_LINK_HANDLING = "CCTExternalLinkHandling";
    public static final String CCT_POST_MESSAGE_API = "CCTPostMessageAPI";
    public static final String CHROME_HOME = "ChromeHome";
    public static final String CONSISTENT_OMNIBOX_GEOLOCATION = "ConsistentOmniboxGeolocation";
    public static final String CONTEXTUAL_SEARCH_SINGLE_ACTIONS = "ContextualSearchSingleActions";
    /** Whether we show an important sites dialog in the "Clear Browsing Data" flow. */
    public static final String IMPORTANT_SITES_IN_CBD = "ImportantSitesInCBD";
    public static final String NO_CREDIT_CARD_ABORT = "NoCreditCardAbort";
    public static final String NTP_FAKE_OMNIBOX_TEXT = "NTPFakeOmniboxText";
    public static final String NTP_FOREIGN_SESSIONS_SUGGESTIONS = "NTPForeignSessionsSuggestions";
    public static final String NTP_SNIPPETS = "NTPSnippets";
    public static final String NTP_SNIPPETS_INCREASED_VISIBILITY = "NTPSnippetsIncreasedVisibility";
    public static final String NTP_SNIPPETS_SAVE_TO_OFFLINE = "NTPSaveToOffline";
    public static final String NTP_SNIPPETS_OFFLINE_BADGE = "NTPOfflineBadge";
    public static final String NTP_SUGGESTIONS_SECTION_DISMISSAL = "NTPSuggestionsSectionDismissal";
    public static final String TAB_REPARENTING = "TabReparenting";
    public static final String VR_SHELL = "VrShell";
    public static final String WEB_PAYMENTS = "WebPayments";
    public static final String WEBAPKS = "WebApks";

    private static native boolean nativeIsEnabled(String featureName);
}
