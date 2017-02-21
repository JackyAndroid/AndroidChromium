// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.services.gcm;

import android.content.Context;

import org.chromium.base.ThreadUtils;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.content.browser.BrowserStartupController;
import org.chromium.content.browser.BrowserStartupController.StartupCallback;

/**
 * Helper Class for GCM UMA Collection.
 */
public class GcmUma {
    // Values for the "Invalidations.GCMUpstreamRequest" UMA histogram. The list is append-only.
    public static final int UMA_UPSTREAM_SUCCESS = 0;
    public static final int UMA_UPSTREAM_SIZE_LIMIT_EXCEEDED = 1;
    public static final int UMA_UPSTREAM_TOKEN_REQUEST_FAILED = 2;
    public static final int UMA_UPSTREAM_SEND_FAILED = 3;
    public static final int UMA_UPSTREAM_COUNT = 4;

    public static void recordGcmUpstreamHistogram(Context context, final int value) {
        onNativeLaunched(context, new Runnable() {
            @Override public void run() {
                RecordHistogram.recordEnumeratedHistogram(
                        "Invalidations.GCMUpstreamRequest", value, UMA_UPSTREAM_COUNT);
            }
        });
    }

    private static void onNativeLaunched(final Context context, final Runnable task) {
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                BrowserStartupController.get(context, LibraryProcessType.PROCESS_BROWSER)
                        .addStartupCompletedObserver(
                                new StartupCallback() {
                                    @Override
                                    public void onSuccess(boolean alreadyStarted) {
                                        task.run();
                                    }

                                    @Override
                                    public void onFailure() {
                                        // Startup failed.
                                    }
                                });
            }
        });
    }
}

