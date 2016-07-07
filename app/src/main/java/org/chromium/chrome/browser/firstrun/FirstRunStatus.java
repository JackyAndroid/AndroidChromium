// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.content.Context;
import android.preference.PreferenceManager;

/**
 * Gets and sets preferences related to the status of the first run experience.
 */
public class FirstRunStatus {

    private static final String FIRST_RUN_FLOW_COMPLETE = "first_run_flow";

    /**
     * Sets the "main First Run Experience flow complete" preference.
     * @param context Any context
     * @param isComplete Whether the main First Run Experience flow is complete
     */
    public static void setFirstRunFlowComplete(Context context, boolean isComplete) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(FIRST_RUN_FLOW_COMPLETE, isComplete)
                .apply();
    }

    /**
     * Returns whether the main First Run Experience flow is complete.
     * Note: that might NOT include "intro"/"what's new" pages, but it always
     * includes ToS and Sign In pages if necessary.
     * @param context Any context
     */
    public static boolean getFirstRunFlowComplete(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(FIRST_RUN_FLOW_COMPLETE, false);
    }

}