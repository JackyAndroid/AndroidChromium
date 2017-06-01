// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.selection;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * Highlight overlay view for selectable items.
 */
public class SelectableItemHighlightView extends View implements Checkable {
    public static final int ANIMATION_DURATION_MS = 150;
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};
    private boolean mIsChecked;

    /**
     * Constructor for inflating from XML.
     */
    public SelectableItemHighlightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Drawable clickDrawable = context.obtainStyledAttributes(new int[] {
                android.R.attr.selectableItemBackground }).getDrawable(0);
        Drawable longClickDrawable = ApiCompatibilityUtils.getDrawable(context.getResources(),
                R.drawable.selectable_item_highlight);
        LayerDrawable ld = new LayerDrawable(new Drawable[] {clickDrawable, longClickDrawable});
        setBackground(ld);
    }

    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void toggle() {
        setChecked(!mIsChecked);
    }

    @Override
    public void setChecked(boolean checked) {
        if (checked == mIsChecked) return;
        mIsChecked = checked;
        refreshDrawableState();
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mIsChecked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }
}
