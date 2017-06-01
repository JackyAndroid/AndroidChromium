// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar;

import android.app.Activity;
import android.content.Context;
import android.view.View.MeasureSpec;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelManager.PanelPriority;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilterHost;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.GestureHandler;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.OverlayPanelEventFilter;
import org.chromium.chrome.browser.compositor.overlays.SceneOverlay;
import org.chromium.chrome.browser.compositor.scene_layer.SceneOverlayLayer;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.common.BrowserControlsState;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.resources.ResourceManager;

import java.util.List;

/**
 * Controls the Overlay Panel.
 */
public class OverlayPanel extends OverlayPanelAnimation implements ActivityStateListener,
        EdgeSwipeHandler, GestureHandler, OverlayPanelContentFactory, SceneOverlay {

    /** The extra dp added around the close button touch target. */
    private static final int CLOSE_BUTTON_TOUCH_SLOP_DP = 5;

    /** State of the Overlay Panel. */
    public static enum PanelState {
        // TODO(pedrosimonetti): consider removing the UNDEFINED state
        UNDEFINED,
        CLOSED,
        PEEKED,
        EXPANDED,
        MAXIMIZED
    }

    /**
     * The reason for a change in the Overlay Panel's state.
     * TODO(mdjones): Separate generic reasons from Contextual Search reasons.
     */
    public enum StateChangeReason {
        UNKNOWN,
        RESET,
        BACK_PRESS,
        TEXT_SELECT_TAP,
        TEXT_SELECT_LONG_PRESS,
        INVALID_SELECTION,
        CLEARED_SELECTION,
        BASE_PAGE_TAP,
        BASE_PAGE_SCROLL,
        SEARCH_BAR_TAP,
        SERP_NAVIGATION,
        TAB_PROMOTION,
        CLICK,
        SWIPE,
        FLING,
        OPTIN,
        OPTOUT,
        CLOSE_BUTTON,
        SUPPRESS,
        UNSUPPRESS,
        FULLSCREEN_ENTERED,
        FULLSCREEN_EXITED,
        INFOBAR_SHOWN,
        INFOBAR_HIDDEN,
        CONTENT_CHANGED,
        KEYBOARD_SHOWN,
        KEYBOARD_HIDDEN,
        TAB_NAVIGATION
    }

    /** The activity this panel is in. */
    protected ChromeActivity mActivity;

    /** The initial height of the Overlay Panel. */
    private float mInitialPanelHeight;

    /** The initial location of a touch on the panel */
    private float mInitialPanelTouchY;

    /** Whether a touch gesture has been detected. */
    private boolean mHasDetectedTouchGesture;

    /** The EventFilter that this panel uses. */
    protected EventFilter mEventFilter;

    /** That factory that creates OverlayPanelContents. */
    private OverlayPanelContentFactory mContentFactory;

    /** Container for content the panel will show. */
    private OverlayPanelContent mContent;

    /** OverlayPanel manager handle for notifications of opening and closing. */
    protected OverlayPanelManager mPanelManager;

    /** If the base page text controls have been cleared. */
    private boolean mDidClearTextControls;

    /** If the panel should be ignoring swipe events (for compatibility mode). */
    private boolean mIgnoreSwipeEvents;

    /** This is used to make sure there is one show request to one close request. */
    private boolean mPanelShown;

    /**
     * Cache the viewport width and height of the screen to filter SceneOverlay#onSizeChanged
     * events.
     */
    private float mViewportWidth;
    private float mViewportHeight;

    // ============================================================================================
    // Constructor
    // ============================================================================================

    /**
     * @param context The current Android {@link Context}.
     * @param updateHost The {@link LayoutUpdateHost} used to request updates in the Layout.
     * @param eventHost The {@link EventFilterHost} used to propagate events.
     * @param panelManager The {@link OverlayPanelManager} responsible for showing panels.
     */
    public OverlayPanel(Context context, LayoutUpdateHost updateHost, EventFilterHost eventHost,
                OverlayPanelManager panelManager) {
        super(context, updateHost);
        mContentFactory = this;

        mPanelManager = panelManager;
        mPanelManager.registerPanel(this);
        mEventFilter = new OverlayPanelEventFilter(mContext, eventHost, this);
    }

    /**
     * Destroy the native components associated with this panel's content.
     */
    public void destroy() {
        closePanel(StateChangeReason.UNKNOWN, false);
        ApplicationStatus.unregisterActivityStateListener(this);
    }

    /**
     * Destroy the components associated with this panel. This should be overridden by panel
     * implementations to destroy text views and other elements.
     */
    protected void destroyComponents() {
        destroyOverlayPanelContent();
    }

    @Override
    protected void onClosed(StateChangeReason reason) {
        mPanelShown = false;
        setBasePageTextControlsVisibility(true);
        destroyComponents();
        mPanelManager.notifyPanelClosed(this, reason);
    }

    // ============================================================================================
    // General API
    // ============================================================================================

    /**
     * @return True if the panel is in the PEEKED state.
     */
    public boolean isPeeking() {
        return doesPanelHeightMatchState(PanelState.PEEKED);
    }

    /**
     * @return Whether the Panel is in its expanded state.
     */
    public boolean isExpanded() {
        return doesPanelHeightMatchState(PanelState.EXPANDED);
    }

    @Override
    public void closePanel(StateChangeReason reason, boolean animate) {
        // If the panel hasn't peeked, then it shouldn't need to close.
        if (!mPanelShown) return;

        super.closePanel(reason, animate);
    }

    /**
     * Request that this panel be shown.
     * @param reason The reason the panel is being shown.
     */
    public void requestPanelShow(StateChangeReason reason) {
        if (mPanelShown) return;

        if (mPanelManager != null) {
            mPanelManager.requestPanelShow(this, reason);
        }
    }

    @Override
    public void peekPanel(StateChangeReason reason) {
        // TODO(mdjones): This is making a protected API public and should be removed. Animation
        // should only be controlled by the OverlayPanelManager.

        // Since the OverlayPanelManager can show panels without requestPanelShow being called, the
        // flag for the panel being shown should be set to true here.
        mPanelShown = true;
        super.peekPanel(reason);
    }

    /**
     * @param url The URL that the panel should load.
     */
    public void loadUrlInPanel(String url) {
        getOverlayPanelContent().loadUrl(url, true);
    }

    /**
     * @param url The URL that the panel should load.
     * @param shouldLoadImmediately If the URL should be loaded immediately when this method is
     *                              called.
     */
    public void loadUrlInPanel(String url, boolean shouldLoadImmediately) {
        getOverlayPanelContent().loadUrl(url, shouldLoadImmediately);
    }

    /**
     * @return True if a URL has been loaded in the panel's current ContentViewCore.
     */
    public boolean isProcessingPendingNavigation() {
        return mContent != null && mContent.isProcessingPendingNavigation();
    }

    /**
     * @param activity The ChromeActivity associated with the panel.
     */
    public void setChromeActivity(ChromeActivity activity) {
        mActivity = activity;
        if (mActivity != null) {
            ApplicationStatus.registerStateListenerForActivity(this, mActivity);
        }

        initializeUiState();
    }

    /**
     * Notify the panel's content that it has been touched.
     * @param x The X position of the touch in dp.
     */
    public void notifyBarTouched(float x) {
        if (!isCoordinateInsideCloseButton(x)) {
            getOverlayPanelContent().showContent();
        }
    }

    /**
     * Acknowledges that there was a touch in the search content view, though no immediate action
     * needs to be taken. This should be overridden by child classes.
     * TODO(mdjones): Get a better name for this.
     */
    public void onTouchSearchContentViewAck() {
    }

    /**
     * Get a panel's display priority. This has a default to MEDIUM and should be overridden by
     * child classes.
     * @return The panel's display priority.
     */
    public PanelPriority getPriority() {
        return PanelPriority.MEDIUM;
    }

    /**
     * @return True if a panel can be suppressed. This should be overridden by each panel.
     */
    public boolean canBeSuppressed() {
        return false;
    }

    /**
     * @return The absolute amount in DP that the browser controls have shifted off screen.
     */
    protected float getBrowserControlsOffsetDp() {
        if (mActivity == null || mActivity.getFullscreenManager() == null) return 0.0f;
        return -mActivity.getFullscreenManager().getTopControlOffset() * mPxToDp;
    }

    /**
     * Set the visibility of the base page text selection controls. This will also attempt to
     * remove focus from the base page to clear any open controls.
     * TODO(mdjones): This should be replaced with focusing the panel's ContentViewCore.
     * @param visible If the text controls are visible.
     */
    protected void setBasePageTextControlsVisibility(boolean visible) {
        if (mActivity == null || mActivity.getActivityTab() == null) return;

        ContentViewCore baseContentView = mActivity.getActivityTab().getContentViewCore();
        if (baseContentView == null) return;

        // If the panel does not have focus or isn't open, return.
        if (isPanelOpened() && mDidClearTextControls && !visible) return;
        if (!isPanelOpened() && !mDidClearTextControls && visible) return;

        mDidClearTextControls = !visible;

        if (!visible) {
            baseContentView.preserveSelectionOnNextLossOfFocus();
            baseContentView.getContainerView().clearFocus();
        }

        baseContentView.updateTextSelectionUI(visible);
    }

    // ============================================================================================
    // ActivityStateListener
    // ============================================================================================

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        boolean isMultiWindowMode = MultiWindowUtils.getInstance().isLegacyMultiWindow(mActivity)
                || MultiWindowUtils.getInstance().isInMultiWindowMode(mActivity);

        // In multi-window mode the activity that was interacted with last is resumed and
        // all others are paused. We should not close Contextual Search in this case,
        // because the activity may be visible even though it is paused.
        if (isMultiWindowMode
                && (newState == ActivityState.PAUSED || newState == ActivityState.RESUMED)) {
            return;
        }

        if (newState == ActivityState.RESUMED
                || newState == ActivityState.STOPPED
                || newState == ActivityState.DESTROYED) {
            closePanel(StateChangeReason.UNKNOWN, false);
        }
    }

    // ============================================================================================
    // Content
    // ============================================================================================

    /**
     * @return True if the panel's content view is showing.
     */
    public boolean isContentShowing() {
        return mContent != null && mContent.isContentShowing();
    }

    /**
     * @return The ContentViewCore that this panel currently holds.
     */
    public ContentViewCore getContentViewCore() {
        // Expose OverlayPanelContent method.
        return mContent != null ? mContent.getContentViewCore() : null;
    }

    /**
     * Create a new OverlayPanelContent object. This can be overridden for tests.
     * @return A new OverlayPanelContent object.
     */
    @Override
    public OverlayPanelContent createNewOverlayPanelContent() {
        return new OverlayPanelContent(new OverlayContentDelegate(),
                new OverlayContentProgressObserver(), mActivity);
    }

    /**
     * Add any other objects that depend on the OverlayPanelContent having already been created.
     */
    private OverlayPanelContent createNewOverlayPanelContentInternal() {
        OverlayPanelContent content = mContentFactory.createNewOverlayPanelContent();

        content.setContentViewClient(new ContentViewClient() {
            @Override
            public int getDesiredWidthMeasureSpec() {
                if (isFullWidthSizePanel()) {
                    return super.getDesiredWidthMeasureSpec();
                } else {
                    return MeasureSpec.makeMeasureSpec(
                            getContentViewWidthPx(),
                            MeasureSpec.EXACTLY);
                }
            }

            @Override
            public int getDesiredHeightMeasureSpec() {
                if (isFullWidthSizePanel()) {
                    return super.getDesiredHeightMeasureSpec();
                } else {
                    return MeasureSpec.makeMeasureSpec(
                            getContentViewHeightPx(),
                            MeasureSpec.EXACTLY);
                }
            }
        });
        return content;
    }

    /**
     * @return A new OverlayPanelContent if the instance was null or the existing one.
     */
    protected OverlayPanelContent getOverlayPanelContent() {
        // Only create the content when necessary
        if (mContent == null) {
            mContent = createNewOverlayPanelContentInternal();
        }
        return mContent;
    }

    /**
     * Destroy the native components of the OverlayPanelContent.
     */
    protected void destroyOverlayPanelContent() {
        // It is possible that an OverlayPanelContent was never created for this panel.
        if (mContent != null) {
            mContent.destroy();
            mContent = null;
        }
    }

    /**
     * Updates the browser controls state for the base tab.  As these values are set at the renderer
     * level, there is potential for this impacting other tabs that might share the same
     * process. See {@link Tab#updateBrowserControlsState(int current, boolean animate)}
     * @param current The desired current state for the controls.  Pass
     *                {@link BrowserControlsState#BOTH} to preserve the current position.
     * @param animate Whether the controls should animate to the specified ending condition or
     *                should jump immediately.
     */
    public void updateBrowserControlsState(int current, boolean animate) {
        Tab currentTab = mActivity.getActivityTab();
        if (currentTab != null) {
            currentTab.updateBrowserControlsState(current, animate);
        }
    }

    /**
     * Sets the top control state based on the internals of the panel.
     */
    public void updateBrowserControlsState() {
        if (mContent == null) return;

        if (isFullWidthSizePanel()) {
            // Consider the ContentView height to be fullscreen, and inform the system that
            // the Toolbar is always visible (from the Compositor's perspective), even though
            // the Toolbar and Base Page might be offset outside the screen. This means the
            // renderer will consider the ContentView height to be the fullscreen height
            // minus the Toolbar height.
            //
            // This is necessary to fix the bugs: crbug.com/510205 and crbug.com/510206
            mContent.updateBrowserControlsState(false, true, false);
        } else {
            mContent.updateBrowserControlsState(true, false, false);
        }
    }

    /**
     * Remove the last entry from history provided it is in a given time frame.
     * @param historyUrl The URL to remove.
     * @param urlTimeMs The time that the URL was visited.
     */
    public void removeLastHistoryEntry(String historyUrl, long urlTimeMs) {
        if (mContent == null) return;
        // Expose OverlayPanelContent method.
        mContent.removeLastHistoryEntry(historyUrl, urlTimeMs);
    }

    /**
     * @return The vertical scroll position of the content.
     */
    public float getContentVerticalScroll() {
        return mContent != null ? mContent.getContentVerticalScroll() : 0.0f;
    }

    // ============================================================================================
    // OverlayPanelBase methods.
    // ============================================================================================

    @Override
    protected void onAnimationFinished() {
        super.onAnimationFinished();

        if (getPanelState() == PanelState.PEEKED || getPanelState() == PanelState.CLOSED) {
            setBasePageTextControlsVisibility(true);
        } else {
            setBasePageTextControlsVisibility(false);
        }
    }

    @Override
    protected int getControlContainerHeightResource() {
        // TODO(mdjones): Investigate passing this in to the constructor instead.
        assert mActivity != null;
        return mActivity.getControlContainerHeightResource();
    }

    // ============================================================================================
    // Layout Integration
    // ============================================================================================

    /**
     * Updates the Panel so it preserves its state when the orientation changes.
     */
    protected void updatePanelForOrientationChange() {
        resizePanelToState(getPanelState(), StateChangeReason.UNKNOWN);
    }

    // ============================================================================================
    // Generic Event Handling
    // ============================================================================================

    /**
     * Handles the beginning of the swipe gesture.
     */
    public void handleSwipeStart() {
        if (animationIsRunning()) {
            cancelHeightAnimation();
        }

        mHasDetectedTouchGesture = false;
        mInitialPanelHeight = getHeight();
    }

    /**
     * Handles the movement of the swipe gesture.
     *
     * @param ty The movement's total displacement in dps.
     */
    public void handleSwipeMove(float ty) {
        if (mContent != null && ty > 0 && getPanelState() == PanelState.MAXIMIZED) {
            // Resets the Content View scroll position when swiping the Panel down
            // after being maximized.
            mContent.resetContentViewScroll();
        }

        // Negative ty value means an upward movement so subtracting ty means expanding the panel.
        setClampedPanelHeight(mInitialPanelHeight - ty);
        requestUpdate();
    }

    /**
     * Handles the end of the swipe gesture.
     */
    public void handleSwipeEnd() {
        // This method will be called after handleFling() and handleClick()
        // methods because we also need to track down the onUpOrCancel()
        // action from the Layout. Therefore the animation to the nearest
        // PanelState should only happen when no other gesture has been
        // detected.
        if (!mHasDetectedTouchGesture) {
            mHasDetectedTouchGesture = true;
            animateToNearestState();
        }
    }

    /**
     * Handles the fling gesture.
     *
     * @param velocity The velocity of the gesture in dps per second.
     */
    public void handleFling(float velocity) {
        mHasDetectedTouchGesture = true;
        animateToProjectedState(velocity);
    }

    /**
     * @param x The x coordinate in dp.
     * @return Whether the given |x| coordinate is inside the close button.
     */
    protected boolean isCoordinateInsideCloseButton(float x) {
        if (LocalizationUtils.isLayoutRtl()) {
            return x <= (getCloseIconX() + getCloseIconDimension() + CLOSE_BUTTON_TOUCH_SLOP_DP);
        } else {
            return x >= (getCloseIconX() - CLOSE_BUTTON_TOUCH_SLOP_DP);
        }
    }

    /**
     * Handles the click gesture.
     *
     * @param time The timestamp of the gesture.
     * @param x The x coordinate of the gesture.
     * @param y The y coordinate of the gesture.
     */
    public void handleClick(long time, float x, float y) {
        mHasDetectedTouchGesture = true;
        if (isCoordinateInsideBasePage(x, y)) {
            closePanel(StateChangeReason.BASE_PAGE_TAP, true);
        } else if (isCoordinateInsideBar(x, y) && !onInterceptBarClick()) {
            handleBarClick(time, x, y);
        }
    }

    /**
     * Handles the click gesture specifically on the bar.
     *
     * @param time The timestamp of the gesture.
     * @param x The x coordinate of the gesture.
     * @param y The y coordinate of the gesture.
     */
    protected void handleBarClick(long time, float x, float y) {
        if (isPeeking()) {
            expandPanel(StateChangeReason.SEARCH_BAR_TAP);
        }
    }

    /**
     * Allows the click on the bar to be intercepted.
     * @return True if the click on the bar was intercepted by this function.
     */
    protected boolean onInterceptBarClick() {
        return false;
    }

    /**
     * If the panel is intercepting the initial bar swipe event. This should be overridden per
     * panel.
     * @return True if the panel intercepted the initial bar swipe.
     */
    public boolean onInterceptBarSwipe() {
        return false;
    }

    // ============================================================================================
    // Gesture Event helpers
    // ============================================================================================

    /**
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     * @return Whether the given coordinate is inside the bar area of the overlay.
     */
    public boolean isCoordinateInsideBar(float x, float y) {
        return isCoordinateInsideOverlayPanel(x, y)
                && y >= getOffsetY() && y <= (getOffsetY() + getBarContainerHeight());
    }

    /**
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     * @return Whether the given coordinate is inside the Overlay Content View area.
     */
    public boolean isCoordinateInsideContent(float x, float y) {
        return isCoordinateInsideOverlayPanel(x, y)
                && y > getContentY();
    }

    /**
     * @return The horizontal offset of the Overlay Content View in dp.
     */
    public float getContentX() {
        return getOffsetX();
    }

    /**
     * @return The vertical offset of the Overlay Content View in dp.
     */
    public float getContentY() {
        return getOffsetY() + getBarContainerHeight();
    }

    /**
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     * @return Whether the given coordinate is inside the Overlay Panel area.
     */
    public boolean isCoordinateInsideOverlayPanel(float x, float y) {
        return y >= getOffsetY() && y <= (getOffsetY() + getHeight())
                &&  x >= getOffsetX() && x <= (getOffsetX() + getWidth());
    }

    /**
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     * @return Whether the given coordinate is inside the Base Page area.
     */
    private boolean isCoordinateInsideBasePage(float x, float y) {
        return !isCoordinateInsideOverlayPanel(x, y);
    }

    @VisibleForTesting
    public void setOverlayPanelContentFactory(OverlayPanelContentFactory factory) {
        mContentFactory = factory;
    }

    // ============================================================================================
    // GestureHandler and EdgeSwipeHandler implementation.
    // ============================================================================================

    @Override
    public void onDown(float x, float y, boolean fromMouse, int buttons) {
        mInitialPanelTouchY = y;
        handleSwipeStart();
    }

    @Override
    public void drag(float x, float y, float deltaX, float deltaY, float tx, float ty) {
        handleSwipeMove(y - mInitialPanelTouchY);
    }

    @Override
    public void onUpOrCancel() {
        handleSwipeEnd();
    }

    @Override
    public void fling(float x, float y, float velocityX, float velocityY) {
        handleFling(velocityY);
    }

    @Override
    public void click(float x, float y, boolean fromMouse, int buttons) {
        // TODO(mdjones): The time param for handleClick is not used anywhere, remove it.
        handleClick(0, x, y);
    }

    @Override
    public void onLongPress(float x, float y) {}

    @Override
    public void onPinch(float x0, float y0, float x1, float y1, boolean firstEvent) {}

    // EdgeSwipeHandler implementation.

    @Override
    public void swipeStarted(ScrollDirection direction, float x, float y) {
        if (onInterceptBarSwipe()) {
            mIgnoreSwipeEvents = true;
            return;
        }
        handleSwipeStart();
    }

    @Override
    public void swipeUpdated(float x, float y, float dx, float dy, float tx, float ty) {
        if (mIgnoreSwipeEvents) return;
        handleSwipeMove(ty);
    }

    @Override
    public void swipeFinished() {
        if (mIgnoreSwipeEvents) {
            mIgnoreSwipeEvents = false;
            return;
        }
        handleSwipeEnd();
    }

    @Override
    public void swipeFlingOccurred(float x, float y, float tx, float ty, float vx, float vy) {
        if (mIgnoreSwipeEvents) return;
        handleFling(vy);
    }

    @Override
    public boolean isSwipeEnabled(ScrollDirection direction) {
        return direction == ScrollDirection.UP && isShowing();
    }

    // Other event handlers.

    /**
     * The user has performed a down event and has not performed a move or up yet. This event is
     * commonly used to provide visual feedback to the user to let them know that their action has
     * been recognized.
     * See {@link GestureDetector.SimpleOnGestureListener#onShowPress()}.
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     */
    public void onShowPress(float x, float y) {}

    // ============================================================================================
    // SceneOverlay implementation.
    // ============================================================================================

    @Override
    public SceneOverlayLayer getUpdatedSceneOverlayTree(LayerTitleCache layerTitleCache,
            ResourceManager resourceManager, float yOffset) {
        return null;
    }

    @Override
    public boolean isSceneOverlayTreeShowing() {
        return isShowing();
    }

    @Override
    public EventFilter getEventFilter() {
        return mEventFilter;
    }

    @Override
    public void onSizeChanged(float width, float height, float visibleViewportOffsetY,
            int orientation) {
        // Filter events that don't change the viewport width or height.
        if (height != mViewportHeight || width != mViewportWidth) {
          // We only care if the orientation is changing or we're shifting in/out of multi-window.
          // In either case the screen's viewport width or height will certainly change.
            mViewportWidth = width;
            mViewportHeight = height;
            resizePanelContentViewCore(width, height);
            onLayoutChanged(width, height, visibleViewportOffsetY);
        }
    }

    /**
     * Resize the panel's ContentViewCore manually since it is not attached to the view hierarchy.
     * TODO(mdjones): Remove the need for this method by supporting multiple ContentViewCores
     * existing simultaneously in the view hierarchy.
     * @param width The new width in dp.
     * @param height The new height in dp.
     */
    protected void resizePanelContentViewCore(float width, float height) {
        if (!isShowing()) return;
        ContentViewCore panelContent = getContentViewCore();
        if (panelContent != null) {
            panelContent.onSizeChanged((int) (width / mPxToDp),
                    (int) (height / mPxToDp), panelContent.getViewportWidthPix(),
                    panelContent.getViewportHeightPix());
            panelContent.onPhysicalBackingSizeChanged(
                    (int) (width / mPxToDp), (int) (height / mPxToDp));
        }
    }

    @Override
    public void getVirtualViews(List<VirtualView> views) {
        // TODO(mdjones): Add views for accessibility.
    }

    @Override
    public boolean handlesTabCreating() {
        // If the panel is not opened, do not handle tab creating.
        if (!isPanelOpened()) return false;
        // Updates BrowserControls' State so the Toolbar becomes visible.
        // TODO(pedrosimonetti): The transition when promoting to a new tab is only smooth
        // if the SearchContentView's vertical scroll position is zero. Otherwise the
        // ContentView will appear to jump in the screen. Coordinate with @dtrainor to solve
        // this problem.
        updateBrowserControlsState(BrowserControlsState.BOTH, false);
        return true;
    }

    @Override
    public boolean shouldHideAndroidBrowserControls() {
        return isPanelOpened();
    }

    @Override
    public boolean updateOverlay(long time, long dt) {
        if (isPanelOpened()) setBasePageTextControlsVisibility(false);
        return super.onUpdateAnimation(time, false);
    }

    @Override
    public void onHideLayout() {
        if (!isShowing()) return;
        closePanel(StateChangeReason.UNKNOWN, false);
    }

    @Override
    public boolean onBackPressed() {
        if (!isPanelOpened()) return false;
        closePanel(StateChangeReason.BACK_PRESS, true);
        return true;
    }

    @Override
    public void tabTitleChanged(int tabId, String title) {}

    @Override
    public void tabStateInitialized() {}

    @Override
    public void tabModelSwitched(boolean incognito) {}

    @Override
    public void tabSelected(long time, boolean incognito, int id, int prevId) {}

    @Override
    public void tabMoved(long time, boolean incognito, int id, int oldIndex, int newIndex) {}

    @Override
    public void tabClosed(long time, boolean incognito, int id) {}

    @Override
    public void tabClosureCancelled(long time, boolean incognito, int id) {}

    @Override
    public void tabCreated(long time, boolean incognito, int id, int prevId, boolean selected) {}

    @Override
    public void tabPageLoadStarted(int id, boolean incognito) {}

    @Override
    public void tabPageLoadFinished(int id, boolean incognito) {}

    @Override
    public void tabLoadStarted(int id, boolean incognito) {}

    @Override
    public void tabLoadFinished(int id, boolean incognito) {}
}
