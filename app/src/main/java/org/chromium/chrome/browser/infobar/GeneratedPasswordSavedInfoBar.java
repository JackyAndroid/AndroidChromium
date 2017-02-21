// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;

/**
 * An infobar to notify that the generated password was saved.
 */
public class GeneratedPasswordSavedInfoBar extends InfoBar {
    private final String mMessageText;
    private final int mInlineLinkRangeStart;
    private final int mInlineLinkRangeEnd;
    private final String mButtonLabel;

    /**
     * Creates and shows the infobar to notify that the generated password was saved.
     * @param iconDrawableId Drawable ID corresponding to the icon that the infobar will show.
     * @param messageText Message to display in the infobar.
     * @param inlineLinkRangeStart The start of the range of the messageText that should be a link.
     * @param inlineLinkRangeEnd The end of the range of the messageText that should be a link.
     * @param buttonLabel String to display on the button.
     */
    public GeneratedPasswordSavedInfoBar(int iconDrawableId, String messageText,
            int inlineLinkRangeStart, int inlineLinkRangeEnd, String buttonLabel) {
        super(iconDrawableId, null, null);
        mMessageText = messageText;
        mInlineLinkRangeStart = inlineLinkRangeStart;
        mInlineLinkRangeEnd = inlineLinkRangeEnd;
        mButtonLabel = buttonLabel;
    }

    /**
     * Used to specify button layout and custom content. Makes infobar display a single button and
     * an inline link in the message.
     * @param layout Handles user interface for the infobar.
     */
    @Override
    public void createContent(InfoBarLayout layout) {
        layout.setButtons(mButtonLabel, null);
        SpannableString message = new SpannableString(mMessageText);
        message.setSpan(
                new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        onLinkClicked();
                    }
                }, mInlineLinkRangeStart, mInlineLinkRangeEnd, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        layout.setMessage(message);
    }

    /**
     * Called when the button is clicked. Notifies the native infobar, which closes the infobar.
     * @param isPrimaryButton True if the clicked button is primary.
     */
    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
        onButtonClicked(ActionType.OK);
    }
}
