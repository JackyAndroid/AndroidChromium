// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import android.app.Application;
import android.content.Context;

/**
 * Service for ChromeBrowserSyncAdapter.
 */
public class ChromeBrowserSyncAdapterService extends ChromiumSyncAdapterService {
    @Override
    protected ChromiumSyncAdapter createChromiumSyncAdapter(
            Context applicationContext, Application application) {
        return new ChromeBrowserSyncAdapter(applicationContext, getApplication());
    }
}
