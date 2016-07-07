// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.appmenu;

/**
 * Allows monitoring of application menu actions.
 */
public interface AppMenuObserver {
    /**
     * Informs when the App Menu visibility changes.
     * @param isVisible Whether the menu is now visible.
     */
    public void onMenuVisibilityChanged(boolean isVisible);
}
