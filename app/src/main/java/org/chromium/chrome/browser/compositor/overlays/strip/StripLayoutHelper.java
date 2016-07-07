// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.overlays.strip;

import static org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.AnimatableAnimation.createAnimation;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;

import org.chromium.base.PerfTraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animation;
import org.chromium.chrome.browser.compositor.layouts.LayoutRenderHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.compositor.layouts.components.CompositorButton;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.phone.stack.StackScroller;
import org.chromium.chrome.browser.compositor.overlays.strip.TabLoadTracker.TabLoadTrackerCallback;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager.TabCreator;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.base.LocalizationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class handles managing the positions and behavior of all tabs in a tab strip.  It is
 * responsible for both responding to UI input events and model change notifications, adjusting and
 * animating the tab strip as required.
 *
 * <p>
 * The stacking and visual behavior is driven by setting a {@link StripStacker}.
 */
public class StripLayoutHelper {
    // Drag Constants
    private static final int REORDER_SCROLL_NONE = 0;
    private static final int REORDER_SCROLL_LEFT = 1;
    private static final int REORDER_SCROLL_RIGHT = 2;

    // Behavior Constants
    private static final float EPSILON = 0.001f;
    private static final int MAX_TABS_TO_STACK = 4;
    private static final float TAN_OF_REORDER_ANGLE_START_THRESHOLD =
            (float) Math.tan(Math.PI / 4.0f);
    private static final float REORDER_OVERLAP_SWITCH_PERCENTAGE = 0.53f;

    // Animation/Timer Constants
    private static final int RESIZE_DELAY_MS = 1500;
    private static final int SPINNER_UPDATE_DELAY_MS = 66;
    // Degrees per milisecond.
    private static final float SPINNER_DPMS = 0.33f;
    private static final int EXPAND_DURATION_MS = 250;
    private static final int ANIM_TAB_CREATED_MS = 150;
    private static final int ANIM_TAB_CLOSED_MS = 150;
    private static final int ANIM_TAB_RESIZE_MS = 150;
    private static final int ANIM_TAB_MOVE_MS = 125;

    // Visibility Constants
    private static final float TAB_STACK_WIDTH_DP = 4.f;
    private static final float TAB_OVERLAP_WIDTH_DP = 24.f;
    private static final float MIN_TAB_WIDTH_DP = 190.f;
    private static final float MAX_TAB_WIDTH_DP = 265.f;
    private static final float REORDER_MOVE_START_THRESHOLD_DP = 50.f;
    private static final float REORDER_EDGE_SCROLL_MAX_SPEED_DP = 1000.f;
    private static final float REORDER_EDGE_SCROLL_START_MIN_DP = 87.4f;
    private static final float REORDER_EDGE_SCROLL_START_MAX_DP = 18.4f;
    private static final float NEW_TAB_BUTTON_Y_OFFSET_DP = 6.f;
    private static final float NEW_TAB_BUTTON_CLICK_SLOP_DP = 4.f;
    private static final float NEW_TAB_BUTTON_WIDTH_DP = 58.f;
    private static final float NEW_TAB_BUTTON_HEIGHT_DP = 32.5f;

    private static final int MESSAGE_RESIZE = 1;
    private static final int MESSAGE_UPDATE_SPINNER = 2;

    // External influences
    private final LayoutUpdateHost mUpdateHost;
    private final LayoutRenderHost mRenderHost;
    private TabModel mModel;
    private TabCreator mTabCreator;
    private TabContentManager mTabContentManager;
    private StripStacker mStripStacker = new StaticStripStacker();

    // Internal State
    private StripLayoutTab[] mStripTabs = new StripLayoutTab[0];
    private StripLayoutTab[] mStripTabsVisuallyOrdered = new StripLayoutTab[0];
    private StripLayoutTab[] mStripTabsToRender = new StripLayoutTab[0];
    private final StripTabEventHandler mStripTabEventHandler = new StripTabEventHandler();
    private final TabLoadTrackerCallback mTabLoadTrackerHost = new TabLoadTrackerCallbackImpl();
    private ChromeAnimation<Animatable<?>> mLayoutAnimations;

    private final CompositorButton mNewTabButton;

    // Layout Constants
    private final float mTabStackWidth;
    private final float mTabOverlapWidth;
    private float mNewTabButtonWidth;
    private final float mMinTabWidth;
    private final float mMaxTabWidth;
    private final float mReorderMoveStartThreshold;
    private final ListPopupWindow mTabMenu;

    // Strip State
    private StackScroller mScroller;
    private int mScrollOffset;
    private float mMinScrollOffset;
    private float mCachedTabWidth;

    // Reorder State
    private int mReorderState = REORDER_SCROLL_NONE;
    private boolean mInReorderMode = false;
    private float mLastReorderX;
    private long mLastReorderScrollTime;

    // UI State
    private StripLayoutTab mInteractingTab;
    private CompositorButton mLastPressedCloseButton;
    private float mWidth;
    private float mHeight;
    private long mLastSpinnerUpdate;
    private float mLeftMargin;
    private float mRightMargin;
    private final boolean mIncognito;
    private float mBrightness;

    // Tab menu item IDs
    public static final int ID_CLOSE_ALL_TABS = 0;

