// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.fullscreen;

import android.app.Activity;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.FrameLayout;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.BaseChromiumApplication;
import org.chromium.base.BaseChromiumApplication.WindowFocusChangedListener;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.chrome.browser.fullscreen.FullscreenHtmlApiHandler.FullscreenHtmlApiDelegate;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.chrome.browser.widget.ControlContainer;
import org.chromium.content.browser.ContentVideoView;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.common.BrowserControlsState;

import java.util.ArrayList;

/**
 * A class that manages control and content views to create the fullscreen mode.
 */
public class ChromeFullscreenManager
        extends FullscreenManager implements ActivityStateListener, WindowFocusChangedListener {

    // The amount of time to delay the control show request after returning to a once visible
    // activity.  This delay is meant to allow Android to run its Activity focusing animation and
    // have the controls scroll back in smoothly once that has finished.
    private static final long ACTIVITY_RETURN_SHOW_REQUEST_DELAY_MS = 100;

    private final Activity mActivity;
    private final Window mWindow;
    private final BrowserStateBrowserControlsVisibilityDelegate mBrowserVisibilityDelegate;
    private final boolean mIsBottomControls;

    private ControlContainer mControlContainer;
    private int mTopControlContainerHeight;
    private int mBottomControlContainerHeight;
    private TabModelSelector mTabModelSelector;
    private TabModelSelectorTabModelObserver mTabModelObserver;

    private float mRendererTopControlOffset = Float.NaN;
    private float mRendererBottomControlOffset = Float.NaN;
    private float mRendererTopContentOffset;
    private float mPreviousContentOffset = Float.NaN;
    private float mControlOffsetRatio;
    private float mPreviousControlOffset;
    private boolean mIsEnteringPersistentModeState;

    private boolean mInGesture;
    private boolean mContentViewScrolling;

    private boolean mBrowserControlsPermanentlyHidden;
    private boolean mBrowserControlsAndroidViewHidden;

    private final ArrayList<FullscreenListener> mListeners = new ArrayList<FullscreenListener>();

    /**
     * A listener that gets notified of changes to the fullscreen state.
     */
    public interface FullscreenListener {
        /**
         * Called whenever the content's offset changes.
         * @param offset The new offset of the content from the top of the screen.
         */
        public void onContentOffsetChanged(float offset);

        /**
         * Called whenever the content's visible offset changes.
         * @param offset The new offset of the visible content from the top of the screen.
         * @param needsAnimate Whether the caller is driving an animation with further updates.
         */
        public void onVisibleContentOffsetChanged(float offset, boolean needsAnimate);

        /**
         * Called when a ContentVideoView is created/destroyed.
         * @param enabled Whether to enter or leave overlay video mode.
         */
        public void onToggleOverlayVideoMode(boolean enabled);
    }

    private final Runnable mUpdateVisibilityRunnable = new Runnable() {
        @Override
        public void run() {
            int visibility = shouldShowAndroidControls() ? View.VISIBLE : View.INVISIBLE;
            if (mControlContainer.getView().getVisibility() == visibility) return;
            // requestLayout is required to trigger a new gatherTransparentRegion(), which
            // only occurs together with a layout and let's SurfaceFlinger trim overlays.
            // This may be almost equivalent to using View.GONE, but we still use View.INVISIBLE
            // since drawing caches etc. won't be destroyed, and the layout may be less expensive.
            mControlContainer.getView().setVisibility(visibility);
            mControlContainer.getView().requestLayout();
        }
    };

    /**
     * Creates an instance of the fullscreen mode manager.
     * @param activity The activity that supports fullscreen.
     * @param isBottomControls Whether or not the browser controls are at the bottom of the screen.
     */
    public ChromeFullscreenManager(Activity activity, boolean isBottomControls) {
        super(activity.getWindow());

        mActivity = activity;
        mWindow = activity.getWindow();
        mIsBottomControls = isBottomControls;
        mBrowserVisibilityDelegate = new BrowserStateBrowserControlsVisibilityDelegate(
                new Runnable() {
                    @Override
                    public void run() {
                        if (getTab() != null) {
                            getTab().updateFullscreenEnabledState();
                        } else if (!mBrowserVisibilityDelegate.isHidingBrowserControlsEnabled()) {
                            setPositionsForTabToNonFullscreen();
                        }
                    }
                });
    }

    /**
     * Initializes the fullscreen manager with the required dependencies.
     *
     * @param controlContainer Container holding the controls (Toolbar).
     * @param modelSelector The tab model selector that will be monitored for tab changes.
     * @param resControlContainerHeight The dimension resource ID for the control container height.
     */
    public void initialize(ControlContainer controlContainer, TabModelSelector modelSelector,
            int resControlContainerHeight) {
        ApplicationStatus.registerStateListenerForActivity(this, mActivity);
        ((BaseChromiumApplication) mActivity.getApplication())
                .registerWindowFocusChangedListener(this);

        mTabModelSelector = modelSelector;
        mTabModelObserver = new TabModelSelectorTabModelObserver(mTabModelSelector) {
            @Override
            public void tabClosureCommitted(Tab tab) {
                setTab(mTabModelSelector.getCurrentTab());
            }

            @Override
            public void allTabsClosureCommitted() {
                setTab(mTabModelSelector.getCurrentTab());
            }

            @Override
            public void tabRemoved(Tab tab) {
                setTab(mTabModelSelector.getCurrentTab());
            }

            @Override
            public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
                setTab(mTabModelSelector.getCurrentTab());
            }

            @Override
            public void didCloseTab(int tabId, boolean incognito) {
                setTab(mTabModelSelector.getCurrentTab());
            }
        };

        assert controlContainer != null;
        mControlContainer = controlContainer;
        Resources resources = mWindow.getContext().getResources();

        int controlContainerHeight = resources.getDimensionPixelSize(resControlContainerHeight);
        if (mIsBottomControls) {
            mTopControlContainerHeight = 0;
            mBottomControlContainerHeight = controlContainerHeight;
        } else {
            mTopControlContainerHeight = controlContainerHeight;
            mBottomControlContainerHeight = 0;
        }

        mRendererTopContentOffset = mTopControlContainerHeight;
        updateControlOffset();
    }

    /**
     * @return Whether or not the browser controls are attached to the bottom of the screen.
     */
    public boolean areBrowserControlsAtBottom() {
        return mIsBottomControls;
    }

    /**
     * @return The visibility delegate that allows browser UI to control the browser control
     *         visibility.
     */
    public BrowserStateBrowserControlsVisibilityDelegate getBrowserVisibilityDelegate() {
        return mBrowserVisibilityDelegate;
    }

    @Override
    public void setTab(Tab tab) {
        Tab previousTab = getTab();
        super.setTab(tab);
        if (tab != null && previousTab != getTab()) {
            mBrowserVisibilityDelegate.showControlsTransient();
        }
        if (tab == null && !mBrowserVisibilityDelegate.isHidingBrowserControlsEnabled()) {
            setPositionsForTabToNonFullscreen();
        }
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        if (newState == ActivityState.STOPPED) {
            // Exit fullscreen in onStop to ensure the system UI flags are set correctly when
            // showing again (on JB MR2+ builds, the omnibox would be covered by the
            // notification bar when this was done in onStart()).
            setPersistentFullscreenMode(false);
        } else if (newState == ActivityState.STARTED) {
            ThreadUtils.postOnUiThreadDelayed(new Runnable() {
                @Override
                public void run() {
                    mBrowserVisibilityDelegate.showControlsTransient();
                }
            }, ACTIVITY_RETURN_SHOW_REQUEST_DELAY_MS);
        } else if (newState == ActivityState.DESTROYED) {
            ApplicationStatus.unregisterActivityStateListener(this);
            ((BaseChromiumApplication) mWindow.getContext().getApplicationContext())
                    .unregisterWindowFocusChangedListener(this);

            mTabModelObserver.destroy();
        }
    }

    @Override
    public void onWindowFocusChanged(Activity activity, boolean hasFocus) {
        if (mActivity != activity) return;
        onWindowFocusChanged(hasFocus);
        // {@link ContentVideoView#getContentVideoView} requires native to have been initialized.
        if (!LibraryLoader.isInitialized()) return;
        ContentVideoView videoView = ContentVideoView.getContentVideoView();
        if (videoView != null) {
            videoView.onFullscreenWindowFocused();
        }
    }

    @Override
    protected FullscreenHtmlApiDelegate createApiDelegate() {
        return new FullscreenHtmlApiDelegate() {
            @Override
            public void onEnterFullscreen() {
                Tab tab = getTab();
                if (areBrowserControlsOffScreen()) {
                    // The browser controls are currently hidden.
                    getHtmlApiHandler().enterFullscreen(tab);
                } else {
                    // We should hide browser controls first.
                    mIsEnteringPersistentModeState = true;
                    tab.updateFullscreenEnabledState();
                }
            }

            @Override
            public boolean cancelPendingEnterFullscreen() {
                boolean wasPending = mIsEnteringPersistentModeState;
                mIsEnteringPersistentModeState = false;
                return wasPending;
            }

            @Override
            public void onFullscreenExited(Tab tab) {
                // At this point, browser controls are hidden. Show browser controls only if it's
                // permitted.
                tab.updateBrowserControlsState(BrowserControlsState.SHOWN, true);
            }

            @Override
            public boolean shouldShowNotificationToast() {
                return !isOverlayVideoMode();
            }
        };
    }

    /**
     * @return The ratio that the browser controls are off screen; this will be a number [0,1]
     *         where 1 is completely hidden and 0 is completely shown.
     */
    private float getBrowserControlHiddenRatio() {
        return mControlOffsetRatio;
    }

    /**
     * @return True if the browser controls are completely off screen.
     */
    public boolean areBrowserControlsOffScreen() {
        return getBrowserControlHiddenRatio() == 1.0f;
    }

    /**
     * @param remove Whether or not to forcefully remove the toolbar.
     */
    public void setBrowserControlsPermamentlyHidden(boolean remove) {
        if (remove == mBrowserControlsPermanentlyHidden) return;
        mBrowserControlsPermanentlyHidden = remove;
        updateVisuals();
    }

    /**
     * @return Whether or not the toolbar is forcefully being removed.
     */
    public boolean areBrowserControlsPermanentlyHidden() {
        return mBrowserControlsPermanentlyHidden;
    }

    /**
     * @return Whether the browser controls should be drawn as a texture.
     */
    public boolean drawControlsAsTexture() {
        return getBrowserControlHiddenRatio() > 0;
    }

    @Override
    public int getTopControlsHeight() {
        return mTopControlContainerHeight;
    }

    /**
     * @return The height of the bottom controls in pixels.
     */
    public int getBottomControlsHeight() {
        return mBottomControlContainerHeight;
    }

    @Override
    public float getContentOffset() {
        if (mBrowserControlsPermanentlyHidden) return 0;
        return mRendererTopContentOffset;
    }

    /**
     * @return The offset of the controls from the top of the screen.
     */
    public float getTopControlOffset() {
        if (mBrowserControlsPermanentlyHidden) return -getTopControlsHeight();
        // This is to avoid a problem with -0f in tests.
        if (mControlOffsetRatio == 0f) return 0f;
        return mControlOffsetRatio * -getTopControlsHeight();
    }

    /**
     * @return The offset of the controls from the bottom of the screen.
     */
    public float getBottomControlOffset() {
        if (mBrowserControlsPermanentlyHidden) return getBottomControlsHeight();
        if (mControlOffsetRatio == 0f) return 0f;
        return mControlOffsetRatio * getBottomControlsHeight();

    }

    /**
     * @return The toolbar control container.
     */
    public ControlContainer getControlContainer() {
        return mControlContainer;
    }

    private void updateControlOffset() {
        float topOffsetRatio = 0;

        float rendererControlOffset;
        if (mIsBottomControls) {
            rendererControlOffset =
                    Math.abs(mRendererBottomControlOffset / getBottomControlsHeight());
        } else {
            rendererControlOffset = Math.abs(mRendererTopControlOffset / getTopControlsHeight());
        }

        final boolean isNaNRendererControlOffset = Float.isNaN(rendererControlOffset);
        if (!isNaNRendererControlOffset) topOffsetRatio = rendererControlOffset;
        mControlOffsetRatio = topOffsetRatio;
    }

    @Override
    public void setOverlayVideoMode(boolean enabled) {
        super.setOverlayVideoMode(enabled);

        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onToggleOverlayVideoMode(enabled);
        }
    }

    /**
     * @return The visible offset of the content from the top of the screen.
     */
    public float getTopVisibleContentOffset() {
        return getTopControlsHeight() + getTopControlOffset();
    }

    /**
     * @param listener The {@link FullscreenListener} to be notified of fullscreen changes.
     */
    public void addListener(FullscreenListener listener) {
        if (!mListeners.contains(listener)) mListeners.add(listener);
    }

    /**
     * @param listener The {@link FullscreenListener} to no longer be notified of fullscreen
     *                 changes.
     */
    public void removeListener(FullscreenListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Updates the content view's viewport size to have it render the content correctly.
     *
     * @param viewCore The ContentViewCore to update.
     */
    public void updateContentViewViewportSize(ContentViewCore viewCore) {
        if (viewCore == null) return;
        if (mInGesture || mContentViewScrolling) return;

        // Update content viewport size only when the browser controls are not animating.
        int contentOffset = (int) mRendererTopContentOffset;
        if (contentOffset != 0 && contentOffset != getTopControlsHeight()) return;
        viewCore.setTopControlsHeight(getTopControlsHeight(), contentOffset > 0);
    }

    @Override
    public void updateContentViewChildrenState() {
        ContentViewCore contentViewCore = getActiveContentViewCore();
        if (contentViewCore == null) return;
        ViewGroup view = contentViewCore.getContainerView();

        float topViewsTranslation = getTopVisibleContentOffset();
        applyTranslationToTopChildViews(view, topViewsTranslation);
        applyMarginToFullChildViews(view, topViewsTranslation);
        updateContentViewViewportSize(contentViewCore);
    }

    /**
     * Utility routine for ensuring visibility updates are synchronized with
     * animation, preventing message loop stalls due to untimely invalidation.
     */
    private void scheduleVisibilityUpdate() {
        final int desiredVisibility = shouldShowAndroidControls() ? View.VISIBLE : View.INVISIBLE;
        if (mControlContainer.getView().getVisibility() == desiredVisibility) return;
        mControlContainer.getView().removeCallbacks(mUpdateVisibilityRunnable);
        mControlContainer.getView().postOnAnimation(mUpdateVisibilityRunnable);
    }

    private void updateVisuals() {
        TraceEvent.begin("FullscreenManager:updateVisuals");

        // Use bottom controls height if top controls have no height.
        float offset = getTopControlOffset();
        if (mIsBottomControls) offset = getBottomControlOffset();

        if (Float.compare(mPreviousControlOffset, offset) != 0) {
            mPreviousControlOffset = offset;

            scheduleVisibilityUpdate();
            if (shouldShowAndroidControls()) {
                mControlContainer.getView().setTranslationY(offset);
            }

            // Whether we need the compositor to draw again to update our animation.
            // Should be |false| when the browser controls are only moved through the page
            // scrolling.
            boolean needsAnimate = shouldShowAndroidControls();
            for (int i = 0; i < mListeners.size(); i++) {
                // Since, in the case of bottom controls, the view is never translated, we don't
                // need to change the information passed into this method.
                // getTopVisibleContentOffset will return 0 which is the expected result.
                mListeners.get(i).onVisibleContentOffsetChanged(
                        getTopVisibleContentOffset(), needsAnimate);
            }
        }

        final Tab tab = getTab();
        if (tab != null && areBrowserControlsOffScreen() && mIsEnteringPersistentModeState) {
            getHtmlApiHandler().enterFullscreen(tab);
            mIsEnteringPersistentModeState = false;
        }

        updateContentViewChildrenState();

        float contentOffset = getContentOffset();
        if (Float.compare(mPreviousContentOffset, contentOffset) != 0) {
            for (int i = 0; i < mListeners.size(); i++) {
                mListeners.get(i).onContentOffsetChanged(contentOffset);
            }
            mPreviousContentOffset = contentOffset;
        }

        TraceEvent.end("FullscreenManager:updateVisuals");
    }

    /**
     * @param hide Whether or not to force the browser controls Android view to hide.  If this is
     *             {@code false} the browser controls Android view will show/hide based on position,
     *             if it is {@code true} the browser controls Android view will always be hidden.
     */
    public void setHideBrowserControlsAndroidView(boolean hide) {
        if (mBrowserControlsAndroidViewHidden == hide) return;
        mBrowserControlsAndroidViewHidden = hide;
        scheduleVisibilityUpdate();
    }

    private boolean shouldShowAndroidControls() {
        if (mBrowserControlsAndroidViewHidden) return false;

        boolean showControls = !drawControlsAsTexture();
        ContentViewCore contentViewCore = getActiveContentViewCore();
        if (contentViewCore == null) return showControls;
        ViewGroup contentView = contentViewCore.getContainerView();

        for (int i = 0; i < contentView.getChildCount(); i++) {
            View child = contentView.getChildAt(i);
            if (!(child.getLayoutParams() instanceof FrameLayout.LayoutParams)) continue;

            FrameLayout.LayoutParams layoutParams =
                    (FrameLayout.LayoutParams) child.getLayoutParams();
            if (Gravity.TOP == (layoutParams.gravity & Gravity.FILL_VERTICAL)) {
                showControls = true;
                break;
            }
        }

        return showControls;
    }

    private void applyMarginToFullChildViews(ViewGroup contentView, float margin) {
        for (int i = 0; i < contentView.getChildCount(); i++) {
            View child = contentView.getChildAt(i);
            if (!(child.getLayoutParams() instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams layoutParams =
                    (FrameLayout.LayoutParams) child.getLayoutParams();

            if (layoutParams.height == LayoutParams.MATCH_PARENT
                    && layoutParams.topMargin != (int) margin) {
                layoutParams.topMargin = (int) margin;
                child.requestLayout();
                TraceEvent.instant("FullscreenManager:child.requestLayout()");
            }
        }
    }

    private void applyTranslationToTopChildViews(ViewGroup contentView, float translation) {
        for (int i = 0; i < contentView.getChildCount(); i++) {
            View child = contentView.getChildAt(i);
            if (!(child.getLayoutParams() instanceof FrameLayout.LayoutParams)) continue;

            FrameLayout.LayoutParams layoutParams =
                    (FrameLayout.LayoutParams) child.getLayoutParams();
            if (Gravity.TOP == (layoutParams.gravity & Gravity.FILL_VERTICAL)) {
                child.setTranslationY(translation);
                TraceEvent.instant("FullscreenManager:child.setTranslationY()");
            }
        }
    }

    private ContentViewCore getActiveContentViewCore() {
        Tab tab = getTab();
        return tab != null ? tab.getContentViewCore() : null;
    }

    @Override
    public void setPositionsForTabToNonFullscreen() {
        Tab tab = getTab();
        if (tab == null || tab.isShowingBrowserControlsEnabled()) {
            setPositionsForTab(0, 0, getTopControlsHeight());
        } else {
            setPositionsForTab(-getTopControlsHeight(), getBottomControlsHeight(), 0);
        }
    }

    @Override
    public void setPositionsForTab(float topControlsOffset, float bottomControlsOffset,
            float topContentOffset) {
        float rendererTopControlOffset =
                Math.round(Math.max(topControlsOffset, -getTopControlsHeight()));
        float rendererBottomControlOffset =
                Math.round(Math.min(bottomControlsOffset, getBottomControlsHeight()));

        float rendererTopContentOffset = Math.min(
                Math.round(topContentOffset), rendererTopControlOffset + getTopControlsHeight());

        if (Float.compare(rendererTopControlOffset, mRendererTopControlOffset) == 0
                && Float.compare(rendererBottomControlOffset, mRendererBottomControlOffset) == 0
                && Float.compare(rendererTopContentOffset, mRendererTopContentOffset) == 0) {
            return;
        }

        mRendererTopControlOffset = rendererTopControlOffset;
        mRendererBottomControlOffset = rendererBottomControlOffset;

        mRendererTopContentOffset = rendererTopContentOffset;
        updateControlOffset();

        updateVisuals();
    }

    /**
     * @param e The dispatched motion event
     * @return Whether or not this motion event is in the top control container area and should be
     *         consumed.
     */
    public boolean onInterceptMotionEvent(MotionEvent e) {
        int bottomPosition;
        int topPosition = 0;
        float offset;

        if (mIsBottomControls) {
            int[] position = new int[2];
            ViewUtils.getRelativeLayoutPosition(mControlContainer.getView().getRootView(),
                    mControlContainer.getView(), position);

            topPosition = position[1];
            bottomPosition = topPosition + getBottomControlsHeight();
            offset = getBottomControlOffset();
        } else {
            bottomPosition = getTopControlsHeight();
            offset = getTopControlOffset();
        }

        return e.getY() < topPosition + offset && e.getY() > bottomPosition + offset
                && !mBrowserControlsAndroidViewHidden;
    }

    /**
     * Notifies the fullscreen manager that a motion event has occurred.
     * @param e The dispatched motion event.
     */
    public void onMotionEvent(MotionEvent e) {
        int eventAction = e.getActionMasked();
        if (eventAction == MotionEvent.ACTION_DOWN
                || eventAction == MotionEvent.ACTION_POINTER_DOWN) {
            mInGesture = true;
            // TODO(qinmin): Probably there is no need to hide the toast as it will go away
            // by itself.
            getHtmlApiHandler().hideNotificationToast();
        } else if (eventAction == MotionEvent.ACTION_CANCEL
                || eventAction == MotionEvent.ACTION_UP) {
            mInGesture = false;
            updateVisuals();
        }
    }

    @Override
    public void onContentViewScrollingStateChanged(boolean scrolling) {
        mContentViewScrolling = scrolling;
        if (!scrolling) updateVisuals();
    }
}
