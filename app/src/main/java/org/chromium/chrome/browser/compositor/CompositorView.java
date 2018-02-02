// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.LayoutProvider;
import org.chromium.chrome.browser.compositor.layouts.LayoutRenderHost;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.resources.StaticResourcePreloads;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.externalnav.IntentWithGesturesHandler;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.tabmodel.TabModelImpl;
import org.chromium.chrome.browser.widget.ClipDrawableProgressBar.DrawingInfo;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.resources.AndroidResourceType;
import org.chromium.ui.resources.ResourceManager;

/**
 * The is the {@link View} displaying the ui compositor results; including webpages and tabswitcher.
 */
@JNINamespace("android")
public class CompositorView
        extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "CompositorView";
    private static final long NANOSECONDS_PER_MILLISECOND = 1000000;

    // Cache objects that should not be created every frame
    private final Rect mCacheViewport = new Rect();
    private final Rect mCacheAppRect = new Rect();
    private final int[] mCacheViewPosition = new int[2];

    private long mNativeCompositorView;
    private final LayoutRenderHost mRenderHost;
    private boolean mEnableTabletTabStack;
    private int mPreviousWindowTop = -1;

    private int mLastLayerCount;

    // A conservative estimate of when a frame is guaranteed to be presented after being submitted.
    private long mFramePresentationDelay;

    // Resource Management
    private ResourceManager mResourceManager;

    // Lazily populated as it is needed.
    private View mRootActivityView;
    private WindowAndroid mWindowAndroid;
    private LayerTitleCache mLayerTitleCache;
    private TabContentManager mTabContentManager;

    private View mRootView;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private boolean mPreloadedResources;

    // The current SurfaceView pixel format. Defaults to OPAQUE.
    private int mCurrentPixelFormat = PixelFormat.OPAQUE;

    /**
     * Creates a {@link CompositorView}. This can be called only after the native library is
     * properly loaded.
     * @param c        The Context to create this {@link CompositorView} in.
     * @param host     The renderer host owning this view.
     */
    public CompositorView(Context c, LayoutRenderHost host) {
        super(c);
        mRenderHost = host;
        resetFlags();
        setVisibility(View.INVISIBLE);
        setZOrderMediaOverlay(true);
    }

    /**
     * @param view The root view of the hierarchy.
     */
    public void setRootView(View view) {
        mRootView = view;
    }

    /**
     * Reset the commandline flags. This gets called after we switch over to the
     * native command line.
     */
    public void resetFlags() {
        CommandLine commandLine = CommandLine.getInstance();
        mEnableTabletTabStack = commandLine.hasSwitch(ChromeSwitches.ENABLE_TABLET_TAB_STACK)
                && DeviceFormFactor.isTablet(getContext());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mRootView != null) {
            mRootView.getWindowVisibleDisplayFrame(mCacheAppRect);

            // Check whether the top position of the window has changed as we always must
            // resize in that case to the specified height spec.  On certain versions of
            // Android when you change the top position (i.e. by leaving fullscreen) and
            // do not shrink the SurfaceView, it will appear to be pinned to the top of
            // the screen under the notification bar and all touch offsets will be wrong
            // as well as a gap will appear at the bottom of the screen.
            int windowTop = mCacheAppRect.top;
            boolean topChanged = windowTop != mPreviousWindowTop;
            mPreviousWindowTop = windowTop;

            Activity activity = mWindowAndroid != null ? mWindowAndroid.getActivity().get() : null;
            boolean isMultiWindow = MultiWindowUtils.getInstance().isLegacyMultiWindow(activity)
                    || MultiWindowUtils.getInstance().isInMultiWindowMode(activity);

            // If the measured width is the same as the allowed width (i.e. the orientation has
            // not changed) and multi-window mode is off, use the largest measured height seen thus
            // far.  This will prevent surface resizes as a result of showing the keyboard.
            if (!topChanged && !isMultiWindow
                    && getMeasuredWidth() == MeasureSpec.getSize(widthMeasureSpec)
                    && getMeasuredHeight() > MeasureSpec.getSize(heightMeasureSpec)) {
                heightMeasureSpec =
                        MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPreviousWindowTop = -1;
    }

    /**
     * @return The ResourceManager.
     */
    public ResourceManager getResourceManager() {
        return mResourceManager;
    }

    /**
     * Should be called for cleanup when the CompositorView instance is no longer used.
     */
    public void shutDown() {
        getHolder().removeCallback(this);
        if (mNativeCompositorView != 0) nativeDestroy(mNativeCompositorView);
        mNativeCompositorView = 0;
    }

    /**
     * Initializes the {@link CompositorView}'s native parts (e.g. the rendering parts).
     * @param lowMemDevice         If this is a low memory device.
     * @param windowAndroid        A {@link WindowAndroid} instance.
     * @param layerTitleCache      A {@link LayerTitleCache} instance.
     * @param tabContentManager    A {@link TabContentManager} instance.
     */
    public void initNativeCompositor(boolean lowMemDevice, WindowAndroid windowAndroid,
            LayerTitleCache layerTitleCache, TabContentManager tabContentManager) {
        mWindowAndroid = windowAndroid;
        mLayerTitleCache = layerTitleCache;
        mTabContentManager = tabContentManager;

        mNativeCompositorView = nativeInit(lowMemDevice,
                windowAndroid.getNativePointer(), layerTitleCache, tabContentManager);

        assert !getHolder().getSurface().isValid()
            : "Surface created before native library loaded.";
        getHolder().addCallback(this);

        // Cover the black surface before it has valid content.
        setBackgroundColor(Color.WHITE);
        setVisibility(View.VISIBLE);

        mFramePresentationDelay = 0;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            Display display =
                    ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            long presentationDeadline = display.getPresentationDeadlineNanos()
                    / NANOSECONDS_PER_MILLISECOND;
            long vsyncPeriod = mWindowAndroid.getVsyncPeriodInMillis();
            mFramePresentationDelay = Math.min(3 * vsyncPeriod,
                    ((presentationDeadline + vsyncPeriod - 1) / vsyncPeriod) * vsyncPeriod);
        }

        // Grab the Resource Manager
        mResourceManager = nativeGetResourceManager(mNativeCompositorView);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return super.onTouchEvent(e);
    }

    /**
     * Enables/disables overlay video mode. Affects alpha blending on this view.
     * @param enabled Whether to enter or leave overlay video mode.
     */
    public void setOverlayVideoMode(boolean enabled) {
        mCurrentPixelFormat = enabled ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
        getHolder().setFormat(mCurrentPixelFormat);
        nativeSetOverlayVideoMode(mNativeCompositorView, enabled);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mNativeCompositorView == 0) return;
        nativeSurfaceChanged(mNativeCompositorView, format, width, height, holder.getSurface());
        mRenderHost.onPhysicalBackingSizeChanged(width, height);
        mSurfaceWidth = width;
        mSurfaceHeight = height;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mNativeCompositorView == 0) return;
        nativeSurfaceCreated(mNativeCompositorView);
        mRenderHost.onSurfaceCreated();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mNativeCompositorView == 0) return;
        nativeSurfaceDestroyed(mNativeCompositorView);
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (mWindowAndroid == null) return;
        if (visibility == View.GONE) {
            mWindowAndroid.onVisibilityChanged(false);
        } else if (visibility == View.VISIBLE) {
            mWindowAndroid.onVisibilityChanged(true);
        }
        IntentWithGesturesHandler.getInstance().clear();
    }

    @CalledByNative
    private void onCompositorLayout() {
        mRenderHost.onCompositorLayout();
    }

    /*
     * On JellyBean there is a known bug where a crashed producer process
     * (i.e. GPU process) does not properly disconnect from the BufferQueue,
     * which means we won't be able to reconnect to it ever again.
     * This workaround forces the creation of a new Surface.
     */
    @CalledByNative
    private void onJellyBeanSurfaceDisconnectWorkaround(boolean inOverlayMode) {
        // There is a bug in JellyBean because of which we will not be able to
        // reconnect to the existing Surface after we launch a new GPU process.
        // We simply trick the JB Android code to allocate a new Surface.
        // It does a strict comparison between the current format and the requested
        // one, even if they are the same in practice. Furthermore, the format
        // does not matter here since the producer-side EGL config overwrites it
        // (but transparency might matter).
        switch (mCurrentPixelFormat) {
            case PixelFormat.OPAQUE:
                mCurrentPixelFormat = PixelFormat.RGBA_8888;
                break;
            case PixelFormat.RGBA_8888:
                mCurrentPixelFormat = inOverlayMode
                        ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
                break;
            case PixelFormat.TRANSLUCENT:
                mCurrentPixelFormat = PixelFormat.RGBA_8888;
                break;
            default:
                assert false;
                Log.e(TAG, "Unknown current pixel format.");
        }
        getHolder().setFormat(mCurrentPixelFormat);
    }

    /**
     * Request compositor view to render a frame.
     */
    public void requestRender() {
        if (mNativeCompositorView != 0) nativeSetNeedsComposite(mNativeCompositorView);
    }

    @CalledByNative
    private void onSwapBuffersCompleted(int pendingSwapBuffersCount) {
        // Clear the color used to cover the uninitialized surface.
        if (getBackground() != null) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    setBackgroundResource(0);
                }
            }, mFramePresentationDelay);
        }

        mRenderHost.onSwapBuffersCompleted(pendingSwapBuffersCount);
    }

    /**
     * Converts the layout into compositor layers. This is to be called on every frame the layout
     * is changing.
     * @param provider               Provides the layout to be rendered.
     * @param forRotation            Whether or not this is a special draw during a rotation.
     */
    public void finalizeLayers(final LayoutProvider provider, boolean forRotation,
            final DrawingInfo progressBarDrawingInfo) {
        TraceEvent.begin("CompositorView:finalizeLayers");
        Layout layout = provider.getActiveLayout();
        if (layout == null || mNativeCompositorView == 0) {
            TraceEvent.end("CompositorView:finalizeLayers");
            return;
        }

        if (!mPreloadedResources) {
            // Attempt to prefetch any necessary resources
            mResourceManager.preloadResources(AndroidResourceType.STATIC,
                    StaticResourcePreloads.getSynchronousResources(getContext()),
                    StaticResourcePreloads.getAsynchronousResources(getContext()));
            mPreloadedResources = true;
        }

        // IMPORTANT: Do not do anything that impacts the compositor layer tree before this line.
        // If you do, you could inadvertently trigger follow up renders.  For further information
        // see dtrainor@, tedchoc@, or klobag@.

        provider.getViewportPixel(mCacheViewport);

        nativeSetLayoutBounds(mNativeCompositorView);

        SceneLayer sceneLayer =
                provider.getUpdatedActiveSceneLayer(mCacheViewport, mLayerTitleCache,
                        mTabContentManager, mResourceManager, provider.getFullscreenManager());

        nativeSetSceneLayer(mNativeCompositorView, sceneLayer);

        final LayoutTab[] tabs = layout.getLayoutTabsToRender();
        final int tabsCount = tabs != null ? tabs.length : 0;
        mLastLayerCount = tabsCount;
        TabModelImpl.flushActualTabSwitchLatencyMetric();
        nativeFinalizeLayers(mNativeCompositorView);
        TraceEvent.end("CompositorView:finalizeLayers");
    }

    /**
     * @return The number of layer put the last frame.
     */
    @VisibleForTesting
    public int getLastLayerCount() {
        return mLastLayerCount;
    }

    // Implemented in native
    private native long nativeInit(boolean lowMemDevice, long nativeWindowAndroid,
            LayerTitleCache layerTitleCache, TabContentManager tabContentManager);
    private native void nativeDestroy(long nativeCompositorView);
    private native ResourceManager nativeGetResourceManager(long nativeCompositorView);
    private native void nativeSurfaceCreated(long nativeCompositorView);
    private native void nativeSurfaceDestroyed(long nativeCompositorView);
    private native void nativeSurfaceChanged(
            long nativeCompositorView, int format, int width, int height, Surface surface);
    private native void nativeFinalizeLayers(long nativeCompositorView);
    private native void nativeSetNeedsComposite(long nativeCompositorView);
    private native void nativeSetLayoutBounds(long nativeCompositorView);
    private native void nativeSetOverlayVideoMode(long nativeCompositorView, boolean enabled);
    private native void nativeSetSceneLayer(long nativeCompositorView, SceneLayer sceneLayer);
}
