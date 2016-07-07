// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import static org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.AnimatableAnimation.createAnimation;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animation;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.overlays.SceneOverlay;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.compositor.scene_layer.SceneOverlayLayer;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract representation of an OpenGL layout tailored to draw tabs. It is a framework used as an
 * alternative to the Android UI for lower level hardware accelerated rendering.
 * This layout also pass through all the events that may happen.
 */

public abstract class Layout implements TabContentManager.ThumbnailChangeListener {
    /**
     * The orientation of the device.
     */
    public interface Orientation {
        public static final int UNSET = 0;
        public static final int PORTRAIT = 1;
        public static final int LANDSCAPE = 2;
    }

    /**
     * The sizing requirements of each layout.  These flags allow each layout to specify certain
     * sizing allowances/constraints by combining them.
     *
     * TODO(dtrainor): Flesh out support for all of these flag combinations.
     */
    public interface SizingFlags {
        /**
         * The toolbar is allowed to be hidden.
         */
        public static final int ALLOW_TOOLBAR_HIDE = 0x1;

        /**
         * The toolbar is allowed to be shown.
         */
        public static final int ALLOW_TOOLBAR_SHOW = 0x10;

        /**
         * The toolbar is allowed to animate between the transitions. Note that this flag is not
         * completely supported in all combinations with the other flags.  As functionality is
         * added and required this will be improved.
         */
        public static final int ALLOW_TOOLBAR_ANIMATE = 0x100;

        /**
         * The layout requires a fullscreen size regardless of the toolbar position.
         */
        public static final int REQUIRE_FULLSCREEN_SIZE = 0x1000;

        /**
         * The layout does not support fullscreen and it should animate the toolbar away.
         * A helper flag to make it easier to specify restrictions.
         */
        public static final int HELPER_NO_FULLSCREEN_SUPPORT =
                ALLOW_TOOLBAR_SHOW | ALLOW_TOOLBAR_ANIMATE;

        /**
         * The layout supports persistent fullscreen and should hide the toolbar immediately.
         * A helper flag to make it easier to specify restrictions.
         */
        public static final int HELPER_HIDE_TOOLBAR_IMMEDIATE = ALLOW_TOOLBAR_HIDE;

        /**
         * The layout fully supports fullscreen and the toolbar is allowed to show, hide, and
         * animate.
         * A helper flag to make it easier to specify restrictions.
         */
        public static final int HELPER_SUPPORTS_FULLSCREEN =
                ALLOW_TOOLBAR_HIDE | ALLOW_TOOLBAR_SHOW | ALLOW_TOOLBAR_ANIMATE;
    }

    // Defines to make the code easier to read.
    public static final boolean NEED_TITLE = true;
    public static final boolean NO_TITLE = false;
    public static final boolean SHOW_CLOSE_BUTTON = true;
    public static final boolean NO_CLOSE_BUTTON = false;

    /** Length of the unstalling animation. **/
    public static final long UNSTALLED_ANIMATION_DURATION_MS = 500;

    // Drawing area properties.
    private float mWidth;
    private float mHeight;
    private float mHeightMinusTopControls;

    /** A {@link Context} instance. */
    private Context mContext;

    /** The current {@link Orientation} of the layout. */
    private int mCurrentOrientation;

    // Tabs
    protected TabModelSelector mTabModelSelector;
    protected TabContentManager mTabContentManager;

    private ChromeAnimation<Animatable<?>> mLayoutAnimations;

    // Tablet tab strip managers.
    private final List<SceneOverlay> mSceneOverlays = new ArrayList<SceneOverlay>();

    // Helpers
    private final LayoutUpdateHost mUpdateHost;
    protected final LayoutRenderHost mRenderHost;
    private EventFilter mEventFilter;

    /** The tabs currently being rendered as part of this layout. The tabs are
     * drawn using the same ordering as this array. */
    protected LayoutTab[] mLayoutTabs;

    // True means that the layout is going to hide as soon as the animation finishes.
    private boolean mIsHiding;

    // The next id to show when the layout is hidden, or TabBase#INVALID_TAB_ID if no change.
    private int mNextTabId = Tab.INVALID_TAB_ID;

    /**
     * The {@link Layout} is not usable until sizeChanged is called.
     * This is convenient this way so we can pre-create the layout before the host is fully defined.
     * @param context      The current Android's context.
     * @param updateHost   The parent {@link LayoutUpdateHost}.
     * @param renderHost   The parent {@link LayoutRenderHost}.
     * @param eventFilter  The {@link EventFilter} this {@link Layout} is listening to.
     */
    public Layout(Context context, LayoutUpdateHost updateHost, LayoutRenderHost renderHost,
            EventFilter eventFilter) {
        mContext = context;
        mUpdateHost = updateHost;
        mRenderHost = renderHost;
        mEventFilter = eventFilter;

        // Invalid sizes
        mWidth = -1;
        mHeight = -1;
        mHeightMinusTopControls = -1;

        mCurrentOrientation = Orientation.UNSET;
    }

