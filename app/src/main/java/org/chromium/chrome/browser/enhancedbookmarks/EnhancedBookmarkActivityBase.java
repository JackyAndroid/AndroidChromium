// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.ChromeApplication;

/**
 * Activity base class that all the EnhnacedBookmark activities inherit. Currently it's responsible
 * for ensuring native library initialization.
 */
abstract class EnhancedBookmarkActivityBase extends AppCompatActivity {
    private static final String TAG = "EnhancedBookmarkActivityBase";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure that native library is loaded.
        try {
            ((ChromeApplication) getApplication())
                    .startBrowserProcessesAndLoadLibrariesSync(true);
        } catch (ProcessInitException e) {
            Log.e(TAG, "Failed to start browser process.", e);
            ChromeApplication.reportStartupErrorAndExit(e);
        }
    }
}
