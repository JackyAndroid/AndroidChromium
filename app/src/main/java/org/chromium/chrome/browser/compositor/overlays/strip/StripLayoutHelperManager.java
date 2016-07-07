// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.overlays.strip;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.LayoutRenderHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.compositor.layouts.components.CompositorButton;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.AreaGestureEventFilter;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.overlays.SceneOverlay;
import org.chromium.chrome.browser.compositor.scene_layer.SceneOverlayLayer;
import org.chromium.chrome.browser.compositor.scene_layer.TabStripSceneLayer;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.ui.resources.ResourceManager;

import java.util.List;

/**
 * This class handles managing which {@link StripLayoutHelper} is currently active and dispatches
 * all input and model events to the proper destination.
 */
public class StripLayoutHelperManager implements SceneOverlay {
    // Visibility Constants
    private static final float TAB_STRIP_HEIGHT_DP = 40.f;

    // Caching Variables
    private final RectF mStripFilterArea = new RectF();

    // 1px border colors
    private static final float BORDER_OPACITY = 0.2f;
    private static final float BORDER_OPACITY_INCOGNITO = 0.4f;

    // Model selector buttons constants.
    private static final float MODEL_SELECTOR_BUTTON_Y_OFFSET_DP = 10.f;
    private static final float MODEL_SELECTOR_BUTTON_RIGHT_PADDING_DP = 6.f;
    private static final float MODEL_SELECTOR_BUTTON_LEFT_PADDING_DP = 3.f;
    private static final float MODEL_SELECTOR_BUTTON_WIDTH_DP = 24.f;
    private static final float MODEL_SELECTOR_BUTTON_HEIGHT_DP = 24.f;

    // External influences
    private TabModelSelector mTabModelSelector;
    private final LayoutUpdateHost mUpdateHost;
    private final LayoutRenderHost mRenderHost;

    // Event Filters
    private final AreaGestureEventFilter mEventFilter;

    // Internal state
    private boolean mIsIncognito;
    private final StripLayoutHelper mNormalHelper;
    private final StripLayoutHelper mIncognitoHelper;

    // UI State
    private float mWidth;
    private final float mHeight;
    private int mOrientation;
    private final CompositorButton mModelSelectorButton;

    private TabStripSceneLayer mTabStripTreeProvider;

    /**
     * Creates an instance of the {@link StripLayoutHelperManager}.
     * @param context           The current Android {@link Context}.
     * @param updateHost        The parent {@link LayoutUpdateHost}.
     * @param renderHost        The {@link LayoutRenderHost}.
     */
    public StripLayoutHelperManager(Context context, LayoutUpdateHost updateHost,
            LayoutRenderHost renderHost, AreaGestureEventFilter eventFilter) {
        mHeight = TAB_STRIP_HEIGHT_DP;

        mUpdateHost = updateHost;
        mRenderHost = renderHost;
        mTabStripTreeProvider = new TabStripSceneLayer(context);

        mEventFilter = eventFilter;

        mNormalHelper = new StripLayoutHelper(context, updateHost, renderHost, false);
        mIncognitoHelper = new StripLayoutHelper(context, updateHost, renderHost, true);

        mModelSelectorButton = new CompositorButton(
                context, MODEL_SELECTOR_BUTTON_WIDTH_DP, MODEL_SELECTOR_BUTTON_HEIGHT_DP);
        mModelSelectorButton.setIncognito(false);
        mModelSelectorButton.setVisible(false);
        // Pressed resources are the same as the unpressed resources.
        mModelSelectorButton.setResources(R.drawable.btn_tabstrip_switch_normal,
                R.drawable.btn_tabstrip_switch_normal, R.drawable.btn_tabstrip_switch_incognito,
                R.drawable.btn_tabstrip_switch_incognito);
        mModelSelectorButton.setY(MODEL_SELECTOR_BUTTON_Y_OFFSET_DP);

        Resources res = context.getResources();
        mModelSelectorButton.setAccessibilityDescription(
                res.getString(R.string.accessibility_tabstrip_btn_incognito_toggle_standard),
                res.getString(R.string.accessibility_tabstrip_btn_incognito_toggle_incognito));

        onContextChanged(context);
    }

    /**
     * Cleans up internal state.
     */
    public void destroy() {
        mTabStripTreeProvider.destroy();
        mTabStripTreeProvider = null;
    }

