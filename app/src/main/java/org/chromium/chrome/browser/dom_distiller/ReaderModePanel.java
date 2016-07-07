// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import static org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.AnimatableAnimation.createAnimation;

import android.content.Context;
import android.os.SystemClock;

import org.chromium.base.CommandLine;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeEventFilter.ScrollDirection;
import org.chromium.chrome.browser.compositor.scene_layer.ReaderModeSceneLayer;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.dom_distiller.ReaderModeButtonView.ReaderModeButtonViewDelegate;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.resources.ResourceManager;

import java.util.concurrent.TimeUnit;

/**
 * Manages UI effects for reader mode including hiding and showing the
 * reader mode and reader mode preferences toolbar icon and hiding the
 * top controls when a reader mode page has finished loading.
 *
 * TODO(aruslan): combine with ContextualSearchPanel.
 */
public class ReaderModePanel implements ChromeAnimation.Animatable<ReaderModePanel.Property> {
    // TODO(aruslan): pull this from the FullscreenManager.
    private static final float TOOLBAR_HEIGHT_DP = 56.0f;

    private static final float PANEL_HEIGHT_DP = TOOLBAR_HEIGHT_DP;
    private static final float SHADOW_HEIGHT_DP = 4.0f;
    private static final float MINIMAL_BORDER_X_DP = 4.0f;
    private static final float DARKEN_LAYOUTTAB_BRIGHTNESS = 0.3f;
    private static final float MAX_LAYOUTTAB_DISPLACEMENT = 3.0f * TOOLBAR_HEIGHT_DP;

    private static final float SNAP_BACK_THRESHOLD = 0.3f;
    private static final long BASE_ANIMATION_DURATION_MS = 500;

    /**
     * Panel's host interface.
     */
    public interface ReaderModePanelHost {
        /**
         * @return Reader mode header background color.
         */
        int getReaderModeHeaderBackgroundColor();

        /**
         * @return One of ReaderModeManager.POSSIBLE, NOT_POSSIBLE, STARTED constants.
         */
        int getReaderModeStatus();

        /**
         * @return An associated tab.
         */
        Tab getTab();

        /**
         * @param X X-coordinate in dp
         * @param Y Y-coordinate in dp
         * @return Whether a given coordinates are within the bounds of the "dismiss" button
         */
        boolean isInsideDismissButton(float x, float y);

        /**
         * Creates the Reader Mode control if necessary.
         */
        void createReaderModeControl();

        /**
         * Destroys the Reader Mode control.
         */
        void destroyReaderModeControl();
    }

    /**
     * Layout integration interface.
     */
    public interface ReaderModePanelLayoutDelegate {
        /**
         * Requests a next update to refresh the transforms and changing properties.
         */
        void requestUpdate();

        /**
         * Sets the brightness of the LayoutTab to a given value.
         * @param v Brightness
         */
        void setLayoutTabBrightness(float v);

        /**
         * Sets the Y offset of the LayoutTab to a given value.
         * @param v Y-offset in dp
         */
        void setLayoutTabY(float v);
    }

    /**
     * Properties that can be animated by using a
     * {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable}.
     */
    public enum Property {
        /**
         * Parametric vertical slider from
         * -1.0 (panel is out of screen) to
         * 0.0 (panel is on screen) to
         * 1.0 (panel covers the entire screen)
         */
        SLIDING_T,
        /**
         * Horizontal slider, offset in dp
         */
        X,
    }

    private float mSlidingT;
    private float mX;

    private ScrollDirection mSwipeDirection;  // set in swipeStarted
    private float mInitialPanelDistanceFromBottom;  // distance from the bottom at swipeStarted
    private float mInitialX;  // X at swipeStarted

    /**
     * The animation set.
     */
    private ChromeAnimation<ChromeAnimation.Animatable<?>> mLayoutAnimations;

    private boolean mIsReaderModePanelHidden;
    private boolean mIsReaderModePanelDismissed;
    private boolean mIsFullscreenModeEntered;
    private boolean mIsInfobarContainerShown;