    /**
     * Adds a {@link SceneOverlay} that can potentially be shown on top of this {@link Layout}.  The
     * {@link SceneOverlay}s added to this {@link Layout} will be cascaded in the order they are
     * added.  The {@link SceneOverlay} added first will become the content of the
     * {@link SceneOverlay} added second, and so on.
     * @param helper A {@link SceneOverlay} to add as a potential overlay for this {@link Layout}.
     */
    public void addSceneOverlay(SceneOverlay helper) {
        assert !mSceneOverlays.contains(helper);
        mSceneOverlays.add(helper);
    }

    /**
     * Cleans up any internal state.  This object should not be used after this call.
     */
    public void destroy() {
    }

    /**
     * @return The current {@link Context} instance associated with this {@link Layout}.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * @return Whether the {@link Layout} is currently active.
     */
    public boolean isActive() {
        return mUpdateHost.isActiveLayout(this);
    }

    /**
     * @return A {@link View} that should be the one the user can interact with.  This can be
     *         {@code null} if there is no {@link View} representing {@link Tab} content that should
     *         be interacted with at this time.
     */
    public View getViewForInteraction() {
        if (!shouldDisplayContentOverlay()) return null;

        Tab tab = mTabModelSelector.getCurrentTab();
        if (tab == null) return null;

        return tab.getView();
    }

    /**
     * TODO(dtrainor): Remove after method is no longer used downstream.
     * Used to get a list of Android {@link View}s that represent both the normal content as well as
     * overlays.
     * @param views A {@link List} that will be populated with {@link View}s that represent all of
     *                the content in this {@link Layout}.
     */
    public void getAllViews(List<View> views) {
        getAllContentViews(views);
    }

    /**
     * Used to get a list of Android {@link View}s that represent both the normal content as well as
     * overlays.
     * @param views A {@link List} that will be populated with {@link View}s that represent all of
     *                the content in this {@link Layout}.
     */
    public void getAllContentViews(List<View> views) {
        Tab tab = mTabModelSelector.getCurrentTab();
        if (tab == null) return;
        tab.getAllContentViews(views);
    }

    /**
     * Used to get a list of {@link ContentViewCore}s that represent both the normal content as well
     * as overlays.  These are all {@link ContentViewCore}s currently showing or rendering content
     * for this {@link Layout}.
     * @param contents A {@link List} that will be populated with {@link ContentViewCore}s currently
     *                 rendering content related to this {@link Layout}.
     */
    public void getAllContentViewCores(List<ContentViewCore> contents) {
        Tab tab = mTabModelSelector.getCurrentTab();
        if (tab == null) return;
        tab.getAllContentViewCores(contents);
    }

