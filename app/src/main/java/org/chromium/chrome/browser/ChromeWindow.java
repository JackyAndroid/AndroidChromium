// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;

import org.chromium.chrome.browser.infobar.InfoBarIdentifier;
import org.chromium.chrome.browser.infobar.SimpleConfirmInfoBarBuilder;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.ui.base.ActivityWindowAndroid;

/**
 * The window that has access to the main activity and is able to create and receive intents,
 * and show error messages.
 */
public class ChromeWindow extends ActivityWindowAndroid {
    /**
     * Creates Chrome specific ActivityWindowAndroid.
     * @param activity The activity that owns the ChromeWindow.
     */
    public ChromeWindow(ChromeActivity activity) {
        super(activity);
    }

    /**
     * Shows an infobar error message overriding the WindowAndroid implementation.
     */
    @Override
    protected void showCallbackNonExistentError(String error) {
        Activity activity = getActivity().get();

        // We can assume that activity is a ChromeActivity because we require one to be passed in
        // in the constructor.
        Tab tab = activity != null ? ((ChromeActivity) activity).getActivityTab() : null;

        if (tab != null) {
            SimpleConfirmInfoBarBuilder.create(
                    tab, InfoBarIdentifier.CHROME_WINDOW_ERROR, error, false);
        } else {
            super.showCallbackNonExistentError(error);
        }
    }
}
