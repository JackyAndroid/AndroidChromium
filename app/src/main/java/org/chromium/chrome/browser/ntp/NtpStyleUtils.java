// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.res.Resources;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ntp.snippets.SnippetsConfig;

/**
 * Utility class for figuring out which colors and dimensions to use for the NTP. This class is
 * needed while we transition the NTP to the new material design spec.
 */
public final class NtpStyleUtils {
    private NtpStyleUtils() {}

    public static int getBackgroundColorResource(Resources res, boolean isIncognito) {
        if (isIncognito) return ApiCompatibilityUtils.getColor(res, R.color.ntp_bg_incognito);

        return shouldUseMaterialDesign()
                ? ApiCompatibilityUtils.getColor(res, R.color.ntp_material_design_bg)
                : ApiCompatibilityUtils.getColor(res, R.color.ntp_bg);
    }

    public static int getToolbarBackgroundColorResource(Resources res) {
        return shouldUseMaterialDesign()
                ? ApiCompatibilityUtils.getColor(res, R.color.ntp_material_design_bg)
                : ApiCompatibilityUtils.getColor(res, R.color.ntp_bg);
    }

    public static int getSearchBoxHeight(Resources res) {
        return shouldUseMaterialDesign()
                ? res.getDimensionPixelSize(R.dimen.ntp_search_box_material_height)
                : res.getDimensionPixelSize(R.dimen.ntp_search_box_height);
    }

    public static boolean shouldUseMaterialDesign() {
        return SnippetsConfig.isEnabled()
                || ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_MATERIAL_DESIGN);
    }
}
