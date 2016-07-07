// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.AreaGestureEventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilterHost;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.GestureHandler;
import org.chromium.chrome.browser.compositor.overlays.strip.StripLayoutHelperManager;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.ui.resources.ResourceManager;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

import java.util.List;

/**
 * {@link LayoutManagerChromeTablet} is the specialization of {@link LayoutManagerChrome} for
 * the tablet.
 */
public class LayoutManagerChromeTablet extends LayoutManagerChrome {
    // Event Filters
    private final TabStripEventFilter mTabStripFilter;

    // Internal State
    private final String mDefaultTitle;

    private StripLayoutHelperManager mTabStripLayoutHelperManager;
    private TabModelSelectorTabObserver mTabObserver;

    /**
     * Creates an instance of a {@link LayoutManagerChromePhone}.
     * @param host                     A {@link LayoutManagerHost} instance.
     * @param overviewLayoutFactoryDelegate A {@link OverviewLayoutFactoryDelegate} instance.
     */
    public LayoutManagerChromeTablet(
            LayoutManagerHost host, OverviewLayoutFactoryDelegate overviewLayoutFactoryDelegate) {
        super(host, overviewLayoutFactoryDelegate);
        Context context = host.getContext();

        // Build Event Filters
        mTabStripFilter = new TabStripEventFilter(
                context, this, new TabStripEventHandler(), null, false, false);

        mTabStripLayoutHelperManager = new StripLayoutHelperManager(
                context, this, mHost.getLayoutRenderHost(), mTabStripFilter);

        // Set up state
        mDefaultTitle = context.getString(R.string.tab_loading_default_title);

        addGlobalSceneOverlay(mTabStripLayoutHelperManager);

        setNextLayout(null);
    }

    @Override
    public void destroy() {
        super.destroy();

        if (mTabStripLayoutHelperManager != null) {
            mTabStripLayoutHelperManager.destroy();
            mTabStripLayoutHelperManager = null;
        }

        if (mTabObserver != null) {
            mTabObserver.destroy();
            mTabObserver = null;
        }
    }

    @Override
    protected ToolbarSwipeHandler createToolbarSwipeHandler(LayoutProvider provider) {
        return new TabletToolbarSwipeHandler(provider);
    }

    @Override
    public void tabSelected(int tabId, int prevId, boolean incognito) {
        if (getActiveLayout() == mStaticLayout || getActiveLayout() == mOverviewListLayout) {
            super.tabSelected(tabId, prevId, incognito);
        } else {
            startShowing(mStaticLayout, false);
            // TODO(dtrainor, jscholler): This is hacky because we're relying on it to set the
            // internal tab to show and not start hiding until we're done calling finalizeShowing().
            // This prevents a flicker because we properly build and set the internal
            // {@link LayoutTab} before actually showing the {@link TabView}.
            if (getActiveLayout() != null) {
                getActiveLayout().onTabSelected(time(), tabId, prevId, incognito);
            }
            if (getActiveLayout() != null) getActiveLayout().onTabSelecting(time(), tabId);
        }
    }

    @Override
    protected void tabCreated(int id, int sourceId, TabLaunchType launchType, boolean incognito,
            boolean willBeSelected, float originX, float originY) {
        if (getFullscreenManager() != null) getFullscreenManager().showControlsTransient();
        super.tabCreated(id, sourceId, launchType, incognito, willBeSelected, originX, originY);
    }

    @Override
    protected void tabClosed(int id, int nextId, boolean incognito) {
        super.tabClosed(id, nextId, incognito);
    }

    @Override
    protected void tabClosureCommitted(int id, boolean incognito) {
        super.tabClosureCommitted(id, incognito);
        if (mTitleCache != null) mTitleCache.remove(id);
    }

    @Override
    protected void tabModelSwitched(boolean incognito) {
        super.tabModelSwitched(incognito);
        getTabModelSelector().commitAllTabClosures();
    }

    @Override
    public void init(TabModelSelector selector, TabCreatorManager creator,
            TabContentManager content, ViewGroup androidContentContainer,
            ContextualSearchManagementDelegate contextualSearchDelegate,
            DynamicResourceLoader dynamicResourceLoader) {
        if (mTabStripLayoutHelperManager != null) {
            mTabStripLayoutHelperManager.setTabModelSelector(selector, creator, content);
        }

        super.init(selector, creator, content, androidContentContainer, contextualSearchDelegate,
                dynamicResourceLoader);

        mTabObserver = new TabModelSelectorTabObserver(selector) {
            @Override
            public void onFaviconUpdated(Tab tab) {
                updateTitle(tab);
            }

            @Override
            public void onTitleUpdated(Tab tab) {
                updateTitle(tab);
            }
        };

        // Make sure any tabs already restored get loaded into the title cache.
        List<TabModel> models = selector.getModels();
        for (int i = 0; i < models.size(); i++) {
            TabModel model = models.get(i);
            for (int j = 0; j < model.getCount(); j++) {
                Tab tab = model.getTabAt(j);
                if (tab != null && mTitleCache != null) {
                    mTitleCache.put(tab.getId(), getTitleBitmap(tab), getFaviconBitmap(tab),
                            tab.isIncognito(), tab.isTitleDirectionRtl());
                }
            }
        }
    }

