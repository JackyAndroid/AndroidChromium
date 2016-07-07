// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.content.Context;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 * The First Run Experience fragment that allows the user to accept Terms of Service ("ToS") and
 * Privacy Notice, and to opt-in to the usage statistics and crash reports collection ("UMA",
 * User Metrics Analysis) as defined in the Chrome Privacy Notice.
 */
public class ToSAndUMAFirstRunFragment extends FirstRunPage {
    private Button mAcceptButton;
    private CheckBox mSendReportCheckBox;
    private TextView mTosAndPrivacy;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fre_tosanduma, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAcceptButton = (Button) view.findViewById(R.id.terms_accept);
        mSendReportCheckBox = (CheckBox) view.findViewById(R.id.send_report_checkbox);
        mTosAndPrivacy = (TextView) view.findViewById(R.id.tos_and_privacy);

        mAcceptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getPageDelegate().acceptTermsOfService(mSendReportCheckBox.isChecked());
            }
        });

        int paddingStart = getResources().getDimensionPixelSize(R.dimen.fre_tos_checkbox_padding);
        ApiCompatibilityUtils.setPaddingRelative(mSendReportCheckBox,
                ApiCompatibilityUtils.getPaddingStart(mSendReportCheckBox) + paddingStart,
                mSendReportCheckBox.getPaddingTop(),
                ApiCompatibilityUtils.getPaddingEnd(mSendReportCheckBox),
                mSendReportCheckBox.getPaddingBottom());

        mSendReportCheckBox.setChecked(!getPageDelegate().isNeverUploadCrashDump());

        mTosAndPrivacy.setMovementMethod(LinkMovementMethod.getInstance());

        ClickableSpan clickableTermsSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                if (!isAdded()) return;
                getPageDelegate().showEmbedContentViewActivity(R.string.terms_of_service_title,
                        R.string.chrome_terms_of_service_url);
            }
        };

        ClickableSpan clickablePrivacySpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                if (!isAdded()) return;
                getPageDelegate().showEmbedContentViewActivity(R.string.privacy_notice_title,
                        R.string.chrome_privacy_notice_url);
            }
        };
        mTosAndPrivacy.setText(SpanApplier.applySpans(getString(R.string.fre_tos_and_privacy),
                new SpanInfo("<LINK1>", "</LINK1>", clickableTermsSpan),
                new SpanInfo("<LINK2>", "</LINK2>", clickablePrivacySpan)));
    }

    @Override
    public boolean shouldSkipPageOnCreate(Context appContext) {
        return PrefServiceBridge.getInstance().isFirstRunEulaAccepted();
    }
}
