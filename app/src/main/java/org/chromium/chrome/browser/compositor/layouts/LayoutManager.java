// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ObserverList;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.Layout.Orientation;
import org.chromium.chrome.browser.compositor.layouts.Layout.SizingFlags;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilterHost;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.content.browser.SPenSupport;
import org.chromium.ui.resources.ResourceManager;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

import java.util.List;

/**
 * A class that is responsible for managing an active {@link Layout} to show to the screen.  This
 * includes lifecycle managment like showing/hiding this {@link Layout}.
 */
public abstract class LayoutManager implements LayoutUpdateHost, LayoutProvider, EventFilterHost {
    /** Sampling at 60 fps. */
    private static final long FRAME_DELTA_TIME_MS = 16;

    /** Used to convert pixels to dp. */
    protected final float mPxToDp;

    /** The {@link LayoutManagerHost}, who is responsible for showing the active {@link Layout}. */
    protected final LayoutManagerHost mHost;

    /** The last X coordinate of the last {@link MotionEvent#ACTION_DOWN} event. */
    protected int mLastTapX;

    /** The last Y coordinate of the last {@link MotionEvent#ACTION_DOWN} event. */
    protected int mLastTapY;

    // External Dependencies
    private TabModelSelector mTabModelSelector;
    private ViewGroup mContentContainer;

    // External Observers
    private final ObserverList<SceneChangeObserver> mSceneChangeObservers;

    // Current Layout State
    private Layout mActiveLayout;
    private Layout mNextActiveLayout;

    // Current Event Fitler State
    private EventFilter mActiveEventFilter;

    // Internal State
    private int mFullscreenToken = FullscreenManager.INVALID_TOKEN;
    private boolean mUpdateRequested;

    // Sizing State
    private final Rect mLastViewportPx = new Rect();
    private final Rect mLastVisibleViewportPx = new Rect();
    private final Rect mLastFullscreenViewportPx = new Rect();
    protected final RectF mLastViewportDp = new RectF();
    protected final RectF mLastVisibleViewportDp = new RectF();
    protected final RectF mLastFullscreenViewportDp = new RectF();

    protected float mLastContentWidthDp;
    protected float mLastContentHeightDp;
    protected float mLastHeightMinusTopControlsDp;

    private final RectF mCachedRectF = new RectF();
    private final Rect mCachedRect = new Rect();
    private final Point mCachedPoint = new Point();

    /**
     * Creates a {@link LayoutManager} instance.
     * @param host A {@link LayoutManagerHost} instance.
     */
    public LayoutManager(LayoutManagerHost host) {
        mHost = host;
        mPxToDp = 1.f / mHost.getContext().getResources().getDisplayMetrics().density;
        mSceneChangeObservers = new ObserverList<SceneChangeObserver>();

        int hostWidth = host.getWidth();
        int hostHeight = host.getHeight();
        mLastViewportPx.set(0, 0, hostWidth, hostHeight);
        mLastVisibleViewportPx.set(0, 0, hostWidth, hostHeight);
        mLastFullscreenViewportPx.set(0, 0, hostWidth, hostHeight);

        mLastContentWidthDp = hostWidth * mPxToDp;
        mLastContentHeightDp = hostHeight * mPxToDp;
        mLastViewportDp.set(0, 0, mLastContentWidthDp, mLastContentHeightDp);
        mLastVisibleViewportDp.set(0, 0, mLastContentWidthDp, mLastContentHeightDp);
        mLastFullscreenViewportDp.set(0, 0, mLastContentWidthDp, mLastContentHeightDp);

        mLastHeightMinusTopControlsDp = mLastContentHeightDp;
    }

    /**
     * @return The actual current time of the app in ms.
     */
    public static long time() {
        return SystemClock.uptimeMillis();
    }

    /**
     * Gives the {@link LayoutManager} a chance to intercept and process touch events from the
     * Android {@link View} system.
     * @param e                 The {@link MotionEvent} that might be intercepted.
     * @param isKeyboardShowing Whether or not the keyboard is showing.
     * @return                  Whether or not this current touch gesture should be intercepted and
     *                          continually forwarded to this class.
     */
    public boolean onInterceptTouchEvent(MotionEvent e, boolean isKeyboardShowing) {
        if (mActiveLayout == null) return false;

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            mLastTapX = (int) e.getX();
            mLastTapY = (int) e.getY();
        }

