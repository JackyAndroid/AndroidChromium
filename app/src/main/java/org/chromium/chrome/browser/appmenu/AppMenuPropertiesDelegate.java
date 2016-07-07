// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.appmenu;

import android.view.Menu;

/**
 * Interface for the App Handler to query the desired state of the App Menu.
 */
public interface AppMenuPropertiesDelegate {

    /**
     * @return Whether the App Menu should be shown.
     */
    boolean shouldShowAppMenu();

    /**
     * @return Resource layout id for the footer if there should be one. O otherwise.
     */
    int getFooterResourceId();

    /**
     * Allows the delegate to show and hide items before the App Menu is shown.
     * @param mMenu Menu that will be used as the source for the App Menu pop up.
     */
    void prepareMenu(Menu mMenu);
}
