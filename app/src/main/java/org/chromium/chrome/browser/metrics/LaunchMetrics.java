// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.util.Pair;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Used for recording metrics about Chrome launches.
 */
@JNINamespace("metrics")
public class LaunchMetrics {
    // Each list item is a pair of the url and where it was added from e.g. from the add to
    // homescreen menu item, an app banner, or unknown. The mapping of int source values to
    // their string names is found in the C++ ShortcutInfo struct.
    private static final List<Pair<String, Integer>> sActivityUrls =
            new ArrayList<Pair<String, Integer>>();
    private static final List<Pair<String, Integer>> sTabUrls =
            new ArrayList<Pair<String, Integer>>();

    /**
     * Records the launch of a standalone Activity for a URL (i.e. a WebappActivity)
     * added from a specific source.
     * @param url URL that kicked off the Activity's creation.
     * @param source integer id of the source from where the URL was added.
     */
    public static void recordHomeScreenLaunchIntoStandaloneActivity(String url, int source) {
        sActivityUrls.add(new Pair<String, Integer>(url, source));
    }

    /**
     * Records the launch of a Tab for a URL (i.e. a Home screen shortcut).
     * @param url URL that kicked off the Tab's creation.
     * @param source integer id of the source from where the URL was added.
     */
    public static void recordHomeScreenLaunchIntoTab(String url, int source) {
        sTabUrls.add(new Pair<String, Integer>(url, source));
    }

    /**
     * Calls out to native code to record URLs that have been launched via the Home screen.
     * This intermediate step is necessary because Activity.onCreate() may be called when
     * the native library has not yet been loaded.
     * @param webContents WebContents for the current Tab.
     */
    public static void commitLaunchMetrics(WebContents webContents) {
        for (Pair<String, Integer> item : sActivityUrls) {
            nativeRecordLaunch(true, item.first, item.second, webContents);
        }
        for (Pair<String, Integer> item : sTabUrls) {
            nativeRecordLaunch(false, item.first, item.second, webContents);
        }
        sActivityUrls.clear();
        sTabUrls.clear();
    }

    private static native void nativeRecordLaunch(
            boolean standalone, String url, int source, WebContents webContents);
}
