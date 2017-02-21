// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

import org.chromium.chrome.R;

/**
 * Subclass of {@link android.support.v4.widget.SwipeRefreshLayout} that supports containing a
 * single ViewGroup. The ViewGroup must contain a ListView such that the top of the list is
 * aligned to the top of the parent view. Scrolling past the top of the list triggers a
 * refresh.
 */
public class SwipeRefreshWidget extends android.support.v4.widget.SwipeRefreshLayout {
    private ListView mListView;

    public SwipeRefreshWidget(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    @Override
    public boolean canChildScrollUp() {
        if (mListView == null) {
            mListView = (ListView) findViewById(R.id.physical_web_urls_list);
        }

        // The real child we care about is the list, so check if that can scroll.
        return mListView.getChildCount() > 0
                && (mListView.getFirstVisiblePosition() > 0
                || mListView.getChildAt(0).getTop() < mListView.getPaddingTop());
    }
}
