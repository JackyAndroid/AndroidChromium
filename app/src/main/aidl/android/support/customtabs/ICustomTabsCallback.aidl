// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package android.support.customtabs;

import android.os.Bundle;

/**
 * Interface to a CustomTabsCallback.
 * @hide
 */
oneway interface ICustomTabsCallback {
    void onNavigationEvent(int navigationEvent, in Bundle extras) = 1;
    void extraCallback(String callbackName, in Bundle args) = 2;
}
