// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.services;

/**
 * Callback used to get the result of the Android EDU ownership check.
 */
public interface AndroidEduOwnerCheckCallback {
    /**
     * Indicates whether this is an Android EDU device or not.
     */
    public void onSchoolCheckDone(boolean isAndroidEduDevice);
}
