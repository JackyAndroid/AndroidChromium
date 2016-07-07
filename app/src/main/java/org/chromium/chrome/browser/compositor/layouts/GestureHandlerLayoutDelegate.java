// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import org.chromium.chrome.browser.compositor.layouts.eventfilter.GestureHandler;

/**
 * A {@link GestureHandler} that takes a {@link LayoutProvider} and delegates all gesture events
 * to {@link LayoutProvider#getActiveLayout()}.
 */
class GestureHandlerLayoutDelegate implements GestureHandler {
    private final LayoutProvider mLayoutProvider;

    /**
     * Creates an instance of the {@link GestureHandlerLayoutDelegate}.
     * @param provider A {@link LayoutProvider} instance.
     */
    public GestureHandlerLayoutDelegate(LayoutProvider provider) {
        mLayoutProvider = provider;
    }

    @Override
    public void onDown(float x, float y, boolean fromMouse, int buttons) {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().onDown(LayoutManager.time(), x, y);
    }

    @Override
    public void onUpOrCancel() {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().onUpOrCancel(LayoutManager.time());
    }

    @Override
    public void drag(float x, float y, float dx, float dy, float tx, float ty) {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().drag(LayoutManager.time(), x, y, dx, dy);
    }

    @Override
    public void click(float x, float y, boolean fromMouse, int buttons) {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().click(LayoutManager.time(), x, y);
    }

    @Override
    public void fling(float x, float y, float velocityX, float velocityY) {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().fling(LayoutManager.time(), x, y, velocityX, velocityY);
    }

    @Override
    public void onLongPress(float x, float y) {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().onLongPress(LayoutManager.time(), x, y);
    }

    @Override
    public void onPinch(float x0, float y0, float x1, float y1, boolean firstEvent) {
        if (mLayoutProvider.getActiveLayout() == null) return;
        mLayoutProvider.getActiveLayout().onPinch(LayoutManager.time(), x0, y0, x1, y1, firstEvent);
    }
}