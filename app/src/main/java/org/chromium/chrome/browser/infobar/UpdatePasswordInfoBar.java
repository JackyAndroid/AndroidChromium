// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Spinner;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ResourceId;
import org.chromium.chrome.browser.infobar.InfoBarControlLayout.InfoBarArrayAdapter;

/**
 * The Update Password infobar offers the user the ability to update a password for the site.
 */
public class UpdatePasswordInfoBar extends ConfirmInfoBar {
    private final String[] mUsernames;
    private final int mTitleLinkRangeStart;
    private final int mTitleLinkRangeEnd;
    private final String mTitle;
    private Spinner mUsernamesSpinner;

    @CalledByNative
    private static InfoBar show(int enumeratedIconId, String[] usernames, String message,
            int titleLinkStart, int titleLinkEnd, String primaryButtonText) {
        return new UpdatePasswordInfoBar(ResourceId.mapToDrawableId(enumeratedIconId), usernames,
                message, titleLinkStart, titleLinkEnd, primaryButtonText);
    }

    private UpdatePasswordInfoBar(int iconDrawbleId, String[] usernames, String message,
            int titleLinkStart, int titleLinkEnd, String primaryButtonText) {
        super(iconDrawbleId, null, message, null, primaryButtonText, null);
        mTitleLinkRangeStart = titleLinkStart;
        mTitleLinkRangeEnd = titleLinkEnd;
        mTitle = message;
        mUsernames = usernames;
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

        InfoBarControlLayout controlLayout = layout.addControlLayout();
        if (mUsernames.length > 1) {
            InfoBarArrayAdapter<String> usernamesAdapter =
                    new InfoBarArrayAdapter<String>(getContext(), mUsernames);
            mUsernamesSpinner = controlLayout.addSpinner(
                    R.id.password_infobar_accounts_spinner, usernamesAdapter);
        } else {
            controlLayout.addDescription(mUsernames[0]);
        }
    }

    @CalledByNative
    private int getSelectedUsername() {
        return mUsernames.length == 1 ? 0 : mUsernamesSpinner.getSelectedItemPosition();
    }
}
