// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ResourceId;

/**
 * The Save Password infobar offers the user the ability to save a password for the site.
 * Appearance and behaviour of infobar buttons depends on from where infobar was
 * triggered.
 */
public class SavePasswordInfoBar extends ConfirmInfoBar {
    private final int mTitleLinkRangeStart;
    private final int mTitleLinkRangeEnd;
    private final String mTitle;
    private final String mFirstRunExperienceMessage;

    @CalledByNative
    private static InfoBar show(int enumeratedIconId, String message, int titleLinkStart,
            int titleLinkEnd, String primaryButtonText, String secondaryButtonText,
            String firstRunExperienceMessage) {
        return new SavePasswordInfoBar(ResourceId.mapToDrawableId(enumeratedIconId), message,
                titleLinkStart, titleLinkEnd, primaryButtonText, secondaryButtonText,
                firstRunExperienceMessage);
    }

    private SavePasswordInfoBar(int iconDrawbleId, String message, int titleLinkStart,
            int titleLinkEnd, String primaryButtonText, String secondaryButtonText,
            String firstRunExperienceMessage) {
        super(null, iconDrawbleId, null, message, null, primaryButtonText, secondaryButtonText);
        mTitleLinkRangeStart = titleLinkStart;
        mTitleLinkRangeEnd = titleLinkEnd;
        mTitle = message;
        mFirstRunExperienceMessage = firstRunExperienceMessage;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);
        if (mTitleLinkRangeStart != 0 && mTitleLinkRangeEnd != 0) {
            SpannableString title = new SpannableString(mTitle);
            title.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View view) {
                    onLinkClicked();
                }
            }, mTitleLinkRangeStart, mTitleLinkRangeEnd, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            layout.setMessage(title);
        }

        if (!TextUtils.isEmpty(mFirstRunExperienceMessage)) {
            TextView firstRunExperienceMessageView =
                    (TextView) LayoutInflater.from(getContext())
                            .inflate(R.layout.smart_lock_save_prompt_first_run, null);
            firstRunExperienceMessageView.setText(mFirstRunExperienceMessage);
            layout.setCustomContent(firstRunExperienceMessageView);
        }
    }
}
