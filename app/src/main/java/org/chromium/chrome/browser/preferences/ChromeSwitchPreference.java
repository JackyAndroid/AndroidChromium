// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.preference.SwitchPreference;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.R;

/**
 * A super-powered SwitchPreference designed especially for Chrome. Special features:
 *  - Supports managed preferences
 *  - Displays a material-styled switch, even on pre-L devices
 */
public class ChromeSwitchPreference extends SwitchPreference {

    private ManagedPreferenceDelegate mManagedPrefDelegate;

    private boolean mDontUseSummaryAsTitle;
    private boolean mDrawDivider;

    /**
     * Constructor for inflating from XML.
     */
    public ChromeSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_switch);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ChromeSwitchPreference);
        mDontUseSummaryAsTitle =
                a.getBoolean(R.styleable.ChromeSwitchPreference_dontUseSummaryAsTitle, false);
        mDrawDivider = a.getBoolean(R.styleable.ChromeSwitchPreference_drawDivider, false);
        a.recycle();
    }

    /**
     * Sets the ManagedPreferenceDelegate which will determine whether this preference is managed.
     */
    public void setManagedPreferenceDelegate(ManagedPreferenceDelegate delegate) {
        mManagedPrefDelegate = delegate;
        if (mManagedPrefDelegate != null) mManagedPrefDelegate.initPreference(this);
    }

    /**
     * Sets whether a horizontal divider line should be drawn at the bottom of this preference.
     */
    public void setDrawDivider(boolean drawDivider) {
        if (mDrawDivider != drawDivider) {
            mDrawDivider = drawDivider;
            notifyChanged();
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        if (mDrawDivider) {
            int left = view.getPaddingLeft();
            int right = view.getPaddingRight();
            int top = view.getPaddingTop();
            int bottom = view.getPaddingBottom();
            view.setBackground(DividerDrawable.create(getContext()));
            view.setPadding(left, top, right, bottom);
        }

        SwitchCompat switchView = (SwitchCompat) view.findViewById(R.id.switch_widget);
        // On BLU Life Play devices SwitchPreference.setWidgetLayoutResource() does nothing. As a
        // result, the user will see a non-material Switch and switchView will be null, hence the
        // null check below. http://crbug.com/451447
        if (switchView != null) {
            switchView.setChecked(isChecked());
        }

        TextView title = (TextView) view.findViewById(android.R.id.title);
        title.setSingleLine(false);
        if (!mDontUseSummaryAsTitle && TextUtils.isEmpty(getTitle())) {
            TextView summary = (TextView) view.findViewById(android.R.id.summary);
            title.setText(summary.getText());
            title.setVisibility(View.VISIBLE);
            summary.setVisibility(View.GONE);
        }

        if (mManagedPrefDelegate != null) mManagedPrefDelegate.onBindViewToPreference(this, view);
    }

    @Override
    protected void onClick() {
        if (mManagedPrefDelegate != null && mManagedPrefDelegate.onClickPreference(this)) return;
        super.onClick();
    }

    /**
     * Draws a horizontal list divider line at the bottom of its drawing area.
     *
     * Because ?android:attr/listDivider may be a 9-patch, there's no way to achieve this drawing
     * effect with the platform Drawable classes; hence this custom Drawable.
     */
    private static class DividerDrawable extends LayerDrawable {

        static DividerDrawable create(Context context) {
            TypedArray a = context.obtainStyledAttributes(new int[] { android.R.attr.listDivider });
            Drawable listDivider = a.getDrawable(0);
            a.recycle();
            return new DividerDrawable(new Drawable[] { listDivider });
        }

        private DividerDrawable(Drawable[] layers) {
            super(layers);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            int listDividerHeight = getDrawable(0).getIntrinsicHeight();
            setLayerInset(0, 0, bounds.height() - listDividerHeight, 0, 0);
            super.onBoundsChange(bounds);
        }
    }
}
