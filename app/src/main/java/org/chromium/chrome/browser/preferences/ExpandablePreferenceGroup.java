// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceGroup;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

import java.util.Locale;

/**
 * A preference category that accepts clicks for toggling on/off.
 */
public class ExpandablePreferenceGroup extends PreferenceGroup {
    private Drawable mDrawable;
    private ImageView mImageView;

    // Whether the PreferenceGroup is in an expanded or collapsed state.
    private boolean mExpanded;

    public ExpandablePreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.preferenceStyle);
        setWidgetLayoutResource(R.layout.site_list_expandable_header);
    }

    /**
     * Set the title for the preference group.
     * @param resourceId The resource id of the text to use.
     * @param count The number of entries the preference group contains.
     */
    public void setGroupTitle(int resourceId, int count) {
        SpannableStringBuilder spannable =
                new SpannableStringBuilder(getContext().getResources().getString(resourceId));
        String prefCount = String.format(Locale.getDefault(), " - %d", count);
        spannable.append(prefCount);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                       0,
                       spannable.length() - prefCount.length(),
                       Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            spannable.setSpan(new TypefaceSpan("sans-serif-medium"),
                       0,
                       spannable.length() - prefCount.length(),
                       Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Color the first part of the title blue.
        ForegroundColorSpan blueSpan = new ForegroundColorSpan(
                ApiCompatibilityUtils.getColor(getContext().getResources(),
                        R.color.pref_accent_color));
        spannable.setSpan(blueSpan, 0, spannable.length() - prefCount.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Gray out the total count of items.
        int gray = ApiCompatibilityUtils.getColor(getContext().getResources(),
                R.color.expandable_group_dark_gray);
        spannable.setSpan(new ForegroundColorSpan(gray),
                   spannable.length() - prefCount.length(),
                   spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        setTitle(spannable);
    }

    public void setExpanded(boolean expanded) {
        mExpanded = expanded;
    }

    @Override
    public void setIcon(Drawable drawable) {
        mDrawable = drawable;
        if (mImageView != null) mImageView.setImageDrawable(mDrawable);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mImageView = (ImageView) view.findViewById(R.id.expando);
        if (mDrawable != null) mImageView.setImageDrawable(mDrawable);

        // For accessibility, read out the whole title and whether the group is collapsed/expanded.
        String description = getTitle() + getContext().getResources().getString(mExpanded
                ? R.string.accessibility_expanded_group
                : R.string.accessibility_collapsed_group);
        view.setContentDescription(description);
    }
}
