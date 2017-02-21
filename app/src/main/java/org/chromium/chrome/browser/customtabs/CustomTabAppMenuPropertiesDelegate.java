// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.Menu;
import android.view.MenuItem;

import org.chromium.base.BuildInfo;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.tab.Tab;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * App menu properties delegate for {@link CustomTabActivity}.
 */
public class CustomTabAppMenuPropertiesDelegate extends AppMenuPropertiesDelegate {
    private static final String SAMPLE_URL = "https://www.google.com";

    private final boolean mShowShare;
    private final boolean mIsMediaViewer;

    private final List<String> mMenuEntries;
    private final Map<MenuItem, Integer> mItemToIndexMap = new HashMap<MenuItem, Integer>();
    private final AsyncTask<Void, Void, String> mDefaultBrowserFetcher;

    private boolean mIsCustomEntryAdded;

    /**
     * Creates an {@link CustomTabAppMenuPropertiesDelegate} instance.
     */
    public CustomTabAppMenuPropertiesDelegate(final ChromeActivity activity,
            List<String> menuEntries, boolean showShare, final boolean isOpenedByChrome,
            final boolean isMediaViewer) {
        super(activity);
        mMenuEntries = menuEntries;
        mShowShare = showShare;
        mIsMediaViewer = isMediaViewer;

        mDefaultBrowserFetcher = new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String packageLabel = null;
                if (isOpenedByChrome) {
                    // If the Custom Tab was created by Chrome, Chrome should open it.
                    packageLabel = BuildInfo.getPackageLabel(activity);
                } else {
                    // Check if there is a default handler for the Intent.  If so, grab its label.
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SAMPLE_URL));
                    PackageManager pm = activity.getPackageManager();
                    ResolveInfo info = pm.resolveActivity(intent, 0);
                    if (info != null && info.match != 0) {
                        packageLabel = info.loadLabel(pm).toString();
                    }
                }

                return packageLabel == null
                        ? activity.getString(R.string.menu_open_in_product_default)
                        : activity.getString(R.string.menu_open_in_product, packageLabel);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void prepareMenu(Menu menu) {
        Tab currentTab = mActivity.getActivityTab();
        if (currentTab != null) {
            MenuItem forwardMenuItem = menu.findItem(R.id.forward_menu_id);
            forwardMenuItem.setEnabled(currentTab.canGoForward());

            mReloadMenuItem = menu.findItem(R.id.reload_menu_id);
            mReloadMenuItem.setIcon(R.drawable.btn_reload_stop);
            loadingStateChanged(currentTab.isLoading());

            MenuItem shareItem = menu.findItem(R.id.share_row_menu_id);
            shareItem.setVisible(mShowShare);
            shareItem.setEnabled(mShowShare);
            if (mShowShare) {
                ShareHelper.configureDirectShareMenuItem(
                        mActivity, menu.findItem(R.id.direct_share_menu_id));
            }

            MenuItem iconRow = menu.findItem(R.id.icon_row_menu_id);
            MenuItem openInChromeItem = menu.findItem(R.id.open_in_browser_id);
            if (mIsMediaViewer) {
                // Most of the menu items don't make sense when viewing media.
                iconRow.setVisible(false);
                openInChromeItem.setVisible(false);
            } else {
                try {
                    openInChromeItem.setTitle(mDefaultBrowserFetcher.get());
                } catch (InterruptedException | ExecutionException e) {
                    openInChromeItem.setTitle(
                            mActivity.getString(R.string.menu_open_in_product_default));
                }
            }

            // Add custom menu items. Make sure they are only added once.
            if (!mIsCustomEntryAdded) {
                mIsCustomEntryAdded = true;
                for (int i = 0; i < mMenuEntries.size(); i++) {
                    MenuItem item = menu.add(0, 0, 1, mMenuEntries.get(i));
                    mItemToIndexMap.put(item, i);
                }
            }
        }
    }

    /**
     * @return The index that the given menu item should appear in the result of
     *         {@link CustomTabIntentDataProvider#getMenuTitles()}. Returns -1 if item not found.
     */
    public int getIndexOfMenuItem(MenuItem menuItem) {
        if (!mItemToIndexMap.containsKey(menuItem)) {
            return -1;
        }
        return mItemToIndexMap.get(menuItem).intValue();
    }

    @Override
    public int getFooterResourceId() {
        return mIsMediaViewer ? 0 : R.layout.powered_by_chrome_footer;
    }

    /**
     * Get the {@link MenuItem} object associated with the given title. If multiple menu items have
     * the same title, a random one will be returned. This method is for testing purpose _only_.
     */
    @VisibleForTesting
    MenuItem getMenuItemForTitle(String title) {
        for (MenuItem item : mItemToIndexMap.keySet()) {
            if (item.getTitle().equals(title)) return item;
        }
        return null;
    }
}
