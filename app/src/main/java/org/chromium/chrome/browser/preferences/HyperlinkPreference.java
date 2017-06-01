// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.EmbedContentViewActivity;

/**
 * A preference that navigates to an URL.
 */
public class HyperlinkPreference extends Preference {

    private final int mTitleResId;
    private final int mUrlResId;
    private final boolean mImitateWebLink;

    public HyperlinkPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.HyperlinkPreference, 0, 0);
        mUrlResId = a.getResourceId(R.styleable.HyperlinkPreference_url, 0);
        mImitateWebLink = a.getBoolean(R.styleable.HyperlinkPreference_imitateWebLink, false);
        a.recycle();
        mTitleResId = getTitleRes();
    }

    @Override
    protected void onClick() {
        EmbedContentViewActivity.show(getContext(), mTitleResId, mUrlResId);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        TextView titleView = (TextView) view.findViewById(android.R.id.title);
        titleView.setSingleLine(false);

        if (mImitateWebLink) {
            setSelectable(false);

            titleView.setClickable(true);
            titleView.setTextColor(titleView.getPaint().linkColor);
            titleView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    HyperlinkPreference.this.onClick();
                }
            });
        }
    }
}
