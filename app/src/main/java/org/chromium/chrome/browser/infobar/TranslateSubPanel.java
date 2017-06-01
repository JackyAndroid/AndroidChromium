// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;

/**
 * Interface to group the different sub panels of the translate infobar.
 * TODO(dfalcantara): Absorb this into the InfoBarView class when the TranslateInfoBar is split up.
 */
public interface TranslateSubPanel {

    /**
     * Creates a View containing the content of the new subpanel.
     * @param context Context containing the View's resources.
     * @param layout InfoBarLayout to insert controls into.
     */
    void createContent(Context context, InfoBarLayout layout);

    void onButtonClicked(boolean primary);

}
