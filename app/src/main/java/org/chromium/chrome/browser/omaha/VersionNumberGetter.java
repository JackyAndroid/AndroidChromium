// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omaha;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import org.chromium.base.BuildInfo;

/**
 * Stubbed class for getting version numbers from the rest of Chrome.  Override the functions for
 * unit tests.
 */
public class VersionNumberGetter {
    /**
     * Retrieve the latest version we know about from disk.
     * This function incurs I/O, so make sure you don't use it from the main thread.
     * @return The latest version if we retrieved one from the Omaha server, or "" if we haven't.
     */
    public String getLatestKnownVersion(
            Context context, String prefPackage, String prefLatestVersion) {
        assert Looper.myLooper() != Looper.getMainLooper();
        SharedPreferences prefs = context.getSharedPreferences(prefPackage, Context.MODE_PRIVATE);
        return prefs.getString(prefLatestVersion, "");
    }

    /**
     * Retrieve the version of Chrome we're using.
     * @return The latest version if we retrieved one from the Omaha server, or "" if we haven't.
     */
    public String getCurrentlyUsedVersion(Context context) {
        return BuildInfo.getPackageVersionName(context);
    }

    /**
     * Gets the milestone from an AboutVersionStrings#getApplicationVersion string. These strings
     * are of the format "ProductName xx.xx.xx.xx".
     *
     * @param version The version to extract the milestone number from.
     * @return The milestone of the given version string.
     */
    public static int getMilestoneFromVersionNumber(String version) {
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Application version incorrectly formatted");
        }

        version = version.replaceAll("[^\\d.]", "");

        // Parse out the version numbers.
        String[] pieces = version.split("\\.");
        if (pieces.length != 4) {
            throw new IllegalArgumentException("Application version incorrectly formatted");
        }

        try {
            return Integer.parseInt(pieces[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Application version incorrectly formatted");
        }
    }
}