        Point offsets = getMotionOffsets(e);
        mActiveEventFilter =
                mActiveLayout.findInterceptingEventFilter(e, offsets, isKeyboardShowing);
        if (mActiveEventFilter != null) mActiveLayout.unstallImmediately();
        return mActiveEventFilter != null;
    }

    /**
     * Gives the {@link LayoutManager} a chance to process the touch events from the Android
     * {@link View} system.
     * @param e A {@link MotionEvent} instance.
     * @return  Whether or not {@code e} was consumed.
     */
    public boolean onTouchEvent(MotionEvent e) {
        if (mActiveEventFilter == null) return false;

        boolean consumed = mActiveEventFilter.onTouchEvent(e);
        Point offsets = getMotionOffsets(e);
        if (offsets != null) mActiveEventFilter.setCurrentMotionEventOffsets(offsets.x, offsets.y);
        return consumed;
    }

    @Override
    public boolean propagateEvent(MotionEvent e) {
        if (e == null) return false;

        View view = getActiveLayout().getViewForInteraction();
        if (view == null) return false;

        e.offsetLocation(-view.getLeft(), -view.getTop());
        return view.dispatchTouchEvent(e);
    }

    @Override
    public int getViewportWidth() {
        return mHost.getWidth();
    }

    private Point getMotionOffsets(MotionEvent e) {
        int actionMasked = e.getActionMasked();
        if (SPenSupport.isSPenSupported(mHost.getContext())) {
            actionMasked = SPenSupport.convertSPenEventAction(actionMasked);
        }

        if (actionMasked == MotionEvent.ACTION_DOWN
                || actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
            getViewportPixel(mCachedRect);

            mCachedPoint.set(-mCachedRect.left, -mCachedRect.top);
            return mCachedPoint;
        } else if (actionMasked == MotionEvent.ACTION_UP
                || actionMasked == MotionEvent.ACTION_CANCEL
                || actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
            mCachedPoint.set(0, 0);
            return mCachedPoint;
        }

        return null;
    }

    /**
     * Updates the state of the active {@link Layout} if needed.  This updates the animations and
     * cascades the changes to the tabs.
     */
    public void onUpdate() {
        TraceEvent.begin("LayoutDriver:onUpdate");
        onUpdate(time(), FRAME_DELTA_TIME_MS);
        TraceEvent.end("LayoutDriver:onUpdate");
    }

    /**
     * Updates the state of the layout.
     * @param timeMs The time in milliseconds.
     * @param dtMs   The delta time since the last update in milliseconds.
     * @return       Whether or not the {@link LayoutManager} needs more updates.
     */
    @VisibleForTesting
    public boolean onUpdate(long timeMs, long dtMs) {
        if (!mUpdateRequested) return false;
        mUpdateRequested = false;
        final Layout layout = getActiveLayout();
        if (layout != null && layout.onUpdate(timeMs, dtMs) && layout.isHiding()) {
            layout.doneHiding();
        }
        return mUpdateRequested;
    }

    /**
     * Initializes the {@link LayoutManager}.  Must be called before using this object.
     * @param selector                 A {@link TabModelSelector} instance.
     * @param creator                  A {@link TabCreatorManager} instance.
     * @param content                  A {@link TabContentManager} instance.
     * @param androidContentContainer  A {@link ViewGroup} for Android views to be bound to.
     * @param contextualSearchDelegate A {@link ContextualSearchDelegate} instance.
     * @param dynamicResourceLoader    A {@link DynamicResourceLoader} instance.
     */
    public void init(TabModelSelector selector, TabCreatorManager creator,
            TabContentManager content, ViewGroup androidContentContainer,
            ContextualSearchManagementDelegate contextualSearchDelegate,
            DynamicResourceLoader dynamicResourceLoader) {
        mTabModelSelector = selector;
        mContentContainer = androidContentContainer;

        if (mNextActiveLayout != null) startShowing(mNextActiveLayout, true);
    }

    /**
     * Cleans up and destroys this object.  It should not be used after this.
     */
    public void destroy() {
        mSceneChangeObservers.clear();
    }

    /**
     * @param observer Adds {@code observer} to be notified when the active {@code Layout} changes.
     */
    public void addSceneChangeObserver(SceneChangeObserver observer) {
        mSceneChangeObservers.addObserver(observer);
    }

    /**
     * @param observer Removes {@code observer}.
     */
    public void removeSceneChangeObserver(SceneChangeObserver observer) {
        mSceneChangeObservers.removeObserver(observer);
    }

    @Override
    public SceneLayer getUpdatedActiveSceneLayer(Rect viewport, Rect contentViewport,
            LayerTitleCache layerTitleCache, TabContentManager tabContentManager,
            ResourceManager resourceManager, ChromeFullscreenManager fullscreenManager) {
        return mActiveLayout.getUpdatedSceneLayer(viewport, contentViewport, layerTitleCache,
                tabContentManager, resourceManager, fullscreenManager);
    }

    /**
     * Called when the viewport has been changed.  Override this to be notified when
     * {@link #pushNewViewport(Rect, Rect, int)} calls actually change the current viewport.
     * @param viewportDp The new viewport in dp.
     */
    protected void onViewportChanged(RectF viewportDp) {
        if (getActiveLayout() != null) {
            getActiveLayout().sizeChanged(viewportDp, mLastVisibleViewportDp,
                    mLastFullscreenViewportDp, mLastHeightMinusTopControlsDp, getOrientation());
        }
    }

    /**
     * Should be called from an external source when the viewport changes.  {@code viewport} and
     * {@code visibleViewport} are different, as the top controls might be covering part of the
     * viewport but a {@link Layout} might want to consume the whole space (or not).
     * @param viewport               The new viewport in px.
     * @param visibleViewport        The new visible viewport in px.
     * @param heightMinusTopControls The height of the viewport minus the top controls.
     */
    public final void pushNewViewport(
            Rect viewport, Rect visibleViewport, int heightMinusTopControls) {
        mLastViewportPx.set(viewport);
        mLastVisibleViewportPx.set(visibleViewport);

        mLastViewportDp.set(viewport.left * mPxToDp, viewport.top * mPxToDp,
                viewport.right * mPxToDp, viewport.bottom * mPxToDp);
        mLastVisibleViewportDp.set(visibleViewport.left * mPxToDp, visibleViewport.top * mPxToDp,
                visibleViewport.right * mPxToDp, visibleViewport.bottom * mPxToDp);
        mLastFullscreenViewportDp.set(0, 0, viewport.right * mPxToDp, viewport.bottom * mPxToDp);
        mLastHeightMinusTopControlsDp = heightMinusTopControls * mPxToDp;

        propagateViewportToActiveLayout();
    }

    /**
     * @return The default {@link Layout} to show when {@link Layout}s get hidden and the next
     *         {@link Layout} to show isn't known.
     */
    protected abstract Layout getDefaultLayout();

    // TODO(dtrainor): Remove these from this control class.  Split the interface?
    @Override public abstract void initLayoutTabFromHost(final int tabId);

    @Override
    public abstract LayoutTab createLayoutTab(int id, boolean incognito, boolean showCloseButton,
            boolean isTitleNeeded, float maxContentWidth, float maxContentHeight);

    @Override public abstract void releaseTabLayout(int id);

    /**
     * @return The {@link TabModelSelector} instance this class knows about.
     */
    protected TabModelSelector getTabModelSelector() {
        return mTabModelSelector;
    }

    /**
     * @return The next {@link Layout} that will be shown.  If no {@link Layout} has been set
     *         since the last time {@link #startShowing(Layout, boolean)} was called, this will be
     *         {@link #getDefaultLayout()}.
     */
    protected Layout getNextLayout() {
        return mNextActiveLayout != null ? mNextActiveLayout : getDefaultLayout();
    }

    @Override
    public Layout getActiveLayout() {
        return mActiveLayout;
    }

    @Override
    public RectF getViewportDp(RectF rect) {
        if (rect == null) rect = new RectF();

        if (getActiveLayout() == null) {
            rect.set(mLastViewportDp);
            return rect;
        }

        final int flags = getActiveLayout().getSizingFlags();
        if ((flags & SizingFlags.REQUIRE_FULLSCREEN_SIZE) != 0) {
            rect.set(mLastFullscreenViewportDp);
        } else if ((flags & SizingFlags.ALLOW_TOOLBAR_HIDE) != 0) {
            rect.set(mLastViewportDp);
        } else {
            rect.set(mLastVisibleViewportDp);
        }

        return rect;
    }

    @Override
    public Rect getViewportPixel(Rect rect) {
        if (rect == null) rect = new Rect();

        if (getActiveLayout() == null) {
            rect.set(mLastViewportPx);
            return rect;
        }

        final int flags = getActiveLayout().getSizingFlags();
        if ((flags & SizingFlags.REQUIRE_FULLSCREEN_SIZE) != 0) {
            rect.set(mLastFullscreenViewportPx);
        } else if ((flags & SizingFlags.ALLOW_TOOLBAR_HIDE) != 0) {
            rect.set(mLastViewportPx);
        } else {
            rect.set(mLastVisibleViewportPx);
        }
        return rect;
    }

    @Override
    public ChromeFullscreenManager getFullscreenManager() {
        return mHost != null ? mHost.getFullscreenManager() : null;
    }

    @Override
    public void requestUpdate() {
        if (!mUpdateRequested) mHost.requestRender();
        mUpdateRequested = true;
    }

    @Override
    public void startHiding(int nextTabId, boolean hintAtTabSelection) {
        requestUpdate();
        if (hintAtTabSelection) {
            for (SceneChangeObserver observer : mSceneChangeObservers) {
                observer.onTabSelectionHinted(nextTabId);
            }
        }
    }

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    @Override
    public void doneHiding() {
        // TODO: If next layout is default layout clear caches (should this be a sub layout thing?)

        assert mNextActiveLayout != null : "Need to have a next active layout.";
        if (mNextActiveLayout != null) {
            startShowing(mNextActiveLayout, true);
        }
    }

    @Override
    public void doneShowing() {}

    /**
     * Should be called by control logic to show a new {@link Layout}.
     *
     * TODO(dtrainor, clholgat): Clean up the show logic to guarantee startHiding/doneHiding get
     * called.
     *
     * @param layout  The new {@link Layout} to show.
     * @param animate Whether or not {@code layout} should animate as it shows.
     */
    protected void startShowing(Layout layout, boolean animate) {
        assert mTabModelSelector != null : "init() must be called first.";
        assert layout != null : "Can't show a null layout.";

        // Set the new layout
        setNextLayout(null);
        Layout oldLayout = getActiveLayout();
        if (oldLayout != layout) {
            if (oldLayout != null) {
                oldLayout.detachViews();
            }
            layout.contextChanged(mHost.getContext());
            layout.attachViews(mContentContainer);
            mActiveLayout = layout;
        }

        ChromeFullscreenManager fullscreenManager = mHost.getFullscreenManager();
        if (fullscreenManager != null) {
            // Release any old fullscreen token we were holding.
            fullscreenManager.hideControlsPersistent(mFullscreenToken);
            mFullscreenToken = FullscreenManager.INVALID_TOKEN;

            // Grab a new fullscreen token if this layout can't be in fullscreen.
            final int flags = getActiveLayout().getSizingFlags();
            if ((flags & SizingFlags.ALLOW_TOOLBAR_HIDE) == 0) {
                mFullscreenToken = fullscreenManager.showControlsPersistent();
            }

            // Hide the toolbar immediately if the layout wants it gone quickly.
            fullscreenManager.setTopControlsPermamentlyHidden(
                    flags == SizingFlags.HELPER_HIDE_TOOLBAR_IMMEDIATE);
        }

        propagateViewportToActiveLayout();
        getActiveLayout().show(time(), animate);
        mHost.setContentOverlayVisibility(getActiveLayout().shouldDisplayContentOverlay());
        mHost.requestRender();

        // Notify observers about the new scene.
        for (SceneChangeObserver observer : mSceneChangeObservers) {
            observer.onSceneChange(getActiveLayout());
        }
    }

    /**
     * Sets the next {@link Layout} to show after the current {@link Layout} is finished and is done
     * hiding.
     * @param layout The new {@link Layout} to show.
     */
    public void setNextLayout(Layout layout) {
        mNextActiveLayout = (layout == null) ? getDefaultLayout() : layout;
    }

    @Override
    public boolean isActiveLayout(Layout layout) {
        return layout == mActiveLayout;
    }

    /**
     * Get a list of virtual views for accessibility.
     *
     * @param views A List to populate with virtual views.
     */
    public abstract void getVirtualViews(List<VirtualView> views);

    /**
     * @return The {@link EdgeSwipeHandler} responsible for processing swipe events for the toolbar.
     */
    public abstract EdgeSwipeHandler getTopSwipeHandler();

    /**
     * Should be called when the user presses the back button on the phone.
     * @return Whether or not the back button was consumed by the active {@link Layout}.
     */
    public abstract boolean onBackPressed();

    private void propagateViewportToActiveLayout() {
        getViewportDp(mCachedRectF);

        float width = mCachedRectF.width();
        float height = mCachedRectF.height();
        mLastContentWidthDp = width;
        mLastContentHeightDp = height;
        onViewportChanged(mCachedRectF);
    }

    private int getOrientation() {
        if (mLastContentWidthDp > mLastContentHeightDp) {
            return Orientation.LANDSCAPE;
        } else {
            return Orientation.PORTRAIT;
        }
    }
}
