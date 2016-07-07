// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.infobar;

import android.content.Context;

import org.chromium.chrome.R;

/**
 * Options panel shown in the after translate infobar.
 */
public class TranslateAlwaysPanel implements TranslateSubPanel {

    private final TranslateOptions mOptions;
    private final SubPanelListener mListener;

    TranslateAlwaysPanel(SubPanelListener listener, TranslateOptions options) {
        mOptions = options;
        mListener = listener;
    }

    @Override
    public void createContent(Context context, InfoBarLayout layout) {
        layout.setMessage(context.getString(
                R.string.translate_infobar_translation_done, mOptions.targetLanguage()));

        if (!mOptions.triggeredFromMenu()) {
            TranslateCheckBox checkBox = new TranslateCheckBox(context, mOptions, mListener);
            layout.setCustomContent(checkBox);
        }

        layout.setButtons(context.getString(R.string.translate_button_done),
                context.getString(R.string.translate_show_original));
    }

    @Override
    public void onButtonClicked(boolean primary) {
        if (primary) {
            mListener.onPanelClosed(ActionType.NONE);
        } else {
            mListener.onPanelClosed(ActionType.TRANSLATE_SHOW_ORIGINAL);
        }
    }
}
