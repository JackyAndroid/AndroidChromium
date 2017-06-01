// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.share;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

import java.util.List;

/**
 * Adapter that provides the list of activities via which a web page can be shared.
 */
class ShareDialogAdapter extends ArrayAdapter<ResolveInfo> {
    private final LayoutInflater mInflater;
    private final PackageManager mManager;

    /**
     * @param context Context used to for layout inflation.
     * @param manager PackageManager used to query for activity information.
     * @param objects The list of possible share intents.
     */
    public ShareDialogAdapter(Context context, PackageManager manager, List<ResolveInfo> objects) {
        super(context, R.layout.share_dialog_item, objects);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mManager = manager;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = mInflater.inflate(R.layout.share_dialog_item, parent, false);
        } else {
            view = convertView;
        }
        TextView text = (TextView) view.findViewById(R.id.text);
        ImageView icon = (ImageView) view.findViewById(R.id.icon);

        text.setText(getItem(position).loadLabel(mManager));
        icon.setImageDrawable(loadIconForResolveInfo(getItem(position)));
        return view;
    }

    private Drawable loadIconForResolveInfo(ResolveInfo info) {
        try {
            final int iconRes = info.getIconResource();
            if (iconRes != 0) {
                Resources res = mManager.getResourcesForApplication(info.activityInfo.packageName);
                Drawable icon = ApiCompatibilityUtils.getDrawable(res, iconRes);
                return icon;
            }
        } catch (NameNotFoundException | NotFoundException e) {
            // Could not find the icon. loadIcon call below will return the default app icon.
        }
        return info.loadIcon(mManager);
    }

}