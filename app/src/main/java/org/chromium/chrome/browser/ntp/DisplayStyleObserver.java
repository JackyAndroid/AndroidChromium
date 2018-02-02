// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

/**
 * Gets notified of changes in the display style.
 *
 * @see UiConfig.DisplayStyle
 * @see UiConfig#getDisplayStyle()
 * @see org.chromium.chrome.browser.ntp.cards.DisplayStyleObserverAdapter
 */
public interface DisplayStyleObserver {
    void onDisplayStyleChanged(@UiConfig.DisplayStyle int newDisplayStyle);
}
