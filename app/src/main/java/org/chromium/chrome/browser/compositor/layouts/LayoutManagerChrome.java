// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.ViewGroup;

import org.chromium.base.ObserverList;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.TitleCache;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.content.TitleBitmapFactory;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.BlackHoleEventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.GestureEventFilter;
import org.chromium.chrome.browser.compositor.overlays.SceneOverlay;
import org.chromium.chrome.browser.compositor.overlays.strip.StripLayoutHelperManager;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelector.CloseAllTabsDelegate;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.widget.OverviewListLayout;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

import java.util.List;

/**
 * A {@link Layout} controller for the more complicated Chrome browser.  This is currently a
 * superset of {@link LayoutManagerDocument}.
 */
public class LayoutManagerChrome
        extends LayoutManagerDocument implements OverviewModeBehavior, CloseAllTabsDelegate {
    // Layouts
    /** An {@link Layout} that should be used as the accessibility tab switcher. */
    protected OverviewListLayout mOverviewListLayout;
    /** A {@link Layout} that should be used when the user is swiping sideways on the toolbar. */
    protected ToolbarSwipeLayout mToolbarSwipeLayout;
    /** A {@link Layout} that should be used when the user is in the tab switcher. */
    protected Layout mOverviewLayout;

    // Event Filters
    /** A {@link EventFilter} that consumes all touch events. */
    protected EventFilter mBlackHoleEventFilter;
    private final GestureEventFilter mGestureEventFilter;

    // Event Filter Handlers
    private final EdgeSwipeHandler mToolbarSwipeHandler;

    // Internal State
    /** A {@link TitleCache} instance that stores all title/favicon bitmaps as CC resources. */
    protected TitleCache mTitleCache;
    /** Responsible for building non-incognito titles. */
    protected TitleBitmapFactory mStandardTitleBitmapFactory;
    /** Responsible for building all incognito titles. */
    protected TitleBitmapFactory mIncognitoTitleBitmapFactory;
    /** Whether or not animations are enabled.  This can disable certain layouts or effects. */
    private boolean mEnableAnimations = true;
    private boolean mCreatingNtp;
    private final ObserverList<OverviewModeObserver> mOverviewModeObservers;
    private TabModelSelectorObserver mTabModelSelectorObserver;
    private TabModelObserver mTabModelObserver;
    private TabModelSelectorTabObserver mTabSelectorTabObserver;

    /**
     * Protected class to handle {@link TabModelObserver} related tasks. Extending classes will
     * need to override any related calls to add new functionality */
    protected class LayoutManagerTabModelObserver extends EmptyTabModelObserver {
        @Override
        public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
            if (tab.getId() != lastId) tabSelected(tab.getId(), lastId, tab.isIncognito());
        }

        @Override
        public void willAddTab(Tab tab, TabLaunchType type) {
            // Open the new tab
            if (type == TabLaunchType.FROM_INSTANT || type == TabLaunchType.FROM_RESTORE) return;

            tabCreating(getTabModelSelector().getCurrentTabId(), tab.getUrl(), tab.isIncognito());
        }

        @Override
        public void didAddTab(Tab tab, TabLaunchType launchType) {
            int tabId = tab.getId();
            if (launchType != TabLaunchType.FROM_INSTANT
                    && launchType != TabLaunchType.FROM_RESTORE) {
                boolean incognito = tab.isIncognito();
                boolean willBeSelected = launchType != TabLaunchType.FROM_LONGPRESS_BACKGROUND
                        || (!getTabModelSelector().isIncognitoSelected() && incognito);
                float lastTapX = LocalizationUtils.isLayoutRtl() ? mLastContentWidthDp : 0.f;
                float lastTapY = 0.f;
                if (launchType != TabLaunchType.FROM_MENU_OR_OVERVIEW) {
                    float heightDelta =
                            mLastFullscreenViewportDp.height() - mLastVisibleViewportDp.height();
                    lastTapX = mPxToDp * mLastTapX;
                    lastTapY = mPxToDp * mLastTapY - heightDelta;
                }

                tabCreated(tabId, getTabModelSelector().getCurrentTabId(), launchType, incognito,
                        willBeSelected, lastTapX, lastTapY);
            }
        }

        @Override
        public void didCloseTab(Tab tab) {
            tabClosed(tab);
        }

        @Override
        public void tabPendingClosure(Tab tab) {
            tabClosed(tab);
        }

        @Override
        public void tabClosureUndone(Tab tab) {
            tabClosureCancelled(tab.getId(), tab.isIncognito());
        }

        @Override
        public void tabClosureCommitted(Tab tab) {
            LayoutManagerChrome.this.tabClosureCommitted(tab.getId(), tab.isIncognito());
        }

        @Override
        public void didMoveTab(Tab tab, int newIndex, int curIndex) {
            tabMoved(tab.getId(), curIndex, newIndex, tab.isIncognito());
        }
    }

    /**
     * Delegate of a factory to create an overview layout.
     */
    public interface OverviewLayoutFactoryDelegate {
        /**
         * @param context     The current Android's context.
         * @param updateHost  The {@link LayoutUpdateHost} view for this layout.
         * @param renderHost  The {@link LayoutRenderHost} view for this layout.
         * @param eventFilter The {@link EventFilter} that is needed for this view.
         */
        Layout createOverviewLayout(Context context, LayoutUpdateHost updateHost,
                LayoutRenderHost renderHost, EventFilter eventFilter);
    }

    /**
     * Creates the {@link LayoutManagerChrome} instance.
     * @param host              A {@link LayoutManagerHost} instance.
     * @param overviewLayoutFactoryDelegate A {@link OverviewLayoutFactoryDelegate} instance.
     */
    public LayoutManagerChrome(
            LayoutManagerHost host, OverviewLayoutFactoryDelegate overviewLayoutFactoryDelegate) {
        super(host);
        Context context = host.getContext();
        LayoutRenderHost renderHost = host.getLayoutRenderHost();

        // Set up state
        mStandardTitleBitmapFactory =
                new TitleBitmapFactory(context, false, R.drawable.default_favicon);
        mIncognitoTitleBitmapFactory =
                new TitleBitmapFactory(context, true, R.drawable.default_favicon_white);
        mOverviewModeObservers = new ObserverList<OverviewModeObserver>();

        // Build Event Filter Handlers
        mToolbarSwipeHandler = new ToolbarSwipeHandler(this);

        // Build Event Filters
        mBlackHoleEventFilter = new BlackHoleEventFilter(context, this);
        mGestureEventFilter = new GestureEventFilter(context, this, mGestureHandler);

        // Build Layouts
        mOverviewListLayout =
                new OverviewListLayout(context, this, renderHost, mBlackHoleEventFilter);
        mToolbarSwipeLayout =
                new ToolbarSwipeLayout(context, this, renderHost, mBlackHoleEventFilter);
        if (overviewLayoutFactoryDelegate != null) {
            mOverviewLayout = overviewLayoutFactoryDelegate.createOverviewLayout(
                    context, this, renderHost, mGestureEventFilter);
        }
    }

    /**
     * @return The {@link TabModelObserver} instance this class should be using.
     */
    protected LayoutManagerTabModelObserver createTabModelObserver() {
        return new LayoutManagerTabModelObserver();
    }

    /**
     * @return A list of virtual views representing compositor rendered views.
     */
    @Override
    public void getVirtualViews(List<VirtualView> views) {
        if (getActiveLayout() != null) {
            getActiveLayout().getVirtualViews(views);
        }
    }

    /**
     * @return The {@link EdgeSwipeHandler} responsible for processing swipe events for the toolbar.
     */
    @Override
    public EdgeSwipeHandler getTopSwipeHandler() {
        return mToolbarSwipeHandler;
    }

    @Override
    public void init(TabModelSelector selector, TabCreatorManager creator,
            TabContentManager content, ViewGroup androidContentContainer,
            ContextualSearchManagementDelegate contextualSearchDelegate,
            DynamicResourceLoader dynamicResourceLoader) {
        // TODO: TitleCache should be a part of the ResourceManager.
        mTitleCache = mHost.getTitleCache();

        // Initialize Layouts
        mToolbarSwipeLayout.setTabModelSelector(selector, content);
        mOverviewListLayout.setTabModelSelector(selector, content);
        if (mOverviewLayout != null) mOverviewLayout.setTabModelSelector(selector, content);

        super.init(selector, creator, content, androidContentContainer, contextualSearchDelegate,
                dynamicResourceLoader);

        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                tabModelSwitched(newModel.isIncognito());
            }
        };
        selector.addObserver(mTabModelSelectorObserver);
        selector.setCloseAllTabsDelegate(this);

        mTabModelObserver = createTabModelObserver();
        for (TabModel model : selector.getModels()) model.addObserver(mTabModelObserver);

        mTabSelectorTabObserver = new TabModelSelectorTabObserver(selector) {
            @Override
            public void onLoadStarted(Tab tab, boolean toDifferentDocument) {
                tabLoadStarted(tab.getId(), tab.isIncognito());
            }

            @Override
            public void onLoadStopped(Tab tab, boolean toDifferentDocument) {
                tabLoadFinished(tab.getId(), tab.isIncognito());
            }

            @Override
            public void onPageLoadStarted(Tab tab, String url) {
                tabPageLoadStarted(tab.getId(), tab.isIncognito());
            }

            @Override
            public void onPageLoadFinished(Tab tab) {
                tabPageLoadFinished(tab.getId(), tab.isIncognito());
            }

            @Override
            public void onPageLoadFailed(Tab tab, int errorCode) {
                tabPageLoadFinished(tab.getId(), tab.isIncognito());
            }

            @Override
            public void onCrash(Tab tab, boolean sadTabShown) {
                tabPageLoadFinished(tab.getId(), tab.isIncognito());
            }
        };
    }

    @Override
    public void destroy() {
        super.destroy();
        if (mTabModelSelectorObserver != null) {
            getTabModelSelector().removeObserver(mTabModelSelectorObserver);
        }
        if (mTabModelObserver != null) {
            for (TabModel model : getTabModelSelector().getModels()) {
                model.removeObserver(mTabModelObserver);
            }
        }
        if (mTabSelectorTabObserver != null) mTabSelectorTabObserver.destroy();
        mOverviewModeObservers.clear();

        if (mOverviewLayout != null) {
            mOverviewLayout.destroy();
            mOverviewLayout = null;
        }
        mOverviewListLayout.destroy();
        mToolbarSwipeLayout.destroy();
    }

    @Override
    protected void addGlobalSceneOverlay(SceneOverlay helper) {
        super.addGlobalSceneOverlay(helper);
        mOverviewListLayout.addSceneOverlay(helper);
        mToolbarSwipeLayout.addSceneOverlay(helper);
        if (mOverviewLayout != null) mOverviewLayout.addSceneOverlay(helper);
    }

    /**
     * Meant to be overridden by child classes for when they need to extend the toolbar side swipe
     * functionality.
     * @param provider A {@link LayoutProvider} instance.
     * @return         A {@link ToolbarSwipeHandler} instance that will be used by internal layouts.
     */
    protected ToolbarSwipeHandler createToolbarSwipeHandler(LayoutProvider provider) {
        return new ToolbarSwipeHandler(provider);
    }

    /**
     * Simulates a click on the view at the specified pixel offset
     * from the top left of the view.
     * This is used by UI tests.
     * @param x Coordinate of the click in dp.
     * @param y Coordinate of the click in dp.
     */
    @VisibleForTesting
    public void simulateClick(float x, float y) {
        if (getActiveLayout() != null) getActiveLayout().click(time(), x, y);
    }

    /**
     * Simulates a drag and issues Up-event to commit the drag.
     * @param x  Coordinate to start the Drag from in dp.
     * @param y  Coordinate to start the Drag from in dp.
     * @param dX Amount of drag in X direction in dp.
     * @param dY Amount of drag in Y direction in dp.
     */
    @VisibleForTesting
    public void simulateDrag(float x, float y, float dX, float dY) {
        if (getActiveLayout() != null) {
            getActiveLayout().onDown(0, x, y);
            getActiveLayout().drag(0, x, y, dX, dY);
            getActiveLayout().onUpOrCancel(time());
        }
    }

    private boolean isOverviewLayout(Layout layout) {
        return layout != null && (layout == mOverviewLayout || layout == mOverviewListLayout);
    }

    @Override
    protected void startShowing(Layout layout, boolean animate) {
        mCreatingNtp = false;
        super.startShowing(layout, animate);

        Layout layoutBeingShown = getActiveLayout();

        // Check if a layout is showing that should hide the contextual search bar.
        if (mContextualSearchDelegate != null
                && (isOverviewLayout(layoutBeingShown)
                           || layoutBeingShown == mToolbarSwipeLayout)) {
            mContextualSearchDelegate.dismissContextualSearchBar();
        }

        // Check if we should notify OverviewModeObservers.
        if (isOverviewLayout(layoutBeingShown)) {
            boolean showToolbar =
                    !mEnableAnimations || getTabModelSelector().getCurrentModel().getCount() <= 0;
            for (OverviewModeObserver observer : mOverviewModeObservers) {
                observer.onOverviewModeStartedShowing(showToolbar);
            }
        }
    }

    @Override
    public void startHiding(int nextTabId, boolean hintAtTabSelection) {
        super.startHiding(nextTabId, hintAtTabSelection);

        Layout layoutBeingHidden = getActiveLayout();
        if (isOverviewLayout(layoutBeingHidden)) {
            boolean showToolbar = true;
            if (mEnableAnimations && layoutBeingHidden == mOverviewLayout) {
                final LayoutTab tab = layoutBeingHidden.getLayoutTab(nextTabId);
                showToolbar = tab != null ? !tab.showToolbar() : true;
            }

            boolean creatingNtp = layoutBeingHidden == mOverviewLayout && mCreatingNtp;

            for (OverviewModeObserver observer : mOverviewModeObservers) {
                observer.onOverviewModeStartedHiding(showToolbar, creatingNtp);
            }
        }
    }

    @Override
    public void doneShowing() {
        super.doneShowing();

        if (isOverviewLayout(getActiveLayout())) {
            for (OverviewModeObserver observer : mOverviewModeObservers) {
                observer.onOverviewModeFinishedShowing();
            }
        }
    }

    @Override
    public void doneHiding() {
        Layout layoutBeingHidden = getActiveLayout();

        if (getNextLayout() == getDefaultLayout()) {
            Tab tab = getTabModelSelector() != null ? getTabModelSelector().getCurrentTab() : null;
            emptyCachesExcept(tab != null ? tab.getId() : Tab.INVALID_TAB_ID);
        }

        super.doneHiding();

        if (isOverviewLayout(layoutBeingHidden)) {
            for (OverviewModeObserver observer : mOverviewModeObservers) {
                observer.onOverviewModeFinishedHiding();
            }
        }
    }

    @VisibleForTesting
    public void tabSelected(int tabId, int prevId, boolean incognito) {
        // Update the model here so we properly set the right selected TabModel.
        if (getActiveLayout() != null) {
            getActiveLayout().onTabSelected(time(), tabId, prevId, incognito);
        }
    }

    /**
     * Should be called when a tab created event is triggered.
     * @param id             The id of the tab that was created.
     * @param sourceId       The id of the creating tab if any.
     * @param launchType     How the tab was launched.
     * @param incognito      Whether or not the created tab is incognito.
     * @param willBeSelected Whether or not the created tab will be selected.
     * @param originX        The x coordinate of the action that created this tab in dp.
     * @param originY        The y coordinate of the action that created this tab in dp.
     */
    protected void tabCreated(int id, int sourceId, TabLaunchType launchType, boolean incognito,
            boolean willBeSelected, float originX, float originY) {
        Tab newTab = TabModelUtils.getTabById(getTabModelSelector().getModel(incognito), id);
        mCreatingNtp = newTab != null && newTab.isNativePage();

        int newIndex = TabModelUtils.getTabIndexById(getTabModelSelector().getModel(incognito), id);
        getActiveLayout().onTabCreated(
                time(), id, newIndex, sourceId, incognito, !willBeSelected, originX, originY);
    }

    /**
     * Should be called when a tab creating event is triggered (called before the tab is done being
     * created).
     * @param sourceId    The id of the creating tab if any.
     * @param url         The url of the created tab.
     * @param isIncognito Whether or not created tab will be incognito.
     */
    protected void tabCreating(int sourceId, String url, boolean isIncognito) {
        if (getActiveLayout() != null) getActiveLayout().onTabCreating(sourceId);
    }

    /**
     * Should be called when a tab closed event is triggered.
     * @param id        The id of the closed tab.
     * @param nextId    The id of the next tab that will be visible, if any.
     * @param incognito Whether or not the closed tab is incognito.
     */
    protected void tabClosed(int id, int nextId, boolean incognito) {
        if (getActiveLayout() != null) getActiveLayout().onTabClosed(time(), id, nextId, incognito);
    }

    private void tabClosed(Tab tab) {
        Tab currentTab =
                getTabModelSelector() != null ? getTabModelSelector().getCurrentTab() : null;
        int nextTabId = currentTab != null ? currentTab.getId() : Tab.INVALID_TAB_ID;
        tabClosed(tab.getId(), nextTabId, tab.isIncognito());
    }

    /**
     * Called when a tab closure has been committed and all tab cleanup should happen.
     * @param id        The id of the closed tab.
     * @param incognito Whether or not the closed tab is incognito.
     */
    protected void tabClosureCommitted(int id, boolean incognito) {
        if (getActiveLayout() != null) {
            getActiveLayout().onTabClosureCommitted(time(), id, incognito);
        }
    }

    @Override
    public boolean closeAllTabsRequest(boolean incognito) {
        if (!isOverviewLayout(getActiveLayout()) || !getActiveLayout().handlesCloseAll()) {
            return false;
        }
        getActiveLayout().onTabsAllClosing(time(), incognito);
        return true;
    }

    /**
     * Called when the selected tab model has switched.
     * @param incognito Whether or not the new current tab model is incognito.
     */
    protected void tabModelSwitched(boolean incognito) {
        if (getActiveLayout() != null) getActiveLayout().onTabModelSwitched(incognito);
    }

    private void tabMoved(int id, int oldIndex, int newIndex, boolean incognito) {
        if (getActiveLayout() != null) {
            getActiveLayout().onTabMoved(time(), id, oldIndex, newIndex, incognito);
        }
    }

    private void tabPageLoadStarted(int id, boolean incognito) {
        if (getActiveLayout() != null) getActiveLayout().onTabPageLoadStarted(id, incognito);
    }

    private void tabPageLoadFinished(int id, boolean incognito) {
        if (getActiveLayout() != null) getActiveLayout().onTabPageLoadFinished(id, incognito);
    }

    private void tabLoadStarted(int id, boolean incognito) {
        if (getActiveLayout() != null) getActiveLayout().onTabLoadStarted(id, incognito);
    }

    private void tabLoadFinished(int id, boolean incognito) {
        if (getActiveLayout() != null) getActiveLayout().onTabLoadFinished(id, incognito);
    }

    private void tabClosureCancelled(int id, boolean incognito) {
        if (getActiveLayout() != null) {
            getActiveLayout().onTabClosureCancelled(time(), id, incognito);
        }
    }

    @Override
    public void initLayoutTabFromHost(int tabId) {
        super.initLayoutTabFromHost(tabId);

        if (getTabModelSelector() == null || getActiveLayout() == null) return;

        TabModelSelector selector = getTabModelSelector();
        Tab tab = selector.getTabById(tabId);
        if (tab == null) return;

        LayoutTab layoutTab = getExistingLayoutTab(tabId);
        if (layoutTab == null) return;

        if (mTitleCache != null && layoutTab.isTitleNeeded()) {
            mTitleCache.put(tabId, getTitleBitmap(tab), getFaviconBitmap(tab), tab.isIncognito(),
                    tab.isTitleDirectionRtl());
        }
    }

    /**
     * Builds a title bitmap for a {@link Tab}. This function does not do anything in the
     * general case because only the phone need to bake special resource.
     *
     * @param tab The tab to build the title bitmap for.
     * @return The Title bitmap
     */
    protected Bitmap getTitleBitmap(Tab tab) {
        TitleBitmapFactory titleBitmapFactory =
                tab.isIncognito() ? mIncognitoTitleBitmapFactory : mStandardTitleBitmapFactory;

        return titleBitmapFactory.getTitleBitmap(mHost.getContext(), getTitleForTab(tab));
    }

    /**
     * Comes up with a valid title to return for a tab.
     * @param tab The {@link Tab} to build a title for.
     * @return    The title to use.
     */
    protected String getTitleForTab(Tab tab) {
        String title = tab.getTitle();
        if (TextUtils.isEmpty(title)) title = tab.getUrl();
        return title;
    }

    /**
     * Builds a favicon bitmap for a {@link Tab}. This function does not do anything in the
     * general case because only the phone need to bake special texture.
     *
     * @param tab The tab to build the title bitmap for.
     * @return The Favicon bitmap
     */
    protected Bitmap getFaviconBitmap(Tab tab) {
        TitleBitmapFactory titleBitmapFactory =
                tab.isIncognito() ? mIncognitoTitleBitmapFactory : mStandardTitleBitmapFactory;
        return titleBitmapFactory.getFaviconBitmap(mHost.getContext(), tab.getFavicon());
    }

    /**
     * @return The {@link OverviewListLayout} managed by this class.
     */
    @VisibleForTesting
    public Layout getOverviewListLayout() {
        return mOverviewListLayout;
    }

    /**
     * @return The overview layout {@link Layout} managed by this class.
     */
    @VisibleForTesting
    public Layout getOverviewLayout() {
        return mOverviewLayout;
    }

    /**
     * @return The {@link StripLayoutHelperManager} managed by this class.
     */
    @VisibleForTesting
    public StripLayoutHelperManager getStripLayoutHelperManager() {
        return null;
    }

    /**
     * @return Whether or not to use the accessibility layout.
     */
    protected boolean useAccessibilityLayout() {
        return DeviceClassManager.isAccessibilityModeEnabled(mHost.getContext())
                || DeviceClassManager.enableAccessibilityLayout();
    }

    /**
     * Show the overview {@link Layout}.  This is generally a {@link Layout} that visibly represents
     * all of the {@link Tab}s opened by the user.
     * @param animate Whether or not to animate the transition to overview mode.
     */
    public void showOverview(boolean animate) {
        boolean useAccessibility = useAccessibilityLayout();

        boolean accessibilityIsVisible =
                useAccessibility && getActiveLayout() == mOverviewListLayout;
        boolean normalIsVisible = getActiveLayout() == mOverviewLayout && mOverviewLayout != null;

        // We only want to use the AccessibilityOverviewLayout if the following are all valid:
        // 1. We're already showing the AccessibilityOverviewLayout OR we're using accessibility.
        // 2. We're not already showing the normal OverviewLayout (or we are on a tablet, in which
        //    case the normal layout is always visible).
        if ((accessibilityIsVisible || useAccessibility) && !normalIsVisible) {
            startShowing(mOverviewListLayout, animate);
        } else if (mOverviewLayout != null) {
            startShowing(mOverviewLayout, animate);
        }
    }

    /**
     * Hides the current {@link Layout}, returning to the default {@link Layout}.
     * @param animate Whether or not to animate the transition to the default {@link Layout}.
     */
    public void hideOverview(boolean animate) {
        Layout activeLayout = getActiveLayout();
        if (activeLayout != null && !activeLayout.isHiding()) {
            if (animate) {
                activeLayout.onTabSelecting(time(), Tab.INVALID_TAB_ID);
            } else {
                startHiding(Tab.INVALID_TAB_ID, false);
                doneHiding();
            }
        }
    }

    /**
     * @param enabled Whether or not to allow model-reactive animations (tab creation, closing,
     *                etc.).
     */
    public void setEnableAnimations(boolean enabled) {
        mEnableAnimations = enabled;
    }

    /**
     * @return Whether animations should be done for model changes.
     */
    @VisibleForTesting
    public boolean animationsEnabled() {
        return mEnableAnimations;
    }

    @Override
    public boolean overviewVisible() {
        Layout activeLayout = getActiveLayout();
        return isOverviewLayout(activeLayout) && !activeLayout.isHiding();
    }

    @Override
    public void addOverviewModeObserver(OverviewModeObserver listener) {
        mOverviewModeObservers.addObserver(listener);
    }

    @Override
    public void removeOverviewModeObserver(OverviewModeObserver listener) {
        mOverviewModeObservers.removeObserver(listener);
    }

    /**
     * A {@link EdgeSwipeHandler} meant to respond to edge events for the toolbar.
     */
    protected class ToolbarSwipeHandler extends EdgeSwipeHandlerLayoutDelegate {
        /**
         * Creates an instance of the {@link ToolbarSwipeHandler}.
         * @param provider A {@link LayoutProvider} instance.
         */
        public ToolbarSwipeHandler(LayoutProvider provider) {
            super(provider);
        }

        @Override
        public void swipeStarted(ScrollDirection direction, float x, float y) {
            if (direction == ScrollDirection.DOWN) {
                startShowing(mOverviewLayout, true);
                super.swipeStarted(direction, x, y);
            } else if (direction == ScrollDirection.LEFT || direction == ScrollDirection.RIGHT) {
                startShowing(mToolbarSwipeLayout, true);
                super.swipeStarted(direction, x, y);
            }
        }

        @Override
        public boolean isSwipeEnabled(ScrollDirection direction) {
            FullscreenManager manager = mHost.getFullscreenManager();
            if (getActiveLayout() != mStaticLayout
                    || !DeviceClassManager.enableToolbarSwipe(
                               FeatureUtilities.isDocumentMode(mHost.getContext()))
                    || (manager != null && manager.getPersistentFullscreenMode())) {
                return false;
            }

            boolean isAccessibility =
                    DeviceClassManager.isAccessibilityModeEnabled(mHost.getContext());
            return direction == ScrollDirection.LEFT || direction == ScrollDirection.RIGHT
                    || (direction == ScrollDirection.DOWN && mOverviewLayout != null
                               && !isAccessibility);
        }
    }

    /**
     * @param id The id of the {@link Tab} to search for.
     * @return   A {@link Tab} instance or {@code null} if it could be found.
     */
    protected Tab getTabById(int id) {
        TabModelSelector selector = getTabModelSelector();
        return selector == null ? null : selector.getTabById(id);
    }
}
