// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.LayoutRenderHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.layouts.phone.stack.Stack;
import org.chromium.chrome.browser.compositor.layouts.phone.stack.StackTab;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.compositor.scene_layer.TabListSceneLayer;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.resources.ResourceManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Defines the layout for 2 stacks and manages the events to switch between
 * them.
 */

public class StackLayout extends Layout implements Animatable<StackLayout.Property> {
    public enum Property {
        INNER_MARGIN_PERCENT,
        STACK_SNAP,
        STACK_OFFSET_Y_PERCENT,
    }

    private enum SwipeMode { NONE, SEND_TO_STACK, SWITCH_STACK }

    private static final String TAG = "StackLayout";
    // Width of the partially shown stack when there are multiple stacks.
    private static final int MIN_INNER_MARGIN_PERCENT_DP = 55;
    private static final float INNER_MARGIN_PERCENT_PERCENT = 0.17f;

    // Speed of the automatic fling in dp/ms
    private static final float FLING_SPEED_DP = 1.5f; // dp / ms
    private static final int FLING_MIN_DURATION = 100; // ms

    private static final float THRESHOLD_TO_SWITCH_STACK = 0.4f;
    private static final float THRESHOLD_TIME_TO_SWITCH_STACK_INPUT_MODE = 200;

    /**
     * The delta time applied on the velocity from the fling. This is to compute the kick to help
     * switching the stack.
     */
    private static final float SWITCH_STACK_FLING_DT = 1.0f / 30.0f;

    /** The array of potentially visible stacks. The code works for only 2 stacks. */
    private final Stack[] mStacks;

    /** Rectangles that defines the area where each stack need to be laid out. */
    private final RectF[] mStackRects;

    private int mStackAnimationCount;

    private float mFlingSpeed = 0; // pixel/ms

    /** Whether the current fling animation is the result of switching stacks. */
    private boolean mFlingFromModelChange;

    private boolean mClicked;

    // If not overscroll, then mRenderedScrollIndex == mScrollIndex;
    // Otherwise, mRenderedScrollIndex is updated with the actual index passed in
    // from the event handler; and mRenderedScrollIndex is the value we get
    // after map mScrollIndex through a decelerate function.
    // Here we use float as index so we can smoothly animate the transition between stack.
    private float mRenderedScrollOffset = 0.0f;
    private float mScrollIndexOffset = 0.0f;

    private final int mMinMaxInnerMargin;
    private float mInnerMarginPercent;
    private float mStackOffsetYPercent;

    private SwipeMode mInputMode = SwipeMode.NONE;
    private float mLastOnDownX;
    private float mLastOnDownY;
    private long mLastOnDownTimeStamp;
    private final float mMinShortPressThresholdSqr; // Computed from Android ViewConfiguration
    private final float mMinDirectionThreshold; // Computed from Android ViewConfiguration

    // Pre-allocated temporary arrays that store id of visible tabs.
    // They can be used to call populatePriorityVisibilityList.
    // We use StackTab[] instead of ArrayList<StackTab> because the sorting function does
    // an allocation to iterate over the elements.
    // Do not use out of the context of {@link #updateTabPriority}.
    private StackTab[] mSortedPriorityArray = null;

    private final ArrayList<Integer> mVisibilityArray = new ArrayList<Integer>();
    private final VisibilityComparator mVisibilityComparator = new VisibilityComparator();
    private final OrderComparator mOrderComparator = new OrderComparator();
    private Comparator<StackTab> mSortingComparator = mVisibilityComparator;

    private static final int LAYOUTTAB_ASYNCHRONOUS_INITIALIZATION_BATCH_SIZE = 4;
    private boolean mDelayedLayoutTabInitRequired = false;

    private Boolean mTemporarySelectedStack;

    // Orientation Variables
    private PortraitViewport mCachedPortraitViewport = null;
    private PortraitViewport mCachedLandscapeViewport = null;

    private final ViewGroup mViewContainer;

    private final TabListSceneLayer mSceneLayer;

    /**
     * @param context     The current Android's context.
     * @param updateHost  The {@link LayoutUpdateHost} view for this layout.
     * @param renderHost  The {@link LayoutRenderHost} view for this layout.
     * @param eventFilter The {@link EventFilter} that is needed for this view.
     */
    public StackLayout(Context context, LayoutUpdateHost updateHost, LayoutRenderHost renderHost,
            EventFilter eventFilter) {
        super(context, updateHost, renderHost, eventFilter);

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mMinDirectionThreshold = configuration.getScaledTouchSlop();
        mMinShortPressThresholdSqr =
                configuration.getScaledPagingTouchSlop() * configuration.getScaledPagingTouchSlop();

        mMinMaxInnerMargin = (int) (MIN_INNER_MARGIN_PERCENT_DP + 0.5);
        mFlingSpeed = FLING_SPEED_DP;
        mStacks = new Stack[2];
        mStacks[0] = new Stack(context, this);
        mStacks[1] = new Stack(context, this);
        mStackRects = new RectF[2];
        mStackRects[0] = new RectF();
        mStackRects[1] = new RectF();

        mViewContainer = new FrameLayout(getContext());
        mSceneLayer = new TabListSceneLayer();
    }

    @Override
    public int getSizingFlags() {
        return SizingFlags.ALLOW_TOOLBAR_SHOW | SizingFlags.REQUIRE_FULLSCREEN_SIZE;
    }

