// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.infobar;

import android.content.Context;

import org.chromium.chrome.R;

/**
 * Never panel shown in the translate infobar
 */
public class TranslateNeverPanel implements TranslateSubPanel {

    private final TranslateOptions mOptions;
    private final SubPanelListener mListener;

    public TranslateNeverPanel(SubPanelListener listener, TranslateOptions options) {
        mOptions = options;
        mListener = listener;
    }

    @Override
    public void createContent(Context context, InfoBarLayout layout) {
        String changeLanguage = context.getString(
                R.string.translate_never_translate_message_text, mOptions.sourceLanguageName());
        layout.setMessage(changeLanguage);

        layout.setButtons(context.getString(R.string.translate_never_translate_site),
                context.getString(R.string.translate_never_translate_language,
                        mOptions.sourceLanguageName()));
    }

    @Override
    public void onButtonClicked(boolean primary) {
        if (primary) {
            mOptions.toggleNeverTranslateDomainState(true);
        } else {
            mOptions.toggleNeverTranslateLanguageState(true);
        }
        mListener.onPanelClosed(ActionType.NONE);
    }
}
