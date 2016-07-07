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
}
