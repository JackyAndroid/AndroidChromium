// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.LayoutRenderHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.widget.accessibility.AccessibilityTabModelAdapter.AccessibilityTabModelAdapterListener;
import org.chromium.chrome.browser.widget.accessibility.AccessibilityTabModelWrapper;

/**
 * A {@link Layout} that shows the tabs as two {@link ListView}s, one for each {@link TabModel} to
 * represent.
 */
public class OverviewListLayout extends Layout implements AccessibilityTabModelAdapterListener {
    private AccessibilityTabModelWrapper mTabModelWrapper;
    private final float mDpToPx;
    private final SceneLayer mSceneLayer;

    public OverviewListLayout(Context context, LayoutUpdateHost updateHost,
            LayoutRenderHost renderHost, EventFilter eventFilter) {
        super(context, updateHost, renderHost, eventFilter);
        mDpToPx = context.getResources().getDisplayMetrics().density;
        mSceneLayer = new SceneLayer();
    }

    @Override
    public void attachViews(ViewGroup container) {
        if (mTabModelWrapper == null) {
            mTabModelWrapper =
                    (AccessibilityTabModelWrapper) LayoutInflater.from(container.getContext())
                            .inflate(R.layout.accessibility_tab_switcher, null);
            mTabModelWrapper.setup(this);
            mTabModelWrapper.setTabModelSelector(mTabModelSelector);
            adjustForFullscreen();
        }

        if (container == null) return;

        if (mTabModelWrapper.getParent() == null) {
            container.addView(mTabModelWrapper);
        }
    }

    @Override
    public int getSizingFlags() {
        return SizingFlags.REQUIRE_FULLSCREEN_SIZE;
    }

    @Override
    protected void notifySizeChanged(float width, float height, int orientation) {
        adjustForFullscreen();
    }

    private void adjustForFullscreen() {
        if (mTabModelWrapper == null) return;
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) mTabModelWrapper.getLayoutParams();
        if (params == null) return;
        params.topMargin = (int) ((getHeight() - getHeightMinusTopControls()) * mDpToPx);
        mTabModelWrapper.setLayoutParams(params);
    }

    @Override
    public boolean handlesTabClosing() {
        return true;
    }

    @Override
    public boolean handlesTabCreating() {
        return true;
    }

    @Override
    public boolean handlesCloseAll() {
        return true;
    }

    @Override
    public void onTabCreating(int sourceTabId) {
        super.onTabCreating(sourceTabId);
        startHiding(sourceTabId, false);
    }

    @Override
    public void onTabCreated(long time, int tabId, int tabIndex, int sourceTabId,
            boolean newIsIncognito, boolean background, float originX, float originY) {
        super.onTabCreated(
                time, tabId, tabIndex, sourceTabId, newIsIncognito, background, originX, originY);
        startHiding(tabId, false);
    }

    @Override
    public void onTabModelSwitched(boolean incognito) {
        super.onTabModelSwitched(incognito);
        if (mTabModelWrapper == null) return;
        mTabModelWrapper.setStateBasedOnModel();
    }

    @Override
    public void show(long time, boolean animate) {
        super.show(time, animate);
        if (mTabModelWrapper == null) return;
        mTabModelWrapper.setStateBasedOnModel();

        doneShowing();
    }

    @Override
    public void startHiding(int nextId, boolean hintAtTabSelection) {
        super.startHiding(nextId, hintAtTabSelection);

        doneHiding();
    }

    @Override
    public void detachViews() {
        if (mTabModelSelector != null) mTabModelSelector.commitAllTabClosures();
        if (mTabModelWrapper != null) {
            ViewGroup parent = (ViewGroup) mTabModelWrapper.getParent();
            if (parent != null) parent.removeView(mTabModelWrapper);
        }
    }

    @Override
    public void setTabModelSelector(
            TabModelSelector tabModelSelector, TabContentManager tabContentManager) {
        super.setTabModelSelector(tabModelSelector, tabContentManager);

        if (mTabModelWrapper == null) return;
        mTabModelWrapper.setTabModelSelector(tabModelSelector);
    }

    @VisibleForTesting
    public ViewGroup getContainer() {
        return mTabModelWrapper;
    }

    @Override
    public void onTabsAllClosing(long time, boolean incognito) {
        super.onTabsAllClosing(time, incognito);

        TabModel model = mTabModelSelector.getModel(incognito);
        while (model.getCount() > 0) TabModelUtils.closeTabByIndex(model, 0);

        if (incognito) {
            mTabModelSelector.selectModel(!incognito);
        }
        if (mTabModelWrapper == null) return;
        mTabModelWrapper.setStateBasedOnModel();
    }

    @Override
    public void onTabClosureCommitted(long time, int tabId, boolean incognito) {
        mTabModelWrapper.setStateBasedOnModel();
    }

    @Override
    public void showTab(int tabId) {
        onTabSelecting(0, tabId);
    }

    @Override
    protected SceneLayer getSceneLayer() {
        return mSceneLayer;
    }
}
