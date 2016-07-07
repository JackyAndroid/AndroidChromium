// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;


/**
 * A simple infobar that contains a message and a close icon on the right side.
 * This is used only in the context of Java code and is not associated with any native
 * InfoBarDelegate.
 *
 * TODO(newt): merge this into InfoBar.java
 */
public class MessageInfoBar extends InfoBar {

    /**
     * Creates an infobar with a message and a close button.
     * @param title the text displayed in the infobar
     */
    public MessageInfoBar(CharSequence title) {
        this(null, 0, title);
    }

    /**
     * Creates an infobar with an icon, a message and a close button.
     * @param listener A listener to be notified when the infobar is dismissed, or null.
     * @param iconResourceId The icon to display in the infobar, or 0 if no icon should be shown.
     * @param title The text to display in the infobar.
     */
    public MessageInfoBar(InfoBarListeners.Dismiss listener, int iconResourceId,
            CharSequence title) {
        super(listener, iconResourceId, null, title);
    }
}
