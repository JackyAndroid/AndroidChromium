// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.appmenu;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * A menu icon that supports the checkable state.
 */
public class AppMenuItemIcon extends ImageView {
    private static final int[] CHECKED_STATE_SET = new int[] {android.R.attr.state_checked};
    private boolean mCheckedState;

    public AppMenuItemIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Sets whether the item is checked and refreshes the View if necessary.
     */
    protected void setChecked(boolean state) {
        if (state == mCheckedState) return;
        mCheckedState = state;
        refreshDrawableState();
    }

    @Override
    public void setPressed(boolean state) {
        // We don't want to highlight the checkbox icon since the parent item is already
        // highlighted.
        return;
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mCheckedState) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }
}