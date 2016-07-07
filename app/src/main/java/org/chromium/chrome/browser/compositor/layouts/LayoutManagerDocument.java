// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.ViewGroup;

import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.CascadeEventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.ContextualSearchEventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EmptyEdgeSwipeHandler;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.GestureHandler;
import org.chromium.chrome.browser.compositor.layouts.phone.ContextualSearchLayout;
import org.chromium.chrome.browser.compositor.overlays.SceneOverlay;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManager.ContextualSearchContentViewDelegate;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchStaticEventFilter;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchStaticEventFilter.ContextualSearchTapHandler;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.dom_distiller.ReaderModeEdgeSwipeHandler;
import org.chromium.chrome.browser.dom_distiller.ReaderModePanel;
import org.chromium.chrome.browser.dom_distiller.ReaderModeStaticEventFilter;
import org.chromium.chrome.browser.dom_distiller.ReaderModeStaticEventFilter.ReaderModePanelSelector;
import org.chromium.chrome.browser.dom_distiller.ReaderModeStaticEventFilter.ReaderModeTapHandler;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelSelector;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

import java.util.List;

/**
 * A {@link Layout} controller for a simple document use case.  This class is responsible for
 * driving all {@link Layout}s that get shown via the {@link LayoutManager}.
 */
