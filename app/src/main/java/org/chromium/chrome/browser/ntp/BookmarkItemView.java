// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.AppCompatTextView;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.BookmarksPageView.BookmarksPageManager;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.components.bookmarks.BookmarkId;

/**
 * Displays the bookmark item along with the favicon. This item can be clicked to be taken to that
 * page.
 */
class BookmarkItemView extends AppCompatTextView implements OnCreateContextMenuListener,
        MenuItem.OnMenuItemClickListener, OnClickListener {

    // Context menu item IDs.
    static final int ID_OPEN_IN_NEW_TAB = 0;
    static final int ID_OPEN_IN_INCOGNITO_TAB = 1;
    static final int ID_DELETE = 2;
    static final int ID_EDIT = 3;

    /**
     * Drawing-related values that can be shared between instances of BookmarkItem.
     */
    static final class DrawingData {

        private final int mPadding;
        private final int mMinHeight;
        private final int mFaviconSize;
        private final int mFaviconContainerSize;
        private final int mTextSize;
        private final int mTextColor;

        /**
         * Initialize shared values used for drawing the favicon, borders and shadows.
         * @param context The view context in which the BookmarkItem will be drawn.
         */
        DrawingData(Context context) {
            Resources res = context.getResources();
            mPadding = res.getDimensionPixelOffset(R.dimen.ntp_list_item_padding);
            mMinHeight = res.getDimensionPixelSize(R.dimen.ntp_list_item_min_height);
            mFaviconSize = res.getDimensionPixelSize(R.dimen.default_favicon_size);
            mFaviconContainerSize = res.getDimensionPixelSize(
                    R.dimen.ntp_list_item_favicon_container_size);
            mTextSize = res.getDimensionPixelSize(R.dimen.ntp_list_item_text_size);
            mTextColor = ApiCompatibilityUtils.getColor(res, R.color.ntp_list_item_text);
        }
    }

    private final BookmarksPageManager mManager;
    private final DrawingData mDrawingData;
    private String mTitle;
    private String mUrl;
    private BookmarkId mId;
    private boolean mIsFolder;
    private boolean mIsEditable;
    private boolean mIsManaged;
    private Bitmap mFavicon;

    /**
     * @param context The view context in which this item will be shown.
     * @param manager The BookmarksPageManager used to handle clicks.
     * @param id The id of the bookmark item.
     * @param title The title of the page.
     * @param url The URL of the page.
     * @param isEditable Whether this bookmark item can be edited.
     * @param isManaged Whether this is a managed bookmark.
     */
    @SuppressLint("InlinedApi")
    BookmarkItemView(Context context, BookmarksPageManager manager, BookmarkId id, String title,
            String url, boolean isEditable, boolean isManaged, DrawingData drawingData) {
        super(context);
        mManager = manager;
        mDrawingData = drawingData;

        setTextColor(mDrawingData.mTextColor);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, mDrawingData.mTextSize);
        setMinimumHeight(mDrawingData.mMinHeight);
        setGravity(Gravity.CENTER_VERTICAL);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.END);
        ApiCompatibilityUtils.setTextAlignment(this, View.TEXT_ALIGNMENT_VIEW_START);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                new int[] { R.attr.listChoiceBackgroundIndicator });
        Drawable background = a.getDrawable(0);
        a.recycle();
        setBackground(background);

        setOnClickListener(this);
        setOnCreateContextMenuListener(this);

        reset(id, title, url, isEditable, isManaged);
    }

    /**
     * Resets the view contents so that it can be reused in the listview.
     * @param id The id of the bookmark item.
     * @param title The title of the page.
     * @param url The URL of the page.
     * @param isEditable Whether this bookmark item can be edited.
     * @param isManaged Whether this is a managed bookmark.
     * @return boolean Whether the values were reset needing a favicon refetch.
     */
    public boolean reset(BookmarkId id, String title, String url, boolean isEditable,
            boolean isManaged) {
        // Reset drawable state so ripples don't continue when the view is reused.
        jumpDrawablesToCurrentState();

        if (mId != null && mId.equals(id) && TextUtils.equals(title, mTitle)
                && TextUtils.equals(url, mUrl) && isEditable == mIsEditable
                && isManaged == mIsManaged) {
            return false;
        }
        mTitle = title;
        mUrl = url;
        mIsFolder = TextUtils.isEmpty(mUrl);
        mIsEditable = isEditable;
        mIsManaged = isManaged;
        mId = id;
        setText(mTitle);
        setFavicon(null);
        if (mIsFolder) {
            setContentDescription(getResources().getString(
                    R.string.accessibility_bookmark_folder, mTitle));
        }
        return true;
    }

    /** @return The URL of this bookmark item. */
    public String getUrl() {
        return mUrl;
    }

    /** @return The title of this bookmark item. */
    public String getTitle() {
        return mTitle;
    }

    /** @return Whether the BookmarkItem is a folder. */
    public boolean isFolder() {
        return mIsFolder;
    }

    /** @return The bookmark/folder id. */
    public BookmarkId getBookmarkId() {
        return mId;
    }

    /** @return The favicon of this bookmark item. */
    public Bitmap getFavicon() {
        return mFavicon;
    }

    /**
     * Updates the favicon and triggers a redraw with the new favicon
     * @param favicon The new favicon to display. May be null.
     */
    void setFavicon(Bitmap favicon) {
        int padding = mDrawingData.mPadding;
        int startPadding = padding;
        int drawablePadding = mDrawingData.mPadding;
        Drawable faviconDrawable = null;
        mFavicon = favicon;
        if (favicon != null || mIsFolder) {
            int iconSize;
            if (mIsFolder) {
                faviconDrawable = TintedDrawable.constructTintedDrawable(getResources(),
                        mIsManaged ? R.drawable.eb_managed : R.drawable.eb_folder);
                iconSize = mDrawingData.mFaviconContainerSize;
            } else {
                faviconDrawable = new BitmapDrawable(getResources(), favicon);
                iconSize = mDrawingData.mFaviconSize;
                startPadding += (mDrawingData.mFaviconContainerSize - iconSize) / 2;
                drawablePadding += (mDrawingData.mFaviconContainerSize - iconSize + 1) / 2;
            }
            faviconDrawable.setBounds(0, 0, iconSize, iconSize);
            setCompoundDrawablePadding(drawablePadding);
        } else {
            startPadding = 2 * padding + mDrawingData.mFaviconContainerSize;
        }
        ApiCompatibilityUtils.setPaddingRelative(this, startPadding, 0, padding, 0);
        ApiCompatibilityUtils.setCompoundDrawablesRelative(this, faviconDrawable, null, null, null);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (mManager.isDestroyed()) return;
        if (!mManager.isContextMenuEnabled()) return;
        if (!mIsFolder && mManager.shouldShowOpenInNewTab()) {
            menu.add(Menu.NONE, ID_OPEN_IN_NEW_TAB, Menu.NONE,
                    R.string.contextmenu_open_in_new_tab).setOnMenuItemClickListener(this);
        }
        if (!mIsFolder && mManager.shouldShowOpenInNewIncognitoTab()) {
            menu.add(Menu.NONE, ID_OPEN_IN_INCOGNITO_TAB, Menu.NONE,
                     R.string.contextmenu_open_in_incognito_tab).setOnMenuItemClickListener(this);
        }
        if (mIsEditable && !mManager.isIncognito()) {
            menu.add(Menu.NONE, ID_EDIT, Menu.NONE,
                    mIsFolder ? R.string.contextmenu_edit_folder : R.string.edit_bookmark)
                    .setOnMenuItemClickListener(this);
            menu.add(Menu.NONE, ID_DELETE, Menu.NONE, mIsFolder
                    ? R.string.contextmenu_delete_folder : R.string.contextmenu_delete_bookmark)
                            .setOnMenuItemClickListener(this);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (mManager.isDestroyed()) return true;
        switch (item.getItemId()) {
            case ID_OPEN_IN_NEW_TAB:
                mManager.openInNewTab(this);
                return true;
            case ID_OPEN_IN_INCOGNITO_TAB:
                mManager.openInNewIncognitoTab(this);
                return true;
            case ID_DELETE:
                mManager.delete(this);
                return true;
            case ID_EDIT:
                mManager.edit(this);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onClick(View v) {
        if (mManager.isDestroyed()) return;
        mManager.open(this);
    }
}
