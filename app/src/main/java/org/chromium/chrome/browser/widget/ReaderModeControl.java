// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.chrome.R;

import org.chromium.ui.resources.dynamics.ViewResourceAdapter;

/**
 * Root ControlContainer for the Reader Mode panel.
 * Handles user interaction with the Reader Mode control.
 * See {@link ContextualSearchBarControl} for inspiration, based on ToolbarControlContainer.
 */
public class ReaderModeControl extends LinearLayout {
    private ViewResourceAdapter mResourceAdapter;
    private TextView mReaderViewText;
    private boolean mIsDirty = false;

    /**
     * Constructs a new control container.
     * <p>
     * This constructor is used when inflating from XML.
     *
     * @param context The context used to build this view.
     * @param attrs The attributes used to determine how to construct this view.
     */
    public ReaderModeControl(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @return The {@link ViewResourceAdapter} that exposes this {@link View} as a CC resource.
     */
    public ViewResourceAdapter getResourceAdapter() {
        return mResourceAdapter;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        mReaderViewText = (TextView) findViewById(R.id.main_text);
        mResourceAdapter = new ViewResourceAdapter(findViewById(R.id.reader_mode_view));
        mIsDirty = true;
    }

    @Override
    public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
        ViewParent parent = super.invalidateChildInParent(location, dirty);
        // TODO(pedrosimonetti): ViewGroup#invalidateChildInParent() is being called multiple
        // times with different rectangles (for each of the individual repaints it seems). This
        // means in order to invalidate it only once we need to keep track of the dirty state,
        // and call ViewResourceAdapter#invalidate() only once per change of state, passing
        // "null" to indicate that the whole area should be invalidated. This can be deleted
        // if we stop relying on an Android View to render our Search Bar Text.
        if (mIsDirty && mResourceAdapter != null) {
            mIsDirty = false;
            mResourceAdapter.invalidate(null);
        }
        return parent;
    }

    /**
     * Sets the reader mode text to display in the control.
     */
    public void setReaderModeText() {
        mReaderViewText.setText(R.string.reader_view_text);
        mIsDirty = true;
    }

    /**
     * @param X X-coordinate in dp
     * @param Y Y-coordinate in dp
     * @return Whether a given coordinates are within the bounds of the "dismiss" button
     */
    public boolean isInsideDismissButton(float x, float y) {
        View view = findViewById(R.id.main_close);
        final int left = (int) (view.getLeft() + view.getTranslationX());
        final int top = (int) (view.getTop() + view.getTranslationY());
        final int right = left + view.getWidth();
        final int bottom = top + view.getHeight();
        final int ix = (int) x;
        final int iy = (int) y;
        return ix >= left && ix < right && iy >= top && iy < bottom;
    }
}
