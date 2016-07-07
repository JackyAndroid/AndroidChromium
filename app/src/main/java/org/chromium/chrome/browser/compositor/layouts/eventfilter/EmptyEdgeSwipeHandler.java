// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.eventfilter;

import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;

/**
 * An empty implementation of a {@link EdgeSwipeHandler}.
 */
public class EmptyEdgeSwipeHandler implements EdgeSwipeHandler {
    @Override
    public void swipeStarted(ScrollDirection direction, float x, float y) {
    }

    @Override
    public void swipeUpdated(float x, float y, float dx, float dy, float tx, float ty) {
    }

    @Override
    public void swipeFinished() {
    }

    @Override
    public void swipeFlingOccurred(float x, float y, float tx, float ty, float vx, float vy) {
    }

    @Override
    public boolean isSwipeEnabled(ScrollDirection direction) {
        return true;
    }

}