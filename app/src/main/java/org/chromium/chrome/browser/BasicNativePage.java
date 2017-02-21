// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.widget.FrameLayout.LayoutParams;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * A basic implementation of a white {@link NativePage} that docks below the toolbar.
 */
public abstract class BasicNativePage implements NativePage {

    private final Activity mActivity;
    private final Tab mTab;
    private final int mBackgroundColor;
    private final int mThemeColor;

    private String mUrl;

    public BasicNativePage(Activity activity, Tab tab) {
        initialize(activity, tab);
        mActivity = activity;
        mTab = tab;
        mBackgroundColor = ApiCompatibilityUtils.getColor(activity.getResources(),
                R.color.default_primary_color);
        mThemeColor = ApiCompatibilityUtils.getColor(
                activity.getResources(), R.color.default_primary_color);

        Resources res = mActivity.getResources();

        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        layoutParams.setMargins(0,
                res.getDimensionPixelSize(R.dimen.tab_strip_height)
                + res.getDimensionPixelSize(R.dimen.toolbar_height_no_shadow),
                0, 0);
        getView().setLayoutParams(layoutParams);
    }

    /**
     * Subclasses shall implement this method to initialize the UI that they hold.
     */
    protected abstract void initialize(Activity activity, Tab tab);

    @Override
    public abstract View getView();

    @Override
    public String getUrl() {
        return mUrl;
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
    public boolean needsToolbarShadow() {
        return true;
    }

    @Override
    public void updateForUrl(String url) {
        mUrl = url;
    }

    @Override
    public void destroy() { }

    /**
     * Tells the native page framework that the url should be changed.
     */
    public void onStateChange(String url) {
        if (url.equals(mUrl)) return;
        mTab.loadUrl(new LoadUrlParams(url));
    }
}