    @Override
    protected LayoutManagerTabModelObserver createTabModelObserver() {
        return new LayoutManagerTabModelObserver() {
            @Override
            public void didAddTab(Tab tab, TabLaunchType launchType) {
                super.didAddTab(tab, launchType);
                updateTitle(getTabById(tab.getId()));
            }
        };
    }

    @Override
    protected String getTitleForTab(Tab tab) {
        String title = super.getTitleForTab(tab);
        if (TextUtils.isEmpty(title)) title = mDefaultTitle;
        return title;
    }

    @Override
    public StripLayoutHelperManager getStripLayoutHelperManager() {
        return mTabStripLayoutHelperManager;
    }

    @Override
    public SceneLayer getUpdatedActiveSceneLayer(Rect viewport, Rect contentViewport,
            LayerTitleCache layerTitleCache, TabContentManager tabContentManager,
            ResourceManager resourceManager, ChromeFullscreenManager fullscreenManager) {
        mTabStripLayoutHelperManager.setBrightness(getActiveLayout().getToolbarBrightness());
        return super.getUpdatedActiveSceneLayer(viewport, contentViewport, layerTitleCache,
                tabContentManager, resourceManager, fullscreenManager);
    }

    private void updateTitle(Tab tab) {
        if (tab != null && mTitleCache != null) {
            mTitleCache.put(tab.getId(), getTitleBitmap(tab), getFaviconBitmap(tab),
                    tab.isIncognito(), tab.isTitleDirectionRtl());

            getActiveLayout().tabTitleChanged(tab.getId(), getTitleForTab(tab));
        }
        requestUpdate();
    }

    private class TabletToolbarSwipeHandler extends ToolbarSwipeHandler {
        public TabletToolbarSwipeHandler(LayoutProvider provider) {
            super(provider);
        }

        @Override
        public boolean isSwipeEnabled(ScrollDirection direction) {
            if ((direction == ScrollDirection.LEFT || direction == ScrollDirection.RIGHT)
                    && (getTabModelSelector() == null
                               || getTabModelSelector().getCurrentModel().getCount() <= 1)) {
                return false;
            }

            return super.isSwipeEnabled(direction);
        }
    }

    private class TabStripEventHandler implements GestureHandler {
        @Override
        public void onDown(float x, float y, boolean fromMouse, int buttons) {
            mTabStripLayoutHelperManager.onDown(time(), x, y, fromMouse, buttons);
        }

        @Override
        public void onUpOrCancel() {
            mTabStripLayoutHelperManager.onUpOrCancel(time());
        }

        @Override
        public void drag(float x, float y, float dx, float dy, float tx, float ty) {
            mTabStripLayoutHelperManager.drag(time(), x, y, dx, dy, tx, ty);
        }

        @Override
        public void click(float x, float y, boolean fromMouse, int buttons) {
            mTabStripLayoutHelperManager.click(time(), x, y, fromMouse, buttons);
        }

        @Override
        public void fling(float x, float y, float velocityX, float velocityY) {
            mTabStripLayoutHelperManager.fling(time(), x, y, velocityX, velocityY);
        }

        @Override
        public void onLongPress(float x, float y) {
            mTabStripLayoutHelperManager.onLongPress(time(), x, y);
        }

        @Override
        public void onPinch(float x0, float y0, float x1, float y1, boolean firstEvent) {
            // Not implemented.
        }
    }

    private class TabStripEventFilter extends AreaGestureEventFilter {
        public TabStripEventFilter(Context context, EventFilterHost host, GestureHandler handler,
                RectF triggerRect, boolean autoOffset, boolean useDefaultLongPress) {
            super(context, host, handler, triggerRect, autoOffset, useDefaultLongPress);
        }

        @Override
        public boolean onInterceptTouchEventInternal(MotionEvent e, boolean isKeyboardShowing) {
            if (getActiveLayout().isTabStripEventFilterEnabled()) {
                return super.onInterceptTouchEventInternal(e, isKeyboardShowing);
            }

            return false;
        }
    }
}
