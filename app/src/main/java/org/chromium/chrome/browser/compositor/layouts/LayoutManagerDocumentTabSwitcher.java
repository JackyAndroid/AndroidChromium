// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import org.chromium.base.ObserverList;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.compositor.TitleCache;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.BlackHoleEventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.GestureEventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.GestureHandler;
import org.chromium.chrome.browser.compositor.layouts.phone.StackLayout;
import org.chromium.chrome.browser.compositor.overlays.SceneOverlay;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.dom_distiller.ReaderModeManagerDelegate;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.OverviewListLayout;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

/**
 * A {@link Layout} controller for enabling tab switcher on document mode.
 * Note that lots of fields and methods here intentionally duplicate those of LayoutManagerChrome
 * without further refactoring them because because this is for a UX experiment, and
 * we might scrap this as a whole.
 * See https://crbug.com/520327 for more.
 */
public class LayoutManagerDocumentTabSwitcher
        extends LayoutManagerDocument implements OverviewModeBehavior {
    /** A {@link Layout} that should be used when the user is swiping sideways on the toolbar. */
    private final OverviewListLayout mOverviewListLayout;
    private final StackLayout mOverviewLayout;

    // Event Filter Handlers
    /** A {@link GestureHandler} that will delegate all events to {@link #getActiveLayout()}. */
    private final BlackHoleEventFilter mBlackHoleEventFilter;
    private final GestureEventFilter mGestureEventFilter;

    private TitleCache mTitleCache;
    private final ObserverList<OverviewModeObserver> mOverviewModeObservers =
            new ObserverList<OverviewModeObserver>();

    /**
     * Creates a {@link LayoutManagerDocumentTabSwitcher} instance.
     * @param host            A {@link LayoutManagerHost} instance.
     */
    public LayoutManagerDocumentTabSwitcher(LayoutManagerHost host) {
        super(host);
        Context context = host.getContext();
        LayoutRenderHost renderHost = host.getLayoutRenderHost();

        mBlackHoleEventFilter = new BlackHoleEventFilter(context, this);
        mGestureEventFilter = new GestureEventFilter(context, this, mGestureHandler);
        mOverviewListLayout =
                new OverviewListLayout(context, this, renderHost, mBlackHoleEventFilter);
        mOverviewLayout = new StackLayout(context, this, renderHost, mGestureEventFilter);
    }

    @Override
    public void init(TabModelSelector selector, TabCreatorManager creator,
            TabContentManager content, ViewGroup androidContentContainer,
            ContextualSearchManagementDelegate contextualSearchDelegate,
            ReaderModeManagerDelegate readerModeManagerDelegate,
            DynamicResourceLoader dynamicResourceLoader) {
        super.init(selector, creator, content, androidContentContainer, contextualSearchDelegate,
                readerModeManagerDelegate, dynamicResourceLoader);

        mTitleCache = mHost.getTitleCache();
        TabModelSelector documentTabSelector = ChromeApplication.getDocumentTabModelSelector();
        mOverviewListLayout.setTabModelSelector(documentTabSelector, content);
        mOverviewLayout.setTabModelSelector(documentTabSelector, content);

        // TODO(changwan): do we really need this?
        startShowing(getDefaultLayout(), false);
    }

    @Override
    public void destroy() {
        super.destroy();

        if (mOverviewListLayout != null) mOverviewListLayout.destroy();
        if (mOverviewLayout != null) mOverviewLayout.destroy();
    }

    /**
     * Adds the {@link SceneOverlay} across all {@link Layout}s owned by this class.
     * @param helper A {@link SceneOverlay} instance.
     */
    @Override
    protected void addGlobalSceneOverlay(SceneOverlay helper) {
        super.addGlobalSceneOverlay(helper);
        if (mOverviewListLayout != null) {
            mOverviewListLayout.addSceneOverlay(helper);
        }
        if (mOverviewLayout != null) {
            mOverviewLayout.addSceneOverlay(helper);
        }
    }

    @Override
    public void initLayoutTabFromHost(final int tabId) {
        if (mTitleCache != null) {
            mTitleCache.remove(tabId);
        }
        super.initLayoutTabFromHost(tabId);
    }

    @Override
    public void releaseTabLayout(int id) {
        super.releaseTabLayout(id);
        if (mTitleCache != null) mTitleCache.remove(id);
    }

    @Override
    public void doneHiding() {
        super.doneHiding();
        // Remove transition animation when switching to another tab, in accordance to
        // moveToFront() in ActivityDelegate.
        Activity activity = (Activity) mHost.getContext();
        if (activity != null) {
            activity.overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void startShowing(Layout layout, boolean animate) {
        super.startShowing(layout, animate);

        Layout layoutBeingShown = getActiveLayout();

        // Check if we should notify OverviewModeObservers.
        if (isOverviewLayout(layoutBeingShown)) {
            boolean showToolbar = false;
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
            if (layoutBeingHidden == mOverviewLayout) {
                final LayoutTab tab = layoutBeingHidden.getLayoutTab(nextTabId);
                // Note: this value is reversed in LayoutManagerChrome#startHiding.
                showToolbar = tab != null ? tab.showToolbar() : true;
            }

            boolean delayAnimation = false;
            for (OverviewModeObserver observer : mOverviewModeObservers) {
                observer.onOverviewModeStartedHiding(showToolbar, delayAnimation);
            }
        }
    }

    private boolean isOverviewLayout(Layout layout) {
        return layout != null && (layout == mOverviewLayout || layout == mOverviewListLayout);
    }

    @Override
    public void addOverviewModeObserver(OverviewModeObserver listener) {
        mOverviewModeObservers.addObserver(listener);
    }

    @Override
    public void removeOverviewModeObserver(OverviewModeObserver listener) {
        mOverviewModeObservers.removeObserver(listener);
    }

    @Override
    public boolean overviewVisible() {
        Layout activeLayout = getActiveLayout();
        return isOverviewLayout(activeLayout) && !activeLayout.isHiding();
    }

    public void toggleOverview() {
        Tab tab = getTabModelSelector().getCurrentTab();
        ContentViewCore contentViewCore = tab != null ? tab.getContentViewCore() : null;

        if (!overviewVisible()) {
            mHost.hideKeyboard(new Runnable() {
                @Override
                public void run() {
                    showOverview(true);
                }
            });
            if (contentViewCore != null) {
                contentViewCore.setAccessibilityState(false);
            }
        } else {
            Layout activeLayout = getActiveLayout();
            if (activeLayout instanceof StackLayout) {
                ((StackLayout) activeLayout).commitOutstandingModelState(LayoutManager.time());
            }
            if (getTabModelSelector().getCurrentModel().getCount() != 0) {
                // Don't hide overview if current tab stack is empty()
                hideOverview(true);

                // hideOverview could change the current tab.  Update the local variables.
                tab = getTabModelSelector().getCurrentTab();
                contentViewCore = tab != null ? tab.getContentViewCore() : null;

                if (contentViewCore != null) {
                    contentViewCore.setAccessibilityState(true);
                }
            }
        }
    }

    /**
     * @return Whether or not to use the accessibility layout.
     */
    private boolean useAccessibilityLayout() {
        return DeviceClassManager.isAccessibilityModeEnabled(mHost.getContext())
                || DeviceClassManager.enableAccessibilityLayout();
    }

    /**
     * Show the overview {@link Layout}.  This is generally a {@link Layout} that visibly represents
     * all of the {@link Tab}s opened by the user.
     * @param animate Whether or not to animate the transition to overview mode.
     */
    private void showOverview(boolean animate) {
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
    private void hideOverview(boolean animate) {
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
}