    @Override
    public SceneOverlayLayer getUpdatedSceneOverlayTree(LayerTitleCache layerTitleCache,
            ResourceManager resourceManager, float yOffset) {
        assert mTabStripTreeProvider != null;

        mTabStripTreeProvider.pushAndUpdateStrip(this, layerTitleCache, resourceManager,
                getActiveStripLayoutHelper().getStripLayoutTabsToRender(), yOffset);
        return mTabStripTreeProvider;
    }

    @Override
    public EventFilter getEventFilter() {
        return mEventFilter;
    }

    @Override
    public void onSizeChanged(
            float width, float height, float visibleViewportOffsetY, int orientation) {
        mWidth = width;
        mOrientation = orientation;
        mModelSelectorButton.setX(
                mWidth - MODEL_SELECTOR_BUTTON_WIDTH_DP - MODEL_SELECTOR_BUTTON_RIGHT_PADDING_DP);
        mNormalHelper.onSizeChanged(mWidth, mHeight);
        mIncognitoHelper.onSizeChanged(mWidth, mHeight);

        mStripFilterArea.set(0, 0, mWidth, Math.min(getHeight(), visibleViewportOffsetY));
        mEventFilter.setEventArea(mStripFilterArea);
    }

    public CompositorButton getNewTabButton() {
        return getActiveStripLayoutHelper().getNewTabButton();
    }

    public CompositorButton getModelSelectorButton() {
        return mModelSelectorButton;
    }

    @Override
    public void getVirtualViews(List<VirtualView> views) {
        if (mModelSelectorButton.isVisible()) views.add(mModelSelectorButton);
        getActiveStripLayoutHelper().getVirtualViews(views);
    }

    /**
     * @return The brightness of background tabs in the tabstrip.
     */
    public float getBackgroundTabBrightness() {
        return getActiveStripLayoutHelper().getBackgroundTabBrightness();
    }

    /**
     * @param brightness Sets the brightness for the entire tabstrip.
     */
    public void setBrightness(float brightness) {
        getActiveStripLayoutHelper().setBrightness(brightness);
    }

    /**
     * @return The brightness of the entire tabstrip.
     */
    public float getBrightness() {
        return getActiveStripLayoutHelper().getBrightness();
    }

    /**
     * Sets the {@link TabModelSelector} that this {@link StripLayoutHelperManager} will visually
     * represent, and various objects associated with it.
     * @param modelSelector The {@link TabModelSelector} to visually represent.
     * @param tabCreatorManager The {@link TabCreatorManager}, used to create new tabs.
     * @param tabContentManager The {@link TabContentManager}, used to provide display content for
     *                          tabs.
     */
    public void setTabModelSelector(TabModelSelector modelSelector,
            TabCreatorManager tabCreatorManager, TabContentManager tabContentManager) {
        if (mTabModelSelector == modelSelector) return;
        mTabModelSelector = modelSelector;
        mNormalHelper.setTabModel(mTabModelSelector.getModel(false), tabContentManager,
                tabCreatorManager.getTabCreator(false));
        mIncognitoHelper.setTabModel(mTabModelSelector.getModel(true), tabContentManager,
                tabCreatorManager.getTabCreator(true));
        tabModelSwitched(mTabModelSelector.isIncognitoSelected());
    }

    @Override
    public void tabTitleChanged(int tabId, String title) {
        getActiveStripLayoutHelper().tabTitleChanged(tabId, title);
    }

    public float getHeight() {
        return mHeight;
    }

