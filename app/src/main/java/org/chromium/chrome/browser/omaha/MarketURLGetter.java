// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omaha;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

/**
 * Grabs the URL that points to the Android Market page for Chrome.
 * This incurs I/O, so don't use it from the main thread.
 */
public class MarketURLGetter {
    public String getMarketURL(
            Context applicationContext, String prefPackage, String prefMarketUrl) {
        assert Looper.myLooper() != Looper.getMainLooper();

        SharedPreferences prefs = applicationContext.getSharedPreferences(
                prefPackage, Context.MODE_PRIVATE);
        return prefs.getString(prefMarketUrl, "");
    }
}
