// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

/**
 * A collection of listeners for different infobar events.
 */
public class InfoBarListeners {

    /**
     * Called whenever an infobar is dismissed, either manually or as a side
     * effect of a navigation, replacing the infobar...
     */
    public interface Dismiss {
        void onInfoBarDismissed(InfoBar infoBar);
    }

    public interface Confirm extends Dismiss {
        /**
         * Called whenever an infobar's confirm or cancel button is clicked.
         */
        public void onConfirmInfoBarButtonClicked(
                ConfirmInfoBar infoBar, boolean confirm);
    }

}
