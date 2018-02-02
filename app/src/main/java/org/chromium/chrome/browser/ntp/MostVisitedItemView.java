// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;

/**
 * The view for a most visited item. Displays the title of the page beneath a large icon. If a large
 * icon isn't available, displays a rounded rectangle with a single letter in its place.
 */
public class MostVisitedItemView extends FrameLayout {

    /**
     * Constructor for inflating from XML.
     */
    public MostVisitedItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Sets the title text.
     */
    public void setTitle(String title) {
        ((TextView) findViewById(R.id.most_visited_title)).setText(title);
    }

    /**
     * Sets the icon.
     */
    public void setIcon(Drawable icon) {
        ImageView iconView = (ImageView) findViewById(R.id.most_visited_icon);
        iconView.setImageDrawable(icon);
    }

    /**
     * Sets whether the page is available offline.
     */
    public void setOfflineAvailable(boolean offlineAvailable) {
        findViewById(R.id.offline_badge).setVisibility(
                offlineAvailable ? View.VISIBLE : View.INVISIBLE);
    }
}
