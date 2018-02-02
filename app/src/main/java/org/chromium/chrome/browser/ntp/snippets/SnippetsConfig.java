// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import org.chromium.chrome.browser.ChromeFeatureList;

/**
 * Provides configuration details for NTP snippets.
 */
public final class SnippetsConfig {
    private SnippetsConfig() {}

    public static boolean isEnabled() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_SNIPPETS);
    }

    public static boolean isSaveToOfflineEnabled() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_SNIPPETS_SAVE_TO_OFFLINE);
    }

    public static boolean isOfflineBadgeEnabled() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_SNIPPETS_OFFLINE_BADGE);
    }

    public static boolean isSectionDismissalEnabled() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_SUGGESTIONS_SECTION_DISMISSAL);
    }

    /** https://crbug.com/660837 */
    public static boolean isIncreasedCardVisibilityEnabled() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_SNIPPETS_INCREASED_VISIBILITY);
    }
}
