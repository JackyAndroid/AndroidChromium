// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import android.app.Application;
import android.content.Context;

/**
 * A sync adapter for Chrome.
 */
public class ChromeBrowserSyncAdapter extends ChromiumSyncAdapter {
    public ChromeBrowserSyncAdapter(Context context, Application application) {
        super(context, application);
    }

    @Override
    protected boolean useAsyncStartup() {
        return false;
    }
}
