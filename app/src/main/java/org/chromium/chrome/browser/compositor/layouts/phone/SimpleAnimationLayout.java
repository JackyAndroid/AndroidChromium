// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone;

import android.content.Context;
import android.graphics.Rect;
import android.view.animation.Interpolator;

import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.LayoutRenderHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.layouts.phone.stack.Stack;
import org.chromium.chrome.browser.compositor.layouts.phone.stack.StackAnimation;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.compositor.scene_layer.TabListSceneLayer;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.ui.interpolators.BakedBezierInterpolator;
import org.chromium.ui.resources.ResourceManager;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * This class handles animating the opening of new tabs.
 */
public class SimpleAnimationLayout
        extends Layout implements Animatable<SimpleAnimationLayout.Property> {
    /**
     * Animation properties
     */
    public enum Property { DISCARD_AMOUNT }

    /** Duration of the first step of the background animation: zooming out, rotating in */
    private static final long BACKGROUND_STEP1_DURATION = 300;
    /** Duration of the second step of the background animation: pause */
    private static final long BACKGROUND_STEP2_DURATION = 150;
    /** Duration of the third step of the background animation: zooming in, sliding out */
    private static final long BACKGROUND_STEP3_DURATION = 300;
    /** Start time offset of the third step of the background animation */
    private static final long BACKGROUND_STEP3_START =
            BACKGROUND_STEP1_DURATION + BACKGROUND_STEP2_DURATION;
    /** Percentage of the screen covered by the new tab */
    private static final float BACKGROUND_COVER_PCTG = 0.5f;

    /** The time duration of the animation */
    protected static final int FOREGROUND_ANIMATION_DURATION = 300;

    /** The time duration of the animation */
    protected static final int TAB_CLOSED_ANIMATION_DURATION = 250;

    /**
     * A cached {@link LayoutTab} representation of the currently closing tab. If it's not
     * null, it means tabClosing() has been called to start animation setup but
     * tabClosed() has not yet been called to finish animation startup
     */
    private LayoutTab mClosedTab;

    private LayoutTab mAnimatedTab;
    private final TabListSceneLayer mSceneLayer;

    /**
     * Creates an instance of the {@link SimpleAnimationLayout}.
     * @param context     The current Android's context.
     * @param updateHost  The {@link LayoutUpdateHost} view for this layout.
     * @param renderHost  The {@link LayoutRenderHost} view for this layout.
     * @param eventFilter The {@link EventFilter} that is needed for this view.
     */
    public SimpleAnimationLayout(Context context, LayoutUpdateHost updateHost,
            LayoutRenderHost renderHost, EventFilter eventFilter) {
        super(context, updateHost, renderHost, eventFilter);
        mSceneLayer = new TabListSceneLayer();
    }

    @Override
    public int getSizingFlags() {
        return SizingFlags.HELPER_SUPPORTS_FULLSCREEN;
    }

    @Override
    public void show(long time, boolean animate) {
        super.show(time, animate);

        if (mTabModelSelector != null && mTabContentManager != null) {
            Tab tab = mTabModelSelector.getCurrentTab();
            if (tab != null && tab.isNativePage()) mTabContentManager.cacheTabThumbnail(tab);
        }

        reset();
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
    protected void updateLayout(long time, long dt) {
        super.updateLayout(time, dt);
        if (mLayoutTabs == null) return;
        boolean needUpdate = false;
        for (int i = mLayoutTabs.length - 1; i >= 0; i--) {
            needUpdate = mLayoutTabs[i].updateSnap(dt) || needUpdate;
        }
        if (needUpdate) requestUpdate();
    }

    @Override
    public void onTabCreating(int sourceTabId) {
        super.onTabCreating(sourceTabId);
        reset();

        // Make sure any currently running animations can't influence tab if we are reusing it.
        forceAnimationToFinish();

        ensureSourceTabCreated(sourceTabId);
    }

    private void ensureSourceTabCreated(int sourceTabId) {
        if (mLayoutTabs != null && mLayoutTabs.length == 1
                && mLayoutTabs[0].getId() == sourceTabId) {
            return;
        }
        // Just draw the source tab on the screen.
        TabModel sourceModel = mTabModelSelector.getModelForTabId(sourceTabId);
        if (sourceModel == null) return;
        LayoutTab sourceLayoutTab =
                createLayoutTab(sourceTabId, sourceModel.isIncognito(), NO_CLOSE_BUTTON, NO_TITLE);
        sourceLayoutTab.setBorderAlpha(0.0f);

        mLayoutTabs = new LayoutTab[] {sourceLayoutTab};
        updateCacheVisibleIds(new LinkedList<Integer>(Arrays.asList(sourceTabId)));
    }

    @Override
    public void onTabCreated(long time, int id, int index, int sourceId, boolean newIsIncognito,
            boolean background, float originX, float originY) {
        super.onTabCreated(time, id, index, sourceId, newIsIncognito, background, originX, originY);
        ensureSourceTabCreated(sourceId);
        if (background && mLayoutTabs != null && mLayoutTabs.length > 0) {
            tabCreatedInBackground(id, sourceId, newIsIncognito, originX, originY);
        } else {
            tabCreatedInForeground(id, sourceId, newIsIncognito, originX, originY);
        }
    }

    /**
     * Animate opening a tab in the foreground.
     *
     * @param id             The id of the new tab to animate.
     * @param sourceId       The id of the tab that spawned this new tab.
     * @param newIsIncognito true if the new tab is an incognito tab.
     * @param originX        The X coordinate of the last touch down event that spawned this tab.
     * @param originY        The Y coordinate of the last touch down event that spawned this tab.
     */
    private void tabCreatedInForeground(
            int id, int sourceId, boolean newIsIncognito, float originX, float originY) {
        LayoutTab newLayoutTab = createLayoutTab(id, newIsIncognito, NO_CLOSE_BUTTON, NO_TITLE);
        if (mLayoutTabs == null || mLayoutTabs.length == 0) {
            mLayoutTabs = new LayoutTab[] {newLayoutTab};
        } else {
            mLayoutTabs = new LayoutTab[] {mLayoutTabs[0], newLayoutTab};
        }
        updateCacheVisibleIds(new LinkedList<Integer>(Arrays.asList(id, sourceId)));

        newLayoutTab.setBorderAlpha(0.0f);
        newLayoutTab.setStaticToViewBlend(1.f);

        forceAnimationToFinish();

        Interpolator interpolator = BakedBezierInterpolator.TRANSFORM_CURVE;
        addToAnimation(newLayoutTab, LayoutTab.Property.SCALE, 0.f, 1.f,
                FOREGROUND_ANIMATION_DURATION, 0, false, interpolator);
        addToAnimation(newLayoutTab, LayoutTab.Property.ALPHA, 0.f, 1.f,
                FOREGROUND_ANIMATION_DURATION, 0, false, interpolator);
        addToAnimation(newLayoutTab, LayoutTab.Property.X, originX, 0.f,
                FOREGROUND_ANIMATION_DURATION, 0, false, interpolator);
        addToAnimation(newLayoutTab, LayoutTab.Property.Y, originY, 0.f,
                FOREGROUND_ANIMATION_DURATION, 0, false, interpolator);

        mTabModelSelector.selectModel(newIsIncognito);
        startHiding(id, false);
    }

    /**
     * Animate opening a tab in the background.
     *
     * @param id             The id of the new tab to animate.
     * @param sourceId       The id of the tab that spawned this new tab.
     * @param newIsIncognito true if the new tab is an incognito tab.
     * @param originX        The X screen coordinate in dp of the last touch down event that spawned
     *                       this tab.
     * @param originY        The Y screen coordinate in dp of the last touch down event that spawned
     *                       this tab.
     */
    private void tabCreatedInBackground(
            int id, int sourceId, boolean newIsIncognito, float originX, float originY) {
        LayoutTab newLayoutTab = createLayoutTab(id, newIsIncognito, NO_CLOSE_BUTTON, NEED_TITLE);
        // mLayoutTabs should already have the source tab from tabCreating().
        assert mLayoutTabs.length == 1;
        LayoutTab sourceLayoutTab = mLayoutTabs[0];
        mLayoutTabs = new LayoutTab[] {sourceLayoutTab, newLayoutTab};
        updateCacheVisibleIds(new LinkedList<Integer>(Arrays.asList(id, sourceId)));

        forceAnimationToFinish();

        newLayoutTab.setBorderAlpha(0.0f);
        final float scale = StackAnimation.SCALE_AMOUNT;
        final float margin = Math.min(getWidth(), getHeight()) * (1.0f - scale) / 2.0f;

        // Step 1: zoom out the source tab and bring in the new tab
        addToAnimation(sourceLayoutTab, LayoutTab.Property.SCALE, 1.0f, scale,
                BACKGROUND_STEP1_DURATION, 0, false, BakedBezierInterpolator.TRANSFORM_CURVE);
        addToAnimation(sourceLayoutTab, LayoutTab.Property.X, 0.0f, margin,
                BACKGROUND_STEP1_DURATION, 0, false, BakedBezierInterpolator.TRANSFORM_CURVE);
        addToAnimation(sourceLayoutTab, LayoutTab.Property.Y, 0.0f, margin,
                BACKGROUND_STEP1_DURATION, 0, false, BakedBezierInterpolator.TRANSFORM_CURVE);
        addToAnimation(sourceLayoutTab, LayoutTab.Property.BORDER_SCALE, 1.0f / scale, 1.0f,
                BACKGROUND_STEP1_DURATION, 0, false, BakedBezierInterpolator.TRANSFORM_CURVE);
        addToAnimation(sourceLayoutTab, LayoutTab.Property.BORDER_ALPHA, 0.0f, 1.0f,
                BACKGROUND_STEP1_DURATION, 0, false, BakedBezierInterpolator.TRANSFORM_CURVE);

        float pauseX = margin;
        float pauseY = margin;
        if (getOrientation() == Orientation.PORTRAIT) {
            pauseY = BACKGROUND_COVER_PCTG * getHeight();
        } else {
            pauseX = BACKGROUND_COVER_PCTG * getWidth();
        }

        addToAnimation(newLayoutTab, LayoutTab.Property.ALPHA, 0.0f, 1.0f,
                BACKGROUND_STEP1_DURATION / 2, 0, false, BakedBezierInterpolator.FADE_IN_CURVE);
        addToAnimation(newLayoutTab, LayoutTab.Property.SCALE, 0.f, scale,
                BACKGROUND_STEP1_DURATION, 0, false, BakedBezierInterpolator.FADE_IN_CURVE);
        addToAnimation(newLayoutTab, LayoutTab.Property.X, originX, pauseX,
                BACKGROUND_STEP1_DURATION, 0, false, BakedBezierInterpolator.FADE_IN_CURVE);
        addToAnimation(newLayoutTab, LayoutTab.Property.Y, originY, pauseY,
                BACKGROUND_STEP1_DURATION, 0, false, BakedBezierInterpolator.FADE_IN_CURVE);

        // step 2: pause and admire the nice tabs

        // step 3: zoom in the source tab and slide down the new tab
        addToAnimation(sourceLayoutTab, LayoutTab.Property.SCALE, scale, 1.0f,
                BACKGROUND_STEP3_DURATION, BACKGROUND_STEP3_START, true,
                BakedBezierInterpolator.TRANSFORM_CURVE);
        addToAnimation(sourceLayoutTab, LayoutTab.Property.X, margin, 0.0f,
                BACKGROUND_STEP3_DURATION, BACKGROUND_STEP3_START, true,
                BakedBezierInterpolator.TRANSFORM_CURVE);
        addToAnimation(sourceLayoutTab, LayoutTab.Property.Y, margin, 0.0f,
                BACKGROUND_STEP3_DURATION, BACKGROUND_STEP3_START, true,
                BakedBezierInterpolator.TRANSFORM_CURVE);
        addToAnimation(sourceLayoutTab, LayoutTab.Property.BORDER_SCALE, 1.0f, 1.0f / scale,
                BACKGROUND_STEP3_DURATION, BACKGROUND_STEP3_START, true,
                BakedBezierInterpolator.TRANSFORM_CURVE);
        addToAnimation(sourceLayoutTab, LayoutTab.Property.BORDER_ALPHA, 1.0f, 0.0f,
                BACKGROUND_STEP3_DURATION, BACKGROUND_STEP3_START, true,
                BakedBezierInterpolator.TRANSFORM_CURVE);

        addToAnimation(newLayoutTab, LayoutTab.Property.ALPHA, 1.f, 0.f, BACKGROUND_STEP3_DURATION,
                BACKGROUND_STEP3_START, true, BakedBezierInterpolator.FADE_OUT_CURVE);
        if (getOrientation() == Orientation.PORTRAIT) {
            addToAnimation(newLayoutTab, LayoutTab.Property.Y, pauseY, getHeight(),
                    BACKGROUND_STEP3_DURATION, BACKGROUND_STEP3_START, true,
                    BakedBezierInterpolator.FADE_OUT_CURVE);
        } else {
            addToAnimation(newLayoutTab, LayoutTab.Property.X, pauseX, getWidth(),
                    BACKGROUND_STEP3_DURATION, BACKGROUND_STEP3_START, true,
                    BakedBezierInterpolator.FADE_OUT_CURVE);
        }

        mTabModelSelector.selectModel(newIsIncognito);
        startHiding(sourceId, false);
    }

    /**
     * Set up for the tab closing animation
     */
    @Override
    public void onTabClosing(long time, int id) {
        reset();

        // Make sure any currently running animations can't influence tab if we are reusing it.
        forceAnimationToFinish();

        // Create the {@link LayoutTab} for the tab before it is destroyed.
        TabModel model = mTabModelSelector.getModelForTabId(id);
        if (model != null) {
            mClosedTab = createLayoutTab(id, model.isIncognito(), NO_CLOSE_BUTTON, NO_TITLE);
            mClosedTab.setBorderAlpha(0.0f);
            mLayoutTabs = new LayoutTab[] {mClosedTab};
            updateCacheVisibleIds(new LinkedList<Integer>(Arrays.asList(id)));
        } else {
            mLayoutTabs = null;
            mClosedTab = null;
        }
        // Only close the id at the end when we are done querying the model.
        super.onTabClosing(time, id);
    }

    /**
     * Animate the closing of a tab
     */
    @Override
    public void onTabClosed(long time, int id, int nextId, boolean incognito) {
        super.onTabClosed(time, id, nextId, incognito);

        if (mClosedTab != null) {
            TabModel nextModel = mTabModelSelector.getModelForTabId(nextId);
            if (nextModel != null) {
                LayoutTab nextLayoutTab =
                        createLayoutTab(nextId, nextModel.isIncognito(), NO_CLOSE_BUTTON, NO_TITLE);
                nextLayoutTab.setDrawDecoration(false);

                mLayoutTabs = new LayoutTab[] {nextLayoutTab, mClosedTab};
                updateCacheVisibleIds(
                        new LinkedList<Integer>(Arrays.asList(nextId, mClosedTab.getId())));
            } else {
                mLayoutTabs = new LayoutTab[] {mClosedTab};
            }

            forceAnimationToFinish();
            mAnimatedTab = mClosedTab;
            addToAnimation(this, Property.DISCARD_AMOUNT, 0, getDiscardRange(),
                    TAB_CLOSED_ANIMATION_DURATION, 0, false,
                    BakedBezierInterpolator.FADE_OUT_CURVE);

            mClosedTab = null;
            if (nextModel != null) {
                mTabModelSelector.selectModel(nextModel.isIncognito());
            }
        }
        startHiding(nextId, false);
    }

    /**
     * Updates the position, scale, rotation and alpha values of mAnimatedTab.
     *
     * @param discard The value that specify how far along are we in the discard animation. 0 is
     *                filling the screen. Valid values are [-range .. range] where range is
     *                computed by {@link SimpleAnimationLayout#getDiscardRange()}.
     */
    private void setDiscardAmount(float discard) {
        if (mAnimatedTab != null) {
            final float range = getDiscardRange();
            final float scale = Stack.computeDiscardScale(discard, range, true);

            final float deltaX = mAnimatedTab.getOriginalContentWidth();
            final float deltaY = mAnimatedTab.getOriginalContentHeight() / 2.f;
            mAnimatedTab.setX(deltaX * (1.f - scale));
            mAnimatedTab.setY(deltaY * (1.f - scale));
            mAnimatedTab.setScale(scale);
            mAnimatedTab.setBorderScale(scale);
            mAnimatedTab.setAlpha(Stack.computeDiscardAlpha(discard, range));
        }
    }

    /**
     * @return The range of the discard amount.
     */
    private float getDiscardRange() {
        return Math.min(getWidth(), getHeight()) * Stack.DISCARD_RANGE_SCREEN;
    }

    @Override
    public boolean onUpdateAnimation(long time, boolean jumpToEnd) {
        return super.onUpdateAnimation(time, jumpToEnd) && mClosedTab == null;
    }

    /**
     * Resets the internal state.
     */
    private void reset() {
        mLayoutTabs = null;
        mAnimatedTab = null;
        mClosedTab = null;
    }

    /**
     * Sets a property for an animation.
     * @param prop The property to update
     * @param value New value of the property
     */
    @Override
    public void setProperty(Property prop, float value) {
        switch (prop) {
            case DISCARD_AMOUNT:
                setDiscardAmount(value);
                break;
            default:
        }
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