    /**
     * Get a list of virtual views for accessibility.
     *
     * @param views A List to populate with virtual views.
     */
    public void getVirtualViews(List<VirtualView> views) {
        // TODO(dtrainor): Investigate order.
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).getVirtualViews(views);
        }
    }

    /**
     * Creates a {@link LayoutTab}.
     * @param id              The id of the reference {@link Tab} in the {@link TabModel}.
     * @param isIncognito     Whether the new tab is incognito.
     * @param showCloseButton True to show and activate a close button on the border.
     * @param isTitleNeeded   Whether a title will be shown.
     * @return                The newly created {@link LayoutTab}.
     */
    public LayoutTab createLayoutTab(int id, boolean isIncognito,
            boolean showCloseButton, boolean isTitleNeeded) {
        return createLayoutTab(id, isIncognito, showCloseButton, isTitleNeeded, -1.f, -1.f);
    }

    /**
     * Creates a {@link LayoutTab}.
     * @param id               The id of the reference {@link Tab} in the {@link TabModel}.
     * @param isIncognito      Whether the new tab is incognito.
     * @param showCloseButton  True to show and activate a close button on the border.
     * @param isTitleNeeded    Whether a title will be shown.
     * @param maxContentWidth  The max content width of the tab.  Negative numbers will use the
     *                         original content width.
     * @param maxContentHeight The max content height of the tab.  Negative numbers will use the
     *                         original content height.
     * @return                 The newly created {@link LayoutTab}.
     */
    public LayoutTab createLayoutTab(int id, boolean isIncognito, boolean showCloseButton,
            boolean isTitleNeeded, float maxContentWidth, float maxContentHeight) {
        LayoutTab layoutTab = mUpdateHost.createLayoutTab(
                id, isIncognito, showCloseButton, isTitleNeeded, maxContentWidth, maxContentHeight);
        initLayoutTabFromHost(layoutTab);
        return layoutTab;
    }

    /**
     * Releases the data we keep for that {@link LayoutTab}.
     * @param layoutTab The {@link LayoutTab} to release.
     */
    public void releaseTabLayout(LayoutTab layoutTab) {
        mUpdateHost.releaseTabLayout(layoutTab.getId());
    }

    /**
     * Update the animation and give chance to cascade the changes.
     * @param time The current time of the app in ms.
     * @param dt   The delta time between update frames in ms.
     * @return     Whether the layout is done updating.
     */
    public final boolean onUpdate(long time, long dt) {
        final boolean doneAnimating = onUpdateAnimation(time, false);

        // Don't update the layout if onUpdateAnimation ended up making a new layout active.
        if (mUpdateHost.isActiveLayout(this)) updateLayout(time, dt);

        return doneAnimating;
    }

    /**
     * Layout-specific updates. Cascades the values updated by the animations.
     * @param time The current time of the app in ms.
     * @param dt   The delta time between update frames in ms.
     */
    protected void updateLayout(long time, long dt) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).updateOverlay(time, dt);
        }
    }

    /**
     * Request that the renderer render a frame (after the current frame). This
     * should be called whenever a new frame should be rendered.
     */
    public void requestRender() {
        mRenderHost.requestRender();
    }

    /**
     * Requests one more frame of refresh for the transforms and changing properties. Primarily,
     * this is so animations can continue to animate.
     */
    public void requestUpdate() {
        mUpdateHost.requestUpdate();
    }

    /**
     * Called when the context and size of the view has changed.
     *
     * @param context     The current Android's context.
     */
    public void contextChanged(Context context) {
        mContext = context;
        LayoutTab.resetDimensionConstants(context);
    }

    /**
     * Called when the size of the viewport has changed.
     * @param availableViewport      The actual viewport this {@link Layout} should be rendering to.
     * @param visibleViewport        The visible viewport that represents the area on the screen
     *                               this {@link Layout} gets to draw to (potentially takes into
     *                               account top controls).
     * @param screenViewport         The viewport of the screen.
     * @param heightMinusTopControls The height the {@link Layout} gets excluding the height of the
     *                               top controls.  TODO(dtrainor): Look at getting rid of this.
     * @param orientation            The new orientation.  Valid values are defined by
     *                               {@link Orientation}.
     */
    public final void sizeChanged(RectF availableViewport, RectF visibleViewport,
            RectF screenViewport, float heightMinusTopControls, int orientation) {
        // 1. Pull out this Layout's specific width and height properties based on the available
        // viewport.
        float width = availableViewport.width();
        float height = availableViewport.height();

        // 2. Check if any Layout-specific properties have changed.
        boolean layoutPropertiesChanged = mWidth != width
                || mHeight != height
                || mHeightMinusTopControls != heightMinusTopControls
                || mCurrentOrientation != orientation;

        // 3. Update the internal sizing properties.
        mWidth = width;
        mHeight = height;
        mHeightMinusTopControls = heightMinusTopControls;
        mCurrentOrientation = orientation;

        // 4. Notify the actual Layout if necessary.
        if (layoutPropertiesChanged) {
            notifySizeChanged(width, height, orientation);
        }

        // 5. TODO(dtrainor): Notify the overlay objects.
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).onSizeChanged(width, height, visibleViewport.top, orientation);
        }
    }

    /**
     * Notifies when the size or the orientation of the view has actually changed.
     *
     * @param width       The new width in dp.
     * @param height      The new height in dp.
     * @param orientation The new orientation.
     */
    protected void notifySizeChanged(float width, float height, int orientation) { }

    /**
     * Notify the a title has changed.
     *
     * @param tabId The id of the tab that has changed.
     * @param title The new title.
     */
    public void tabTitleChanged(int tabId, String title) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabTitleChanged(tabId, title);
        }
    }

    /**
     * Sets the managers needed to for the layout to get information from outside. The managers
     * are tailored to be called from the GL thread.
     *
     * @param modelSelector The {@link TabModelSelector} to be set on the layout.
     * @param manager       The {@link TabContentManager} to get tab display content.
     */
    public void setTabModelSelector(TabModelSelector modelSelector, TabContentManager manager) {
        if (mTabContentManager != null) mTabContentManager.removeThumbnailChangeListener(this);
        mTabModelSelector = modelSelector;
        mTabContentManager = manager;
        if (mTabContentManager != null) mTabContentManager.addThumbnailChangeListener(this);
    }

    /**
     * @return Information about the sizing requirements of this layout.
     */
    public int getSizingFlags() {
        return SizingFlags.HELPER_NO_FULLSCREEN_SUPPORT;
    }

    /**
     * Informs this cache of the relevant {@link Tab} {@code id}s that will be used in the
     * near future.
     */
    protected void updateCacheVisibleIds(List<Integer> priority) {
        if (mTabContentManager != null) mTabContentManager.updateVisibleIds(priority);
    }

    /**
     * To be called when the layout is starting a transition out of the view mode.
     * @param nextTabId          The id of the next tab.
     * @param hintAtTabSelection Whether or not the new tab selection should be broadcast as a hint
     *                           potentially before this {@link Layout} is done hiding and the
     *                           selection occurs.
     */
    public void startHiding(int nextTabId, boolean hintAtTabSelection) {
        mUpdateHost.startHiding(nextTabId, hintAtTabSelection);
        mIsHiding = true;
        mNextTabId = nextTabId;
    }

    /**
     * @return True is the layout is in the process of hiding itself.
     */
    public boolean isHiding() {
        return mIsHiding;
    }

    /**
     * @return The incognito state of the layout.
     */
    public boolean isIncognito() {
        return mTabModelSelector.isIncognitoSelected();
    }

    /**
     * To be called when the transition into the layout is done.
     */
    public void doneShowing() {
        mUpdateHost.doneShowing();
    }

    /**
     * To be called when the transition out of the view mode is done.
     * This is currently called by the renderer when all the animation are done while hiding.
     */
    public void doneHiding() {
        mIsHiding = false;
        if (mNextTabId != Tab.INVALID_TAB_ID) {
            TabModel model = mTabModelSelector.getModelForTabId(mNextTabId);
            if (model != null) {
                TabModelUtils.setIndex(model, TabModelUtils.getTabIndexById(model, mNextTabId));
            }
            mNextTabId = Tab.INVALID_TAB_ID;
        }
        mUpdateHost.doneHiding();
    }

    /**
     * Called when a tab is getting selected. Typically when exiting the overview mode.
     * @param time  The current time of the app in ms.
     * @param tabId The id of the selected tab.
     */
    public void onTabSelecting(long time, int tabId) {
        startHiding(tabId, true);
    }

    /**
     * Initialize the layout to be shown.
     * @param time   The current time of the app in ms.
     * @param animate Whether to play an entry animation.
     */
    public void show(long time, boolean animate) {
        mIsHiding = false;
        mNextTabId = Tab.INVALID_TAB_ID;
    }

    /**
     * Hands the layout an Android view to attach it's views to.
     * @param container The Android View to attach the layout's views to.
     */
    public void attachViews(ViewGroup container) { }

    /**
     * Signal to the Layout to detach it's views from the container.
     */
    public void detachViews() { }

    /**
     * Called when the swipe animation get initiated. It gives a chance to initialize everything.
     * @param time      The current time of the app in ms.
     * @param direction The direction the swipe is in.
     * @param x         The horizontal coordinate the swipe started at in dp.
     * @param y         The vertical coordinate the swipe started at in dp.
     */
    public void swipeStarted(long time, ScrollDirection direction, float x, float y) { }

    /**
     * Updates a swipe gesture.
     * @param time The current time of the app in ms.
     * @param x    The horizontal coordinate the swipe is currently at in dp.
     * @param y    The vertical coordinate the swipe is currently at in dp.
     * @param dx   The horizontal delta since the last update in dp.
     * @param dy   The vertical delta since the last update in dp.
     * @param tx   The horizontal difference between the start and the current position in dp.
     * @param ty   The vertical difference between the start and the current position in dp.
     */
    public void swipeUpdated(long time, float x, float y, float dx, float dy, float tx, float ty) {
    }

    /**
     * Called when the swipe ends; most likely on finger up event. It gives a chance to start
     * an ending animation to exit the mode gracefully.
     * @param time The current time of the app in ms.
     */
    public void swipeFinished(long time) { }

    /**
     * Called when the user has cancelled a swipe; most likely if they have dragged their finger
     * back to the starting position.  Some handlers will throw swipeFinished() instead.
     * @param time The current time of the app in ms.
     */
    public void swipeCancelled(long time) { }

    /**
     * Fling from a swipe gesture.
     * @param time The current time of the app in ms.
     * @param x    The horizontal coordinate the swipe is currently at in dp.
     * @param y    The vertical coordinate the swipe is currently at in dp.
     * @param tx   The horizontal difference between the start and the current position in dp.
     * @param ty   The vertical difference between the start and the current position in dp.
     * @param vx   The horizontal velocity of the fling.
     * @param vy   The vertical velocity of the fling.
     */
    public void swipeFlingOccurred(long time, float x, float y, float tx, float ty, float vx,
            float vy) { }

    /**
     * Forces the current animation to finish and broadcasts the proper event.
     */
    protected void forceAnimationToFinish() {
        if (mLayoutAnimations != null) {
            mLayoutAnimations.updateAndFinish();
            mLayoutAnimations = null;
            onAnimationFinished();
        }
    }

    /**
     * @return The width of the drawing area.
     */
    public float getWidth() {
        return mWidth;
    }

    /**
     * @return The height of the drawing area.
     */
    public float getHeight() {
        return mHeight;
    }

    /**
     * @return The height of the drawing area minus the top controls.
     */
    public float getHeightMinusTopControls() {
        return mHeightMinusTopControls;
    }

    /**
     * @see Orientation
     * @return The orientation of the screen (portrait or landscape). Values are defined by
     *         {@link Orientation}.
     */
    public int getOrientation() {
        return mCurrentOrientation;
    }

    /**
     * @return Whether or not any tabs in this layer use the toolbar resource.
     */
    public boolean usesToolbarLayer() {
        if (mLayoutTabs == null) return false;
        for (int i = 0; i < mLayoutTabs.length; i++) {
            if (mLayoutTabs[i].showToolbar()) return true;
        }
        return false;
    }

    /**
     * @param currentOffset The current top controls offset in dp.
     * @return {@link Float#NaN} if no offset should be used, or a value in dp if the top controls
     *         offset should be overridden.
     */
    public float getTopControlsOffset(float currentOffset) {
        return Float.NaN;
    }

    /**
     * Initializes a {@link LayoutTab} with data from the {@link LayoutUpdateHost}. This function
     * eventually needs to be called but may be overridden to manage the posting traffic.
     *
     * @param layoutTab The {@link LayoutTab} To initialize from a
     *                  {@link Tab} on the UI thread.
     * @return          Whether the asynchronous initialization of the {@link LayoutTab} has really
     *                  been posted.
     */
    protected boolean initLayoutTabFromHost(LayoutTab layoutTab) {
        if (layoutTab.isInitFromHostNeeded()) {
            mUpdateHost.initLayoutTabFromHost(layoutTab.getId());
            return true;
        }
        return false;
    }

    /**
     * Called on touch drag event.
     * @param time      The current time of the app in ms.
     * @param x         The y coordinate of the end of the drag event.
     * @param y         The y coordinate of the end of the drag event.
     * @param deltaX    The number of pixels dragged in the x direction.
     * @param deltaY    The number of pixels dragged in the y direction.
     */
    public void drag(long time, float x, float y, float deltaX, float deltaY) { }

    /**
     * Called on touch fling event. This is called before the onUpOrCancel event.
     *
     * @param time      The current time of the app in ms.
     * @param x         The y coordinate of the start of the fling event.
     * @param y         The y coordinate of the start of the fling event.
     * @param velocityX The amount of velocity in the x direction.
     * @param velocityY The amount of velocity in the y direction.
     */
    public void fling(long time, float x, float y, float velocityX, float velocityY) { }

    /**
     * Called on onDown event.
     *
     * @param time      The time stamp in millisecond of the event.
     * @param x         The x position of the event.
     * @param y         The y position of the event.
     */
    public void onDown(long time, float x, float y) { }

    /**
     * Called on long press touch event.
     * @param time The current time of the app in ms.
     * @param x    The x coordinate of the position of the press event.
     * @param y    The y coordinate of the position of the press event.
     */
    public void onLongPress(long time, float x, float y) { }

    /**
     * Called on click. This is called before the onUpOrCancel event.
     * @param time The current time of the app in ms.
     * @param x    The x coordinate of the position of the click.
     * @param y    The y coordinate of the position of the click.
     */
    public void click(long time, float x, float y) { }

    /**
     * Called on up or cancel touch events. This is called after the click and fling event if any.
     * @param time The current time of the app in ms.
     */
    public void onUpOrCancel(long time) { }

    /**
     * Called when at least 2 touch events are detected.
     * @param time       The current time of the app in ms.
     * @param x0         The x coordinate of the first touch event.
     * @param y0         The y coordinate of the first touch event.
     * @param x1         The x coordinate of the second touch event.
     * @param y1         The y coordinate of the second touch event.
     * @param firstEvent The pinch is the first of a sequence of pinch events.
     */
    public void onPinch(long time, float x0, float y0, float x1, float y1, boolean firstEvent) { }

    /**
     * Called by the LayoutManager when an animation should be killed.
     */
    public void unstallImmediately() { }

    /**
     * Called by the LayoutManager when an animation should be killed.
     * @param tabId The tab that the kill signal is associated with
     */
    public void unstallImmediately(int tabId) { }

    /**
     * Called by the LayoutManager when they system back button is pressed.
     * @return Whether or not the layout consumed the event.
     */
    public boolean onBackPressed() {
        return false;
    }

    /**
     * Called when a tab get selected. Typically when a tab get closed and the new current tab get
     * selected.
     * @param time      The current time of the app in ms.
     * @param tabId     The id of the selected tab.
     * @param prevId    The id of the previously selected tab.
     * @param incognito Whether or not the affected model was incognito.
     */
    public void onTabSelected(long time, int tabId, int prevId, boolean incognito) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabSelected(time, incognito, tabId, prevId);
        }
    }

    /**
     * Called when a tab is about to be closed. When called, the closing tab will still
     * be part of the model.
     * @param time  The current time of the app in ms.
     * @param tabId The id of the tab being closed
     */
    public void onTabClosing(long time, int tabId) {
    }

    /**
     * Called when a tab is being closed. When called, the closing tab will not
     * be part of the model.
     * @param time      The current time of the app in ms.
     * @param tabId     The id of the tab being closed.
     * @param nextTabId The id if the tab that is being switched to.
     * @param incognito Whether or not the affected model was incognito.
     */
    public void onTabClosed(long time, int tabId, int nextTabId, boolean incognito) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabClosed(time, incognito, tabId);
        }
    }

    /**
     * Called when all the tabs in the current stack need to be closed.
     * When called, the tabs will still be part of the model.
     * @param time      The current time of the app in ms.
     * @param incognito True if this the incognito tab model should close all tabs, false otherwise.
     */
    public void onTabsAllClosing(long time, boolean incognito) {
    }

    /**
     * Called before a tab is created from the top left button.
     *
     * @param sourceTabId The id of the source tab.
     */
    public void onTabCreating(int sourceTabId) { }

    /**
     * Called when a tab is created from the top left button.
     * @param time           The current time of the app in ms.
     * @param tabId          The id of the newly created tab.
     * @param tabIndex       The index of the newly created tab.
     * @param sourceTabId    The id of the source tab.
     * @param newIsIncognito Whether the new tab is incognito.
     * @param background     Whether the tab is created in the background.
     * @param originX        The X screen coordinate in dp of the last touch down event that spawned
     *                       this tab.
     * @param originY        The Y screen coordinate in dp of the last touch down event that spawned
     *                       this tab.
     */
    public void onTabCreated(long time, int tabId, int tabIndex, int sourceTabId,
            boolean newIsIncognito, boolean background, float originX, float originY) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabCreated(time, newIsIncognito, tabId, sourceTabId, !background);
        }
    }

    /**
     * Called when the current tabModel switched (e.g. standard -> incognito).
     *
     * @param incognito True if the new model is incognito.
     */
    public void onTabModelSwitched(boolean incognito) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabModelSwitched(incognito);
        }
    }

    /**
     * Called when a tab has been moved in the tabModel.
     * @param time      The current time of the app in ms.
     * @param tabId     The id of the Tab.
     * @param oldIndex  The old index of the tab in the tabModel.
     * @param newIndex  The new index of the tab in the tabModel.
     * @param incognito True if the tab is incognito.
     */
    public void onTabMoved(long time, int tabId, int oldIndex, int newIndex, boolean incognito) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabMoved(time, incognito, tabId, oldIndex, newIndex);
        }
    }

    /**
     * Called when a tab has started loading.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito.
     */
    public void onTabPageLoadStarted(int id, boolean incognito) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabPageLoadStarted(id, incognito);
        }
    }

    /**
     * Called when a tab has finished loading.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito.
     */
    public void onTabPageLoadFinished(int id, boolean incognito) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabPageLoadFinished(id, incognito);
        }
    }

    /**
     * Called when a tab has started loading resources.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito.
     */
    public void onTabLoadStarted(int id, boolean incognito) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabLoadStarted(id, incognito);
        }
    }

    /**
     * Called when a tab has stopped loading resources.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito.
     */
    public void onTabLoadFinished(int id, boolean incognito) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabLoadFinished(id, incognito);
        }
    }

    /**
     * Called when a tab close has been undone and the tab has been restored.
     * @param time      The current time of the app in ms.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito
     */
    public void onTabClosureCancelled(long time, int id, boolean incognito) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            mSceneOverlays.get(i).tabClosureCancelled(time, incognito, id);
        }
    }

    /**
     * Called when a tab is finally closed if the action was previously undoable.
     * @param time      The current time of the app in ms.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito
     */
    public void onTabClosureCommitted(long time, int id, boolean incognito) { }

    @Override
    public void onThumbnailChange(int id) {
        requestUpdate();
    }

    /**
     * Steps the animation forward and updates all the animated values.
     * @param time      The current time of the app in ms.
     * @param jumpToEnd Whether to finish the animation.
     * @return          Whether the animation was finished.
     */
    protected boolean onUpdateAnimation(long time, boolean jumpToEnd) {
        boolean finished = true;
        if (mLayoutAnimations != null) {
            if (jumpToEnd) {
                finished = mLayoutAnimations.finished();
                mLayoutAnimations.updateAndFinish();
            } else {
                finished = mLayoutAnimations.update(time);
            }

            if (finished || jumpToEnd) {
                mLayoutAnimations = null;
                onAnimationFinished();
            }
            requestUpdate();
        }
        return finished;
    }

    /**
     * @return Whether or not there is an animation currently being driven by this {@link Layout}.
     */
    @VisibleForTesting
    public boolean isLayoutAnimating() {
        return mLayoutAnimations != null && !mLayoutAnimations.finished();
    }

    /**
     * Called when layout-specific actions are needed after the animation finishes.
     */
    protected void onAnimationStarted() {
    }

    /**
     * Called when layout-specific actions are needed after the animation finishes.
     */
    protected void onAnimationFinished() {
    }

    /**
     * Creates an {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation
     * .AnimatableAnimation} and adds it to the animation.
     * Automatically sets the start value at the beginning of the animation.
     */
    protected <T extends Enum<?>> void addToAnimation(Animatable<T> object, T prop, float start,
            float end, long duration, long startTime) {
        addToAnimation(object, prop, start, end, duration, startTime, false);
    }

    /**
     * Creates an {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation
     * .AnimatableAnimation} and it to the animation. Uses a deceleration interpolator by default.
     */
    protected <T extends Enum<?>> void addToAnimation(Animatable<T> object, T prop, float start,
            float end, long duration, long startTime, boolean setStartValueAfterDelay) {
        addToAnimation(object, prop, start, end, duration, startTime, setStartValueAfterDelay,
                ChromeAnimation.getDecelerateInterpolator());
    }

    /**
     * Creates an {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation
     * .AnimatableAnimation} and
     * adds it to the animation.
     *
     * @param <T>                     The Enum type of the Property being used
     * @param object                  The object being animated
     * @param prop                    The property being animated
     * @param start                   The starting value of the animation
     * @param end                     The ending value of the animation
     * @param duration                The duration of the animation in ms
     * @param startTime               The start time in ms
     * @param setStartValueAfterDelay See {@link Animation#setStartValueAfterStartDelay(boolean)}
     * @param interpolator            The interpolator to use for the animation
     */
    protected <T extends Enum<?>> void addToAnimation(Animatable<T> object, T prop, float start,
            float end, long duration, long startTime, boolean setStartValueAfterDelay,
            Interpolator interpolator) {
        ChromeAnimation.Animation<Animatable<?>> component = createAnimation(object, prop, start,
                end, duration, startTime, setStartValueAfterDelay, interpolator);
        addToAnimation(component);
    }

    /**
     * Appends an Animation to the current animation set and starts it immediately.  If the set is
     * already finished or doesn't exist, the animation set is also started.
     */
    protected void addToAnimation(ChromeAnimation.Animation<Animatable<?>> component) {
        if (mLayoutAnimations == null || mLayoutAnimations.finished()) {
            onAnimationStarted();
            mLayoutAnimations = new ChromeAnimation<Animatable<?>>();
            mLayoutAnimations.start();
        }
        component.start();
        mLayoutAnimations.add(component);
        requestUpdate();
    }

    /**
     * @return whether or not the animation is currently being run.
     */
    protected boolean animationIsRunning() {
        return mLayoutAnimations != null && !mLayoutAnimations.finished();
    }

    /**
     * Cancels any animation for the given object and property.
     * @param object The object being animated.
     * @param prop   The property to search for.
     */
    protected <T extends Enum<?>> void cancelAnimation(Animatable<T> object, T prop) {
        if (mLayoutAnimations != null) {
            mLayoutAnimations.cancel(object, prop);
        }
    }

    /**
     * @return The {@link LayoutTab}s to be drawn.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public LayoutTab[] getLayoutTabsToRender() {
        return mLayoutTabs;
    }

    /**
     * @param id The id of the {@link LayoutTab} to search for.
     * @return   A {@link LayoutTab} represented by a {@link Tab} with an id of {@code id}.
     */
    public LayoutTab getLayoutTab(int id) {
        if (mLayoutTabs != null) {
            for (int i = 0; i < mLayoutTabs.length; i++) {
                if (mLayoutTabs[i].getId() == id) return mLayoutTabs[i];
            }
        }
        return null;
    }

    /**
     * @return Whether the layout is handling the model updates when a tab is closing.
     */
    public boolean handlesTabClosing() {
        return false;
    }

    /**
     * @return Whether the layout is handling the model updates when a tab is creating.
     */
    public boolean handlesTabCreating() {
        return false;
    }

    /**
     * @return Whether the layout is handling the model updates when closing all the tabs.
     */
    public boolean handlesCloseAll() {
        return false;
    }

    /**
     * @return True if the content decoration layer should be shown.
     */
    public boolean shouldDisplayContentOverlay() {
        return false;
    }

    /**
     * @return True if the currently active content view is shown in the normal interactive mode.
     */
    public boolean isTabInteractive() {
        return false;
    }

    /**
     * @return The number of drawn tabs. This does not count the one culled out.
     */
    @VisibleForTesting
    public int getLayoutTabsDrawnCount() {
        return mRenderHost.getLayoutTabsDrawnCount();
    }

    /**
     * Setting this will only take effect the next time this layout is shown.  If it is currently
     * showing the original filter will still be used.
     * @param filter
     */
    public void setEventFilter(EventFilter filter) {
        mEventFilter = filter;
    }

    /**
     * @param e                 The {@link MotionEvent} to consider.
     * @param offsets           The current touch offsets that should be applied to the
     *                          {@link EventFilter}s.
     * @param isKeyboardShowing Whether or not the keyboard is showing.
     * @return The {@link EventFilter} the {@link Layout} is listening to.
     */
    public EventFilter findInterceptingEventFilter(
            MotionEvent e, Point offsets, boolean isKeyboardShowing) {
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            EventFilter eventFilter = mSceneOverlays.get(i).getEventFilter();
            if (offsets != null) eventFilter.setCurrentMotionEventOffsets(offsets.x, offsets.y);
            if (eventFilter.onInterceptTouchEvent(e, isKeyboardShowing)) return eventFilter;
        }

        if (mEventFilter != null) {
            if (offsets != null) mEventFilter.setCurrentMotionEventOffsets(offsets.x, offsets.y);
            if (mEventFilter.onInterceptTouchEvent(e, isKeyboardShowing)) return mEventFilter;
        }
        return null;
    }

    /**
     * Build a {@link SceneLayer} if it hasn't already been built, and update it and return it.
     *
     * @param contentViewport   A viewport in which to display content.
     * @param layerTitleCache   A layer title cache.
     * @param tabContentManager A tab content manager.
     * @param resourceManager   A resource manager.
     * @param fullscreenManager A fullscreen manager.
     * @return                  A {@link SceneLayer} that represents the content for this
     *                          {@link Layout}.
     */
    public final SceneLayer getUpdatedSceneLayer(Rect viewport,
            Rect contentViewport, LayerTitleCache layerTitleCache,
            TabContentManager tabContentManager, ResourceManager resourceManager,
            ChromeFullscreenManager fullscreenManager) {
        updateSceneLayer(viewport, contentViewport, layerTitleCache, tabContentManager,
                resourceManager, fullscreenManager);

        float offsetPx = fullscreenManager != null ? fullscreenManager.getControlOffset() : 0.f;
        float dpToPx = getContext().getResources().getDisplayMetrics().density;
        float offsetDp = offsetPx / dpToPx;
        float offsetOverride = getTopControlsOffset(offsetDp);
        if (!Float.isNaN(offsetOverride)) offsetDp = offsetOverride;

        SceneLayer content = getSceneLayer();
        for (int i = 0; i < mSceneOverlays.size(); i++) {
            SceneOverlayLayer overlayLayer = mSceneOverlays.get(i).getUpdatedSceneOverlayTree(
                    layerTitleCache, resourceManager, offsetDp);

            overlayLayer.setContentTree(content);
            content = overlayLayer;
        }

        return content;
    }

    /**
     * @return Whether or not to force the top controls Android view to hide.
     */
    public boolean forceHideTopControlsAndroidView() {
        return false;
    }

    /**
     * @return The toolbar brightness.
     */
    public float getToolbarBrightness() {
        return 1.0f;
    }

    /**
     * @return Whether the tabstrip's event filter is enabled.
     */
    public boolean isTabStripEventFilterEnabled() {
        return true;
    }

    /**
     * Get an instance of {@link SceneLayer}. Any class inheriting {@link Layout}
     * should override this function in order for other functions to work.
     *
     * @return The scene layer for this {@link Layout}.
     */
    protected abstract SceneLayer getSceneLayer();

    /**
     * Update {@link SceneLayer} instance this layout holds. Any class inheriting {@link Layout}
     * should override this function in order for other functions to work.
     */
    protected void updateSceneLayer(Rect viewport, Rect contentViewport,
            LayerTitleCache layerTitleCache, TabContentManager tabContentManager,
            ResourceManager resourceManager, ChromeFullscreenManager fullscreenManager) {
    }
}
