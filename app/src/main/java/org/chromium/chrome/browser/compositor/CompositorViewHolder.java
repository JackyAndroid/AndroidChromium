// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.widget.ExploreByTouchHelper;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import org.chromium.base.SysUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.Invalidator.Client;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutRenderHost;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.content.ContentOffsetProvider;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.dom_distiller.ReaderModeManagerDelegate;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager.FullscreenListener;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabContentViewParent;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.widget.ClipDrawableProgressBar.DrawingInfo;
import org.chromium.chrome.browser.widget.ControlContainer;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.SPenSupport;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.resources.ResourceManager;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds a {@link CompositorView}. This level of indirection is needed to benefit from
 * the {@link android.view.ViewGroup#onInterceptTouchEvent(android.view.MotionEvent)} capability on
 * available on {@link android.view.ViewGroup}s.
 * This class also holds the {@link LayoutManager} responsible to describe the items to be
 * drawn by the UI compositor on the native side.
 */
public class CompositorViewHolder extends CoordinatorLayout
        implements ContentOffsetProvider, LayoutManagerHost, LayoutRenderHost, Invalidator.Host,
                FullscreenListener {

    private boolean mIsKeyboardShowing = false;

    private final Invalidator mInvalidator = new Invalidator();
    private LayoutManager mLayoutManager;
    private LayerTitleCache mLayerTitleCache;
    private CompositorView mCompositorView;

    private boolean mContentOverlayVisiblity = true;

    private int mPendingSwapBuffersCount;

    private final ArrayList<Invalidator.Client> mPendingInvalidations =
            new ArrayList<Invalidator.Client>();
    private boolean mSkipInvalidation = false;

    /**
     * A task to be performed after a resize event.
     */
    private Runnable mPostHideKeyboardTask;

    private TabModelSelector mTabModelSelector;
    private ChromeFullscreenManager mFullscreenManager;
    private View mAccessibilityView;
    private CompositorAccessibilityProvider mNodeProvider;
    private boolean mFullscreenTouchEvent = false;
    private float mLastContentOffset = 0;
    private float mLastVisibleContentOffset = 0;

    /** The toolbar control container. **/
    private ControlContainer mControlContainer;

    /** The currently visible Tab. */
    private Tab mTabVisible;

    /** The currently attached View. */
    private TabContentViewParent mView;

    private TabObserver mTabObserver;
    private boolean mEnableCompositorTabStrip;

    // Cache objects that should not be created frequently.
    private final Rect mCacheViewport = new Rect();
    private final Rect mCacheVisibleViewport = new Rect();
    private DrawingInfo mProgressBarDrawingInfo;

    // If we've drawn at least one frame.
    private boolean mHasDrawnOnce = false;

    /**
     * This view is created on demand to display debugging information.
     */
    private static class DebugOverlay extends View {
        private final List<Pair<Rect, Integer>> mRectangles = new ArrayList<Pair<Rect, Integer>>();
        private final Paint mPaint = new Paint();
        private boolean mFirstPush = true;

        /**
         * @param context The current Android's context.
         */
        public DebugOverlay(Context context) {
            super(context);
        }

        /**
         * Pushes a rectangle to be drawn on the screen on top of everything.
         *
         * @param rect  The rectangle to be drawn on screen
         * @param color The color of the rectangle
         */
        public void pushRect(Rect rect, int color) {
            if (mFirstPush) {
                mRectangles.clear();
                mFirstPush = false;
            }
            mRectangles.add(new Pair<Rect, Integer>(rect, color));
            invalidate();
        }

        @SuppressFBWarnings("NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
        @Override
        protected void onDraw(Canvas canvas) {
            for (int i = 0; i < mRectangles.size(); i++) {
                mPaint.setColor(mRectangles.get(i).second);
                canvas.drawRect(mRectangles.get(i).first, mPaint);
            }
            mFirstPush = true;
        }
    }

    private DebugOverlay mDebugOverlay;

    private View mUrlBar;

    /**
     * Creates a {@link CompositorView}.
     * @param c The Context to create this {@link CompositorView} in.
     */
    public CompositorViewHolder(Context c) {
        super(c);

        internalInit();
    }

    /**
     * Creates a {@link CompositorView}.
     * @param c     The Context to create this {@link CompositorView} in.
     * @param attrs The AttributeSet used to create this {@link CompositorView}.
     */
    public CompositorViewHolder(Context c, AttributeSet attrs) {
        super(c, attrs);

        internalInit();
    }

    private void internalInit() {
        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onContentChanged(Tab tab) {
                CompositorViewHolder.this.onContentChanged();
            }
        };

        mEnableCompositorTabStrip = DeviceFormFactor.isTablet(getContext());

        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                propagateViewportToLayouts(right - left, bottom - top);

                // If there's an event that needs to occur after the keyboard is hidden, post
                // it as a delayed event.  Otherwise this happens in the midst of the
                // ContentView's relayout, which causes the ContentView to relayout on top of the
                // stack view.  The 30ms is arbitrary, hoping to let the view get one repaint
                // in so the full page is shown.
                if (mPostHideKeyboardTask != null) {
                    new Handler().postDelayed(mPostHideKeyboardTask, 30);
                    mPostHideKeyboardTask = null;
                }
            }
        });

        mCompositorView = new CompositorView(getContext(), this);
        // mCompositorView should always be the first child.
        addView(mCompositorView, 0,
                new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * @param layoutManager The {@link LayoutManager} instance that will be driving what
     *                      shows in this {@link CompositorViewHolder}.
     */
    public void setLayoutManager(LayoutManager layoutManager) {
        mLayoutManager = layoutManager;
        propagateViewportToLayouts(getWidth(), getHeight());
    }

    /**
     * @param view The root view of the hierarchy.
     */
    public void setRootView(View view) {
        mCompositorView.setRootView(view);
    }

    /**
     * @param controlContainer The ControlContainer.
     */
    public void setControlContainer(ControlContainer controlContainer) {
        DynamicResourceLoader loader = mCompositorView.getResourceManager() != null
                ? mCompositorView.getResourceManager().getDynamicResourceLoader()
                : null;
        if (loader != null && mControlContainer != null) {
            loader.unregisterResource(R.id.control_container);
        }
        mControlContainer = controlContainer;
        if (loader != null && mControlContainer != null) {
            loader.registerResource(
                    R.id.control_container, mControlContainer.getToolbarResourceAdapter());
        }
    }

    /**
     * Reset command line flags. This gets called after the native library finishes
     * loading.
     */
    public void resetFlags() {
        mCompositorView.resetFlags();
    }

    /**
     * Should be called for cleanup when the CompositorView instance is no longer used.
     */
    public void shutDown() {
        setTab(null);
        if (mLayerTitleCache != null) mLayerTitleCache.shutDown();
        mCompositorView.shutDown();
    }

    /**
     * This is called when the native library are ready.
     */
    public void onNativeLibraryReady(
            WindowAndroid windowAndroid, TabContentManager tabContentManager) {
        assert mLayerTitleCache == null : "Should be called once";

        if (DeviceClassManager.enableLayerDecorationCache()) {
            mLayerTitleCache = new LayerTitleCache(getContext());
        }

        mCompositorView.initNativeCompositor(
                SysUtils.isLowEndDevice(), windowAndroid, mLayerTitleCache, tabContentManager);

        if (mLayerTitleCache != null) {
            mLayerTitleCache.setResourceManager(getResourceManager());
        }

        if (mControlContainer != null) {
            mCompositorView.getResourceManager().getDynamicResourceLoader().registerResource(
                    R.id.control_container, mControlContainer.getToolbarResourceAdapter());
        }
    }

    /**
     * Perform any initialization necessary for showing a reparented tab.
     */
    public void prepareForTabReparenting() {
        if (mHasDrawnOnce) return;

        // Set the background to white while we wait for the first swap of buffers. This gets
        // corrected inside the view.
        mCompositorView.setBackgroundColor(Color.WHITE);
    }

    @Override
    public ResourceManager getResourceManager() {
        return mCompositorView.getResourceManager();
    }

    /**
     * @return The {@link DynamicResourceLoader} for registering resources.
     */
    public DynamicResourceLoader getDynamicResourceLoader() {
        return mCompositorView.getResourceManager().getDynamicResourceLoader();
    }

    /**
     * @return The {@link Invalidator} instance that is driven by this {@link CompositorViewHolder}.
     */
    public Invalidator getInvalidator() {
        return mInvalidator;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        boolean consumedBySuper = super.onInterceptTouchEvent(e);
        if (consumedBySuper) return true;

        if (mLayoutManager == null) return false;

        mFullscreenTouchEvent = false;
        if (mFullscreenManager != null && mFullscreenManager.onInterceptMotionEvent(e)
                && !mEnableCompositorTabStrip) {
            // Don't eat the event if the new tab strip is enabled.
            mFullscreenTouchEvent = true;
            return true;
        }

        setContentViewMotionEventOffsets(e, false);
        return mLayoutManager.onInterceptTouchEvent(e, mIsKeyboardShowing);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean consumedBySuper = super.onTouchEvent(e);
        if (consumedBySuper) return true;

        if (mFullscreenManager != null) mFullscreenManager.onMotionEvent(e);
        if (mFullscreenTouchEvent) return true;
        boolean consumed = mLayoutManager != null && mLayoutManager.onTouchEvent(e);
        setContentViewMotionEventOffsets(e, true);
        return consumed;
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent e) {
        setContentViewMotionEventOffsets(e, true);
        return super.onInterceptHoverEvent(e);
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent e) {
        if (mNodeProvider != null) {
            if (mNodeProvider.dispatchHoverEvent(e)) {
                return true;
            }
        }
        return super.dispatchHoverEvent(e);
    }

    @Override
    public boolean dispatchDragEvent(DragEvent e) {
        ContentViewCore contentViewCore = mTabVisible.getContentViewCore();
        if (contentViewCore == null) return false;

        if (mLayoutManager != null) mLayoutManager.getViewportPixel(mCacheViewport);
        contentViewCore.setCurrentTouchEventOffsets(-mCacheViewport.left, -mCacheViewport.top);
        boolean ret = super.dispatchDragEvent(e);

        int action = e.getAction();
        if (action == DragEvent.ACTION_DRAG_EXITED || action == DragEvent.ACTION_DRAG_ENDED
                || action == DragEvent.ACTION_DROP) {
            contentViewCore.setCurrentTouchEventOffsets(0.f, 0.f);
        }
        return ret;
    }

    /**
     * @return The {@link LayoutManager} associated with this view.
     */
    public LayoutManager getLayoutManager() {
        return mLayoutManager;
    }

    /**
     * @return The SurfaceView used by the Compositor.
     */
    public SurfaceView getSurfaceView() {
        return mCompositorView;
    }

    private View getActiveView() {
        if (mLayoutManager == null || mTabModelSelector == null) return null;
        Tab tab = mTabModelSelector.getCurrentTab();
        return tab != null ? tab.getContentView() : null;
    }

    private ContentViewCore getActiveContent() {
        if (mLayoutManager == null || mTabModelSelector == null) return null;
        Tab tab = mTabModelSelector.getCurrentTab();
        return tab != null ? tab.getActiveContentViewCore() : null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        View view = getActiveView();
        if (view != null && setSizeOfUnattachedView(view)) requestRender();
    }

    @Override
    public void onPhysicalBackingSizeChanged(int width, int height) {
        ContentViewCore content = getActiveContent();
        if (content != null) adjustPhysicalBackingSize(content, width, height);
    }

    /**
     * Called whenever the host activity is started.
     */
    public void onStart() {
        if (mFullscreenManager != null) {
            mLastContentOffset = mFullscreenManager.getContentOffset();
            mLastVisibleContentOffset = mFullscreenManager.getTopVisibleContentOffset();
            mFullscreenManager.addListener(this);
        }
        requestRender();
    }

    /**
     * Called whenever the host activity is stopped.
     */
    public void onStop() {
        if (mFullscreenManager != null) mFullscreenManager.removeListener(this);
    }

    @Override
    public void onContentOffsetChanged(float offset) {
        mLastContentOffset = offset;
        propagateViewportToLayouts(getWidth(), getHeight());
    }

    @Override
    public void onVisibleContentOffsetChanged(float offset, boolean needsAnimate) {
        mLastVisibleContentOffset = offset;
        propagateViewportToLayouts(getWidth(), getHeight());
        if (needsAnimate) requestRender();
    }

    @Override
    public void onToggleOverlayVideoMode(boolean enabled) {
        if (mCompositorView != null) {
            mCompositorView.setOverlayVideoMode(enabled);
        }
    }

    private void setContentViewMotionEventOffsets(MotionEvent e, boolean canClear) {
        // TODO(dtrainor): Factor this out to LayoutDriver.
        if (e == null || mTabVisible == null) return;

        ContentViewCore contentViewCore = mTabVisible.getContentViewCore();
        if (contentViewCore == null) return;

        int actionMasked = e.getActionMasked();

        if (SPenSupport.isSPenSupported(getContext())) {
            actionMasked = SPenSupport.convertSPenEventAction(actionMasked);
        }

        if (actionMasked == MotionEvent.ACTION_DOWN
                || actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
            if (mLayoutManager != null) mLayoutManager.getViewportPixel(mCacheViewport);
            contentViewCore.setCurrentTouchEventOffsets(-mCacheViewport.left, -mCacheViewport.top);
        } else if (canClear && (actionMasked == MotionEvent.ACTION_UP
                                       || actionMasked == MotionEvent.ACTION_CANCEL
                                       || actionMasked == MotionEvent.ACTION_HOVER_EXIT)) {
            contentViewCore.setCurrentTouchEventOffsets(0.f, 0.f);
        }
    }

    private void propagateViewportToLayouts(int contentWidth, int contentHeight) {
        int heightMinusBrowserControls = contentHeight
                - (getTopControlsHeightPixels() + getBottomControlsHeightPixels());
        int bottomControlOffset = mFullscreenManager != null
                ? (int) mFullscreenManager.getBottomControlOffset() : 0;
        int viewportBottom =
                contentHeight - (getBottomControlsHeightPixels() - bottomControlOffset);

        // The only time that mCacheViewport and mCacheVisibleViewport are different is when the
        // browser has manipulated the browser controls offset.
        mCacheViewport.set(0, (int) mLastContentOffset, contentWidth, viewportBottom);
        mCacheVisibleViewport.set(0, (int) mLastVisibleContentOffset, contentWidth, viewportBottom);
        // TODO(changwan): check if this can be merged with setContentMotionEventOffsets.
        if (mTabVisible != null && mTabVisible.getContentViewCore() != null) {
            mTabVisible.getContentViewCore().setSmartClipOffsets(
                    -mCacheViewport.left, -mCacheViewport.top);
        }
        if (mLayoutManager != null) {
            mLayoutManager.pushNewViewport(
                    mCacheViewport, mCacheVisibleViewport, heightMinusBrowserControls);
        }
    }

    /**
     * To be called once a frame before commit.
     */
    @Override
    public void onCompositorLayout() {
        TraceEvent.begin("CompositorViewHolder:layout");
        if (mLayoutManager != null) {
            mLayoutManager.onUpdate();

            if (!DeviceFormFactor.isTablet(getContext()) && mControlContainer != null) {
                if (mProgressBarDrawingInfo == null) mProgressBarDrawingInfo = new DrawingInfo();
                mControlContainer.getProgressBarDrawingInfo(mProgressBarDrawingInfo);
            } else {
                assert mProgressBarDrawingInfo == null;
            }

            mCompositorView.finalizeLayers(mLayoutManager, false,
                    mProgressBarDrawingInfo);
        }

        TraceEvent.end("CompositorViewHolder:layout");
    }

    @Override
    public void requestRender() {
        mCompositorView.requestRender();
    }

    @Override
    public void onSurfaceCreated() {
        mPendingSwapBuffersCount = 0;
        flushInvalidation();
    }

    @Override
    public void onSwapBuffersCompleted(int pendingSwapBuffersCount) {
        TraceEvent.instant("onSwapBuffersCompleted");

        // Wait until the second frame to turn off the placeholder background on
        // tablets so the tab strip has time to start drawing.
        final ViewGroup controlContainer = (ViewGroup) mControlContainer;
        if (controlContainer != null && controlContainer.getBackground() != null && mHasDrawnOnce) {
            post(new Runnable() {
                @Override
                public void run() {
                    controlContainer.setBackgroundResource(0);
                }
            });
        }

        mHasDrawnOnce = true;

        mPendingSwapBuffersCount = pendingSwapBuffersCount;

        if (!mSkipInvalidation || pendingSwapBuffersCount == 0) flushInvalidation();
        mSkipInvalidation = !mSkipInvalidation;
    }

    @Override
    public void setContentOverlayVisibility(boolean show) {
        if (show != mContentOverlayVisiblity) {
            mContentOverlayVisiblity = show;
            updateContentOverlayVisibility(mContentOverlayVisiblity);
        }
    }

    @Override
    public LayoutRenderHost getLayoutRenderHost() {
        return this;
    }

    @Override
    public int getLayoutTabsDrawnCount() {
        return mCompositorView.getLastLayerCount();
    }

    @Override
    public void pushDebugRect(Rect rect, int color) {
        if (mDebugOverlay == null) {
            mDebugOverlay = new DebugOverlay(getContext());
            addView(mDebugOverlay);
        }
        mDebugOverlay.pushRect(rect, color);
    }

    @Override
    public void loadPersitentTextureDataIfNeeded() {}

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mIsKeyboardShowing = UiUtils.isKeyboardShowing(getContext(), this);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed) {
            propagateViewportToLayouts(r - l, b - t);
        }
        super.onLayout(changed, l, t, r, b);

        invalidateAccessibilityProvider();
    }

    @Override
    public void clearChildFocus(View child) {
        // Override this method so that the ViewRoot doesn't go looking for a new
        // view to take focus. It will find the URL Bar, focus it, then refocus this
        // later, causing a keyboard flicker.
    }

    @Override
    public ChromeFullscreenManager getFullscreenManager() {
        return mFullscreenManager;
    }

    /**
     * Sets a fullscreen handler.
     * @param fullscreen A fullscreen handler.
     */
    public void setFullscreenHandler(ChromeFullscreenManager fullscreen) {
        mFullscreenManager = fullscreen;
        if (mFullscreenManager != null) {
            mLastContentOffset = mFullscreenManager.getContentOffset();
            mLastVisibleContentOffset = mFullscreenManager.getTopVisibleContentOffset();
            mFullscreenManager.addListener(this);
        }
        propagateViewportToLayouts(getWidth(), getHeight());
    }

    @Override
    public int getBrowserControlsBackgroundColor() {
        return mTabVisible == null ? Color.WHITE : mTabVisible.getThemeColor();
    }

    @Override
    public float getBrowserControlsUrlBarAlpha() {
        return mTabVisible == null
                ? 1.f
                : ColorUtils.getTextBoxAlphaForToolbarBackground(mTabVisible);
    }

    @Override
    public boolean areBrowserControlsPermanentlyHidden() {
        return mFullscreenManager != null
                && mFullscreenManager.areBrowserControlsPermanentlyHidden();
    }

    @Override
    public int getTopControlsHeightPixels() {
        return mFullscreenManager != null ? mFullscreenManager.getTopControlsHeight() : 0;
    }

    /**
     * @return The height of the bottom conrols in pixels.
     */
    public int getBottomControlsHeightPixels() {
        return mFullscreenManager != null ? mFullscreenManager.getBottomControlsHeight() : 0;
    }

    @Override
    public int getOverlayTranslateY() {
        return areBrowserControlsPermanentlyHidden()
                ? getTopControlsHeightPixels()
                : mCacheVisibleViewport.top;
    }

    /**
     * Sets the URL bar. This is needed so that the ContentViewHolder can find out
     * whether it can claim focus.
     */
    public void setUrlBar(View urlBar) {
        mUrlBar = urlBar;
    }

    @Override
    public void onAttachedToWindow() {
        mInvalidator.set(this);
        super.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        if (mLayoutManager != null) mLayoutManager.destroy();
        flushInvalidation();
        mInvalidator.set(null);
        super.onDetachedFromWindow();

        // Removes the accessibility node provider from this view.
        if (mNodeProvider != null) {
            mAccessibilityView.setAccessibilityDelegate(null);
            mNodeProvider = null;
            removeView(mAccessibilityView);
            mAccessibilityView = null;
        }
    }

    /**
     * @return True if the currently active content view is shown in the normal interactive mode.
     */
    public boolean isTabInteractive() {
        return mLayoutManager != null && mLayoutManager.getActiveLayout() != null
                && mLayoutManager.getActiveLayout().isTabInteractive() && mContentOverlayVisiblity
                && mView != null;
    }

    @Override
    public void hideKeyboard(Runnable postHideTask) {
        // When this is called we actually want to hide the keyboard whatever owns it.
        // This includes hiding the keyboard, and dropping focus from the URL bar.
        // See http://crbug/236424
        // TODO(aberent) Find a better place to put this, possibly as part of a wider
        // redesign of focus control.
        if (mUrlBar != null) mUrlBar.clearFocus();
        boolean wasVisible = false;
        if (hasFocus()) {
            wasVisible = UiUtils.hideKeyboard(this);
        }
        if (wasVisible) {
            mPostHideKeyboardTask = postHideTask;
        } else {
            postHideTask.run();
        }
    }

    /**
     * Sets the appropriate objects this class should represent.
     * @param tabModelSelector        The {@link TabModelSelector} this View should hold and
     *                                represent.
     * @param tabCreatorManager       The {@link TabCreatorManager} for this view.
     * @param tabContentManager       The {@link TabContentManager} for the tabs.
     * @param androidContentContainer The {@link ViewGroup} the {@link LayoutManager} should bind
     *                                Android content to.
     * @param contextualSearchManager A {@link ContextualSearchManagementDelegate} instance.
     * @param readerModeManager       A {@link ReaderModeManagerDelegate} instance.
     */
    public void onFinishNativeInitialization(TabModelSelector tabModelSelector,
            TabCreatorManager tabCreatorManager, TabContentManager tabContentManager,
            ViewGroup androidContentContainer,
            ContextualSearchManagementDelegate contextualSearchManager,
            ReaderModeManagerDelegate readerModeManager) {
        assert mLayoutManager != null;
        mLayoutManager.init(tabModelSelector, tabCreatorManager, tabContentManager,
                androidContentContainer, contextualSearchManager, readerModeManager,
                mCompositorView.getResourceManager().getDynamicResourceLoader());
        mTabModelSelector = tabModelSelector;
        tabModelSelector.addObserver(new EmptyTabModelSelectorObserver() {
            @Override
            public void onChange() {
                onContentChanged();
            }

            @Override
            public void onNewTabCreated(Tab tab) {
                initializeTab(tab);
            }
        });

        mLayerTitleCache.setTabModelSelector(mTabModelSelector);

        onContentChanged();
    }

    private void updateContentOverlayVisibility(boolean show) {
        if (mView == null) return;
        ContentViewCore content = getActiveContent();
        if (show) {
            if (mView.getParent() != this) {
                // During tab creation, we temporarily add the new tab's view to a FrameLayout to
                // measure and lay it out. This way we could show the animation in the stack view.
                // Therefore we should remove the view from that temporary FrameLayout here.
                UiUtils.removeViewFromParent(mView);

                if (content != null) {
                    assert content.isAlive();
                    content.getContainerView().setVisibility(View.VISIBLE);
                    if (mFullscreenManager != null) {
                        mFullscreenManager.updateContentViewViewportSize(content);
                    }
                }

                CoordinatorLayout.LayoutParams layoutParams;
                if (mView.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
                    layoutParams = (CoordinatorLayout.LayoutParams) mView.getLayoutParams();
                } else {
                    layoutParams = new CoordinatorLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                }
                layoutParams.setBehavior(mView.getBehavior());
                // CompositorView has index of 0; TabContentViewParent has index of 1; omnibox
                // result container (the scrim) has index of 2, Snackbar (if any) has index of 3.
                // Setting index here explicitly to avoid TabContentViewParent hiding the scrim.
                // TODO(ianwen): Use more advanced technologies to ensure z-order of the children of
                // this class, instead of hard-coding.
                addView(mView, 1, layoutParams);

                setFocusable(false);
                setFocusableInTouchMode(false);

                // Claim focus for the new view unless the user is currently using the URL bar.
                if (mUrlBar == null || !mUrlBar.hasFocus()) mView.requestFocus();
            }
        } else {
            if (mView.getParent() == this) {
                setFocusable(true);
                setFocusableInTouchMode(true);

                if (content != null) {
                    if (content.isAlive()) content.getContainerView().setVisibility(View.INVISIBLE);
                }
                removeView(mView);
            }
        }
    }

    @Override
    public void onContentChanged() {
        if (mTabModelSelector == null) {
            // Not yet initialized, onContentChanged() will eventually get called by
            // setTabModelSelector.
            return;
        }
        Tab tab = mTabModelSelector.getCurrentTab();
        setTab(tab);
    }

    @Override
    public void onContentViewCoreAdded(ContentViewCore content) {
        // TODO(dtrainor): Look into rolling this into onContentChanged().
        initializeContentViewCore(content);
        setSizeOfUnattachedView(content.getContainerView());
    }

    private void setTab(Tab tab) {
        if (tab != null) tab.loadIfNeeded();

        TabContentViewParent newView = tab != null ? tab.getView() : null;
        if (mView == newView) return;

        // TODO(dtrainor): Look into changing this only if the views differ, but still parse the
        // ContentViewCore list even if they're the same.
        updateContentOverlayVisibility(false);

        if (mTabVisible != tab) {
            if (mTabVisible != null) mTabVisible.removeObserver(mTabObserver);
            if (tab != null) tab.addObserver(mTabObserver);
        }

        mTabVisible = tab;
        mView = newView;

        updateContentOverlayVisibility(mContentOverlayVisiblity);

        if (mTabVisible != null) initializeTab(mTabVisible);
    }

    /**
     * Sets the correct size for {@link View} on {@code tab} and sets the correct rendering
     * parameters on {@link ContentViewCore} on {@code tab}.
     * @param tab The {@link Tab} to initialize.
     */
    private void initializeTab(Tab tab) {
        ContentViewCore content = tab.getActiveContentViewCore();
        if (content != null) initializeContentViewCore(content);

        View view = tab.getContentView();
        if (view != tab.getView() || !tab.isNativePage()) setSizeOfUnattachedView(view);
    }

    /**
     * Initializes the rendering surface parameters of {@code contentViewCore}.  Note that this does
     * not size the actual {@link ContentViewCore}.
     * @param contentViewCore The {@link ContentViewCore} to initialize.
     */
    private void initializeContentViewCore(ContentViewCore contentViewCore) {
        contentViewCore.setCurrentTouchEventOffsets(0.f, 0.f);
        contentViewCore.setTopControlsHeight(getTopControlsHeightPixels(),
                contentViewCore.doBrowserControlsShrinkBlinkSize());
        contentViewCore.setBottomControlsHeight(getBottomControlsHeightPixels());

        adjustPhysicalBackingSize(contentViewCore,
                mCompositorView.getWidth(), mCompositorView.getHeight());
    }

    /**
     * Adjusts the physical backing size of a given ContentViewCore. This method will first check
     * if the ContentViewCore's client wants to override the size and, if so, it will use the
     * values provided by the {@link ContentViewClient#getDesiredWidthMeasureSpec()} and
     * {@link ContentViewClient#getDesiredHeightMeasureSpec()} methods. If no value is provided
     * in one of these methods, the values from the |width| and |height| arguments will be
     * used instead.
     *
     * @param contentViewCore The {@link ContentViewCore} to resize.
     * @param width The default width.
     * @param height The default height.
     */
    private void adjustPhysicalBackingSize(ContentViewCore contentViewCore, int width, int height) {
        ContentViewClient client = contentViewCore.getContentViewClient();

        int desiredWidthMeasureSpec = client.getDesiredWidthMeasureSpec();
        if (MeasureSpec.getMode(desiredWidthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            width = MeasureSpec.getSize(desiredWidthMeasureSpec);
        }

        int desiredHeightMeasureSpec = client.getDesiredHeightMeasureSpec();
        if (MeasureSpec.getMode(desiredHeightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            height = MeasureSpec.getSize(desiredHeightMeasureSpec);
        }

        contentViewCore.onPhysicalBackingSizeChanged(width, height);
    }

    /**
     * Resize {@code view} to match the size of this {@link FrameLayout}.  This will only happen if
     * {@code view} is not {@code null} and if {@link View#getWindowToken()} returns {@code null}
     * (the {@link View} is not part of the view hierarchy).
     * @param view The {@link View} to resize.
     * @return     Whether or not {@code view} was resized.
     */
    private boolean setSizeOfUnattachedView(View view) {
        // Need to call layout() for the following View if it is not attached to the view hierarchy.
        // Calling onSizeChanged() is dangerous because if the View has a different size than the
        // ContentViewCore it might think a future size update is a NOOP and not call
        // onSizeChanged() on the ContentViewCore.
        if (view == null || view.getWindowToken() != null) return false;
        int width = getWidth();
        int height = getHeight();
        view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        return true;
    }

    @Override
    public TitleCache getTitleCache() {
        return mLayerTitleCache;
    }

    @Override
    public void deferInvalidate(Client client) {
        if (mPendingSwapBuffersCount <= 0) {
            client.doInvalidate();
        } else if (!mPendingInvalidations.contains(client)) {
            mPendingInvalidations.add(client);
        }
    }

    private void flushInvalidation() {
        if (mPendingInvalidations.isEmpty()) return;
        TraceEvent.instant("CompositorViewHolder.flushInvalidation");
        for (int i = 0; i < mPendingInvalidations.size(); i++) {
            mPendingInvalidations.get(i).doInvalidate();
        }
        mPendingInvalidations.clear();
    }

    @Override
    public void invalidateAccessibilityProvider() {
        if (mNodeProvider != null) {
            mNodeProvider.sendEventForVirtualView(mNodeProvider.getFocusedVirtualView(),
                    AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            mNodeProvider.invalidateRoot();
        }
    }

    /**
     * Called when the accessibility enabled state changes.
     * @param enabled Whether accessibility is enabled.
     */
    public void onAccessibilityStatusChanged(boolean enabled) {
        // Instantiate and install the accessibility node provider on this view if necessary.
        // This overrides any hover event listeners or accessibility delegates
        // that may have been added elsewhere.
        if (enabled && (mNodeProvider == null)) {
            mAccessibilityView = new View(getContext());
            addView(mAccessibilityView);
            mNodeProvider = new CompositorAccessibilityProvider(mAccessibilityView);
            ViewCompat.setAccessibilityDelegate(mAccessibilityView, mNodeProvider);
        }
    }

    /**
     * Class used to provide a virtual view hierarchy to the Accessibility
     * framework for this view and its contained items.
     * <p>
     * <strong>NOTE:</strong> This class is fully backwards compatible for
     * compilation, but will only provide touch exploration on devices running
     * Ice Cream Sandwich and above.
     * </p>
     */
    private class CompositorAccessibilityProvider extends ExploreByTouchHelper {
        private final float mDpToPx;
        List<VirtualView> mVirtualViews = new ArrayList<VirtualView>();
        private final Rect mPlaceHolderRect = new Rect(0, 0, 1, 1);
        private static final String PLACE_HOLDER_STRING = "";
        private final RectF mTouchTarget = new RectF();
        private final Rect mPixelRect = new Rect();

        public CompositorAccessibilityProvider(View forView) {
            super(forView);
            mDpToPx = getContext().getResources().getDisplayMetrics().density;
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            if (mVirtualViews == null) return INVALID_ID;
            for (int i = 0; i < mVirtualViews.size(); i++) {
                if (mVirtualViews.get(i).checkClicked(x / mDpToPx, y / mDpToPx)) {
                    return i;
                }
            }
            return INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            if (mLayoutManager == null) return;
            mVirtualViews.clear();
            mLayoutManager.getVirtualViews(mVirtualViews);
            for (int i = 0; i < mVirtualViews.size(); i++) {
                virtualViewIds.add(i);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(
                int virtualViewId, int action, Bundle arguments) {
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_CLICK:
                    return true;
            }

            return false;
        }

        @Override
        protected void onPopulateEventForVirtualView(int virtualViewId, AccessibilityEvent event) {
            if (mVirtualViews == null || mVirtualViews.size() <= virtualViewId) {
                // TODO(clholgat): Remove this work around when the Android bug is fixed.
                // crbug.com/420177
                event.setContentDescription(PLACE_HOLDER_STRING);
                return;
            }
            VirtualView view = mVirtualViews.get(virtualViewId);

            event.setContentDescription(view.getAccessibilityDescription());
            event.setClassName(CompositorViewHolder.class.getName());
        }

        @Override
        protected void onPopulateNodeForVirtualView(
                int virtualViewId, AccessibilityNodeInfoCompat node) {
            if (mVirtualViews == null || mVirtualViews.size() <= virtualViewId) {
                // TODO(clholgat): Remove this work around when the Android bug is fixed.
                // crbug.com/420177
                node.setBoundsInParent(mPlaceHolderRect);
                node.setContentDescription(PLACE_HOLDER_STRING);
                return;
            }
            VirtualView view = mVirtualViews.get(virtualViewId);
            view.getTouchTarget(mTouchTarget);

            node.setBoundsInParent(rectToPx(mTouchTarget));
            node.setContentDescription(view.getAccessibilityDescription());
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_FOCUS);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
        }

        private Rect rectToPx(RectF rect) {
            rect.roundOut(mPixelRect);
            mPixelRect.left = (int) (mPixelRect.left * mDpToPx);
            mPixelRect.top = (int) (mPixelRect.top * mDpToPx);
            mPixelRect.right = (int) (mPixelRect.right * mDpToPx);
            mPixelRect.bottom = (int) (mPixelRect.bottom * mDpToPx);

            // Don't let any zero sized rects through, they'll cause parent
            // size errors in L.
            if (mPixelRect.width() == 0) {
                mPixelRect.right = mPixelRect.left + 1;
            }
            if (mPixelRect.height() == 0) {
                mPixelRect.bottom = mPixelRect.top + 1;
            }
            return mPixelRect;
        }
    }
}