    private ContentViewCore mDistilledContentViewCore;
    private boolean mDidStartLoad;
    private boolean mDidFinishLoad;
    private WebContentsObserver mDistilledContentObserver;
    private boolean mDidFirstNonEmptyDistilledPaint;
    private ReaderModePanelLayoutDelegate mLayoutDelegate;
    private WebContents mOriginalWebContent;

    private float mLayoutWidth;
    private float mLayoutHeight;
    private boolean mIsToolbarShowing;
    private float mDpToPx;

    /**
     * ContentViewClient state to override when the distilled ContentViewCore is set on the Tab.
     */
    private float mTopControlsOffsetYPix;
    private float mContentOffsetYPix;
    private float mOverdrawBottomHeightPix;

    /**
     * The {@link ReaderModePanelHost} used to get reader mode status and the associated tab.
     */
    private final ReaderModePanelHost mReaderModeHost;

    /**
     * The SceneLayer responsible for drawing the panel.
     */
    private ReaderModeSceneLayer mSceneLayer;

    /**
     * Non-animated button support.
     */
    private boolean mAllowAnimatedButton;
    private ReaderModeButtonView mReaderModeButtonView;

    public ReaderModePanel(ReaderModePanelHost readerModeHost, Context context) {
        mReaderModeHost = readerModeHost;

        // Make sure all WebContents are destroyed when a tab is closed: crbug.com/496653
        mReaderModeHost.getTab().addObserver(new EmptyTabObserver() {
            @Override
            public void onDestroyed(Tab tab) {
                destroyCachedOriginalWebContent();
                destroyDistilledContentViewCore();
            }
        });

        mAllowAnimatedButton = CommandLine.getInstance().hasSwitch(
                ChromeSwitches.ENABLE_READER_MODE_BUTTON_ANIMATION);

        mLayoutWidth = 0.0f;
        mLayoutHeight = 0.0f;
        mDpToPx = 1.0f;

        mSlidingT = -1.0f;
        mX = 0.0f;

        float dpToPx = context.getResources().getDisplayMetrics().density;
        mSceneLayer = new ReaderModeSceneLayer(dpToPx);
    }

    /**
     * Get this panel's SceneLayer.
     * NOTE(mdjones): This overrides a method in OverlayPanel once the refactor is complete.
     */
    public SceneLayer getSceneLayer() {
        return mSceneLayer;
    }

    /**
     * Update this panel's SceneLayer.
     * NOTE(mdjones): This overrides a method in OverlayPanel once the refactor is complete.
     * @param resourceManager Resource manager for static resources.
     */
    public void updateSceneLayer(ResourceManager resourceManager) {
        mSceneLayer.update(this, resourceManager);
    }

    /**
     * Destroys the panel and associated resources.
     */
    public void onDestroy() {
        hideButtonBar();
    }

    /**
     * Set the layout delegate.
     * @param layoutDelegate A {@link ReaderModePanelLayoutDelegate} to call.
     */
    public void setLayoutDelegate(ReaderModePanelLayoutDelegate layoutDelegate) {
        mLayoutDelegate = layoutDelegate;
        requestUpdate();
    }

    // ChromeAnimation.Animatable<Property>:

    private void setSlidingT(float val) {
        mSlidingT = val;
        if (mLayoutDelegate != null) {
            mLayoutDelegate.setLayoutTabBrightness(getTabBrightness());
            mLayoutDelegate.setLayoutTabY(getTabYOffset());
        }
    }

    @Override
    public void setProperty(Property prop, float val) {
        switch (prop) {
            case SLIDING_T:
                setSlidingT(val);
                break;
            case X:
                mX = val;
                break;
        }
    }

    private static float clamp(float val, float lower, float higher) {
        return val < lower ? lower : (val > higher ? higher : val);
    }

    private static float interp(float factor, float start, float end) {
        return start + clamp(factor, 0.0f, 1.0f) * (end - start);
    }

    private float getPanelDistanceFromBottom() {
        if (mSlidingT < 0.0f) return interp(mSlidingT + 1.0f, 0.0f, PANEL_HEIGHT_DP);
        return PANEL_HEIGHT_DP + interp(mSlidingT, 0.0f, getFullscreenHeight());
    }

