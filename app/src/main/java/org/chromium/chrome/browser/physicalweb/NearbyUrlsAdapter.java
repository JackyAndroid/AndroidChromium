// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;

import java.util.HashMap;

/**
 * Adapter for displaying nearby URLs and associated metadata.
 */
class NearbyUrlsAdapter extends ArrayAdapter<PwsResult> {
    private final HashMap<String, Bitmap> mIconUrlToIconMap;

    /**
     * Construct an empty NearbyUrlsAdapter.
     */
    public NearbyUrlsAdapter(Context context) {
        super(context, 0);
        mIconUrlToIconMap = new HashMap<>();
    }

    /**
     * Update the favicon for a nearby URL.
     * @param iconUrl The icon URL as returned by PWS
     * @param icon The favicon to display
     */
    public void setIcon(String iconUrl, Bitmap icon) {
        if (iconUrl != null && icon != null) {
            mIconUrlToIconMap.put(iconUrl, icon);
            notifyDataSetChanged();
        }
    }

    /**
     * Clear the list, forgetting cached URL metadata and icons.
     */
    @Override
    public void clear() {
        super.clear();
        mIconUrlToIconMap.clear();
    }

    /**
     * Get the view for an item in the data set.
     * @param position Index of the list view item within the array.
     * @param view The old view to reuse, if possible.
     * @param viewGroup The parent that this view will eventually be attached to.
     * @return A view corresponding to the list view item.
     */
    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            view = inflater.inflate(R.layout.physical_web_list_item_nearby_url, viewGroup, false);
        }

        TextView titleTextView = (TextView) view.findViewById(R.id.nearby_urls_title);
        TextView urlTextView = (TextView) view.findViewById(R.id.nearby_urls_url);
        TextView descriptionTextView = (TextView) view.findViewById(R.id.nearby_urls_description);
        ImageView iconImageView = (ImageView) view.findViewById(R.id.nearby_urls_icon);

        PwsResult pwsResult = getItem(position);
        Bitmap iconBitmap = mIconUrlToIconMap.get(pwsResult.iconUrl);

        titleTextView.setText(pwsResult.title);
        urlTextView.setText(pwsResult.siteUrl);
        descriptionTextView.setText(pwsResult.description);
        iconImageView.setImageBitmap(iconBitmap);

        return view;
    }
}
