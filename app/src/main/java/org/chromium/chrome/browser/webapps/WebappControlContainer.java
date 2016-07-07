// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;

import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.widget.ClipDrawableProgressBar.DrawingInfo;
import org.chromium.chrome.browser.widget.ControlContainer;
import org.chromium.chrome.browser.widget.ViewResourceFrameLayout;
import org.chromium.ui.resources.dynamics.ViewResourceAdapter;

/**
 * The control container used by WebApps.
 */
public class WebappControlContainer extends ViewResourceFrameLayout
        implements ControlContainer {

    /** Constructor for inflating from XML. */
    public WebappControlContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public ViewResourceAdapter getToolbarResourceAdapter() {
        return getResourceAdapter();
    }

    @Override
    public void getProgressBarDrawingInfo(DrawingInfo drawingInfoOut) {
    }

    @Override
    public void setSwipeHandler(EdgeSwipeHandler handler) {
    }

    @Override
    public int getToolbarBackgroundColor() {
        return Color.WHITE;
    }
}