    private float getSlidingTForPanelDistanceFromBottom(float distanceFromBottom) {
        if (distanceFromBottom >= PANEL_HEIGHT_DP) {
            return interp(
                    (distanceFromBottom - PANEL_HEIGHT_DP) / getFullscreenHeight(),
                    0.0f, 1.0f);
        }
        return interp(
                (PANEL_HEIGHT_DP - distanceFromBottom) / PANEL_HEIGHT_DP,
                0.0f, -1.0f);
    }

    private float getDistilledContentDistanceFromBottom() {
        if (mSlidingT < 0.0f) return interp(mSlidingT + 1.0f, -PANEL_HEIGHT_DP, 0.0f);
        return interp(mSlidingT, 0.0f, getFullscreenHeight());
    }

    private static float snapBackSlidingT(float v) {
        // We snap asymmetrically: 30% is enough to get it opened, but 70% is necessary to dismiss.
        v = (v < -1.0f + SNAP_BACK_THRESHOLD) ? v : (v >= SNAP_BACK_THRESHOLD ? v : 0.0f);
        return Math.signum(v);
    }

    private static float snapBackX(float v) {
        // Horizontally we snap symmetrically: more than 70% to each side to dismiss.
        v = (v < -1.0f + SNAP_BACK_THRESHOLD) ? v : (v >= 1.0f - SNAP_BACK_THRESHOLD ? v : 0.0f);
        return Math.signum(v);
    }

    // Gesture handling:

    /**
     * @param direction Swipe direction to test
     * @return Whether the swipe in a given direction is enabled
     */
    public boolean isSwipeEnabled(ScrollDirection direction) {
        return !isAnimating();
    }

    /**
     * Called when the swipe is started.
     * @param direction Swipe direction
     * @param x X-coordinate of the starting point in dp
     * @param y Y-coordinate of the starting point in dp
     */
    public void swipeStarted(ScrollDirection direction, float x, float y) {
        if (isAnimating()) return;

        mSwipeDirection = direction;
        mInitialPanelDistanceFromBottom = getPanelDistanceFromBottom();
        mX = getX();

        if (mSwipeDirection == ScrollDirection.UP) activatePreviewOfDistilledMode();

        requestUpdate();
    }

    /**
     * Called when the swipe is continued.
     * @param tx X-offset since the start of the swipe in dp
     * @param ty Y-offset since the start of the swipe in dp
     */
    public void swipeUpdated(float x, float y, float dx, float dy, float tx, float ty) {
        if (isAnimating()) return;

        if (mSwipeDirection == ScrollDirection.LEFT || mSwipeDirection == ScrollDirection.RIGHT) {
            setProperty(ReaderModePanel.Property.X, clamp(mInitialX + tx,
                    -mLayoutWidth + MINIMAL_BORDER_X_DP, mLayoutWidth - MINIMAL_BORDER_X_DP));
        } else {
            setProperty(ReaderModePanel.Property.SLIDING_T,
                    getSlidingTForPanelDistanceFromBottom(mInitialPanelDistanceFromBottom - ty));
        }
        requestUpdate();
    }

    /**
     * Called when the swipe is finished.
     */
    public void swipeFinished() {
        if (isAnimating()) return;

        final float snappedX = snapBackX(mX / mLayoutWidth) * mLayoutWidth;
        final float snappedSlidingT = snapBackSlidingT(mSlidingT);
        if (snappedX <= -mLayoutWidth || snappedX >= mLayoutWidth) dismissButtonBar();
        if (snappedSlidingT < 0.0f) dismissButtonBar();

        animateTo(snappedX, snappedSlidingT, true);
    }

    // Panel layout handling:

    /**
     * @return Whether the panel should be shown.
     */
    public boolean isShowing() {
        return isPanelWithinScreenBounds() || isAnimating() || mDistilledContentViewCore != null;
    }

    /**
     * @return Whether the panel is within screen bounds.
     */
    private boolean isPanelWithinScreenBounds() {
        return mSlidingT > -1.0f;
    }