    @Override
    public void setTabModelSelector(TabModelSelector modelSelector, TabContentManager manager) {
        super.setTabModelSelector(modelSelector, manager);
        mStacks[0].setTabModel(modelSelector.getModel(false));
        mStacks[1].setTabModel(modelSelector.getModel(true));
        resetScrollData();
    }

    /**
     * Get the tab stack state for the specified mode.
     *
     * @param incognito Whether the TabStackState to be returned should be the one for incognito.
     * @return The tab stack state for the given mode.
     * @VisibleForTesting
     */
    public Stack getTabStack(boolean incognito) {
        return mStacks[incognito ? 1 : 0];
    }

    /**
     * Get the tab stack state.
     * @return The tab stack index for the given tab id.
     */
    private int getTabStackIndex() {
        return getTabStackIndex(Tab.INVALID_TAB_ID);
    }

    /**
     * Get the tab stack state for the specified tab id.
     *
     * @param tabId The id of the tab to lookup.
     * @return The tab stack index for the given tab id.
     * @VisibleForTesting
     */
    protected int getTabStackIndex(int tabId) {
        if (tabId == Tab.INVALID_TAB_ID) {
            boolean incognito = mTemporarySelectedStack != null
                    ? mTemporarySelectedStack
                    : mTabModelSelector.isIncognitoSelected();
            return incognito ? 1 : 0;
        } else {
            return TabModelUtils.getTabById(mTabModelSelector.getModel(true), tabId) != null ? 1
                                                                                             : 0;
        }
    }

    /**
     * Get the tab stack state for the specified tab id.
     *
     * @param tabId The id of the tab to lookup.
     * @return The tab stack state for the given tab id.
     * @VisibleForTesting
     */
    protected Stack getTabStack(int tabId) {
        return mStacks[getTabStackIndex(tabId)];
    }

    /**
     * Commits outstanding model states.
     * @param time  The current time of the app in ms.
     */
    public void commitOutstandingModelState(long time) {
        mStacks[1].ensureCleaningUpDyingTabs(time);
        mStacks[0].ensureCleaningUpDyingTabs(time);
    }

    @Override
    public void onTabSelecting(long time, int tabId) {
        commitOutstandingModelState(time);
        if (tabId == Tab.INVALID_TAB_ID) tabId = mTabModelSelector.getCurrentTabId();
        super.onTabSelecting(time, tabId);
        mStacks[getTabStackIndex()].tabSelectingEffect(time, tabId);
        startMarginAnimation(false);
        startYOffsetAnimation(false);
        finishScrollStacks();
    }

    @Override
    public void onTabClosing(long time, int id) {
        Stack stack = getTabStack(id);
        if (stack == null) return;
        stack.tabClosingEffect(time, id);

        // Just in case we closed the last tab of a stack we need to trigger the overlap animation.
        startMarginAnimation(true);

        // Animate the stack to leave incognito mode.
        if (!mStacks[1].isDisplayable()) uiPreemptivelySelectTabModel(false);
    }

    @Override
    public void onTabsAllClosing(long time, boolean incognito) {
        super.onTabsAllClosing(time, incognito);
        getTabStack(incognito).tabsAllClosingEffect(time);
        // trigger the overlap animation.
        startMarginAnimation(true);
        // Animate the stack to leave incognito mode.
        if (!mStacks[1].isDisplayable()) uiPreemptivelySelectTabModel(false);
    }

    @Override
    public void onTabClosureCancelled(long time, int id, boolean incognito) {
        super.onTabClosureCancelled(time, id, incognito);
        getTabStack(incognito).undoClosure(time, id);
    }

    @Override
    public boolean handlesCloseAll() {
        return true;
    }

    @Override
    public boolean handlesTabCreating() {
        return true;
    }

    @Override
    public boolean handlesTabClosing() {
        return true;
    }

