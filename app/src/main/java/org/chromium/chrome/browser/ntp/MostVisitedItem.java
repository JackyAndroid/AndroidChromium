// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;

/**
 * Displays the title, thumbnail, and favicon of a most visited page. The item can be clicked, or
 * long-pressed to trigger a context menu with options to "open in new tab", "open in incognito
 * tab", or "remove".
 */
public class MostVisitedItem implements OnCreateContextMenuListener,
        MenuItem.OnMenuItemClickListener, OnClickListener {

    /**
     * Interface for an object that handles callbacks from a MostVisitedItem.
     */
    public interface MostVisitedItemManager {
        /**
         * Navigates to a most visited page in the existing tab.
         * @param item The most visited item to open.
         */
        void open(MostVisitedItem item);

        /**
         * Allows the manager to add context menu items for a given MostVisitedItem.
         * @param menu The context menu that should be used to add menu items.
         * @param listener Listener that should get the callbacks for context menu selections.
         */
        void onCreateContextMenu(ContextMenu menu, OnMenuItemClickListener listener);

        /**
         * Handles context menu item clicks.
         * @param menuId Id of the menu item that was selected.
         * @param item MostVisitedItem that triggered the context menu.
         * @return Whether a menu item was selected successfully.
         */
        boolean onMenuItemClick(int menuId, MostVisitedItem item);
    }

    private MostVisitedItemManager mManager;
    private String mTitle;
    private String mUrl;
    private int mIndex;
    private int mTileType;
    private View mView;

    /**
     * Constructs a MostVisitedItem with the given manager, title, URL, index, and view.
     *
     * @param manager The NewTabPageManager used to handle clicks and context menu events.
     * @param title The title of the page.
     * @param url The URL of the page.
     * @param index The index of this item in the list of most visited items.
     */
    public MostVisitedItem(MostVisitedItemManager manager, String title, String url, int index) {
        mManager = manager;
        mTitle = title;
        mUrl = url;
        mIndex = index;
        mTileType = MostVisitedTileType.NONE;
    }

    /**
     * Sets the view that will display this item. MostVisitedItem will handle clicks on the view.
     * This should be called exactly once.
     */
    public void initView(View view) {
        assert mView == null;
        mView = view;
        mView.setOnClickListener(this);
        mView.setOnCreateContextMenuListener(this);
    }

    /**
     * @return The view representing this item.
     */
    public View getView() {
        return mView;
    }

    /**
     * @return The URL of this most visited item.
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * @return The title of this most visited item.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * @return The index of this MostVisitedItem in the list of MostVisitedItems.
     */
    public int getIndex() {
        return mIndex;
    }

    /**
     * Updates this item's index in the list of most visited items.
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * @return The visual type of this most visited item. Valid values are listed in
     *         {@link MostVisitedTileType}.
     */
    public int getTileType() {
        return mTileType;
    }

    /**
     * Sets the visual type of this most visited item. Valid values are listed in
     * {@link MostVisitedTileType}.
     */
    public void setTileType(int type) {
        mTileType = type;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        mManager.onCreateContextMenu(menu, this);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return mManager.onMenuItemClick(item.getItemId(), this);
    }

    @Override
    public void onClick(View v) {
        mManager.open(this);
    }
}