public class LayoutManagerDocument extends LayoutManager
        implements ContextualSearchTapHandler, ContextualSearchContentViewDelegate,
                ReaderModeTapHandler {
    // Layouts
    /** A {@link Layout} used for showing a normal web page. */
    protected final StaticLayout mStaticLayout;
    /** A {@link Layout} used for when the contextual search panel is up. */
    protected final ContextualSearchLayout mContextualSearchLayout;

    // Event Filters
    private final EdgeSwipeEventFilter mStaticEdgeEventFilter;
    private final ContextualSearchEventFilter mContextualSearchEventFilter;
    private final EdgeSwipeHandler mToolbarSwipeHandler;

    // Event Filter Handlers
    /** A {@link GestureHandler} that will delegate all events to {@link #getActiveLayout()}. */
    protected final GestureHandler mGestureHandler;
    private final EdgeSwipeHandler mContextualSearchEdgeSwipeHandler;
    private final EdgeSwipeHandler mReaderModeEdgeSwipeHandler;

    // Internal State
    private final SparseArray<LayoutTab> mTabCache = new SparseArray<LayoutTab>();
    private final ContextualSearchPanel mContextualSearchPanel;
    /** A delegate for interacting with the Contextual Search manager. */
    protected ContextualSearchManagementDelegate mContextualSearchDelegate;

    @SuppressWarnings("unused") private TabModelSelectorTabObserver mTabModelSelectorTabObserver;
    private final ReaderModePanelSelector mReaderModePanelSelector;

    /**
     * Creates a {@link LayoutManagerDocument} instance.
     * @param host            A {@link LayoutManagerHost} instance.
     */
    public LayoutManagerDocument(LayoutManagerHost host) {
        super(host);
        Context context = host.getContext();
        LayoutRenderHost renderHost = host.getLayoutRenderHost();

        mContextualSearchPanel = new ContextualSearchPanel(context, this);

        mReaderModePanelSelector = new ReaderModePanelSelector() {
            @Override
            public ReaderModePanel getActiveReaderModePanel() {
                if (mStaticLayout == null || !mStaticLayout.isActive()) return null;
                ReaderModePanel panel = mStaticLayout.getReaderModePanel();
                if (panel == null) return null;
                if (!panel.isShowing() || !panel.isReaderModeCurrentlyAllowed()) return null;
                return panel;
            }
        };

        // Build Event Filter Handlers
        mContextualSearchEdgeSwipeHandler = new ContextualSearchEdgeSwipeHandler(this);
        mReaderModeEdgeSwipeHandler = new ReaderModeEdgeSwipeHandler(
                mReaderModePanelSelector, this);
        mGestureHandler = new GestureHandlerLayoutDelegate(this);
        mToolbarSwipeHandler = new ToolbarSwipeHandler(this);

        // Build Event Filters
        mStaticEdgeEventFilter =
                new EdgeSwipeEventFilter(context, this, new StaticEdgeSwipeHandler());
        mContextualSearchEventFilter = new ContextualSearchEventFilter(
                context, this, mGestureHandler, mContextualSearchPanel);
        EventFilter contextualSearchStaticEventFilter = new ContextualSearchStaticEventFilter(
                context, this, mContextualSearchPanel, mContextualSearchEdgeSwipeHandler, this);
        EventFilter readerModeStaticEventFilter = new ReaderModeStaticEventFilter(
                context, this, mReaderModePanelSelector, mReaderModeEdgeSwipeHandler, this);
        EventFilter staticCascadeEventFilter = new CascadeEventFilter(context, this,
                new EventFilter[] {readerModeStaticEventFilter, contextualSearchStaticEventFilter,
                        mStaticEdgeEventFilter});

        // Build Layouts
        mStaticLayout = new StaticLayout(
                context, this, renderHost, staticCascadeEventFilter, mContextualSearchPanel);
        mContextualSearchLayout = new ContextualSearchLayout(
                context, this, renderHost, mContextualSearchEventFilter, mContextualSearchPanel);

        // Set up layout parameters
        mStaticLayout.setLayoutHandlesTabLifecycles(true);

        setNextLayout(null);
    }

    @Override
    public void init(TabModelSelector selector, TabCreatorManager creator,
            TabContentManager content, ViewGroup androidContentContainer,
            ContextualSearchManagementDelegate contextualSearchDelegate,
            DynamicResourceLoader dynamicResourceLoader) {
        // Save state
        mContextualSearchDelegate = contextualSearchDelegate;

        // Initialize Event Filters
        mStaticEdgeEventFilter.setTabModelSelector(selector);

        // Initialize Layouts
        mStaticLayout.setTabModelSelector(selector, content);
        mContextualSearchLayout.setTabModelSelector(selector, content);

        // Initialize Contextual Search Panel
        mContextualSearchPanel.setManagementDelegate(contextualSearchDelegate);
        mContextualSearchPanel.setDynamicResourceLoader(dynamicResourceLoader);

        // Set back flow communication
        if (contextualSearchDelegate != null) {
            contextualSearchDelegate.setContextualSearchPanel(mContextualSearchPanel);
        }

        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(selector) {
            @Override
            public void onContentChanged(Tab tab) {
                initLayoutTabFromHost(tab.getId());
            }

            @Override
            public void onBackgroundColorChanged(Tab tab, int color) {
                initLayoutTabFromHost(tab.getId());
            }

            @Override
            public void onDidChangeThemeColor(Tab tab, int color) {
                initLayoutTabFromHost(tab.getId());
            }
        };

        super.init(selector, creator, content, androidContentContainer, contextualSearchDelegate,
                dynamicResourceLoader);
    }

    @Override
    public void destroy() {
        super.destroy();

        if (mStaticLayout != null) mStaticLayout.destroy();
        if (mContextualSearchLayout != null) mContextualSearchLayout.destroy();
        if (mContextualSearchPanel != null) mContextualSearchPanel.destroy();
        if (mTabModelSelectorTabObserver != null) mTabModelSelectorTabObserver.destroy();
    }

    @Override
    public void getVirtualViews(List<VirtualView> views) {
        // Nothing to do here yet.
    }

    @Override
    protected void onViewportChanged(RectF viewportDp) {
        super.onViewportChanged(viewportDp);
        for (int i = 0; i < mTabCache.size(); i++) {
            // This assumes that the content width/height is always the size of the host.
            mTabCache.valueAt(i).setContentSize(viewportDp.width(), viewportDp.height());
        }
    }

    /**
     * @return The {@link EdgeSwipeHandler} responsible for processing swipe events for the toolbar.
     */
    @Override
    public EdgeSwipeHandler getTopSwipeHandler() {
        return mToolbarSwipeHandler;
    }

    /**
     * Clears all content associated with {@code tabId} from the internal caches.
     * @param tabId The id of the tab to clear.
     */
    protected void emptyCachesExcept(int tabId) {
        LayoutTab tab = mTabCache.get(tabId);
        mTabCache.clear();
        if (tab != null) mTabCache.put(tabId, tab);
    }

    /**
     * Adds the {@link SceneOverlay} across all {@link Layout}s owned by this class.
     * @param helper A {@link SceneOverlay} instance.
     */
    protected void addGlobalSceneOverlay(SceneOverlay helper) {
        mStaticLayout.addSceneOverlay(helper);
        mContextualSearchLayout.addSceneOverlay(helper);
    }

    /**
     * @param tabId The id of the tab represented by a {@link LayoutTab}.
     * @return      A {@link LayoutTab} if one exists or {@code null} if none can be found.
     */
    protected LayoutTab getExistingLayoutTab(int tabId) {
        return mTabCache.get(tabId);
    }

    @Override
    protected Layout getDefaultLayout() {
        return mStaticLayout;
    }

    @Override
    public void initLayoutTabFromHost(final int tabId) {
        if (getTabModelSelector() == null || getActiveLayout() == null) return;

        TabModelSelector selector = getTabModelSelector();
        Tab tab = selector.getTabById(tabId);
        if (tab == null) return;

        LayoutTab layoutTab = mTabCache.get(tabId);
        if (layoutTab == null) return;

        String url = tab.getUrl();
        boolean isNativePage = url != null && url.startsWith(UrlConstants.CHROME_NATIVE_SCHEME);
        int themeColor = tab.getThemeColor();
        boolean canUseLiveTexture =
                tab.getContentViewCore() != null && !tab.isShowingSadTab() && !isNativePage;
        layoutTab.initFromHost(tab.getBackgroundColor(), tab.shouldStall(), canUseLiveTexture,
                themeColor,
                ColorUtils.getTextBoxColorForToolbarBackground(themeColor),
                ColorUtils.getTextBoxAlphaForToolbarBackground(tab));

        mHost.requestRender();
    }

    @Override
    public LayoutTab createLayoutTab(int id, boolean incognito, boolean showCloseButton,
            boolean isTitleNeeded, float maxContentWidth, float maxContentHeight) {
        LayoutTab tab = mTabCache.get(id);
        if (tab == null) {
            tab = new LayoutTab(id, incognito, mLastContentWidthDp, mLastContentHeightDp,
                    showCloseButton, isTitleNeeded);
            mTabCache.put(id, tab);
        } else {
            tab.init(mLastContentWidthDp, mLastContentHeightDp, showCloseButton, isTitleNeeded);
        }
        if (maxContentWidth > 0.f) tab.setMaxContentWidth(maxContentWidth);
        if (maxContentHeight > 0.f) tab.setMaxContentHeight(maxContentHeight);

        return tab;
    }

    @Override
    public void releaseTabLayout(int id) {
        mTabCache.remove(id);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e, boolean isKeyboardShowing) {
        boolean intercepted = super.onInterceptTouchEvent(e, isKeyboardShowing);
        if (intercepted) getActiveLayout().unstallImmediately();
        return intercepted;
    }

    /**
     * Should be called when the user presses the back button on the phone.
     * @return Whether or not the back button was consumed by the active {@link Layout}.
     */
    @Override
    public boolean onBackPressed() {
        return getActiveLayout() != null && getActiveLayout().onBackPressed();
    }

    @Override
    public void handleTapContextualSearchBar(long time, float x, float y) {
        if (getActiveLayout() == mContextualSearchLayout) return;
        if (mContextualSearchDelegate == null) return;

        // When not in compatibility mode, tapping on the Search Bar will expand the Panel,
        // therefore we must start showing the ContextualSearchLayout.
        // TODO(pedrosimonetti): once we implement the close button, a tap in the Panel might
        // trigger the Panel to close, not expand, so in that case we don't want to show the
        // ContextualSearchLayout. Coordinate with dtrainor@ to solve this. It might be
        // necessary for the ContextualSearchPanel to be able to trigger the display of the
        // ContextualSearchLayout.
        if (!mContextualSearchDelegate.isRunningInCompatibilityMode()) {
            showContextualSearchLayout(true);
        }

        mContextualSearchPanel.handleClick(time, x, y);
    }

    @Override
    public void setContextualSearchContentViewCore(ContentViewCore contentViewCore) {
        mHost.onContentViewCoreAdded(contentViewCore);
    }

    @Override
    public void releaseContextualSearchContentViewCore() {
        if (getTabModelSelector() == null) return;
        Tab tab = getTabModelSelector().getCurrentTab();
        if (tab != null) tab.updateFullscreenEnabledState();
    }

    private void showContextualSearchLayout(boolean animate) {
        mContextualSearchDelegate.preserveBasePageSelectionOnNextLossOfFocus();
        startShowing(mContextualSearchLayout, animate);
    }

    private class StaticEdgeSwipeHandler extends EmptyEdgeSwipeHandler {
        @Override
        public void swipeStarted(ScrollDirection direction, float x, float y) {
        }

        @Override
        public boolean isSwipeEnabled(ScrollDirection direction) {
            FullscreenManager fullscreenManager = mHost.getFullscreenManager();
            return direction == ScrollDirection.DOWN && fullscreenManager != null
                    && fullscreenManager.getPersistentFullscreenMode();
        }
    }

    private class ContextualSearchEdgeSwipeHandler extends EdgeSwipeHandlerLayoutDelegate {
        public ContextualSearchEdgeSwipeHandler(LayoutProvider provider) {
            super(provider);
        }

        @Override
        public void swipeStarted(ScrollDirection direction, float x, float y) {
            if (isCompatabilityMode()) {
                mContextualSearchDelegate.openResolvedSearchUrlInNewTab();
                return;
            }

            if (getActiveLayout() != mContextualSearchLayout) {
                showContextualSearchLayout(false);
            }

            super.swipeStarted(direction, x, y);
        }

        @Override
        public boolean isSwipeEnabled(ScrollDirection direction) {
            return direction == ScrollDirection.UP
                    && mContextualSearchDelegate != null
                    && mContextualSearchDelegate.isShowingSearchPanel();
        }

        private boolean isCompatabilityMode() {
            return mContextualSearchDelegate != null
                    && mContextualSearchDelegate.isRunningInCompatibilityMode();
        }
    }

    /**
     * A {@link EdgeSwipeHandler} meant to respond to edge events for the toolbar.
     */
    private class ToolbarSwipeHandler extends EdgeSwipeHandlerLayoutDelegate {
        private ScrollDirection mLastScroll;

        /**
         * Creates an instance of the {@link ToolbarSwipeHandler}.
         * @param provider A {@link LayoutProvider} instance.
         */
        public ToolbarSwipeHandler(LayoutProvider provider) {
            super(provider);
        }

        @Override
        public void swipeStarted(ScrollDirection direction, float x, float y) {
            super.swipeStarted(direction, x, y);
            mLastScroll = direction;
        }

        @Override
        public void swipeFinished() {
            super.swipeFinished();
            changeTabs();
        }

        @Override
        public void swipeFlingOccurred(float x, float y, float tx, float ty, float vx, float vy) {
            super.swipeFlingOccurred(x, y, tx, ty, vx, vy);
            changeTabs();
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private void changeTabs() {
            DocumentTabModelSelector selector =
                    ChromeApplication.getDocumentTabModelSelector();
            TabModel tabModel = selector.getCurrentModel();
            int currentIndex = tabModel.index();
            if (mLastScroll == ScrollDirection.LEFT) {
                if (currentIndex < tabModel.getCount() - 1) {
                    TabModelUtils.setIndex(tabModel, currentIndex + 1);
                }
            } else {
                if (currentIndex > 0) {
                    TabModelUtils.setIndex(tabModel, currentIndex - 1);
                }
            }
        }

        @Override
        public boolean isSwipeEnabled(ScrollDirection direction) {
            FullscreenManager manager = mHost.getFullscreenManager();
            if (getActiveLayout() != mStaticLayout
                    || !FeatureUtilities.isDocumentModeEligible(mHost.getContext())
                    || !DeviceClassManager.enableToolbarSwipe(
                               FeatureUtilities.isDocumentMode(mHost.getContext()))
                    || (manager != null && manager.getPersistentFullscreenMode())) {
                return false;
            }

            return direction == ScrollDirection.LEFT || direction == ScrollDirection.RIGHT;
        }
    }

    @Override
    public void handleTapReaderModeBar(long time, float x, float y) {
        ReaderModePanel activePanel = mReaderModePanelSelector.getActiveReaderModePanel();
        if (activePanel != null) activePanel.handleClick(time, x, y);
    }
}
