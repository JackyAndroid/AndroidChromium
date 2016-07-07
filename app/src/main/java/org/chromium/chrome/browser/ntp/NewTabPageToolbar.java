// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.widget.TintedDrawable;

/**
 * The toolbar at the bottom of the new tab page. Contains buttons to open the bookmarks and
 * recent tabs pages.
 */
public class NewTabPageToolbar extends LinearLayout {

    private ViewGroup mBookmarksButton, mRecentTabsButton;

    /**
     * Constructor for inflating from xml.
     */
    public NewTabPageToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public View getBookmarksButton() {
        return mBookmarksButton;
    }

    public View getRecentTabsButton() {
        return mRecentTabsButton;
    }

    @Override
    protected void onFinishInflate() {
        mBookmarksButton = initButton(R.id.bookmarks_button, R.drawable.btn_star);
        mRecentTabsButton = initButton(R.id.recent_tabs_button, R.drawable.btn_recents);
        ((TextView) mBookmarksButton.getChildAt(0)).setText(OfflinePageBridge.isEnabled()
                ? R.string.offline_pages_ntp_button_name : R.string.ntp_bookmarks);
    }

    private ViewGroup initButton(int buttonId, int drawableId) {
        ViewGroup button = (ViewGroup) findViewById(buttonId);
        TextView textView = (TextView) button.getChildAt(0);

        TintedDrawable icon = TintedDrawable.constructTintedDrawable(getResources(), drawableId);
        ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                textView, icon, null, null, null);

        return button;
    }
}
