// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import org.chromium.base.CommandLine;
import org.chromium.chrome.R;
import org.chromium.content.app.ContentApplication;
import org.chromium.content.common.ContentSwitches;


/**
 * Utility class for application level initialization calls.
 */
public final class ApplicationInitialization {
    // Prevent instantiation.
    private ApplicationInitialization() {
    }

    /**
     * Enable fullscreen related startup flags.
     * @param resources Resources to use while calculating initialization constants.
     * @param resControlContainerHeight The resource id for the height of the top controls.
     */
    public static void enableFullscreenFlags(
            Resources resources, Context context, int resControlContainerHeight) {
        ContentApplication.initCommandLine(context);

        CommandLine commandLine = CommandLine.getInstance();
        if (commandLine.hasSwitch(ChromeSwitches.DISABLE_FULLSCREEN)) return;

        TypedValue threshold = new TypedValue();
        resources.getValue(R.dimen.top_controls_show_threshold, threshold, true);
        commandLine.appendSwitchWithValue(
                ContentSwitches.TOP_CONTROLS_SHOW_THRESHOLD, threshold.coerceToString().toString());
        resources.getValue(R.dimen.top_controls_show_threshold, threshold, true);
        commandLine.appendSwitchWithValue(
                ContentSwitches.TOP_CONTROLS_HIDE_THRESHOLD, threshold.coerceToString().toString());
    }
}
