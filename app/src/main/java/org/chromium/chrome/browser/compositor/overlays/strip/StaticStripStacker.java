// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.overlays.strip;

import org.chromium.chrome.browser.util.MathUtils;

/**
 * A stacker that tells the {@link StripHelper} how to layer the tabs for the
 * {@link StaticLayout}.  This will basically be focused tab in front with tabs cascading
 * back to each side.
 */
public class StaticStripStacker implements StripStacker {
    @Override
    public boolean canShowCloseButton() {
        return true;
    }

    @Override
    public boolean canSlideTitleText() {
        return true;
    }

    @Override
    public void createVisualOrdering(int selectedIndex, StripLayoutTab[] indexOrderedTabs,
            StripLayoutTab[] outVisualOrderedTabs) {
        assert indexOrderedTabs.length == outVisualOrderedTabs.length;

        selectedIndex = MathUtils.clamp(selectedIndex, 0, indexOrderedTabs.length);

        int outIndex = 0;
        for (int i = 0; i < selectedIndex; i++) {
            outVisualOrderedTabs[outIndex++] = indexOrderedTabs[i];
        }

        for (int i = indexOrderedTabs.length - 1; i >= selectedIndex; --i) {
            outVisualOrderedTabs[outIndex++] = indexOrderedTabs[i];
        }
    }

    @Override
    public void performOcclusionPass(int selectedIndex, StripLayoutTab[] indexOrderedTabs) {
        for (int i = 1; i < indexOrderedTabs.length; i++) {
            StripLayoutTab prevTab = indexOrderedTabs[i - 1];
            StripLayoutTab currTab = indexOrderedTabs[i];

            if ((int) prevTab.getDrawY() == (int) currTab.getDrawY()
                    && (int) prevTab.getDrawX() == (int) currTab.getDrawX()) {
                if (i <= selectedIndex) {
                    prevTab.setVisible(false);
                } else if (i > selectedIndex) {
                    currTab.setVisible(false);
                }
            } else if ((int) prevTab.getDrawX() != (int) currTab.getDrawX()) {
                if (i <= selectedIndex) {
                    prevTab.setVisible(true);
                } else if (i > selectedIndex) {
                    currTab.setVisible(true);
                }
            }

            if (i == selectedIndex) currTab.setVisible(true);
        }
    }
}