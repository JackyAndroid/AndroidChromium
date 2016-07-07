// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.AppCompatTextView;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.BookmarksPageView.BookmarksPageManager;
import org.chromium.components.bookmarks.BookmarkId;

/**
 * Displays the folder hierarchy item. This item can be clicked to be taken to that folder's
 * contents.
 */
public class BookmarkFolderHierarchyItem extends AppCompatTextView implements OnClickListener  {

    private static final int PADDING_DP = 5;

    private final BookmarksPageManager mManager;
    private final String mTitle;
    private final BookmarkId mId;

    BookmarkFolderHierarchyItem(Context context, BookmarksPageManager manager, BookmarkId id,
            String title, boolean isCurrentFolder) {
        super(context);

        mManager = manager;
        mTitle = title;
        mId = id;
        if (!isCurrentFolder) setOnClickListener(this);
        setText(mTitle);
        float density = getResources().getDisplayMetrics().density;
        int horizontalPadding = Math.round(PADDING_DP * density);
        setMinHeight(Math.round(getResources().getDimension(R.dimen.bookmark_folder_min_height)));
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.bookmark_folder_text_size));
        int textColorId = isCurrentFolder ? R.color.light_active_color
                : R.color.ntp_list_header_subtext;
        setTextColor(ApiCompatibilityUtils.getColor(getResources(), textColorId));
        setGravity(Gravity.CENTER_VERTICAL);

        TypedArray a = context.getTheme().obtainStyledAttributes(new int[] {
                R.attr.listChoiceBackgroundIndicator });
        Drawable background = a.getDrawable(0);
        a.recycle();
        setBackground(background);
        setPadding(horizontalPadding, 0, horizontalPadding, 0);
    }

    @Override
    public void onClick(View v) {
        mManager.openFolder(this);
    }

    /**
     * @return The folder id.
     */
    public BookmarkId getFolderId() {
        return mId;
    }
}
