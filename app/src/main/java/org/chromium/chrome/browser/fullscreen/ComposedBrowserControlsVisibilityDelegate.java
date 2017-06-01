// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.fullscreen;

import org.chromium.chrome.browser.tab.BrowserControlsVisibilityDelegate;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate for the visibility of browser controls that combines the results of other delegates.
 */
public class ComposedBrowserControlsVisibilityDelegate
        implements BrowserControlsVisibilityDelegate {

    private final List<BrowserControlsVisibilityDelegate> mDelegates;

    /**
     * Constructs a composed visibility delegate that will generate results based on the delegates
     * passed in.
     */
    public ComposedBrowserControlsVisibilityDelegate(
            BrowserControlsVisibilityDelegate... delegates) {
        mDelegates = new ArrayList<>();
        for (int i = 0; i < delegates.length; i++) mDelegates.add(delegates[i]);
    }

    @Override
    public boolean isShowingBrowserControlsEnabled() {
        for (int i = 0; i < mDelegates.size(); i++) {
            if (!mDelegates.get(i).isShowingBrowserControlsEnabled()) return false;
        }
        return true;
    }

    @Override
    public boolean isHidingBrowserControlsEnabled() {
        for (int i = 0; i < mDelegates.size(); i++) {
            if (!mDelegates.get(i).isHidingBrowserControlsEnabled()) return false;
        }
        return true;
    }

}
