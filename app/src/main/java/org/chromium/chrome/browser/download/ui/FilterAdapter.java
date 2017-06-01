// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi.DownloadUiObserver;
import org.chromium.chrome.browser.widget.TintedDrawable;

/** An adapter that allows selecting an item from a list displayed in the drawer. */
class FilterAdapter extends BaseAdapter
        implements AdapterView.OnItemClickListener, DownloadUiObserver {

    private int mSelectedBackgroundColor;
    private DownloadManagerUi mManagerUi;
    private int mSelectedIndex;

    @Override
    public int getCount() {
        return DownloadFilter.getFilterCount();
    }

    @Override
    public Object getItem(int position) {
        return DownloadFilter.FILTER_LIST[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Resources resources = mManagerUi.getActivity().getResources();

        TextView labelView = null;
        if (convertView instanceof TextView) {
            labelView = (TextView) convertView;
        } else {
            labelView = (TextView) LayoutInflater.from(mManagerUi.getActivity()).inflate(
                    R.layout.download_manager_ui_drawer_filter, null);
        }

        int iconId = DownloadFilter.getDrawableForFilter(position);
        labelView.setText(DownloadFilter.getStringIdForFilter(position));

        Drawable iconDrawable = null;
        if (position == mSelectedIndex) {
            // Highlight the selected item by changing the foreground and background colors.
            labelView.setBackgroundColor(mSelectedBackgroundColor);
            iconDrawable = TintedDrawable.constructTintedDrawable(
                    resources, iconId, R.color.light_active_color);
            labelView.setTextColor(
                    ApiCompatibilityUtils.getColor(resources, R.color.light_active_color));
        } else {
            // Draw the item normally.
            labelView.setBackground(null);
            iconDrawable = TintedDrawable.constructTintedDrawable(
                    resources, iconId, R.color.descriptive_text_color);
            labelView.setTextColor(
                    ApiCompatibilityUtils.getColor(resources, R.color.default_text_color));
        }

        labelView.setCompoundDrawablesWithIntrinsicBounds(iconDrawable, null, null, null);
        return labelView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mManagerUi.onFilterChanged(position);
    }

    public void initialize(DownloadManagerUi manager) {
        mManagerUi = manager;
        mSelectedBackgroundColor = ApiCompatibilityUtils
                .getColor(mManagerUi.getActivity().getResources(), R.color.default_primary_color);
    }

    @Override
    public void onFilterChanged(int filter) {
        if (mSelectedIndex == filter) return;
        mSelectedIndex = filter;
        notifyDataSetChanged();
        mManagerUi.closeDrawer();
    }

    @Override
    public void onManagerDestroyed() {
        mManagerUi = null;
    }
}
