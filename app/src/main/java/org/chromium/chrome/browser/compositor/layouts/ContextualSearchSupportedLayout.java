// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelHost;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.resources.ResourceManager;

import java.util.List;

/**
 * A {@link Layout} that can show a Contextual Search overlay that shows at the
 * bottom and can be swiped upwards.
 * TODO(mdjones): Rename this class to OverlayPanelSupportedLayout.
 */
public abstract class ContextualSearchSupportedLayout extends Layout {
    /**
     * The {@link OverlayPanelHost} that allows the {@link OverlayPanel} to
     * communicate back to the Layout.
     */
    protected final OverlayPanelHost mOverlayPanelHost;

    /**
     * The {@link OverlayPanel} that represents the Contextual Search UI.
     */
    protected final OverlayPanel mSearchPanel;

    /**
     * Size of half pixel in dps.
     */
    private final float mHalfPixelDp;

    /**
     * @param context The current Android context.
     * @param updateHost The {@link LayoutUpdateHost} view for this layout.
     * @param renderHost The {@link LayoutRenderHost} view for this layout.
     * @param eventFilter The {@link EventFilter} that is needed for this view.
     * @param panel The {@link OverlayPanel} that represents the Contextual Search UI.
     */
    public ContextualSearchSupportedLayout(Context context, LayoutUpdateHost updateHost,
            LayoutRenderHost renderHost, EventFilter eventFilter, OverlayPanel panel) {
        super(context, updateHost, renderHost, eventFilter);

        mOverlayPanelHost = new OverlayPanelHost() {
            @Override
            public void hideLayout(boolean immediately) {
                ContextualSearchSupportedLayout.this.hideContextualSearch(immediately);
            }
        };

        mSearchPanel = panel;
        float dpToPx = context.getResources().getDisplayMetrics().density;
        mHalfPixelDp = 0.5f / dpToPx;
    }

    @Override
    public void attachViews(ViewGroup container) {
        mSearchPanel.setContainerView(container);
    }

    @Override
    public void getAllViews(List<View> views) {
        // TODO(dtrainor): If we move ContextualSearch to an overlay, pull the views from there
        // instead in Layout.java.
        if (mSearchPanel != null) {
            ContentViewCore content = mSearchPanel.getContentViewCore();
            if (content != null) views.add(content.getContainerView());
        }
        super.getAllViews(views);
    }

    @Override
    public void getAllContentViewCores(List<ContentViewCore> contents) {
        // TODO(dtrainor): If we move ContextualSearch to an overlay, pull the content from there
        // instead in Layout.java.
        if (mSearchPanel != null) {
            ContentViewCore content =
                    mSearchPanel.getContentViewCore();
            if (content != null) contents.add(content);
        }
        super.getAllContentViewCores(contents);
    }

    @Override
    public void show(long time, boolean animate) {
        mSearchPanel.setHost(mOverlayPanelHost);
        super.show(time, animate);
    }

    /**
     * Hides the Contextual Search Supported Layout.
     * @param immediately Whether it should be hidden immediately.
     */
    protected void hideContextualSearch(boolean immediately) {
        // NOTE(pedrosimonetti): To be implemented by a supported Layout.
    }

    @Override
    protected void notifySizeChanged(float width, float height, int orientation) {
        super.notifySizeChanged(width, height, orientation);

        // NOTE(pedrosimonetti): Due to some floating point madness, getHeight() and
        // getHeightMinusTopControls() might not always be the same when the Toolbar is
        // visible. For this reason, we're comparing to see if the difference between them
        // is less than half pixel. If so, it means the Toolbar is visible.
        final boolean isToolbarVisible = getHeight() - getHeightMinusTopControls() <= mHalfPixelDp;
        mSearchPanel.onSizeChanged(width, height, isToolbarVisible);
    }

    @Override
    protected boolean onUpdateAnimation(long time, boolean jumpToEnd) {
        boolean parentAnimating = super.onUpdateAnimation(time, jumpToEnd);
        boolean panelAnimating = mSearchPanel.onUpdateAnimation(time, jumpToEnd);
        return panelAnimating || parentAnimating;
    }

    @Override
    protected SceneLayer getSceneLayer() {
        return mSearchPanel.getSceneLayer();
    }

    @Override
    protected void updateSceneLayer(Rect viewport, Rect contentViewport,
            LayerTitleCache layerTitleCache, TabContentManager tabContentManager,
            ResourceManager resourceManager, ChromeFullscreenManager fullscreenManager) {
        super.updateSceneLayer(viewport, contentViewport, layerTitleCache, tabContentManager,
                resourceManager, fullscreenManager);
        if (!mSearchPanel.isShowing()) return;

        mSearchPanel.updateSceneLayer(resourceManager);
    }
}
