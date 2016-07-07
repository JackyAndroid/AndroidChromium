// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EmptyEdgeSwipeHandler;

/**
 * A {@link EdgeSwipeHandler} that takes a {@link LayoutProvider} and delegates all swipe events
 * to {@link LayoutProvider#getActiveLayout()}.
 */
public class EdgeSwipeHandlerLayoutDelegate extends EmptyEdgeSwipeHandler {
    private final LayoutProvider mLayoutProvider;

    /**
     * Creates an instance of the {@link EdgeSwipeHandlerLayoutDelegate}.
     * @param provider A {@link LayoutProvider} instance.
     */
    public EdgeSwipeHandlerLayoutDelegate(LayoutProvider provider) {
        mLayoutProvider = provider;
    }

    @Override
    public void swipeStarted(ScrollDirection direction, float x, float y) {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().swipeStarted(LayoutManager.time(), direction, x, y);
    }

    @Override
    public void swipeUpdated(float x, float y, float dx, float dy, float tx, float ty) {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().swipeUpdated(LayoutManager.time(), x, y, dx, dy, tx, ty);
    }

    @Override
    public void swipeFinished() {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().swipeFinished(LayoutManager.time());
    }

    @Override
    public void swipeFlingOccurred(float x, float y, float tx, float ty, float vx, float vy) {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().swipeFlingOccurred(
                LayoutManager.time(), x, y, tx, ty, vx, vy);
    }
}