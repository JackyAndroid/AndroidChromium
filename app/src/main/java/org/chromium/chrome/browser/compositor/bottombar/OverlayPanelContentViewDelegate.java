// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar;

import org.chromium.content.browser.ContentViewCore;

/**
 * The delegate that is notified when the OverlayPanel ContentViewCore is ready to be rendered.
 */
public interface OverlayPanelContentViewDelegate {
    /**
     * Sets the {@code ContentViewCore} associated to the OverlayPanel.
     * @param contentViewCore Reference to the ContentViewCore.
     */
    void setOverlayPanelContentViewCore(ContentViewCore contentViewCore);

    /**
     * Releases the {@code ContentViewCore} associated to the OverlayPanel.
     */
    void releaseOverlayPanelContentViewCore();
}