    /**
     * @return The fullscreen height.
     */
    private float getFullscreenHeight() {
        return mLayoutHeight + TOOLBAR_HEIGHT_DP;
    }

    public float getFullscreenY(float y) {
        if (mIsToolbarShowing) y += TOOLBAR_HEIGHT_DP * mDpToPx;
        return y;
    }

    public float getPanelY() {
        return getFullscreenHeight() - getPanelDistanceFromBottom() - SHADOW_HEIGHT_DP;
    }

    public float getDistilledContentY() {
        return getFullscreenHeight() - getDistilledContentDistanceFromBottom() - SHADOW_HEIGHT_DP;
    }

    public float getWidth() {
        return mLayoutWidth;
    }

    public float getPanelHeight() {
        return getPanelDistanceFromBottom();
    }

    public float getMarginTop() {
        return SHADOW_HEIGHT_DP;
    }

    public float getDistilledHeight() {
        return getDistilledContentDistanceFromBottom();
    }

    public float getX() {
        return mX;
    }

    public float getTextOpacity() {
        return interp(mSlidingT, 1.0f, 0.0f);
    }

    public float getTabBrightness() {
        return interp(mSlidingT, 1.0f, DARKEN_LAYOUTTAB_BRIGHTNESS);
    }

    public float getTabYOffset() {
        return interp(mSlidingT, 0.0f, -MAX_LAYOUTTAB_DISPLACEMENT);
    }

    /**
     * @param currentOffset The current top controls offset in dp.
     * @return {@link Float#NaN} if no offset should be used, or a value in dp
     *         if the top controls offset should be overridden.
     */
    public float getTopControlsOffset(float currentOffsetDp) {
        if (mSlidingT <= 0.0f) return Float.NaN;
        return MathUtils.clamp(getTabYOffset(), -TOOLBAR_HEIGHT_DP, Math.min(currentOffsetDp, 0f));
    }


    public ContentViewCore getDistilledContentViewCore() {
        return mDistilledContentViewCore;
    }

    public boolean didFirstNonEmptyDistilledPaint() {
        return mDidFirstNonEmptyDistilledPaint;
    }

    public int getReaderModeHeaderBackgroundColor() {
        return mReaderModeHost.getReaderModeHeaderBackgroundColor();
    }

    /**
     * Called when the size of the view has changed.
     *
     * @param width  The new width in dp.
     * @param height The new width in dp.
     * @param isToolbarShowing Whether the Toolbar is showing.
     * @param dpToPx Multipler to convert from dp to pixels.
     */
    public void onSizeChanged(float width, float height, boolean isToolbarShowing, float dpToPx) {
        mLayoutWidth = width;
        mLayoutHeight = height;
        mIsToolbarShowing = isToolbarShowing;
        mDpToPx = dpToPx;
    }

    // Layout integration:

    /**
     * Requests a new frame to be updated and rendered.
     */
    private void requestUpdate() {
        if (mLayoutDelegate != null) mLayoutDelegate.requestUpdate();
    }

    // Animation handling:

    /**
     * @return Whether a panel animation is in progress.
     */
    private boolean isAnimating() {
        return mLayoutAnimations != null && !mLayoutAnimations.finished();
    }

    /**
     * Animates to a given target value.
     * @param targetX A target value for the X parameter
     * @param targetSlidingT A target value for the SlidingT parameter
     */
    private void animateTo(float targetX, float targetSlidingT, boolean animate) {
        if (targetSlidingT > 0.0f) activatePreviewOfDistilledMode();

        if (isAnimating()) {
            mLayoutAnimations.cancel(this, Property.SLIDING_T);
            mLayoutAnimations.cancel(this, Property.X);
        }
        if (mLayoutAnimations == null || mLayoutAnimations.finished()) {
            mLayoutAnimations = new ChromeAnimation<Animatable<?>>();
        }

        mLayoutAnimations.add(createAnimation(
                this, Property.SLIDING_T, mSlidingT, targetSlidingT,
                BASE_ANIMATION_DURATION_MS, 0, false,
                ChromeAnimation.getDecelerateInterpolator()));
        mLayoutAnimations.add(createAnimation(
                this, Property.X, mX, targetX,
                BASE_ANIMATION_DURATION_MS, 0, false,
                ChromeAnimation.getDecelerateInterpolator()));
        mLayoutAnimations.start();

        if (!animate) mLayoutAnimations.updateAndFinish();
        requestUpdate();
    }

