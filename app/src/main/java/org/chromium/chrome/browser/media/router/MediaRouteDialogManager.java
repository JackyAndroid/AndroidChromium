// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import android.support.v7.app.MediaRouteChooserDialog;
import android.support.v7.app.MediaRouteControllerDialog;

/**
 * A common interface to be implemented by various media route dialog handlers
 * (e.g {@link MediaRouteChooserDialog} or {@link MediaRouteControllerDialog}.
 */
public interface MediaRouteDialogManager {
    /**
     * Opens the dialog managed by the implementation of the interface.
     */
    void openDialog();

    /**
     * Closes the currently open dialog.
     */
    void closeDialog();

    /**
     * @return if a chooser or controller dialog is shown.
     */
    boolean isShowingDialog();
}
