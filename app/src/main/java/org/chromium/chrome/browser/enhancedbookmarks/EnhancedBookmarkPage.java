// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.enhancedbookmarks.EnhancedBookmarkDelegate.EnhancedBookmarkStateChangeListener;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * A native page holding a {@link EnhancedBookmarkManager} on _tablet_.
 */
public class EnhancedBookmarkPage implements NativePage, EnhancedBookmarkStateChangeListener {
    private final Activity mActivity;
    private final Tab mTab;
    private final String mTitle;
    private final int mBackgroundColor;
    private final int mThemeColor;
    private EnhancedBookmarkManager mManager;

    /**
     * Create a new instance of an enhanced bookmark page.
     * @param activity The activity to get context and manage fragments.
     * @param tab The tab to load urls.
     * @return Null if this method is called on phone. Otherwise, return a new enhanced bookmark
     *         native page for tablet.
     */
    public static EnhancedBookmarkPage buildPage(Activity activity, Tab tab) {
        if (DeviceFormFactor.isTablet(activity)) return new EnhancedBookmarkPage(activity, tab);
        else return null;
    }

    private EnhancedBookmarkPage(Activity activity, Tab tab) {
        mActivity = activity;
        mTab = tab;
        mTitle = activity.getString(OfflinePageBridge.isEnabled()
                ? R.string.offline_pages_saved_pages : R.string.bookmarks);
        mBackgroundColor = ApiCompatibilityUtils.getColor(activity.getResources(),
                R.color.default_primary_color);
        mThemeColor = ApiCompatibilityUtils.getColor(
                activity.getResources(), R.color.default_primary_color);

        mManager = new EnhancedBookmarkManager(mActivity);
        Resources res = mActivity.getResources();

        MarginLayoutParams layoutParams = new MarginLayoutParams(
                MarginLayoutParams.MATCH_PARENT, MarginLayoutParams.MATCH_PARENT);
        layoutParams.setMargins(0,
                res.getDimensionPixelSize(R.dimen.tab_strip_height)
                + res.getDimensionPixelSize(R.dimen.toolbar_height_no_shadow),
                0, 0);
        mManager.getView().setLayoutParams(layoutParams);
        mManager.setUrlChangeListener(this);
    }

    @Override
    public View getView() {
        return mManager.getView();
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getUrl() {
        return mManager.getCurrentUrl();
    }

    @Override
    public String getHost() {
        return UrlConstants.BOOKMARKS_HOST;
    }

    @Override
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public int getThemeColor() {
        return mThemeColor;
    }

    @Override
    public void updateForUrl(String url) {
        mManager.updateForUrl(url);
    }

    @Override
    public void destroy() {
        mManager.destroy();
        mManager = null;
    }

    @Override
    public void onBookmarkUIStateChange(String url) {
        mTab.loadUrl(new LoadUrlParams(url));
    }
}