    private Context mContext;
    /**
     * Creates an instance of the {@link StripLayoutHelper}.
     * @param context         The current Android {@link Context}.
     * @param updateHost      The parent {@link LayoutUpdateHost}.
     * @param renderHost      The {@link LayoutRenderHost}.
     * @param incognito       Whether or not this tab strip is incognito.
     */
    public StripLayoutHelper(Context context, LayoutUpdateHost updateHost,
            LayoutRenderHost renderHost, boolean incognito) {
        mTabStackWidth = TAB_STACK_WIDTH_DP;
        mTabOverlapWidth = TAB_OVERLAP_WIDTH_DP;
        mNewTabButtonWidth = NEW_TAB_BUTTON_WIDTH_DP;

        if (LocalizationUtils.isLayoutRtl()) {
            // In rtl let the tab nest closer to the new tab button.
            mNewTabButtonWidth -= mTabOverlapWidth / 2;
        }
        mRightMargin = LocalizationUtils.isLayoutRtl() ? 0 : mNewTabButtonWidth;
        mLeftMargin = LocalizationUtils.isLayoutRtl() ? mNewTabButtonWidth : 0;
        mMinTabWidth = MIN_TAB_WIDTH_DP;
        mMaxTabWidth = MAX_TAB_WIDTH_DP;
        mReorderMoveStartThreshold = REORDER_MOVE_START_THRESHOLD_DP;
        mUpdateHost = updateHost;
        mRenderHost = renderHost;
        mNewTabButton =
                new CompositorButton(context, NEW_TAB_BUTTON_WIDTH_DP, NEW_TAB_BUTTON_HEIGHT_DP);
        mNewTabButton.setResources(R.drawable.btn_tabstrip_new_tab_normal,
                R.drawable.btn_tabstrip_new_tab_pressed,
                R.drawable.btn_tabstrip_new_incognito_tab_normal,
                R.drawable.btn_tabstrip_new_incognito_tab_pressed);
        mNewTabButton.setIncognito(incognito);
        mNewTabButton.setY(NEW_TAB_BUTTON_Y_OFFSET_DP);
        mNewTabButton.setClickSlop(NEW_TAB_BUTTON_CLICK_SLOP_DP);
        Resources res = context.getResources();
        mNewTabButton.setAccessibilityDescription(
                res.getString(R.string.accessibility_toolbar_btn_new_tab),
                res.getString(R.string.accessibility_toolbar_btn_new_incognito_tab));
        mContext = context;
        mIncognito = incognito;
        mBrightness = 1.f;

        // Create tab menu
        mTabMenu = new ListPopupWindow(mContext);
        mTabMenu.setAdapter(new ArrayAdapter<String>(mContext, R.layout.eb_popup_item,
                new String[] {
                        mContext.getString(!mIncognito ? R.string.menu_close_all_tabs
                                                       : R.string.menu_close_all_incognito_tabs)}));
        mTabMenu.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mTabMenu.dismiss();
                if (position == ID_CLOSE_ALL_TABS) {
                    mModel.closeAllTabs(false, false);
                }
            }
        });

        int menuWidth = mContext.getResources().getDimensionPixelSize(R.dimen.menu_width);
        mTabMenu.setWidth(menuWidth);
        mTabMenu.setModal(true);
    }

    /**
     * Get a list of virtual views for accessibility.
     *
     * @param views A List to populate with virtual views.
     */
    public void getVirtualViews(List<VirtualView> views) {
        for (int i = mStripTabsToRender.length - 1; i >= 0; i--) {
            StripLayoutTab tab = mStripTabsToRender[i];
            tab.getVirtualViews(views);
        }
        if (mNewTabButton.isVisible()) views.add(mNewTabButton);
    }

    /**
     * @return The visually ordered list of visible {@link StripLayoutTab}s.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public StripLayoutTab[] getStripLayoutTabsToRender() {
        return mStripTabsToRender;
    }

    @VisibleForTesting
    public int getTabCount() {
        return mStripTabs.length;
    }

    /**
     * @return A {@link CompositorButton} that represents the positioning of the new tab button.
     */
    public CompositorButton getNewTabButton() {
        return mNewTabButton;
    }

    /**
     * @return The brightness of background tabs in the tabstrip.
     */
    public float getBackgroundTabBrightness() {
        return mInReorderMode ? 0.75f : 1.0f;
    }

    /**
     * Sets the brightness for the entire tabstrip.
     */
    public void setBrightness(float brightness) {
        mBrightness = brightness;
    }

    /**
     * @return The brightness of the entire tabstrip.
     */
    public float getBrightness() {
        return mBrightness;
    }

    /**
     * Allows changing the visual behavior of the tabs in this stack, as specified by
     * {@code stacker}.
     * @param stacker The {@link StripStacker} that should specify how the tabs should be
     *                presented.
     */
    public void setTabStacker(StripStacker stacker) {
        if (stacker != mStripStacker) mUpdateHost.requestUpdate();
        mStripStacker = stacker;

        // Push Stacker properties to tabs.
        for (int i = 0; i < mStripTabs.length; i++) {
            pushStackerPropertiesToTab(mStripTabs[i]);
        }
    }

    /**
     * @parm margin The width of the distance between the left edge of
     *              the screen and first tab.
     */
    public void setLeftMargin(float margin) {
        mLeftMargin = margin;
        mLeftMargin += LocalizationUtils.isLayoutRtl() ? mNewTabButtonWidth : 0;
    }

    /**
     * @param margin The distance between the rightmost tab and the edge of the
     *               screen.
     */
    public void setRightMargin(float margin) {
        mRightMargin = margin;
        mRightMargin += LocalizationUtils.isLayoutRtl() ? 0 : mNewTabButtonWidth;
    }

    /**
     * Updates the size of the virtual tab strip, making the tabs resize and move accordingly.
     * @param width  The new available width.
     * @param height The new height this stack should be.
     */
    public void onSizeChanged(float width, float height) {
        if (mWidth == width && mHeight == height) return;

        boolean widthChanged = mWidth != width;

        mWidth = width;
        mHeight = height;

        for (int i = 0; i < mStripTabs.length; i++) {
            mStripTabs[i].setHeight(mHeight);
        }

        if (widthChanged) computeAndUpdateTabWidth(false);
        if (mStripTabs.length > 0) mUpdateHost.requestUpdate();

        // Dismiss tab menu, similar to how the app menu is dismissed on orientation change
        mTabMenu.dismiss();
    }

    /**
     * Updates all internal resources and dimensions.
     * @param context The current Android {@link Context}.
     */
    public void onContextChanged(Context context) {
        mScroller = new StackScroller(context);
        mContext = context;
    }

    /**
     * Notify the a title has changed.
     *
     * @param tabId     The id of the tab that has changed.
     * @param title     The new title.
     */
    public void tabTitleChanged(int tabId, String title) {
        StripLayoutTab tab = findTabById(tabId);
        if (tab != null) tab.setAccessibilityDescription(title);
    }

    /**
     * Sets the {@link TabModel} that this {@link StripLayoutHelper} will visually represent.
     * @param model The {@link TabModel} to visually represent.
     */
    public void setTabModel(TabModel model, TabContentManager manager, TabCreator tabCreator) {
        if (mModel == model) return;
        mModel = model;
        mTabContentManager = manager;
        mTabCreator = tabCreator;
        computeAndUpdateTabOrders(false);
    }

    /**
     * Helper-specific updates. Cascades the values updated by the animations and flings.
     * @param time The current time of the app in ms.
     * @param dt   The delta time between update frames in ms.
     * @return     Whether or not animations are done.
     */
    public boolean updateLayout(long time, long dt) {
        PerfTraceEvent.instant("StripLayoutHelper:updateLayout");
        final boolean doneAnimating = onUpdateAnimation(time, false);
        updateStrip(time, dt);
        return doneAnimating;
    }

    /**
     * Called when a tab get selected.
     * @param time   The current time of the app in ms.
     * @param id     The id of the selected tab.
     * @param prevId The id of the previously selected tab.
     */
    public void tabSelected(long time, int id, int prevId) {
        if (findTabById(id) == null) {
            tabCreated(time, id, prevId, true);
        } else {
            updateVisualTabOrdering();
            mUpdateHost.requestUpdate();
        }
    }

    /**
     * Called when a tab has been moved in the tabModel.
     * @param time     The current time of the app in ms.
     * @param id       The id of the Tab.
     * @param oldIndex The old index of the tab in the {@link TabModel}.
     * @param newIndex The new index of the tab in the {@link TabModel}.
     */
    public void tabMoved(long time, int id, int oldIndex, int newIndex) {
        reorderTab(id, oldIndex, newIndex, false);

        updateVisualTabOrdering();
        mUpdateHost.requestUpdate();
    }

    /**
     * Called when a tab is being closed. When called, the closing tab will not
     * be part of the model.
     * @param time The current time of the app in ms.
     * @param id   The id of the tab being closed.
     */
    public void tabClosed(long time, int id) {
        if (findTabById(id) == null) return;

        // 1. Find out if we're closing the last tab.  This determines if we resize immediately.
        // We know mStripTabs.length >= 1 because findTabById did not return null.
        boolean closingLastTab = mStripTabs[mStripTabs.length - 1].getId() == id;

        // 2. Rebuild the strip.
        computeAndUpdateTabOrders(!closingLastTab);

        mUpdateHost.requestUpdate();
    }

    /**
     * Called when a tab close has been undone and the tab has been restored.
     * @param time The current time of the app in ms.
     * @param id   The id of the Tab.
     */
    public void tabClosureCancelled(long time, int id) {
        final boolean selected = TabModelUtils.getCurrentTabId(mModel) == id;
        tabCreated(time, id, Tab.INVALID_TAB_ID, selected);
    }

    /**
     * Called when a tab is created from the top left button.
     * @param time     The current time of the app in ms.
     * @param id       The id of the newly created tab.
     * @param prevId   The id of the source tab.
     * @param selected Whether the tab will be selected.
     */
    public void tabCreated(long time, int id, int prevId, boolean selected) {
        if (findTabById(id) != null) return;

        // 1. Build any tabs that are missing.
        computeAndUpdateTabOrders(false);

        // 2. Start an animation for the newly created tab.
        StripLayoutTab tab = findTabById(id);
        if (tab != null) startAnimation(buildTabCreatedAnimation(tab), true);

        // 3. Figure out which tab needs to be visible.
        StripLayoutTab fastExpandTab = findTabById(prevId);
        boolean allowLeftExpand = false;
        if (!selected) {
            fastExpandTab = tab;
            allowLeftExpand = true;
        }

        // 4. Scroll the stack so that the fast expand tab is visible.
        if (fastExpandTab != null) {
            float delta =
                    calculateOffsetToMakeTabVisible(fastExpandTab, false, allowLeftExpand, true);
            if (delta != 0.f) {
                mScroller.startScroll(mScrollOffset, 0, (int) delta, 0, time, EXPAND_DURATION_MS);
            }
        }

        mUpdateHost.requestUpdate();
    }

    /**
     * Called when a tab has started loading.
     * @param id The id of the Tab.
     */
    public void tabPageLoadStarted(int id) {
        StripLayoutTab tab = findTabById(id);
        if (tab != null) tab.pageLoadingStarted();
    }

    /**
     * Called when a tab has finished loading.
     * @param id The id of the Tab.
     */
    public void tabPageLoadFinished(int id) {
        StripLayoutTab tab = findTabById(id);
        if (tab != null) tab.pageLoadingFinished();
    }

    /**
     * Called when a tab has started loading resources.
     * @param id The id of the Tab.
     */
    public void tabLoadStarted(int id) {
        StripLayoutTab tab = findTabById(id);
        if (tab != null) tab.loadingStarted();
    }

    /**
     * Called when a tab has stopped loading resources.
     * @param id The id of the Tab.
     */
    public void tabLoadFinished(int id) {
        StripLayoutTab tab = findTabById(id);
        if (tab != null) tab.loadingFinished();
    }

    /**
     * Called on touch drag event.
     * @param time   The current time of the app in ms.
     * @param x      The y coordinate of the end of the drag event.
     * @param y      The y coordinate of the end of the drag event.
     * @param deltaX The number of pixels dragged in the x direction.
     * @param deltaY The number of pixels dragged in the y direction.
     * @param totalX The total delta x since the drag started.
     * @param totalY The total delta y since the drag started.
     */
    public void drag(
            long time, float x, float y, float deltaX, float deltaY, float totalX, float totalY) {
        resetResizeTimeout(false);

        deltaX = MathUtils.flipSignIf(deltaX, LocalizationUtils.isLayoutRtl());

        // 1. Reset the button state.
        mNewTabButton.drag(x, y);
        if (mLastPressedCloseButton != null) {
            if (!mLastPressedCloseButton.drag(x, y)) mLastPressedCloseButton = null;
        }

        if (mInReorderMode) {
            // 2.a. Handle reordering tabs.
            // This isn't the accumulated delta since the beginning of the drag.  It accumulates
            // the delta X until a threshold is crossed and then the event gets processed.
            float accumulatedDeltaX = x - mLastReorderX;

            if (Math.abs(accumulatedDeltaX) >= 1.f) {
                if (!LocalizationUtils.isLayoutRtl()) {
                    if (deltaX >= 1.f) {
                        mReorderState |= REORDER_SCROLL_RIGHT;
                    } else if (deltaX <= -1.f) {
                        mReorderState |= REORDER_SCROLL_LEFT;
                    }
                } else {
                    if (deltaX >= 1.f) {
                        mReorderState |= REORDER_SCROLL_LEFT;
                    } else if (deltaX <= -1.f) {
                        mReorderState |= REORDER_SCROLL_RIGHT;
                    }
                }

                mLastReorderX = x;
                updateReorderPosition(accumulatedDeltaX);
            }
        } else if (!mScroller.isFinished()) {
            // 2.b. Still scrolling, update the scroll destination here.
            mScroller.setFinalX((int) (mScroller.getFinalX() + deltaX));
        } else {
            // 2.c. Not scrolling.  Check if we need to fast expand.
            float fastExpandDelta =
                    calculateOffsetToMakeTabVisible(mInteractingTab, true, true, true);

            if (mInteractingTab != null && fastExpandDelta != 0.f) {
                if ((fastExpandDelta > 0 && deltaX > 0) || (fastExpandDelta < 0 && deltaX < 0)) {
                    mScroller.startScroll(
                            mScrollOffset, 0, (int) fastExpandDelta, 0, time, EXPAND_DURATION_MS);
                }
            } else {
                updateScrollOffsetPosition((int) (mScrollOffset + deltaX));
            }
        }

        // 3. Check if we should start the reorder mode
        if (!mInReorderMode) {
            final float absTotalX = Math.abs(totalX);
            final float absTotalY = Math.abs(totalY);
            if (totalY > mReorderMoveStartThreshold && absTotalX < mReorderMoveStartThreshold * 2.f
                    && (absTotalX > EPSILON
                               && (absTotalY / absTotalX) > TAN_OF_REORDER_ANGLE_START_THRESHOLD)) {
                startReorderMode(time, x, x - totalX);
            }
        }

        // If we're scrolling at all we aren't interacting with any particular tab.
        // We already kicked off a fast expansion earlier if we needed one.  Reorder mode will
        // repopulate this if necessary.
        if (!mInReorderMode) mInteractingTab = null;
        mUpdateHost.requestUpdate();
    }

    /**
     * Called on touch fling event. This is called before the onUpOrCancel event.
     * @param time      The current time of the app in ms.
     * @param x         The y coordinate of the start of the fling event.
     * @param y         The y coordinate of the start of the fling event.
     * @param velocityX The amount of velocity in the x direction.
     * @param velocityY The amount of velocity in the y direction.
     */
    public void fling(long time, float x, float y, float velocityX, float velocityY) {
        resetResizeTimeout(false);

        velocityX = MathUtils.flipSignIf(velocityX, LocalizationUtils.isLayoutRtl());

        // 1. If we're currently in reorder mode, don't allow the user to fling.
        if (mInReorderMode) return;

        // 2. If we're fast expanding or scrolling, figure out the destination of the scroll so we
        // can apply it to the end of this fling.
        int scrollDeltaRemaining = 0;
        if (!mScroller.isFinished()) {
            scrollDeltaRemaining = mScroller.getFinalX() - mScrollOffset;

            mInteractingTab = null;
            mScroller.forceFinished(true);
        }

        // 3. Kick off the fling.
        mScroller.fling(
                mScrollOffset, 0, (int) velocityX, 0, (int) mMinScrollOffset, 0, 0, 0, 0, 0, time);
        mScroller.setFinalX(mScroller.getFinalX() + scrollDeltaRemaining);
        mUpdateHost.requestUpdate();
    }

    /**
     * Called on onDown event.
     * @param time      The time stamp in millisecond of the event.
     * @param x         The x position of the event.
     * @param y         The y position of the event.
     * @param fromMouse Whether the event originates from a mouse.
     * @param buttons   State of all buttons that are pressed.
     */
    public void onDown(long time, float x, float y, boolean fromMouse, int buttons) {
        resetResizeTimeout(false);

        if (mNewTabButton.onDown(x, y)) {
            mRenderHost.requestRender();
            return;
        }

        final StripLayoutTab clickedTab = getTabAtPosition(x);
        final int index = clickedTab != null
                ? TabModelUtils.getTabIndexById(mModel, clickedTab.getId())
                : TabModel.INVALID_TAB_INDEX;
        // http://crbug.com/472186 : Needs to handle a case that index is invalid.
        // The case could happen when the current tab is touched while we're inflating the rest of
        // the tabs from disk.
        mInteractingTab = index != TabModel.INVALID_TAB_INDEX && index < mStripTabs.length
                ? mStripTabs[index]
                : null;
        boolean clickedClose = clickedTab != null
                               && clickedTab.checkCloseHitTest(x, y);
        if (clickedClose) {
            clickedTab.setClosePressed(true);
            mLastPressedCloseButton = clickedTab.getCloseButton();
            mRenderHost.requestRender();
        }

        if (!mScroller.isFinished()) {
            mScroller.forceFinished(true);
            mInteractingTab = null;
        }

        if (fromMouse && !clickedClose && clickedTab != null
                && clickedTab.getVisiblePercentage() >= 1.f
                && (buttons & MotionEvent.BUTTON_TERTIARY) == 0) {
            startReorderMode(time, x, x);
        }
    }

    /**
     * Called on long press touch event.
     * @param time The current time of the app in ms.
     * @param x    The x coordinate of the position of the press event.
     * @param y    The y coordinate of the position of the press event.
     */
    public void onLongPress(long time, float x, float y) {
        final StripLayoutTab clickedTab = getTabAtPosition(x);
        if (clickedTab != null && clickedTab.checkCloseHitTest(x, y)) {
            clickedTab.setClosePressed(false);
            mRenderHost.requestRender();
            showTabMenu(clickedTab);
        } else {
            resetResizeTimeout(false);
            startReorderMode(time, x, x);
        }
    }

    /**
     * Called on click. This is called before the onUpOrCancel event.
     * @param time      The current time of the app in ms.
     * @param x         The x coordinate of the position of the click.
     * @param y         The y coordinate of the position of the click.
     * @param fromMouse Whether the event originates from a mouse.
     * @param buttons   State of all buttons that were pressed when onDown was invoked.
     */
    public void click(long time, float x, float y, boolean fromMouse, int buttons) {
        resetResizeTimeout(false);

        if (mNewTabButton.click(x, y) && mModel != null) {
            mTabCreator.launchNTP();
            return;
        }

        final StripLayoutTab clickedTab = getTabAtPosition(x);
        if (clickedTab == null || clickedTab.isDying()) return;
        if (clickedTab.checkCloseHitTest(x, y)
                || (fromMouse && (buttons & MotionEvent.BUTTON_TERTIARY) != 0)) {
            // 1. Start the close animation.
            startAnimation(buildTabClosedAnimation(clickedTab), true);

            // 2. Set the dying state of the tab.
            clickedTab.setIsDying(true);

            // 3. Fake a selection on the next tab now.
            Tab nextTab = mModel.getNextTabIfClosed(clickedTab.getId());
            if (nextTab != null) tabSelected(time, nextTab.getId(), clickedTab.getId());

            // 4. Find out if we're closing the last tab.  This determines if we resize immediately.
            boolean lastTab = mStripTabs.length == 0
                    || mStripTabs[mStripTabs.length - 1].getId() == clickedTab.getId();

            // 5. Resize the tabs appropriately.
            resizeTabStrip(!lastTab);
        } else {
            int newIndex = TabModelUtils.getTabIndexById(mModel, clickedTab.getId());
            TabModelUtils.setIndex(mModel, newIndex);
        }
    }

    /**
     * Called on up or cancel touch events. This is called after the click and fling event if any.
     * @param time The current time of the app in ms.
     */
    public void onUpOrCancel(long time) {
        // 1. Reset the last close button pressed state.
        if (mLastPressedCloseButton != null) mLastPressedCloseButton.onUpOrCancel();
        mLastPressedCloseButton = null;

        // 2. Stop any reordering that is happening.
        stopReorderMode();

        // 3. Reset state
        mInteractingTab = null;
        mReorderState = REORDER_SCROLL_NONE;
        if (mNewTabButton.onUpOrCancel() && mModel != null) {
            mTabCreator.launchNTP();
        }
    }

    private boolean onUpdateAnimation(long time, boolean jumpToEnd) {
        // 1. Handle any Scroller movements (flings).
        if (!jumpToEnd) updateScrollOffset(time);

        // 2. Handle reordering automatically scrolling the tab strip.
        handleReorderAutoScrolling(time);

        // 3. Handle layout-wide animations.
        boolean update = false;
        boolean finished = true;
        if (mLayoutAnimations != null) {
            if (jumpToEnd) {
                finished = mLayoutAnimations.finished();
            } else {
                finished = mLayoutAnimations.update(time);
            }
            if (jumpToEnd || finished) finishAnimation();

            update = true;
        }

        // 4. Handle tab-specific content animations.
        for (int i = 0; i < mStripTabs.length; i++) {
            StripLayoutTab tab = mStripTabs[i];
            if (tab.isAnimating()) {
                update = true;
                finished &= tab.onUpdateAnimation(time, jumpToEnd);
            }
        }

        // 5. Update tab spinners.
        updateSpinners(time);

        // 6. Stop any flings if we're trying to stop animations.
        if (jumpToEnd) mScroller.forceFinished(true);

        // 7. Request another update if anything requires it.
        if (update) mUpdateHost.requestUpdate();

        return finished;
    }

    /**
     * @return Whether or not the tabs are moving.
     */
    @VisibleForTesting
    public boolean isAnimating() {
        return mLayoutAnimations != null || !mScroller.isFinished();
    }

    /**
     * Finishes any outstanding animations and propagates any related changes to the
     * {@link TabModel}.
     */
    public void finishAnimation() {
        if (mLayoutAnimations == null) return;

        // 1. Force any outstanding animations to finish.
        mLayoutAnimations.updateAndFinish();
        mLayoutAnimations = null;

        // 2. Figure out which tabs need to be closed.
        ArrayList<StripLayoutTab> tabsToRemove = new ArrayList<StripLayoutTab>();
        for (int i = 0; i < mStripTabs.length; i++) {
            StripLayoutTab tab = mStripTabs[i];
            if (tab.isDying()) tabsToRemove.add(tab);
        }

        // 3. Pass the close notifications to the model.
        for (StripLayoutTab tab : tabsToRemove) {
            TabModelUtils.closeTabById(mModel, tab.getId(), true);
        }

        if (!tabsToRemove.isEmpty()) mUpdateHost.requestUpdate();
    }

    private void startAnimation(Animation<Animatable<?>> animation, boolean finishPrevious) {
        if (finishPrevious) finishAnimation();

        if (mLayoutAnimations == null) {
            mLayoutAnimations = new ChromeAnimation<ChromeAnimation.Animatable<?>>();
        }

        mLayoutAnimations.add(animation);

        mUpdateHost.requestUpdate();
    }

    private void cancelAnimation(StripLayoutTab tab, StripLayoutTab.Property property) {
        if (mLayoutAnimations == null) return;
        mLayoutAnimations.cancel(tab, property);
    }

    private void updateSpinners(long time) {
        long diff = time - mLastSpinnerUpdate;
        float degrees = diff * SPINNER_DPMS;
        boolean tabsToLoad = false;
        for (int i = 0; i < mStripTabs.length; i++) {
            StripLayoutTab tab = mStripTabs[i];
            // TODO(clholgat): Only update if the tab is visible.
            if (tab.isLoading()) {
                tab.addLoadingSpinnerRotation(degrees);
                tabsToLoad = true;
            }
        }
        mLastSpinnerUpdate = time;
        if (tabsToLoad) {
            mStripTabEventHandler.removeMessages(MESSAGE_UPDATE_SPINNER);
            mStripTabEventHandler.sendEmptyMessageDelayed(
                    MESSAGE_UPDATE_SPINNER, SPINNER_UPDATE_DELAY_MS);
        }
    }

    private void updateScrollOffsetPosition(int pos) {
        int oldScrollOffset = mScrollOffset;
        mScrollOffset = MathUtils.clamp(pos, (int) mMinScrollOffset, 0);

        if (mInReorderMode && mScroller.isFinished()) {
            int delta = MathUtils.flipSignIf(
                    oldScrollOffset - mScrollOffset, LocalizationUtils.isLayoutRtl());
            updateReorderPosition(delta);
        }
    }

    private void updateScrollOffset(long time) {
        if (mScroller.computeScrollOffset(time)) {
            updateScrollOffsetPosition(mScroller.getCurrX());
            mUpdateHost.requestUpdate();
        }
    }

    private void updateScrollOffsetLimits() {
        // 1. Compute the width of the available space for all tabs.
        float stripWidth = mWidth - mLeftMargin - mRightMargin;

        // 2. Compute the effective width of every tab.
        float tabsWidth = 0.f;
        for (int i = 0; i < mStripTabs.length; i++) {
            final StripLayoutTab tab = mStripTabs[i];
            tabsWidth += (tab.getWidth() - mTabOverlapWidth) * tab.getWidthWeight();
        }

        // 3. Correct fencepost error in tabswidth;
        tabsWidth = tabsWidth + mTabOverlapWidth;

        // 4. Calculate the minimum scroll offset.  Round > -EPSILON to 0.
        mMinScrollOffset = Math.min(0.f, stripWidth - tabsWidth);
        if (mMinScrollOffset > -EPSILON) mMinScrollOffset = 0.f;

        // 5. Clamp mScrollOffset to make sure it's in the valid range.
        updateScrollOffsetPosition(mScrollOffset);
    }

    private void computeAndUpdateTabOrders(boolean delayResize) {
        final int count = mModel.getCount();
        StripLayoutTab[] tabs = new StripLayoutTab[count];

        for (int i = 0; i < count; i++) {
            int id = mModel.getTabAt(i).getId();
            StripLayoutTab oldTab = findTabById(id);
            tabs[i] = oldTab != null ? oldTab : createStripTab(id);
            tabs[i].setAccessibilityDescription(mModel.getTabAt(i).getTitle());
        }

        int oldStripLength = mStripTabs.length;
        mStripTabs = tabs;

        if (mStripTabs.length != oldStripLength) resizeTabStrip(delayResize);

        updateVisualTabOrdering();
    }

    private void resizeTabStrip(boolean delay) {
        if (delay) {
            resetResizeTimeout(true);
        } else {
            computeAndUpdateTabWidth(true);
        }
    }

    private void updateVisualTabOrdering() {
        if (mStripTabs.length != mStripTabsVisuallyOrdered.length) {
            mStripTabsVisuallyOrdered = new StripLayoutTab[mStripTabs.length];
        }

        mStripStacker.createVisualOrdering(mModel.index(), mStripTabs, mStripTabsVisuallyOrdered);
    }

    private StripLayoutTab createStripTab(int id) {
        // TODO: Cache these
        StripLayoutTab tab =
                new StripLayoutTab(mContext, id, mTabLoadTrackerHost, mRenderHost, mIncognito);
        tab.setHeight(mHeight);
        pushStackerPropertiesToTab(tab);
        return tab;
    }

    private void pushStackerPropertiesToTab(StripLayoutTab tab) {
        tab.setCanShowCloseButton(mStripStacker.canShowCloseButton());
        // TODO(dtrainor): Push more properties as they are added (title text slide, etc?)
    }

    /**
     * @param id The Tab id.
     * @return The StripLayoutTab that corresponds to that tabid.
     */
    @VisibleForTesting
    public StripLayoutTab findTabById(int id) {
        if (mStripTabs == null) return null;
        for (int i = 0; i < mStripTabs.length; i++) {
            if (mStripTabs[i].getId() == id) return mStripTabs[i];
        }
        return null;
    }

    private int findIndexForTab(int id) {
        if (mStripTabs == null) return TabModel.INVALID_TAB_INDEX;
        for (int i = 0; i < mStripTabs.length; i++) {
            if (mStripTabs[i].getId() == id) return i;
        }
        return TabModel.INVALID_TAB_INDEX;
    }

    private void computeAndUpdateTabWidth(boolean animate) {
        // Remove any queued resize messages.
        mStripTabEventHandler.removeMessages(MESSAGE_RESIZE);

        int numTabs = Math.max(mStripTabs.length, 1);

        // 1. Compute the width of the available space for all tabs.
        float stripWidth = mWidth - mLeftMargin - mRightMargin;

        // 2. Compute additional width we gain from overlapping the tabs.
        float overlapWidth = mTabOverlapWidth * (numTabs - 1);

        // 3. Calculate the optimal tab width.
        float optimalTabWidth = (stripWidth + overlapWidth) / numTabs;

        // 4. Calculate the realistic tab width.
        mCachedTabWidth = MathUtils.clamp(optimalTabWidth, mMinTabWidth, mMaxTabWidth);

        // 5. Propagate the tab width to all tabs.
        for (int i = 0; i < mStripTabs.length; i++) {
            StripLayoutTab tab = mStripTabs[i];
            if (tab.isDying()) continue;

            // 5.a. Cancel any outstanding tab width animations.
            cancelAnimation(mStripTabs[i], StripLayoutTab.Property.WIDTH);

            if (animate) {
                startAnimation(buildTabResizeAnimation(tab, mCachedTabWidth), false);
            } else {
                mStripTabs[i].setWidth(mCachedTabWidth);
            }
        }
    }

    private void updateStrip(long time, long dt) {
        if (mModel == null) return;

        // TODO(dtrainor): Remove this once tabCreated() is refactored to be called even from
        // restore.
        if (mStripTabs == null || mModel.getCount() != mStripTabs.length) {
            computeAndUpdateTabOrders(false);
        }

        // 1. Update the scroll offset limits
        updateScrollOffsetLimits();

        // 2. Calculate the ideal tab positions
        computeTabInitialPositions();

        // 3. Calculate the tab stacking.
        computeTabOffsetHelper();

        // 4. Calculate which tabs are visible.
        mStripStacker.performOcclusionPass(mModel.index(), mStripTabs);

        // 5. Create render list.
        createRenderList();

        // 6. Figure out where to put the new tab button.
        updateNewTabButtonState();

        // 7. Check if we have any animations and request an update if so.
        for (int i = 0; i < mStripTabs.length; i++) {
            if (mStripTabs[i].isAnimating()) {
                mUpdateHost.requestUpdate();
                break;
            }
        }
    }

    private void computeTabInitialPositions() {
        // Shift all of the tabs over by the the left margin because we're
        // no longer base lined at 0
        float tabPosition;
        if (!LocalizationUtils.isLayoutRtl()) {
            tabPosition = mScrollOffset + mLeftMargin;
        } else {
            tabPosition = mWidth - mCachedTabWidth - mScrollOffset - mRightMargin;
        }

        for (int i = 0; i < mStripTabs.length; i++) {
            StripLayoutTab tab = mStripTabs[i];
            tab.setIdealX(tabPosition);
            float delta = (tab.getWidth() - mTabOverlapWidth) * tab.getWidthWeight();
            delta = MathUtils.flipSignIf(delta, LocalizationUtils.isLayoutRtl());
            tabPosition += delta;
        }
    }

    private void computeTabOffsetHelper() {
        final int selIndex = mModel.index();

        // 1. Calculate the size of the selected tab.  This is used later to figure out how
        // occluded the tabs are.
        final StripLayoutTab selTab = selIndex >= 0 ? mStripTabs[selIndex] : null;
        final float selTabWidth = selTab != null ? selTab.getWidth() : 0;
        final float selTabVisibleSize = selTabWidth - mTabStackWidth - mTabOverlapWidth;

        for (int i = 0; i < mStripTabs.length; i++) {
            StripLayoutTab tab = mStripTabs[i];

            float posX = tab.getIdealX();

            // 2. Calculate how many tabs are stacked on the left or the right, giving us an idea
            // of where we can stack this current tab.
            int leftStackCount = (i < selIndex) ? Math.min(i, MAX_TABS_TO_STACK)
                                                : Math.min(MAX_TABS_TO_STACK, selIndex)
                            + Math.min(MAX_TABS_TO_STACK, i - selIndex);

            int rightStackCount = (i >= selIndex)
                    ? Math.min(mStripTabs.length - 1 - i, MAX_TABS_TO_STACK)
                    : Math.min(mStripTabs.length - 1 - selIndex, MAX_TABS_TO_STACK)
                            + Math.min(selIndex - i, MAX_TABS_TO_STACK);

            if (LocalizationUtils.isLayoutRtl()) {
                int oldLeft = leftStackCount;
                leftStackCount = rightStackCount;
                rightStackCount = oldLeft;
            }

            // 3. Calculate the proper draw position for the tab.  Clamp based on stacking
            // rules.
            float minDrawX = mTabStackWidth * leftStackCount + mLeftMargin;
            float maxDrawX = mWidth - mTabStackWidth * rightStackCount - mRightMargin;

            float drawX =
                    MathUtils.clamp(posX + tab.getOffsetX(), minDrawX, maxDrawX - tab.getWidth());

            // TODO(dtrainor): Don't set drawX if the tab is closing?
            tab.setDrawX(drawX);
            tab.setDrawY(tab.getOffsetY());

            // 4. Calculate how visible this tab is.
            float visiblePercentage = 1.f;
            if (i != selIndex) {
                final float effectiveTabWidth = Math.max(tab.getWidth(), 1.f);
                final boolean leftStack =
                        LocalizationUtils.isLayoutRtl() ? i > selIndex : i < selIndex;
                final float minVisible = !leftStack ? minDrawX + selTabVisibleSize : minDrawX;
                final float maxVisible = leftStack ? maxDrawX - selTabVisibleSize : maxDrawX;

                final float clippedTabWidth =
                        Math.min(posX + effectiveTabWidth, maxVisible) - Math.max(posX, minVisible);
                visiblePercentage = MathUtils.clamp(clippedTabWidth / effectiveTabWidth, 0.f, 1.f);
            }
            tab.setVisiblePercentage(visiblePercentage);

            // 5. Calculate which index we start sliding content for.
            // When reordering, we don't want to slide the content of the adjacent tabs.
            int contentOffsetIndex = mInReorderMode ? selIndex + 1 : selIndex;

            // 6. Calculate how much the tab is overlapped on the left side or right for RTL.
            float hiddenAmount = 0.f;
            if (i > contentOffsetIndex && i > 0 && mStripStacker.canSlideTitleText()) {
                // 6.a. Get the effective right edge of the previous tab.
                final StripLayoutTab prevTab = mStripTabs[i - 1];
                final float prevLayoutWidth =
                        (prevTab.getWidth() - mTabOverlapWidth) * prevTab.getWidthWeight();
                float prevTabRight = prevTab.getDrawX();
                if (!LocalizationUtils.isLayoutRtl()) prevTabRight += prevLayoutWidth;

                // 6.b. Subtract our current draw X from the previous tab's right edge and
                // get the percentage covered.
                hiddenAmount = Math.max(prevTabRight - drawX, 0);
                if (LocalizationUtils.isLayoutRtl()) {
                    // Invert The amount because we're RTL.
                    hiddenAmount = prevLayoutWidth - hiddenAmount;
                }
            }

            tab.setContentOffsetX(hiddenAmount);
        }
    }

    private void createRenderList() {
        // 1. Figure out how many tabs will need to be rendered.
        int renderCount = 0;
        for (int i = 0; i < mStripTabsVisuallyOrdered.length; ++i) {
            if (mStripTabsVisuallyOrdered[i].isVisible()) renderCount++;
        }

        // 2. Reallocate the render list if necessary.
        if (mStripTabsToRender.length != renderCount) {
            mStripTabsToRender = new StripLayoutTab[renderCount];
        }

        // 3. Populate it with the visible tabs.
        int renderIndex = 0;
        for (int i = 0; i < mStripTabsVisuallyOrdered.length; ++i) {
            if (mStripTabsVisuallyOrdered[i].isVisible()) {
                mStripTabsToRender[renderIndex++] = mStripTabsVisuallyOrdered[i];
            }
        }
    }

    private void updateNewTabButtonState() {
        // 1. Don't display the new tab button if we're in reorder mode.
        if (mInReorderMode || mStripTabs.length == 0) {
            mNewTabButton.setVisible(false);
            return;
        }
        mNewTabButton.setVisible(true);

        float leftEdge = mWidth - mRightMargin;
        float rightEdge = mLeftMargin;

        for (int i = 0; i < mStripTabs.length; i++) {
            StripLayoutTab tab = mStripTabs[i];
            float layoutWidth = (tab.getWidth() - mTabOverlapWidth) * tab.getWidthWeight();
            rightEdge = Math.max(tab.getDrawX() + layoutWidth, rightEdge);
            leftEdge = Math.min(tab.getDrawX(), leftEdge);
        }
        rightEdge = Math.min(rightEdge + mTabOverlapWidth, mWidth - mRightMargin);
        leftEdge = Math.max(leftEdge, mLeftMargin);

        rightEdge -= mTabOverlapWidth / 2;

        // 3. Position the new tab button.
        if (!LocalizationUtils.isLayoutRtl()) {
            mNewTabButton.setX(rightEdge);
        } else {
            mNewTabButton.setX(leftEdge - mNewTabButtonWidth);
        }
    }

    private float calculateOffsetToMakeTabVisible(StripLayoutTab tab, boolean canExpandSelectedTab,
            boolean canExpandLeft, boolean canExpandRight) {
        if (tab == null) return 0.f;

        final int selIndex = mModel.index();
        final int index = TabModelUtils.getTabIndexById(mModel, tab.getId());

        // 1. The selected tab is always visible.  Early out unless we want to unstack it.
        if (selIndex == index && !canExpandSelectedTab) return 0.f;

        // TODO(dtrainor): Use real tab widths here?
        float stripWidth = mWidth - mLeftMargin - mRightMargin;
        final float tabWidth = mCachedTabWidth - mTabOverlapWidth;

        // TODO(dtrainor): Handle maximum number of tabs that can be visibly stacked in these
        // optimal positions.

        // 2. Calculate the optimal minimum and maximum scroll offsets to show the tab.
        float optimalLeft = -index * tabWidth;
        float optimalRight = stripWidth - (index + 1) * tabWidth;

        // 3. Account for the selected tab always being visible.  Need to buffer by one extra
        // tab width depending on if the tab is to the left or right of the selected tab.
        if (index < selIndex) {
            optimalRight -= tabWidth;
        } else if (index > selIndex) {
            optimalLeft += tabWidth;
        }

        // 4. Return the proper deltaX that has to be applied to the current scroll to see the
        // tab.
        if (mScrollOffset < optimalLeft && canExpandLeft) {
            return optimalLeft - mScrollOffset;
        } else if (mScrollOffset > optimalRight && canExpandRight) {
            return optimalRight - mScrollOffset;
        }

        // 5. We don't have to do anything.  Return no delta.
        return 0.f;
    }

    private StripLayoutTab getTabAtPosition(float x) {
        for (int i = mStripTabsVisuallyOrdered.length - 1; i >= 0; i--) {
            final StripLayoutTab tab = mStripTabsVisuallyOrdered[i];
            if (tab.isVisible() && tab.getDrawX() <= x && x <= (tab.getDrawX() + tab.getWidth())) {
                return tab;
            }
        }

        return null;
    }

    /**
     * @param tab The StripLayoutTab to look for.
     * @return The index of the tab in the visual ordering.
     */
    @VisibleForTesting
    public int visualIndexOfTab(StripLayoutTab tab) {
        for (int i = 0; i < mStripTabsVisuallyOrdered.length; i++) {
            if (mStripTabsVisuallyOrdered[i] == tab) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @param tab The StripLayoutTab you're looking at.
     * @return Whether or not this tab is the foreground tab.
     */
    @VisibleForTesting
    public boolean isForegroundTab(StripLayoutTab tab) {
        return tab == mStripTabsVisuallyOrdered[mStripTabsVisuallyOrdered.length - 1];
    }

    private void startReorderMode(long time, float currentX, float startX) {
        if (mInReorderMode) return;

        // 1. Reset the last pressed close button state.
        if (mLastPressedCloseButton != null && mLastPressedCloseButton.isPressed()) {
            mLastPressedCloseButton.setPressed(false);
        }
        mLastPressedCloseButton = null;

        // 2. Check to see if we have a valid tab to start dragging.
        mInteractingTab = getTabAtPosition(startX);
        if (mInteractingTab == null) return;

        // 3. Set initial state parameters.
        mLastReorderScrollTime = 0;
        mReorderState = REORDER_SCROLL_NONE;
        mLastReorderX = startX;
        mInReorderMode = true;

        // 4. Select this tab so that it is always in the foreground.
        TabModelUtils.setIndex(
                mModel, TabModelUtils.getTabIndexById(mModel, mInteractingTab.getId()));

        // 5. Fast expand to make sure this tab is visible.
        float fastExpandDelta = calculateOffsetToMakeTabVisible(mInteractingTab, true, true, true);
        mScroller.startScroll(mScrollOffset, 0, (int) fastExpandDelta, 0, time, EXPAND_DURATION_MS);

        // 6. Request an update.
        mUpdateHost.requestUpdate();
    }

    private void stopReorderMode() {
        if (!mInReorderMode) return;

        // 1. Reset the state variables.
        mLastReorderScrollTime = 0;
        mReorderState = REORDER_SCROLL_NONE;
        mLastReorderX = 0.f;
        mInReorderMode = false;

        // 2. Clear any drag offset.
        startAnimation(buildTabMoveAnimation(mInteractingTab, mInteractingTab.getOffsetX()), true);

        // 3. Request an update.
        mUpdateHost.requestUpdate();
    }

    private void updateReorderPosition(float deltaX) {
        if (!mInReorderMode || mInteractingTab == null) return;

        float offset = mInteractingTab.getOffsetX() + deltaX;
        int curIndex = findIndexForTab(mInteractingTab.getId());

        // 1. Compute the reorder threshold values.
        final float flipWidth = mCachedTabWidth - mTabOverlapWidth;
        final float flipThreshold = REORDER_OVERLAP_SWITCH_PERCENTAGE * flipWidth;

        // 2. Check if we should swap tabs and track the new destination index.
        int destIndex = TabModel.INVALID_TAB_INDEX;
        boolean pastLeftThreshold = offset < -flipThreshold;
        boolean pastRightThreshold = offset > flipThreshold;
        boolean isNotRightMost = curIndex < mStripTabs.length - 1;
        boolean isNotLeftMost = curIndex > 0;

        if (LocalizationUtils.isLayoutRtl()) {
            boolean oldLeft = pastLeftThreshold;
            pastLeftThreshold = pastRightThreshold;
            pastRightThreshold = oldLeft;
        }

        if (pastRightThreshold && isNotRightMost) {
            destIndex = curIndex + 2;
        } else if (pastLeftThreshold && isNotLeftMost) {
            destIndex = curIndex - 1;
        }

        // 3. If we should swap tabs, make the swap.
        if (destIndex != TabModel.INVALID_TAB_INDEX) {
            // 3.a. Since we're about to move the tab we're dragging, adjust it's offset so it
            // stays in the same apparent position.
            boolean shouldFlip =
                    LocalizationUtils.isLayoutRtl() ? destIndex < curIndex : destIndex > curIndex;
            offset += MathUtils.flipSignIf(flipWidth, shouldFlip);

            // 3.b. Swap the tabs.
            reorderTab(mInteractingTab.getId(), curIndex, destIndex, true);
            mModel.moveTab(mInteractingTab.getId(), destIndex);

            // 3.c. Update our curIndex as we have just moved the tab.
            curIndex += destIndex > curIndex ? 1 : -1;

            // 3.d. Update visual tab ordering.
            updateVisualTabOrdering();
        }

        // 4. Limit offset based on tab position.  First tab can't drag left, last tab can't drag
        // right.
        if (curIndex == 0) {
            offset =
                    LocalizationUtils.isLayoutRtl() ? Math.min(0.f, offset) : Math.max(0.f, offset);
        }
        if (curIndex == mStripTabs.length - 1) {
            offset =
                    LocalizationUtils.isLayoutRtl() ? Math.max(0.f, offset) : Math.min(0.f, offset);
        }

        // 5. Set the new offset.
        mInteractingTab.setOffsetX(offset);
    }

    private void reorderTab(int id, int oldIndex, int newIndex, boolean animate) {
        StripLayoutTab tab = findTabById(id);
        if (tab == null || oldIndex == newIndex) return;

        // 1. If the tab is already at the right spot, don't do anything.
        int index = findIndexForTab(id);
        if (index == newIndex) return;

        // 2. Check if it's the tab we are dragging, but we have an old source index.  Ignore in
        // this case because we probably just already moved it.
        if (mInReorderMode && index != oldIndex && tab == mInteractingTab) return;

        // 3. Swap the tabs.
        moveElement(mStripTabs, index, newIndex);

        // 4. Update newIndex to point to the proper element.
        if (index < newIndex) newIndex--;

        // 5. Animate if necessary.
        if (animate) {
            final float flipWidth = mCachedTabWidth - mTabOverlapWidth;
            final int direction = oldIndex <= newIndex ? 1 : -1;
            final float animationLength =
                    MathUtils.flipSignIf(direction * flipWidth, LocalizationUtils.isLayoutRtl());
            StripLayoutTab slideTab = mStripTabs[newIndex - direction];
            startAnimation(buildTabMoveAnimation(slideTab, animationLength), true);
        }
    }

    private void handleReorderAutoScrolling(long time) {
        if (!mInReorderMode) return;

        // 1. Track the delta time since the last auto scroll.
        final float deltaSec =
                mLastReorderScrollTime == 0 ? 0.f : (time - mLastReorderScrollTime) / 1000.f;
        mLastReorderScrollTime = time;

        final float x = mInteractingTab.getDrawX();

        // 2. Calculate the gutters for accelerating the scroll speed.
        // Speed: MAX    MIN                  MIN    MAX
        // |-------|======|--------------------|======|-------|
        final float dragRange = REORDER_EDGE_SCROLL_START_MAX_DP - REORDER_EDGE_SCROLL_START_MIN_DP;
        final float leftMinX = REORDER_EDGE_SCROLL_START_MIN_DP + mLeftMargin;
        final float leftMaxX = REORDER_EDGE_SCROLL_START_MAX_DP + mLeftMargin;
        final float rightMinX =
                mWidth - mLeftMargin - mRightMargin - REORDER_EDGE_SCROLL_START_MIN_DP;
        final float rightMaxX =
                mWidth - mLeftMargin - mRightMargin - REORDER_EDGE_SCROLL_START_MAX_DP;

        // 3. See if the current draw position is in one of the gutters and figure out how far in.
        // Note that we only allow scrolling in each direction if the user has already manually
        // moved that way.
        float dragSpeedRatio = 0.f;
        if ((mReorderState & REORDER_SCROLL_LEFT) != 0 && x < leftMinX) {
            dragSpeedRatio = -(leftMinX - Math.max(x, leftMaxX)) / dragRange;
        } else if ((mReorderState & REORDER_SCROLL_RIGHT) != 0 && x + mCachedTabWidth > rightMinX) {
            dragSpeedRatio = (Math.min(x + mCachedTabWidth, rightMaxX) - rightMinX) / dragRange;
        }

        dragSpeedRatio = MathUtils.flipSignIf(dragSpeedRatio, LocalizationUtils.isLayoutRtl());

        if (dragSpeedRatio != 0.f) {
            // 4.a. We're in a gutter.  Update the scroll offset.
            float dragSpeed = REORDER_EDGE_SCROLL_MAX_SPEED_DP * dragSpeedRatio;
            updateScrollOffsetPosition((int) (mScrollOffset + dragSpeed * deltaSec));

            mUpdateHost.requestUpdate();
        } else {
            // 4.b. We're not in a gutter.  Reset the scroll delta time tracker.
            mLastReorderScrollTime = 0;
        }
    }

    private void resetResizeTimeout(boolean postIfNotPresent) {
        final boolean present = mStripTabEventHandler.hasMessages(MESSAGE_RESIZE);

        if (present) mStripTabEventHandler.removeMessages(MESSAGE_RESIZE);

        if (present || postIfNotPresent) {
            mStripTabEventHandler.sendEmptyMessageAtTime(MESSAGE_RESIZE, RESIZE_DELAY_MS);
        }
    }

    private class StripTabEventHandler extends Handler {
        @Override
        public void handleMessage(Message m) {
            switch (m.what) {
                case MESSAGE_RESIZE:
                    computeAndUpdateTabWidth(true);
                    mUpdateHost.requestUpdate();
                    break;
                case MESSAGE_UPDATE_SPINNER:
                    mUpdateHost.requestUpdate();
                    break;
                default:
                    assert false : "StripTabEventHandler got unknown message " + m.what;
            }
        }
    }

    private class TabLoadTrackerCallbackImpl implements TabLoadTrackerCallback {
        @Override
        public void loadStateChanged(int id) {
            mUpdateHost.requestUpdate();
        }
    }

    private static Animation<Animatable<?>> buildTabCreatedAnimation(StripLayoutTab tab) {
        return createAnimation(tab, StripLayoutTab.Property.Y_OFFSET, tab.getHeight(), 0.f,
                ANIM_TAB_CREATED_MS, 0, false, ChromeAnimation.getLinearInterpolator());
    }

    private static Animation<Animatable<?>> buildTabClosedAnimation(StripLayoutTab tab) {
        return createAnimation(tab, StripLayoutTab.Property.Y_OFFSET, tab.getOffsetY(),
                tab.getHeight(), ANIM_TAB_CLOSED_MS, 0, false,
                ChromeAnimation.getLinearInterpolator());
    }

    private static Animation<Animatable<?>> buildTabResizeAnimation(
            StripLayoutTab tab, float width) {
        return createAnimation(tab, StripLayoutTab.Property.WIDTH, tab.getWidth(), width,
                ANIM_TAB_RESIZE_MS, 0, false, ChromeAnimation.getLinearInterpolator());
    }

    private static Animation<Animatable<?>> buildTabMoveAnimation(
            StripLayoutTab tab, float startX) {
        return createAnimation(tab, StripLayoutTab.Property.X_OFFSET, startX, 0.f, ANIM_TAB_MOVE_MS,
                0, false, ChromeAnimation.getLinearInterpolator());
    }

    private static <T> void moveElement(T[] array, int oldIndex, int newIndex) {
        if (oldIndex <= newIndex) {
            moveElementUp(array, oldIndex, newIndex);
        } else {
            moveElementDown(array, oldIndex, newIndex);
        }
    }

    private static <T> void moveElementUp(T[] array, int oldIndex, int newIndex) {
        assert oldIndex <= newIndex;
        if (oldIndex == newIndex || oldIndex + 1 == newIndex) return;

        T elem = array[oldIndex];
        for (int i = oldIndex; i < newIndex - 1; i++) {
            array[i] = array[i + 1];
        }
        array[newIndex - 1] = elem;
    }

    private static <T> void moveElementDown(T[] array, int oldIndex, int newIndex) {
        assert oldIndex >= newIndex;
        if (oldIndex == newIndex) return;

        T elem = array[oldIndex];
        for (int i = oldIndex - 1; i >= newIndex; i--) {
            array[i + 1] = array[i];
        }
        array[newIndex] = elem;
    }

    /**
     * Sets the current scroll offset of the TabStrip.
     * @param offset The offset to set the TabStrip's scroll state to.
     */
    @VisibleForTesting
    public void testSetScrollOffset(int offset) {
        mScrollOffset = offset;
    }

    /**
     * Starts a fling with the specified velocity.
     * @param velocity The velocity to trigger the fling with.  Negative to go left, positive to go
     * right.
     */
    @VisibleForTesting
    public void testFling(float velocity) {
        fling(SystemClock.uptimeMillis(), 0, 0, velocity, 0);
    }

    /**
     * Displays the tab menu below the anchor tab.
     * @param anchorTab The tab the menu will be anchored to
     */
    private void showTabMenu(StripLayoutTab anchorTab) {
        // 1. Bring the anchor tab to the foreground.
        int tabIndex = TabModelUtils.getTabIndexById(mModel, anchorTab.getId());
        TabModelUtils.setIndex(mModel, tabIndex);

        // 2. Anchor the popupMenu to the view associated with the tab
        View tabView = TabModelUtils.getCurrentTab(mModel).getView();
        mTabMenu.setAnchorView(tabView);

        // 3. Set the vertical offset to align the tab menu with bottom of the tab strip
        int verticalOffset =
                -(tabView.getHeight()
                        - (int) mContext.getResources().getDimension(R.dimen.tab_strip_height))
                - ((MarginLayoutParams) tabView.getLayoutParams()).topMargin;
        mTabMenu.setVerticalOffset(verticalOffset);

        // 4. Set the horizontal offset to align the tab menu with the right side of the tab
        int horizontalOffset = Math.round((anchorTab.getDrawX() + anchorTab.getWidth())
                                       * mContext.getResources().getDisplayMetrics().density)
                - mTabMenu.getWidth()
                - ((MarginLayoutParams) tabView.getLayoutParams()).leftMargin;
        mTabMenu.setHorizontalOffset(horizontalOffset);

        mTabMenu.show();
    }

    /**
     * @return true if the tab menu is showing
     */
    @VisibleForTesting
    public boolean isTabMenuShowing() {
        return mTabMenu.isShowing();
    }

    /**
     * @param menuItemId The id of the menu item to click
     */
    @VisibleForTesting
    public void clickTabMenuItem(int menuItemId) {
        mTabMenu.performItemClick(menuItemId);
    }
}