    public float getWidth() {
        return mWidth;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public float getBorderOpacity() {
        return mIsIncognito ? BORDER_OPACITY_INCOGNITO : BORDER_OPACITY;
    }

    /**
     * Updates all internal resources and dimensions.
     * @param context The current Android {@link Context}.
     */
    public void onContextChanged(Context context) {
        mNormalHelper.onContextChanged(context);
        mIncognitoHelper.onContextChanged(context);
    }

    @Override
    public boolean updateOverlay(long time, long dt) {
        getInactiveStripLayoutHelper().finishAnimation();
        return getActiveStripLayoutHelper().updateLayout(time, dt);
    }

    @Override
    public void tabModelSwitched(boolean incognito) {
        if (incognito == mIsIncognito) return;
        mIsIncognito = incognito;

        updateModelSwitcherButton();

        mUpdateHost.requestUpdate();
    }

    private void updateModelSwitcherButton() {
        mModelSelectorButton.setIncognito(mIsIncognito);
        if (mTabModelSelector != null) {
            boolean isVisible = mTabModelSelector.getModel(true).getCount() != 0;
            mModelSelectorButton.setVisible(isVisible);

            float rightMargin = isVisible
                    ? MODEL_SELECTOR_BUTTON_WIDTH_DP + MODEL_SELECTOR_BUTTON_RIGHT_PADDING_DP
                            + MODEL_SELECTOR_BUTTON_LEFT_PADDING_DP
                    : 0.0f;
            mNormalHelper.setRightMargin(rightMargin);
            mIncognitoHelper.setRightMargin(rightMargin);
        }
    }

    @Override
    public void tabSelected(long time, boolean incognito, int id, int prevId) {
        getStripLayoutHelper(incognito).tabSelected(time, id, prevId);
    }

    @Override
    public void tabMoved(long time, boolean incognito, int id, int oldIndex, int newIndex) {
        getStripLayoutHelper(incognito).tabMoved(time, id, oldIndex, newIndex);
    }

    @Override
    public void tabClosed(long time, boolean incognito, int id) {
        getStripLayoutHelper(incognito).tabClosed(time, id);
        updateModelSwitcherButton();
    }

    @Override
    public void tabClosureCancelled(long time, boolean incognito, int id) {
        getStripLayoutHelper(incognito).tabClosureCancelled(time, id);
        updateModelSwitcherButton();
    }

    @Override
    public void tabCreated(long time, boolean incognito, int id, int prevId, boolean selected) {
        getStripLayoutHelper(incognito).tabCreated(time, id, prevId, selected);
    }

    @Override
    public void tabPageLoadStarted(int id, boolean incognito) {
        getStripLayoutHelper(incognito).tabPageLoadStarted(id);
    }

    @Override
    public void tabPageLoadFinished(int id, boolean incognito) {
        getStripLayoutHelper(incognito).tabPageLoadFinished(id);
    }

    @Override
    public void tabLoadStarted(int id, boolean incognito) {
        getStripLayoutHelper(incognito).tabLoadStarted(id);
    }

    @Override
    public void tabLoadFinished(int id, boolean incognito) {
        getStripLayoutHelper(incognito).tabLoadFinished(id);
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
        mModelSelectorButton.drag(x, y);
        getActiveStripLayoutHelper().drag(time, x, y, deltaX, deltaY, totalX, totalY);
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
        getActiveStripLayoutHelper().fling(time, x, y, velocityX, velocityY);
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
        if (mModelSelectorButton.onDown(x, y)) return;
        getActiveStripLayoutHelper().onDown(time, x, y, fromMouse, buttons);
    }

    /**
     * Called on long press touch event.
     * @param time The current time of the app in ms.
     * @param x    The x coordinate of the position of the press event.
     * @param y    The y coordinate of the position of the press event.
     */
    public void onLongPress(long time, float x, float y) {
        getActiveStripLayoutHelper().onLongPress(time, x, y);
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
        if (mModelSelectorButton.click(x, y) && mTabModelSelector != null) {
            getActiveStripLayoutHelper().finishAnimation();
            if (!mModelSelectorButton.isVisible()) return;
            mTabModelSelector.selectModel(!mTabModelSelector.isIncognitoSelected());
            return;
        }
        getActiveStripLayoutHelper().click(time, x, y, fromMouse, buttons);
    }

    /**
     * Called on up or cancel touch events. This is called after the click and fling event if any.
     * @param time The current time of the app in ms.
     */
    public void onUpOrCancel(long time) {
        if (mModelSelectorButton.onUpOrCancel() && mTabModelSelector != null) {
            getActiveStripLayoutHelper().finishAnimation();
            if (!mModelSelectorButton.isVisible()) return;
            mTabModelSelector.selectModel(!mTabModelSelector.isIncognitoSelected());
            return;
        }
        getActiveStripLayoutHelper().onUpOrCancel(time);
    }

    /**
     * @param incognito Whether or not you want the incognito StripLayoutHelper
     * @return The requested StripLayoutHelper.
     */
    @VisibleForTesting
    public StripLayoutHelper getStripLayoutHelper(boolean incognito) {
        return incognito ? mIncognitoHelper : mNormalHelper;
    }

    /**
     * @return The currently visible strip layout helper.
     */
    @VisibleForTesting
    public StripLayoutHelper getActiveStripLayoutHelper() {
        return getStripLayoutHelper(mIsIncognito);
    }

    private StripLayoutHelper getInactiveStripLayoutHelper() {
        return mIsIncognito ? mNormalHelper : mIncognitoHelper;
    }
}
