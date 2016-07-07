// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * A preference that opens a HelpAndFeedback activity to learn more about the specified context.
 */
public class LearnMorePreference extends Preference {

    private final int mHelpContext;

    public LearnMorePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.LearnMorePreference, 0, 0);
        mHelpContext = a.getResourceId(R.styleable.LearnMorePreference_helpContext, 0);
        a.recycle();
        setTitle(R.string.learn_more);
    }

    @Override
    protected void onClick() {
        HelpAndFeedback.getInstance(getContext())
                .show((Activity) getContext(), getContext().getString(mHelpContext),
                        Profile.getLastUsedProfile(), null);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        TextView titleView = (TextView) view.findViewById(android.R.id.title);
        titleView.setSingleLine(false);

        setSelectable(false);

        titleView.setClickable(true);
        titleView.setTextColor(titleView.getPaint().linkColor);
        titleView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnMorePreference.this.onClick();
            }
        });
    }
}