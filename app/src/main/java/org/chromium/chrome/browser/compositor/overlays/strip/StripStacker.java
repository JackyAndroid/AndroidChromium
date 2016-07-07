// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.overlays.strip;

import org.chromium.chrome.browser.compositor.layouts.Layout;

/**
 * An interface that defines how to stack tabs and how they should look visually.  This lets
 * certain components customize how the {@link StripLayoutHelper} functions and how other
 * {@link Layout}s visually order tabs.
 */
public interface StripStacker {
    /**
     * @return Whether or not the close button can be shown.  Note that even if it can be shown,
     *         it might not be due to how much of the tab is actually visible to preserve proper hit
     *         target sizes.
     */
    public boolean canShowCloseButton();

    /**
     * @return Whether or not the title text can slide to the right to stay visible.
     */
    public boolean canSlideTitleText();

    /**
     * This gives the implementing class a chance to determine how the tabs should be ordered
     * visually.  The positioning logic is the same regardless, this just has to do with visual
     * stacking.
     *
     * @param selectedIndex The selected index of the tabs.
     * @param indexOrderedTabs A list of tabs ordered by index.
     * @param outVisualOrderedTabs The new list of tabs, ordered from back to front visually.
     */
    public void createVisualOrdering(int selectedIndex, StripLayoutTab[] indexOrderedTabs,
            StripLayoutTab[] outVisualOrderedTabs);

    /**
     * Performs an occlusion pass, setting the visibility on tabs depending on whether or not they
     * overlap each other perfectly.  This is relegated to this interface because the implementing
     * class knows the proper visual order to optimize this pass.
     * @param selectedIndex The selected index of the tabs.
     * @param indexOrderedTabs A list of tabs ordered by index.
     */
    public void performOcclusionPass(int selectedIndex, StripLayoutTab[] indexOrderedTabs);
}