    @Override
    public void attachViews(ViewGroup container) {
        // TODO(dtrainor): This is a hack.  We're attaching to the parent of the view container
        // which is the content container of the Activity.
        ((ViewGroup) container.getParent())
                .addView(mViewContainer,
                        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public void detachViews() {
        if (mViewContainer.getParent() != null) {
            ((ViewGroup) mViewContainer.getParent()).removeView(mViewContainer);
        }
        mViewContainer.removeAllViews();
    }

    /**
     * @return A {@link ViewGroup} that {@link Stack}s can use to interact with the Android view
     *         hierarchy.
     */
    public ViewGroup getViewContainer() {
        return mViewContainer;
    }

    @Override
    public boolean onBackPressed() {
        // Force any in progress animations to end. This was introduced because
        // we end up with 0 tabs if the animation for all tabs closing is still
        // running when the back button is pressed. We should finish the animation
        // and close Chrome instead.
        // See http://crbug.com/522447
        onUpdateAnimation(SystemClock.currentThreadTimeMillis(), true);
        return false;
    }

    @Override
    public void onTabCreating(int sourceTabId) {
        // Force any in progress animations to end. This was introduced because
        // we end up with 0 tabs if the animation for all tabs closing is still
        // running when a new tab is created.
        // See http://crbug.com/496557
        onUpdateAnimation(SystemClock.currentThreadTimeMillis(), true);
    }

    @Override
    public void onTabCreated(long time, int id, int tabIndex, int sourceId, boolean newIsIncognito,
            boolean background, float originX, float originY) {
        super.onTabCreated(
                time, id, tabIndex, sourceId, newIsIncognito, background, originX, originY);
        startHiding(id, false);
        mStacks[getTabStackIndex(id)].tabCreated(time, id);
        startMarginAnimation(false);
        uiPreemptivelySelectTabModel(newIsIncognito);
    }

    @Override
    public void onTabModelSwitched(boolean toIncognitoTabModel) {
        flingStacks(toIncognitoTabModel);
        mFlingFromModelChange = true;
    }

    @Override
    public boolean onUpdateAnimation(long time, boolean jumpToEnd) {
        boolean animationsWasDone = super.onUpdateAnimation(time, jumpToEnd);
        boolean finishedView0 = mStacks[0].onUpdateViewAnimation(time, jumpToEnd);
        boolean finishedView1 = mStacks[1].onUpdateViewAnimation(time, jumpToEnd);
        boolean finishedCompositor0 = mStacks[0].onUpdateCompositorAnimations(time, jumpToEnd);
        boolean finishedCompositor1 = mStacks[1].onUpdateCompositorAnimations(time, jumpToEnd);
        if (animationsWasDone && finishedView0 && finishedView1 && finishedCompositor0
                && finishedCompositor1) {
            return true;
        } else {
            if (!animationsWasDone || !finishedCompositor0 || !finishedCompositor1) {
                requestStackUpdate();
            }

            return false;
        }
    }

    @Override
    protected void onAnimationStarted() {
        if (mStackAnimationCount == 0) super.onAnimationStarted();
    }

    @Override
    protected void onAnimationFinished() {
        mFlingFromModelChange = false;
        if (mTemporarySelectedStack != null) {
            mTabModelSelector.selectModel(mTemporarySelectedStack);
            mTemporarySelectedStack = null;
        }
        if (mStackAnimationCount == 0) super.onAnimationFinished();
    }

    /**
     * Called when a UI element is attempting to select a tab.  This will perform the animation
     * and then actually propagate the action.  This starts hiding this layout which, when complete,
     * will actually select the tab.
     * @param time The current time of the app in ms.
     * @param id   The id of the tab to select.
     */
    public void uiSelectingTab(long time, int id) {
        onTabSelecting(time, id);
    }

    /**
     * Called when a UI element is attempting to close a tab.  This will perform the required close
     * animations.  When the UI is ready to actually close the tab
     * {@link #uiDoneClosingTab(long, int, boolean, boolean)} should be called to actually propagate
     * the event to the model.
     * @param time The current time of the app in ms.
     * @param id   The id of the tab to close.
     */
    public void uiRequestingCloseTab(long time, int id) {
        // Start the tab closing effect if necessary.
        getTabStack(id).tabClosingEffect(time, id);

        int incognitoCount = mTabModelSelector.getModel(true).getCount();
        TabModel model = mTabModelSelector.getModelForTabId(id);
        if (model != null && model.isIncognito()) incognitoCount--;
        boolean incognitoVisible = incognitoCount > 0;

        // Make sure we show/hide both stacks depending on which tab we're closing.
        startMarginAnimation(true, incognitoVisible);
        if (!incognitoVisible) uiPreemptivelySelectTabModel(false);
    }

    /**
     * Called when a UI element is done animating the close tab effect started by
     * {@link #uiRequestingCloseTab(long, int)}.  This actually pushes the close event to the model.
     * @param time      The current time of the app in ms.
     * @param id        The id of the tab to close.
     * @param canUndo   Whether or not this close can be undone.
     * @param incognito Whether or not this was for the incognito stack or not.
     */
    public void uiDoneClosingTab(long time, int id, boolean canUndo, boolean incognito) {
        // If homepage is enabled and there is a maximum of 1 tab in both models
        // (this is the last tab), the tab closure cannot be undone.
        canUndo &= !(HomepageManager.isHomepageEnabled(getContext())
                           && (mTabModelSelector.getModel(true).getCount()
                                              + mTabModelSelector.getModel(false).getCount()
                                      < 2));

        // Propagate the tab closure to the model.
        TabModelUtils.closeTabById(mTabModelSelector.getModel(incognito), id, canUndo);
    }

    public void uiDoneClosingAllTabs(boolean incognito) {
        // Propagate the tab closure to the model.
        mTabModelSelector.getModel(incognito).closeAllTabs(false, false);
    }

    /**
     * Called when a {@link Stack} instance is done animating the stack enter effect.
     */
    public void uiDoneEnteringStack() {
        mSortingComparator = mVisibilityComparator;
        doneShowing();
    }

    private void uiPreemptivelySelectTabModel(boolean incognito) {
        onTabModelSwitched(incognito);
    }

    /**
     * Starts the animation for the opposite stack to slide in or out when entering
     * or leaving stack view.  The animation should be super fast to match more or less
     * the fling animation.
     * @param enter True if the stack view is being entered, false if the stack view
     *              is being left.
     */
    private void startMarginAnimation(boolean enter) {
        startMarginAnimation(enter, mStacks[1].isDisplayable());
    }

    private void startMarginAnimation(boolean enter, boolean showIncognito) {
        float start = mInnerMarginPercent;
        float end = enter && showIncognito ? 1.0f : 0.0f;
        if (start != end) {
            addToAnimation(this, Property.INNER_MARGIN_PERCENT, start, end, 200, 0);
        }
    }

    private void startYOffsetAnimation(boolean enter) {
        float start = mStackOffsetYPercent;
        float end = enter ? 1.f : 0.f;
        if (start != end) {
            addToAnimation(this, Property.STACK_OFFSET_Y_PERCENT, start, end, 300, 0);
        }
    }

    @Override
    public void show(long time, boolean animate) {
        super.show(time, animate);

        Tab tab = mTabModelSelector.getCurrentTab();
        if (tab != null && tab.isNativePage()) mTabContentManager.cacheTabThumbnail(tab);

        // Remove any views in case we're getting another call to show before we hide (quickly
        // toggling the tab switcher button).
        mViewContainer.removeAllViews();

        for (int i = mStacks.length - 1; i >= 0; --i) {
            mStacks[i].reset();
            if (mStacks[i].isDisplayable()) {
                mStacks[i].show();
            } else {
                mStacks[i].cleanupTabs();
            }
        }
        // Initialize the animation and the positioning of all the elements
        mSortingComparator = mOrderComparator;
        resetScrollData();
        for (int i = mStacks.length - 1; i >= 0; --i) {
            if (mStacks[i].isDisplayable()) {
                boolean offscreen = (i != getTabStackIndex());
                mStacks[i].stackEntered(time, !offscreen);
            }
        }
        startMarginAnimation(true);
        startYOffsetAnimation(true);
        flingStacks(getTabStackIndex() == 1);

        if (!animate) onUpdateAnimation(time, true);

        // We will render before we get a call to updateLayout.  Need to make sure all of the tabs
        // we need to render are up to date.
        updateLayout(time, 0);
    }

    @Override
    public void swipeStarted(long time, ScrollDirection direction, float x, float y) {
        mStacks[getTabStackIndex()].swipeStarted(time, direction, x, y);
    }

    @Override
    public void swipeUpdated(long time, float x, float y, float dx, float dy, float tx, float ty) {
        mStacks[getTabStackIndex()].swipeUpdated(time, x, y, dx, dy, tx, ty);
    }

    @Override
    public void swipeFinished(long time) {
        mStacks[getTabStackIndex()].swipeFinished(time);
    }

    @Override
    public void swipeCancelled(long time) {
        mStacks[getTabStackIndex()].swipeCancelled(time);
    }

    @Override
    public void swipeFlingOccurred(
            long time, float x, float y, float tx, float ty, float vx, float vy) {
        mStacks[getTabStackIndex()].swipeFlingOccurred(time, x, y, tx, ty, vx, vy);
    }

    private void requestStackUpdate() {
        // TODO(jgreenwald): It isn't always necessary to invalidate both
        // stacks.
        mStacks[0].requestUpdate();
        mStacks[1].requestUpdate();
    }

    @Override
    public void notifySizeChanged(float width, float height, int orientation) {
        mCachedLandscapeViewport = null;
        mCachedPortraitViewport = null;
        mStacks[0].notifySizeChanged(width, height, orientation);
        mStacks[1].notifySizeChanged(width, height, orientation);
        resetScrollData();
        requestStackUpdate();
    }

    @Override
    public void contextChanged(Context context) {
        super.contextChanged(context);
        StackTab.resetDimensionConstants(context);
        mStacks[0].contextChanged(context);
        mStacks[1].contextChanged(context);
        requestStackUpdate();
    }

    @Override
    public void drag(long time, float x, float y, float amountX, float amountY) {
        SwipeMode oldInputMode = mInputMode;
        mInputMode = computeInputMode(time, x, y, amountX, amountY);

        if (oldInputMode == SwipeMode.SEND_TO_STACK && mInputMode == SwipeMode.SWITCH_STACK) {
            mStacks[getTabStackIndex()].onUpOrCancel(time);
        } else if (oldInputMode == SwipeMode.SWITCH_STACK
                && mInputMode == SwipeMode.SEND_TO_STACK) {
            onUpOrCancel(time);
        }

        if (mInputMode == SwipeMode.SEND_TO_STACK) {
            mStacks[getTabStackIndex()].drag(time, x, y, amountX, amountY);
        } else if (mInputMode == SwipeMode.SWITCH_STACK) {
            scrollStacks(getOrientation() == Orientation.PORTRAIT ? amountX : amountY);
        }
    }

    /**
     * Computes the input mode for drag and fling based on the first event position.
     * @param time The current time of the app in ms.
     * @param x    The x layout position of the mouse (without the displacement).
     * @param y    The y layout position of the mouse (without the displacement).
     * @param dx   The x displacement happening this frame.
     * @param dy   The y displacement happening this frame.
     * @return     The input mode to select.
     */
    private SwipeMode computeInputMode(long time, float x, float y, float dx, float dy) {
        if (!mStacks[1].isDisplayable()) return SwipeMode.SEND_TO_STACK;
        int currentIndex = getTabStackIndex();
        if (currentIndex != getViewportParameters().getStackIndexAt(x, y)) {
            return SwipeMode.SWITCH_STACK;
        }
        float relativeX = mLastOnDownX - (x + dx);
        float relativeY = mLastOnDownY - (y + dy);
        float distanceToDownSqr = dx * dx + dy * dy;
        float switchDelta = getOrientation() == Orientation.PORTRAIT ? relativeX : relativeY;
        float otherDelta = getOrientation() == Orientation.PORTRAIT ? relativeY : relativeX;

        // Dragging in the opposite direction of the stack switch
        if (distanceToDownSqr > mMinDirectionThreshold * mMinDirectionThreshold
                && Math.abs(otherDelta) > Math.abs(switchDelta)) {
            return SwipeMode.SEND_TO_STACK;
        }
        // Dragging in a direction the stack cannot switch
        if (Math.abs(switchDelta) > mMinDirectionThreshold) {
            if ((currentIndex == 0) ^ (switchDelta > 0)
                    ^ (getOrientation() == Orientation.PORTRAIT
                              && LocalizationUtils.isLayoutRtl())) {
                return SwipeMode.SEND_TO_STACK;
            }
        }
        if (isDraggingStackInWrongDirection(
                    mLastOnDownX, mLastOnDownY, x, y, dx, dy, getOrientation(), currentIndex)) {
            return SwipeMode.SWITCH_STACK;
        }
        // Not moving the finger
        if (time - mLastOnDownTimeStamp > THRESHOLD_TIME_TO_SWITCH_STACK_INPUT_MODE) {
            return SwipeMode.SEND_TO_STACK;
        }
        // Dragging fast
        if (distanceToDownSqr > mMinShortPressThresholdSqr) {
            return SwipeMode.SWITCH_STACK;
        }
        return SwipeMode.NONE;
    }

    @Override
    public void fling(long time, float x, float y, float vx, float vy) {
        if (mInputMode == SwipeMode.NONE) {
            mInputMode = computeInputMode(
                    time, x, y, vx * SWITCH_STACK_FLING_DT, vy * SWITCH_STACK_FLING_DT);
        }

        if (mInputMode == SwipeMode.SEND_TO_STACK) {
            mStacks[getTabStackIndex()].fling(time, x, y, vx, vy);
        } else if (mInputMode == SwipeMode.SWITCH_STACK) {
            final float velocity = getOrientation() == Orientation.PORTRAIT ? vx : vy;
            final float origin = getOrientation() == Orientation.PORTRAIT ? x : y;
            final float max = getOrientation() == Orientation.PORTRAIT ? getWidth() : getHeight();
            final float predicted = origin + velocity * SWITCH_STACK_FLING_DT;
            final float delta = MathUtils.clamp(predicted, 0, max) - origin;
            scrollStacks(delta);
        }
        requestStackUpdate();
    }

    class PortraitViewport {
        protected float mWidth, mHeight;
        PortraitViewport() {
            mWidth = StackLayout.this.getWidth();
            mHeight = StackLayout.this.getHeightMinusTopControls();
        }

        float getClampedRenderedScrollOffset() {
            if (mStacks[1].isDisplayable() || mFlingFromModelChange) {
                return MathUtils.clamp(mRenderedScrollOffset, 0, -1);
            } else {
                return 0;
            }
        }

        float getInnerMargin() {
            float margin = mInnerMarginPercent
                    * Math.max(mMinMaxInnerMargin, mWidth * INNER_MARGIN_PERCENT_PERCENT);
            return margin;
        }

        int getStackIndexAt(float x, float y) {
            if (LocalizationUtils.isLayoutRtl()) {
                // On RTL portrait mode, stack1 (incognito) is on the left.
                float separation = getStack0Left();
                return x < separation ? 1 : 0;
            } else {
                float separation = getStack0Left() + getWidth();
                return x < separation ? 0 : 1;
            }
        }

        float getStack0Left() {
            return LocalizationUtils.isLayoutRtl()
                    ? getInnerMargin() - getClampedRenderedScrollOffset() * getFullScrollDistance()
                    : getClampedRenderedScrollOffset() * getFullScrollDistance();
        }

        float getWidth() {
            return mWidth - getInnerMargin();
        }

        float getHeight() {
            return mHeight;
        }

        float getStack0Top() {
            return getTopHeightOffset();
        }

        float getStack0ToStack1TranslationX() {
            return Math.round(LocalizationUtils.isLayoutRtl() ? -mWidth + getInnerMargin() : mWidth
                                    - getInnerMargin());
        }

        float getStack0ToStack1TranslationY() {
            return 0.0f;
        }

        float getTopHeightOffset() {
            return (StackLayout.this.getHeight() - getHeightMinusTopControls())
                    * mStackOffsetYPercent;
        }
    }

    class LandscapeViewport extends PortraitViewport {
        LandscapeViewport() {
            // This is purposefully inverted.
            mWidth = StackLayout.this.getHeightMinusTopControls();
            mHeight = StackLayout.this.getWidth();
        }

        @Override
        float getInnerMargin() {
            float margin = mInnerMarginPercent
                    * Math.max(mMinMaxInnerMargin, mWidth * INNER_MARGIN_PERCENT_PERCENT);
            return margin;
        }

        @Override
        int getStackIndexAt(float x, float y) {
            float separation = getStack0Top() + getHeight();
            return y < separation ? 0 : 1;
        }

        @Override
        float getStack0Left() {
            return 0.f;
        }

        @Override
        float getStack0Top() {
            return getClampedRenderedScrollOffset() * getFullScrollDistance()
                    + getTopHeightOffset();
        }

        @Override
        float getWidth() {
            return super.getHeight();
        }

        @Override
        float getHeight() {
            return super.getWidth();
        }

        @Override
        float getStack0ToStack1TranslationX() {
            return super.getStack0ToStack1TranslationY();
        }

        @Override
        float getStack0ToStack1TranslationY() {
            return Math.round(mWidth - getInnerMargin());
        }
    }

    private PortraitViewport getViewportParameters() {
        if (getOrientation() == Orientation.PORTRAIT) {
            if (mCachedPortraitViewport == null) {
                mCachedPortraitViewport = new PortraitViewport();
            }
            return mCachedPortraitViewport;
        } else {
            if (mCachedLandscapeViewport == null) {
                mCachedLandscapeViewport = new LandscapeViewport();
            }
            return mCachedLandscapeViewport;
        }
    }

    @Override
    public void click(long time, float x, float y) {
        // Click event happens before the up event. mClicked is set to mute the up event.
        mClicked = true;
        PortraitViewport viewportParams = getViewportParameters();
        int stackIndexAt = viewportParams.getStackIndexAt(x, y);
        if (stackIndexAt == getTabStackIndex()) {
            mStacks[getTabStackIndex()].click(time, x, y);
        } else {
            flingStacks(getTabStackIndex() == 0);
        }
        requestStackUpdate();
    }

    /**
     * Check if we are dragging stack in a wrong direction.
     *
     * @param downX The X coordinate on the last down event.
     * @param downY The Y coordinate on the last down event.
     * @param x The current X coordinate.
     * @param y The current Y coordinate.
     * @param dx The amount of change in X coordinate.
     * @param dy The amount of change in Y coordinate.
     * @param orientation The device orientation (portrait / landscape).
     * @param stackIndex The index of stack tab.
     * @return True iff we are dragging stack in a wrong direction.
     */
    @VisibleForTesting
    public static boolean isDraggingStackInWrongDirection(float downX, float downY, float x,
            float y, float dx, float dy, int orientation, int stackIndex) {
        float switchDelta = orientation == Orientation.PORTRAIT ? x - downX : y - downY;

        // Should not prevent scrolling even when switchDelta is in a wrong direction.
        if (Math.abs(dx) < Math.abs(dy)) {
            return false;
        }
        return (stackIndex == 0 && switchDelta < 0) || (stackIndex == 1 && switchDelta > 0);
    }

    private void scrollStacks(float delta) {
        cancelAnimation(this, Property.STACK_SNAP);
        float fullDistance = getFullScrollDistance();
        mScrollIndexOffset += MathUtils.flipSignIf(delta / fullDistance,
                getOrientation() == Orientation.PORTRAIT && LocalizationUtils.isLayoutRtl());
        if (canScrollLinearly(getTabStackIndex())) {
            mRenderedScrollOffset = mScrollIndexOffset;
        } else {
            mRenderedScrollOffset = (int) MathUtils.clamp(
                    mScrollIndexOffset, 0, mStacks[1].isDisplayable() ? -1 : 0);
        }
        requestStackUpdate();
    }

    private void flingStacks(boolean toIncognito) {
        // velocityX is measured in pixel per second.
        if (!canScrollLinearly(toIncognito ? 0 : 1)) return;
        setActiveStackState(toIncognito);
        finishScrollStacks();
        requestStackUpdate();
    }

    /**
     * Animate to the final position of the stack.  Unfortunately, both touch-up
     * and fling can be called and this depends on fling always being called last.
     * If fling is called first, onUpOrCancel can override the fling position
     * with the opposite.  For example, if the user does a very small fling from
     * incognito to non-incognito, which leaves the up event in the incognito side.
     */
    private void finishScrollStacks() {
        cancelAnimation(this, Property.STACK_SNAP);
        final int currentModelIndex = getTabStackIndex();
        float delta = Math.abs(currentModelIndex + mRenderedScrollOffset);
        float target = -currentModelIndex;
        if (delta != 0) {
            long duration = FLING_MIN_DURATION
                    + (long) Math.abs(delta * getFullScrollDistance() / mFlingSpeed);
            addToAnimation(this, Property.STACK_SNAP, mRenderedScrollOffset, target, duration, 0);
        } else {
            setProperty(Property.STACK_SNAP, target);
            if (mTemporarySelectedStack != null) {
                mTabModelSelector.selectModel(mTemporarySelectedStack);
                mTemporarySelectedStack = null;
            }
        }
    }

    @Override
    public void onDown(long time, float x, float y) {
        mLastOnDownX = x;
        mLastOnDownY = y;
        mLastOnDownTimeStamp = time;
        mInputMode = computeInputMode(time, x, y, 0, 0);
        mStacks[getTabStackIndex()].onDown(time);
    }

    @Override
    public void onLongPress(long time, float x, float y) {
        mStacks[getTabStackIndex()].onLongPress(time, x, y);
    }

    @Override
    public void onUpOrCancel(long time) {
        int currentIndex = getTabStackIndex();
        int nextIndex = 1 - currentIndex;
        if (!mClicked && Math.abs(currentIndex + mRenderedScrollOffset) > THRESHOLD_TO_SWITCH_STACK
                && mStacks[nextIndex].isDisplayable()) {
            setActiveStackState(nextIndex == 1);
        }
        mClicked = false;
        finishScrollStacks();
        mStacks[getTabStackIndex()].onUpOrCancel(time);
        mInputMode = SwipeMode.NONE;
    }

    /**
     * Pushes a rectangle to be drawn on the screen on top of everything.
     *
     * @param rect  The rectangle to be drawn on screen
     * @param color The color of the rectangle
     */
    public void pushDebugRect(Rect rect, int color) {
        if (rect.left > rect.right) {
            int tmp = rect.right;
            rect.right = rect.left;
            rect.left = tmp;
        }
        if (rect.top > rect.bottom) {
            int tmp = rect.bottom;
            rect.bottom = rect.top;
            rect.top = tmp;
        }
        mRenderHost.pushDebugRect(rect, color);
    }

    @Override
    public void onPinch(long time, float x0, float y0, float x1, float y1, boolean firstEvent) {
        mStacks[getTabStackIndex()].onPinch(time, x0, y0, x1, y1, firstEvent);
    }

    @Override
    protected void updateLayout(long time, long dt) {
        super.updateLayout(time, dt);
        boolean needUpdate = false;

        final PortraitViewport viewport = getViewportParameters();
        mStackRects[0].left = viewport.getStack0Left();
        mStackRects[0].right = mStackRects[0].left + viewport.getWidth();
        mStackRects[0].top = viewport.getStack0Top();
        mStackRects[0].bottom = mStackRects[0].top + viewport.getHeight();
        mStackRects[1].left = mStackRects[0].left + viewport.getStack0ToStack1TranslationX();
        mStackRects[1].right = mStackRects[1].left + viewport.getWidth();
        mStackRects[1].top = mStackRects[0].top + viewport.getStack0ToStack1TranslationY();
        mStackRects[1].bottom = mStackRects[1].top + viewport.getHeight();

        mStacks[0].setStackFocusInfo(1.0f + mRenderedScrollOffset,
                mSortingComparator == mOrderComparator ? mTabModelSelector.getModel(false).index()
                                                       : -1);
        mStacks[1].setStackFocusInfo(-mRenderedScrollOffset, mSortingComparator == mOrderComparator
                        ? mTabModelSelector.getModel(true).index()
                        : -1);

        // Compute position and visibility
        mStacks[0].computeTabPosition(time, mStackRects[0]);
        mStacks[1].computeTabPosition(time, mStackRects[1]);

        // Pre-allocate/resize {@link #mLayoutTabs} before it get populated by
        // computeTabPositionAndAppendLayoutTabs.
        final int tabVisibleCount = mStacks[0].getVisibleCount() + mStacks[1].getVisibleCount();

        if (tabVisibleCount == 0) {
            mLayoutTabs = null;
        } else if (mLayoutTabs == null || mLayoutTabs.length != tabVisibleCount) {
            mLayoutTabs = new LayoutTab[tabVisibleCount];
        }

        int index = 0;
        if (getTabStackIndex() == 1) {
            index = appendVisibleLayoutTabs(time, 0, mLayoutTabs, index);
            index = appendVisibleLayoutTabs(time, 1, mLayoutTabs, index);
        } else {
            index = appendVisibleLayoutTabs(time, 1, mLayoutTabs, index);
            index = appendVisibleLayoutTabs(time, 0, mLayoutTabs, index);
        }
        assert index == tabVisibleCount : "index should be incremented up to tabVisibleCount";

        // Update tab snapping
        for (int i = 0; i < tabVisibleCount; i++) {
            if (mLayoutTabs[i].updateSnap(dt)) needUpdate = true;
        }

        if (needUpdate) requestUpdate();

        // Since we've updated the positions of the stacks and tabs, let's go ahead and update
        // the visible tabs.
        updateTabPriority();
    }

    private int appendVisibleLayoutTabs(long time, int stackIndex, LayoutTab[] tabs, int tabIndex) {
        final StackTab[] stackTabs = mStacks[stackIndex].getTabs();
        if (stackTabs != null) {
            for (int i = 0; i < stackTabs.length; i++) {
                LayoutTab t = stackTabs[i].getLayoutTab();
                if (t.isVisible()) tabs[tabIndex++] = t;
            }
        }
        return tabIndex;
    }

    /**
     * Sets the active tab stack.
     *
     * @param isIncognito True if the model to select is incognito.
     * @return Whether the tab stack index passed in differed from the currently selected stack.
     */
    public boolean setActiveStackState(boolean isIncognito) {
        if (isIncognito == mTabModelSelector.isIncognitoSelected()) return false;
        mTemporarySelectedStack = isIncognito;
        return true;
    }

    private void resetScrollData() {
        mScrollIndexOffset = -getTabStackIndex();
        mRenderedScrollOffset = mScrollIndexOffset;
    }

    /**
     *  Based on the current position, determine if we will map mScrollDistance linearly to
     *  mRenderedScrollDistance. The logic is, if there is only stack, we will not map linearly;
     *  if we are scrolling two the boundary of either of the stacks, we will not map linearly;
     *  otherwise yes.
     */
    private boolean canScrollLinearly(int fromStackIndex) {
        int count = mStacks.length;
        if (!(mScrollIndexOffset <= 0 && -mScrollIndexOffset <= (count - 1))) {
            return false;
        }
        // since we only have two stacks now, we have a shortcut to calculate
        // empty stacks
        return mStacks[fromStackIndex ^ 0x01].isDisplayable();
    }

    private float getFullScrollDistance() {
        float distance =
                getOrientation() == Orientation.PORTRAIT ? getWidth() : getHeightMinusTopControls();
        return distance - 2 * getViewportParameters().getInnerMargin();
    }

    @Override
    public void doneHiding() {
        super.doneHiding();
        mTabModelSelector.commitAllTabClosures();
    }

    /**
     * Extracts the tabs from a stack and append them into a list.
     * @param stack     The stack that contains the tabs.
     * @param outList   The output list where will be the tabs from the stack.
     * @param index     The current number of item in the outList.
     * @return The updated index incremented by the number of tabs in the stack.
     */
    private static int addAllTabs(Stack stack, StackTab[] outList, int index) {
        StackTab[] stackTabs = stack.getTabs();
        if (stackTabs != null) {
            for (int i = 0; i < stackTabs.length; ++i) {
                outList[index++] = stackTabs[i];
            }
        }
        return index;
    }

    /**
     * Comparator that helps ordering StackTab's visibility sorting value in a decreasing order.
     */
    private static class VisibilityComparator implements Comparator<StackTab>, Serializable {
        @Override
        public int compare(StackTab tab1, StackTab tab2) {
            return (int) (tab2.getVisiblitySortingValue() - tab1.getVisiblitySortingValue());
        }
    }

    /**
     * Comparator that helps ordering StackTab's visibility sorting value in a decreasing order.
     */
    private static class OrderComparator implements Comparator<StackTab>, Serializable {
        @Override
        public int compare(StackTab tab1, StackTab tab2) {
            return tab1.getOrderSortingValue() - tab2.getOrderSortingValue();
        }
    }

    private boolean updateSortedPriorityArray(Comparator<StackTab> comparator) {
        final int allTabsCount = mStacks[0].getCount() + mStacks[1].getCount();
        if (allTabsCount == 0) return false;
        if (mSortedPriorityArray == null || mSortedPriorityArray.length != allTabsCount) {
            mSortedPriorityArray = new StackTab[allTabsCount];
        }
        int sortedOffset = 0;
        sortedOffset = addAllTabs(mStacks[0], mSortedPriorityArray, sortedOffset);
        sortedOffset = addAllTabs(mStacks[1], mSortedPriorityArray, sortedOffset);
        assert sortedOffset == mSortedPriorityArray.length;
        Arrays.sort(mSortedPriorityArray, comparator);
        return true;
    }

    /**
     * Updates the priority list of the {@link LayoutTab} and sends it the systems having processing
     * to do on a per {@link LayoutTab} basis. Priority meaning may change based on the current
     * comparator stored in {@link #mSortingComparator}.
     *
     * Do not use {@link #mSortedPriorityArray} out side this context. It is only a member to avoid
     * doing an allocation every frames.
     */
    private void updateTabPriority() {
        if (!updateSortedPriorityArray(mSortingComparator)) return;
        updateTabsVisibility(mSortedPriorityArray);
        updateDelayedLayoutTabInit(mSortedPriorityArray);
    }

    /**
     * Updates the list of visible tab Id that the tab content manager is suppose to serve. The list
     * is ordered by priority. The first ones must be in the manager, then the remaining ones should
     * have at least approximations if possible.
     *
     * @param sortedPriorityArray The array of all the {@link StackTab} sorted by priority.
     */
    private void updateTabsVisibility(StackTab[] sortedPriorityArray) {
        mVisibilityArray.clear();
        for (int i = 0; i < sortedPriorityArray.length; i++) {
            mVisibilityArray.add(sortedPriorityArray[i].getId());
        }
        updateCacheVisibleIds(mVisibilityArray);
    }

    /**
     * Initializes the {@link LayoutTab} a few at a time. This function is to be called once a
     * frame.
     * The logic of that function is not as trivial as it should be because the input array we want
     * to initialize the tab from keeps getting reordered from calls to call. This is needed to
     * get the highest priority tab initialized first.
     *
     * @param sortedPriorityArray The array of all the {@link StackTab} sorted by priority.
     */
    private void updateDelayedLayoutTabInit(StackTab[] sortedPriorityArray) {
        if (!mDelayedLayoutTabInitRequired) return;

        int initialized = 0;
        final int count = sortedPriorityArray.length;
        for (int i = 0; i < count; i++) {
            if (initialized >= LAYOUTTAB_ASYNCHRONOUS_INITIALIZATION_BATCH_SIZE) return;

            LayoutTab layoutTab = sortedPriorityArray[i].getLayoutTab();
            // The actual initialization is done by the parent class.
            if (super.initLayoutTabFromHost(layoutTab)) {
                initialized++;
            }
        }
        if (initialized == 0) mDelayedLayoutTabInitRequired = false;
    }

    @Override
    protected boolean initLayoutTabFromHost(LayoutTab layoutTab) {
        if (layoutTab.isInitFromHostNeeded()) mDelayedLayoutTabInitRequired = true;
        return false;
    }

    /**
     * Sets properties for animations.
     * @param prop The property to update
     * @param p New value of the property
     */
    @Override
    public void setProperty(Property prop, float p) {
        switch (prop) {
            case STACK_SNAP:
                mRenderedScrollOffset = p;
                mScrollIndexOffset = p;
                break;
            case INNER_MARGIN_PERCENT:
                mInnerMarginPercent = p;
                break;
            case STACK_OFFSET_Y_PERCENT:
                mStackOffsetYPercent = p;
                break;
        }
    }

    /**
     * Called by the stacks whenever they start an animation.
     */
    public void onStackAnimationStarted() {
        if (mStackAnimationCount == 0) super.onAnimationStarted();
        mStackAnimationCount++;
    }

    /**
     * Called by the stacks whenever they finish their animations.
     */
    public void onStackAnimationFinished() {
        mStackAnimationCount--;
        if (mStackAnimationCount == 0) super.onAnimationFinished();
    }

    @Override
    protected SceneLayer getSceneLayer() {
        return mSceneLayer;
    }

    @Override
    protected void updateSceneLayer(Rect viewport, Rect contentViewport,
            LayerTitleCache layerTitleCache, TabContentManager tabContentManager,
            ResourceManager resourceManager, ChromeFullscreenManager fullscreenManager) {
        super.updateSceneLayer(viewport, contentViewport, layerTitleCache, tabContentManager,
                resourceManager, fullscreenManager);
        assert mSceneLayer != null;
        mSceneLayer.pushLayers(getContext(), viewport, contentViewport, this, layerTitleCache,
                tabContentManager, resourceManager);
    }
}