    /**
     * Steps the animation forward and updates all the animated values.
     * @param time      The current time of the app in ms.
     * @param jumpToEnd Whether to finish the animation.
     * @return          Whether the animation was finished.
     */
    public boolean onUpdateAnimation(long time, boolean jumpToEnd) {
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
     * Called when layout-specific actions are needed after the animation finishes.
     */
    private void onAnimationFinished() {
        if (mSlidingT >= 1.0f) enterDistilledMode();
        updateBottomButtonBar();
    }

    // Gesture handling:

    /**
     * @param y The y coordinate in dp.
     * @return Whether the given |y| coordinate is inside the Reader mode area.
     */
    public boolean isYCoordinateInsideReaderModePanel(float y) {
        return y >= getPanelY() || y >= getDistilledContentY();
    }

    /**
     * Handles a click in the panel area.
     * @param x X-coordinate in dp
     * @param y Y-coordinate in dp
     */
    public void handleClick(long time, float x, float y) {
        if (mReaderModeHost.isInsideDismissButton(x * mDpToPx + mX, PANEL_HEIGHT_DP / 2)) {
            dismissButtonBar();
            return;
        }

        animateTo(mX, 1.0f, true);
    }

    /**
     * @return Whether the reader mode could be currently allowed.
     */
    public boolean isReaderModeCurrentlyAllowed() {
        return !mIsReaderModePanelHidden && !mIsReaderModePanelDismissed
                && !mIsFullscreenModeEntered && !mIsInfobarContainerShown
                && mReaderModeHost.getTab() != null
                && mReaderModeHost.getTab().getContentViewCore() != null
                && mReaderModeHost.getTab().getContentViewCore().getContext() != null
                && mReaderModeHost.getTab().getWebContents() != null;
    }

    private void nonAnimatedUpdateButtomButtonBar() {
        final int status = mReaderModeHost.getReaderModeStatus();
        final Tab tab = mReaderModeHost.getTab();

        if (mReaderModeButtonView != null
                && (status != ReaderModeManager.POSSIBLE || !isReaderModeCurrentlyAllowed())) {
            // Unfortunately, dismiss() couldn't be used because it might attempt to remove a view
            // while in onLayout, thus causing crash.
            final ReaderModeButtonView buttonView = mReaderModeButtonView;
            mReaderModeButtonView.post(new Runnable() {
                @Override
                public void run() {
                    // Unfortunately, dismiss() couldn't be used because it might attempt
                    // to remove a view while in onLayout, thus causing crash.
                    buttonView.removeFromParentView();
                }
            });
            mReaderModeButtonView = null;
            return;
        }

        if (mReaderModeButtonView == null
                && (status == ReaderModeManager.POSSIBLE && isReaderModeCurrentlyAllowed())) {
            mReaderModeButtonView = ReaderModeButtonView.create(tab.getContentViewCore(),
                    new ReaderModeButtonViewDelegate() {
                        @Override
                        public void onSwipeAway() {
                            dismissButtonBar();
                        }

                        @Override
                        public void onClick() {
                            nonAnimatedEnterDistilledMode();
                        }
                    });
        }
    }

    /**
     * Updates the visibility of the reader mode button bar as required.
     */
    public void updateBottomButtonBar() {
        if (!mAllowAnimatedButton) {
            nonAnimatedUpdateButtomButtonBar();
            return;
        }

        if (isAnimating()) {
            mReaderModeHost.createReaderModeControl();
            return;
        }

        final int status = mReaderModeHost.getReaderModeStatus();
        if (isPanelWithinScreenBounds()
                && (status != ReaderModeManager.POSSIBLE || !isReaderModeCurrentlyAllowed())) {
            animateTo(0.0f, -1.0f, true);
            mReaderModeHost.destroyReaderModeControl();
            destroyCachedOriginalWebContent();
            destroyDistilledContentViewCore();
            requestUpdate();
            return;
        }

        if (!isPanelWithinScreenBounds()
                && (status == ReaderModeManager.POSSIBLE && isReaderModeCurrentlyAllowed())) {
            animateTo(0.0f, 0.0f, true);
            mReaderModeHost.createReaderModeControl();
            requestUpdate();
            return;
        }
    }

    private ContentViewCore createDistillerContentViewCore(
            Context context, WindowAndroid windowAndroid) {
        boolean isHostTabIncognito =
                mReaderModeHost.getTab().getContentViewCore().getWebContents().isIncognito();
        ContentViewCore cvc = new ContentViewCore(context);
        ContentView cv = ContentView.createContentView(context, cvc);
        cvc.initialize(cv, cv, WebContentsFactory.createWebContents(isHostTabIncognito, true),
                windowAndroid);
        cvc.setContentViewClient(new ContentViewClient() {
            @Override
            public void onOffsetsForFullscreenChanged(float topControlsOffsetYPix,
                    float contentOffsetYPix, float overdrawBottomHeightPix) {
                super.onOffsetsForFullscreenChanged(topControlsOffsetYPix, contentOffsetYPix,
                        overdrawBottomHeightPix);
                mTopControlsOffsetYPix = topControlsOffsetYPix;
                mContentOffsetYPix = contentOffsetYPix;
                mOverdrawBottomHeightPix = overdrawBottomHeightPix;
            }
        });
        return cvc;
    }

    /**
     * Prepares the distilled mode.
     */
    public void activatePreviewOfDistilledMode() {
        final long start = SystemClock.elapsedRealtime();

        if (mDistilledContentViewCore != null) return;

        mDidFirstNonEmptyDistilledPaint = false;
        mDidStartLoad = false;
        mDidFinishLoad = false;

        destroyCachedOriginalWebContent();
        mDistilledContentViewCore = createDistillerContentViewCore(
                mReaderModeHost.getTab().getContentViewCore().getContext(),
                mReaderModeHost.getTab().getWindowAndroid());

        mergeNavigationHistory(mDistilledContentViewCore.getWebContents(),
                mReaderModeHost.getTab().getWebContents());

        mDistilledContentObserver = new WebContentsObserver(
                mDistilledContentViewCore.getWebContents()) {
            @Override
            public void didFirstVisuallyNonEmptyPaint() {
                super.didFirstVisuallyNonEmptyPaint();
                mDidFirstNonEmptyDistilledPaint = true;

                RecordHistogram.recordTimesHistogram("DomDistiller.Time.SwipeToPaint",
                        SystemClock.elapsedRealtime() - start, TimeUnit.MILLISECONDS);
            }

            @Override
            public void didStartLoading(String url) {
                super.didStartLoading(url);
                mDidStartLoad = true;
            }

            @Override
            public void didFinishLoad(long frameId, String validatedUrl, boolean isMainFrame) {
                super.didFinishLoad(frameId, validatedUrl, isMainFrame);
                if (isMainFrame) mDidFinishLoad = true;
            }
        };
        mReaderModeHost.getTab().attachOverlayContentViewCore(
                mDistilledContentViewCore, true, false);
        DomDistillerTabUtils.distillAndView(
                mReaderModeHost.getTab().getContentViewCore().getWebContents(),
                mDistilledContentViewCore.getWebContents());
        mDistilledContentViewCore.onShow();
    }

    private void nonAnimatedEnterDistilledMode() {
        RecordUserAction.record("DomDistiller_DistilledPageOpened");
        DomDistillerTabUtils.distillCurrentPageAndView(mReaderModeHost.getTab().getWebContents());
        nonAnimatedUpdateButtomButtonBar();
    }

    private static void mergeNavigationHistory(WebContents target, WebContents source) {
        target.getNavigationController().clearHistory();
        NavigationController distilled = target.getNavigationController();
        NavigationController original = source.getNavigationController();
        if (distilled.canPruneAllButLastCommitted()) {
            distilled.copyStateFromAndPrune(original, false);
        } else if (distilled.canCopyStateOver()) {
            distilled.copyStateFrom(original);
        }
    }

    private void enterDistilledMode() {
        if (!isReaderModeCurrentlyAllowed()) return;

        RecordUserAction.record("DomDistiller_DistilledPageOpened");
        mSlidingT = -1.0f;
        requestUpdate();

        mDistilledContentViewCore.getWebContents().updateTopControlsState(true, false, false);

        mReaderModeHost.getTab().detachOverlayContentViewCore(mDistilledContentViewCore);
        mDistilledContentObserver.destroy();
        mDistilledContentObserver = null;

        mOriginalWebContent = mReaderModeHost.getTab().getWebContents();

        mDistilledContentViewCore.setContentViewClient(new ContentViewClient());
        mReaderModeHost.getTab().swapContentViewCore(mDistilledContentViewCore, false,
                mDidStartLoad, mDidFinishLoad);
        mDistilledContentViewCore.getContentViewClient().onOffsetsForFullscreenChanged(
                mTopControlsOffsetYPix, mContentOffsetYPix, mOverdrawBottomHeightPix);

        mDistilledContentViewCore = null;
        destroyDistilledContentViewCore();

        if (mLayoutDelegate != null) {
            mLayoutDelegate.setLayoutTabBrightness(1.0f);
            mLayoutDelegate.setLayoutTabY(0.0f);
        }

        updateBottomButtonBar();
    }

    private void destroyCachedOriginalWebContent() {
        if (mOriginalWebContent != null) {
            mOriginalWebContent.destroy();
            mOriginalWebContent = null;
        }
    }

    private void destroyDistilledContentViewCore() {
        if (mDistilledContentObserver != null) {
            mDistilledContentObserver.destroy();
            mDistilledContentObserver = null;
        }

        if (mDistilledContentViewCore == null) return;

        mReaderModeHost.getTab().detachOverlayContentViewCore(mDistilledContentViewCore);

        mDistilledContentViewCore.getWebContents().destroy();
        mDistilledContentViewCore.destroy();
        mDistilledContentViewCore = null;
    }

    /**
     * Hides the reader mode button bar if shown.
     */
    public void hideButtonBar() {
        mIsReaderModePanelHidden = true;
        mLayoutAnimations = null;
        updateBottomButtonBar();
    }

    /**
     * Dismisses the reader mode button bar if shown.
     */
    public void dismissButtonBar() {
        mIsReaderModePanelDismissed = true;
        mLayoutAnimations = null;
        updateBottomButtonBar();
    }

    /**
     * Shows the reader mode button bar if necessary.
     */
    public void unhideButtonBar() {
        mIsReaderModePanelHidden = false;
        updateBottomButtonBar();
    }

    /**
     * Temporarily hides the reader mode button while the video is shown.
     */
    public void onEnterFullscreen() {
        mIsFullscreenModeEntered = true;
        mLayoutAnimations = null;
        updateBottomButtonBar();
    }

    /**
     * Re-shows the reader mode button if necessary once the video is exited.
     */
    public void onExitFullscreen() {
        mIsFullscreenModeEntered = false;
        updateBottomButtonBar();
    }

    /**
     * Temporarily hides the reader mode button while the infobars are shown.
     */
    public void onShowInfobarContainer() {
        mIsInfobarContainerShown = true;
        mLayoutAnimations = null;
        updateBottomButtonBar();
    }

    /**
     * Re-shows the reader mode button if necessary once the infobars are dismissed.
     */
    public void onHideInfobarContainer() {
        mIsInfobarContainerShown = false;
        updateBottomButtonBar();
    }

    /**
      * @param tab A {@link Tab}.
      * @return The panel associated with a given Tab.
      */
    public static ReaderModePanel getReaderModePanel(Tab tab) {
        ReaderModeManager manager = tab.getReaderModeManager();
        if (manager == null) return null;
        return manager.getReaderModePanel();
    }
}
