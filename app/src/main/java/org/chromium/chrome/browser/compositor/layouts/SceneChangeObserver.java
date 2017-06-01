// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

/**
 * An observer that listens for active {@link Layout} changes.
 */
public interface SceneChangeObserver {
    /**
     * Called when a layout wants to hint that a new tab might be selected soon.  This is not called
     * every time a tab is selected.
     * @param tabId The id of the tab that might be selected soon.
     */
    void onTabSelectionHinted(int tabId);

    /**
     * Called when the active {@link Layout} changes.
     * @param layout The new active {@link Layout}.
     */
    void onSceneChange(Layout layout);
}