// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.compositor.layouts.components;

import android.graphics.RectF;

/**
 * {@link VirtualView} is the minimal interface that provides information for
 * building accessibility events.
 */
public interface VirtualView {
    /**
     * @return A string with a description of the object for accessibility events.
     */
    String getAccessibilityDescription();

    /**
     * @param A rect that will be populated with the clickable area of the object in dp.
     */
    void getTouchTarget(RectF outTarget);

    /**
     * @param x The x offset of the click in dp.
     * @param y The y offset of the click in dp.
     * @return Whether or not that click occurred inside of the button + slop area.
     */
    boolean checkClicked(float x, float y);
}
