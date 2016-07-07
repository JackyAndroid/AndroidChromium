// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import org.chromium.chrome.browser.compositor.layouts.EdgeSwipeHandlerLayoutDelegate;
import org.chromium.chrome.browser.compositor.layouts.LayoutProvider;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;
import org.chromium.chrome.browser.dom_distiller.ReaderModeStaticEventFilter.ReaderModePanelSelector;

/**
 * An {@link EdgeSwipeHandlerLayoutDelegate} that delegates all swipe events to the currently
 * active Reader Mode Panel.
 */
public class ReaderModeEdgeSwipeHandler extends EdgeSwipeHandlerLayoutDelegate {
    private final ReaderModePanelSelector mReaderModePanelSelector;

    public ReaderModeEdgeSwipeHandler(ReaderModePanelSelector selector, LayoutProvider provider) {
        super(provider);
        mReaderModePanelSelector = selector;
    }

    @Override
    public void swipeStarted(ScrollDirection direction, float x, float y) {
        ReaderModePanel panel = mReaderModePanelSelector.getActiveReaderModePanel();
        if (panel == null) super.swipeStarted(direction, x, y);
        else panel.swipeStarted(direction, x, y);
    }

    @Override
    public void swipeUpdated(float x, float y, float dx, float dy, float tx, float ty) {
        ReaderModePanel panel = mReaderModePanelSelector.getActiveReaderModePanel();
        if (panel == null) super.swipeUpdated(x, y, dx, dy, tx, ty);
        else panel.swipeUpdated(x, y, dx, dy, tx, ty);
    }

    @Override
    public void swipeFinished() {
        ReaderModePanel panel = mReaderModePanelSelector.getActiveReaderModePanel();
        if (panel == null) super.swipeFinished();
        else panel.swipeFinished();
    }

    @Override
    public boolean isSwipeEnabled(ScrollDirection direction) {
        ReaderModePanel panel = mReaderModePanelSelector.getActiveReaderModePanel();
        return panel != null && panel.isSwipeEnabled(direction);
    }
}
