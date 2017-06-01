// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;

import org.chromium.chrome.browser.ntp.ContextMenuManager.ContextMenuItemId;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.ui.mojom.WindowOpenDisposition;

/**
 * Displays the title, thumbnail, and favicon of a most visited page. The item can be clicked, or
 * long-pressed to trigger a context menu with options to "open in new tab", "open in incognito
 * tab", or "remove".
 */
public class MostVisitedItem implements OnCreateContextMenuListener, OnClickListener {
    /**
     * Interface for an object that handles callbacks from a MostVisitedItem.
     */
    public interface MostVisitedItemManager {
        void removeMostVisitedItem(MostVisitedItem item);

        void openMostVisitedItem(int windowDisposition, MostVisitedItem item);
    }

    private NewTabPageManager mManager;
    private String mTitle;
    private String mUrl;
    private String mWhitelistIconPath;
    private boolean mOfflineAvailable;
    private int mIndex;
    private int mTileType;
    private int mSource;
    private View mView;

    /**
     * Constructs a MostVisitedItem with the given manager, title, URL, whitelist icon path, index,
     * and view.
     *
     * @param manager The NewTabPageManager used to handle clicks and context menu events.
     * @param title The title of the page.
     * @param url The URL of the page.
     * @param whitelistIconPath The path to the icon image file, if this is a whitelisted most
     *                          visited item. Empty otherwise.
     * @param offlineAvailable Whether there is an offline copy of the URL available.
     * @param index The index of this item in the list of most visited items.
     * @param source The {@code MostVisitedSource} that generated this item.
     */
    public MostVisitedItem(NewTabPageManager manager, String title, String url,
            String whitelistIconPath, boolean offlineAvailable, int index, int source) {
        mManager = manager;
        mTitle = title;
        mUrl = url;
        mWhitelistIconPath = whitelistIconPath;
        mOfflineAvailable = offlineAvailable;
        mIndex = index;
        mTileType = MostVisitedTileType.NONE;
        mSource = source;
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
     * @return The path of the whitelist icon associated with the URL.
     */
    public String getWhitelistIconPath() {
        return mWhitelistIconPath;
    }

    /**
     * @return Whether this item is available offline.
     */
    public boolean isOfflineAvailable() {
        return mOfflineAvailable;
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

    /**
     * @return The source of this item.  Used for metrics tracking. Valid values are listed in
     * {@code MostVisitedSource}.
     */
    public int getSource() {
        return mSource;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        mManager.getContextMenuManager().createContextMenu(
                menu, v, new ContextMenuManager.Delegate() {
                    @Override
                    public void openItem(int windowDisposition) {
                        mManager.openMostVisitedItem(windowDisposition, MostVisitedItem.this);
                    }

                    @Override
                    public void removeItem() {
                        mManager.removeMostVisitedItem(MostVisitedItem.this);
                    }

                    @Override
                    public String getUrl() {
                        return MostVisitedItem.this.getUrl();
                    }

                    @Override
                    public boolean isItemSupported(@ContextMenuItemId int menuItemId) {
                        return true;
                    }
                });
    }

    @Override
    public void onClick(View v) {
        mManager.openMostVisitedItem(WindowOpenDisposition.CURRENT_TAB, MostVisitedItem.this);
    }
}
