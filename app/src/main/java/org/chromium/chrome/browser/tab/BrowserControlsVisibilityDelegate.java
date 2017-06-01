// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

/**
 * A delegate to determine visibility of the browser controls.
 */
public interface BrowserControlsVisibilityDelegate {
    /**
     * @return Whether hiding browser controls is enabled or not.
     */
    boolean isHidingBrowserControlsEnabled();

    /**
     * @return Whether showing browser controls is enabled or not.
     */
    boolean isShowingBrowserControlsEnabled();
}
