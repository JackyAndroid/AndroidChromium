// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import org.chromium.chrome.browser.init.ProcessInitializationHandler;
import org.chromium.components.invalidation.InvalidationClientService;

/**
 * Extension of the InvalidationClientService that allows Chrome specific initialization.
 */
public class ChromeInvalidationClientService extends InvalidationClientService {

    @Override
    public void onCreate() {
        ProcessInitializationHandler.getInstance().initializePreNative();
        super.onCreate();
    }
}
