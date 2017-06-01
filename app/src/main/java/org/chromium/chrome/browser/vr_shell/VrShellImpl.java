// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glTexParameteri;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.opengl.GLES11Ext;
import android.opengl.GLSurfaceView;
import android.os.StrictMode;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.GvrLayout;

import org.chromium.base.CommandLine;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.UsedByReflection;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabContentViewParent;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.ViewAndroidDelegate;
import org.chromium.ui.base.WindowAndroid;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This view extends from GvrLayout which wraps a GLSurfaceView that renders VR shell.
 */
@JNINamespace("vr_shell")
@UsedByReflection("VrShellDelegate.java")
public class VrShellImpl extends GvrLayout implements GLSurfaceView.Renderer, VrShell {
    private static final String TAG = "VrShellImpl";

    @UsedByReflection("VrShellDelegate.java")
    public static final String VR_EXTRA = "android.intent.extra.VR_LAUNCH";

    private Activity mActivity;

    private final GLSurfaceView mGlSurfaceView;

    private long mNativeVrShell = 0;

    private int mContentTextureHandle;
    private int mUiTextureHandle;
    private FrameListener mContentFrameListener;
    private FrameListener mUiFrameListener;

    private FrameLayout mContentViewCoreContainer;

    // The tab that holds the main ContentViewCore.
    private Tab mTab;

    // The ContentViewCore for the main content rect in VR.
    private ContentViewCore mContentCVC;
    private TabContentViewParent mTabParent;
    private ViewGroup mTabParentParent;

    // TODO(mthiesse): Instead of caching these values, make tab reparenting work for this case.
    private int mOriginalTabParentIndex;
    private ViewGroup.LayoutParams mOriginalLayoutParams;
    private WindowAndroid mOriginalWindowAndroid;

    private VrWindowAndroid mContentVrWindowAndroid;

    private WebContents mUiContents;
    private ContentViewCore mUiCVC;
    private VrWindowAndroid mUiVrWindowAndroid;

    @UsedByReflection("VrShellDelegate.java")
    public VrShellImpl(Activity activity) {
        super(activity);
        mActivity = activity;
        mContentViewCoreContainer = new FrameLayout(getContext()) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                return true;
            }
        };
        addView(mContentViewCoreContainer, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        mGlSurfaceView = new GLSurfaceView(getContext());
        mGlSurfaceView.setEGLContextClientVersion(2);
        mGlSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        mGlSurfaceView.setPreserveEGLContextOnPause(true);
        mGlSurfaceView.setRenderer(this);
        setPresentationView(mGlSurfaceView);

        if (setAsyncReprojectionEnabled(true)) {
            AndroidCompat.setSustainedPerformanceMode(mActivity, true);
        }
    }

    @Override
    public void initializeNative(Tab currentTab, VrShellDelegate delegate) {
        assert currentTab.getContentViewCore() != null;
        mTab = currentTab;
        mContentCVC = mTab.getContentViewCore();
        mContentVrWindowAndroid = new VrWindowAndroid(mActivity);

        mUiVrWindowAndroid = new VrWindowAndroid(mActivity);
        mUiContents = WebContentsFactory.createWebContents(true, false);
        mUiCVC = new ContentViewCore(mActivity, ChromeVersionInfo.getProductVersion());
        ContentView uiContentView = ContentView.createContentView(mActivity, mUiCVC);
        mUiCVC.initialize(ViewAndroidDelegate.createBasicDelegate(uiContentView),
                uiContentView, mUiContents, mUiVrWindowAndroid);

        mNativeVrShell = nativeInit(mContentCVC.getWebContents(),
                mContentVrWindowAndroid.getNativePointer(),
                mUiContents, mUiVrWindowAndroid.getNativePointer());
        mGlSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View v, MotionEvent event) {
                if (!CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_VR_SHELL_DEV)
                        && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    nativeOnTriggerEvent(mNativeVrShell);
                    return true;
                }
                return false;
            }
        });

        uiContentView.setVisibility(View.VISIBLE);
        mUiCVC.onShow();
        mContentViewCoreContainer.addView(uiContentView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        mUiCVC.setBottomControlsHeight(0);
        mUiCVC.setTopControlsHeight(0, false);
        mUiVrWindowAndroid.onVisibilityChanged(true);

        nativeSetDelegate(mNativeVrShell, delegate);

        reparentContentWindow();

        nativeUpdateCompositorLayers(mNativeVrShell);
    }

    private void reparentContentWindow() {
        mOriginalWindowAndroid = mContentCVC.getWindowAndroid();

        mTab.updateWindowAndroid(mContentVrWindowAndroid);

        mTabParent = mTab.getView();
        mTabParentParent = (ViewGroup) mTabParent.getParent();
        mOriginalTabParentIndex = mTabParentParent.indexOfChild(mTabParent);
        mOriginalLayoutParams = mTabParent.getLayoutParams();
        mTabParentParent.removeView(mTabParent);

        mContentViewCoreContainer.addView(mTabParent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void restoreContentWindow() {
        mTab.updateWindowAndroid(mOriginalWindowAndroid);

        // If the tab's view has changed, the necessary view reparenting has already been done.
        if (mTab.getView() == mTabParent) {
            mContentViewCoreContainer.removeView(mTabParent);
            mTabParentParent.addView(mTabParent, mOriginalTabParentIndex, mOriginalLayoutParams);
            mTabParent.requestFocus();
        }
        mTabParent = null;
    }

    private static class FrameListener implements OnFrameAvailableListener {
        final SurfaceTexture mSurfaceTexture;
        final GLSurfaceView mGlSurfaceView;
        boolean mFirstTex = true;

        final Runnable mUpdateTexImage = new Runnable() {
            @Override
            public void run() {
                try {
                    mSurfaceTexture.updateTexImage();
                } catch (IllegalStateException e) {
                }
            }
        };

        public FrameListener(int textureId, GLSurfaceView glSurfaceView) {
            mSurfaceTexture = new SurfaceTexture(textureId);
            mSurfaceTexture.setOnFrameAvailableListener(this);
            mGlSurfaceView = glSurfaceView;
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mFirstTex = false;
            mGlSurfaceView.queueEvent(mUpdateTexImage);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        final int width = mContentCVC.getContainerView().getWidth();
        final int height = mContentCVC.getContainerView().getHeight();
        mContentTextureHandle = createExternalTextureHandle();
        mUiTextureHandle = createExternalTextureHandle();

        mContentFrameListener = new FrameListener(mContentTextureHandle, mGlSurfaceView);
        mUiFrameListener = new FrameListener(mUiTextureHandle, mGlSurfaceView);

        mContentFrameListener.mSurfaceTexture.setDefaultBufferSize(width, height);
        mUiFrameListener.mSurfaceTexture.setDefaultBufferSize(width, height);

        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                nativeContentSurfaceChanged(mNativeVrShell, width, height,
                        new Surface(mContentFrameListener.mSurfaceTexture));
                mContentCVC.onPhysicalBackingSizeChanged(width, height);
                nativeUiSurfaceChanged(mNativeVrShell, width, height,
                        new Surface(mUiFrameListener.mSurfaceTexture));
                mUiCVC.onPhysicalBackingSizeChanged(width, height);
            }
        });

        nativeGvrInit(mNativeVrShell, getGvrApi().getNativeGvrContext());
        nativeInitializeGl(mNativeVrShell, mContentTextureHandle, mUiTextureHandle);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {}

    @Override
    public void onDrawFrame(GL10 gl) {
        // Make sure we've updated the texture at least once. We do this because onFrameAvailable
        // isn't guaranteed to have fired after transitioning to VR. It only fires when the texture
        // is updated either through scrolling, resizing, etc. - none of which we're guaranteed to
        // have done on transition.
        if (mUiFrameListener.mFirstTex) {
            mUiFrameListener.mSurfaceTexture.updateTexImage();
            mUiFrameListener.mFirstTex = false;
        }
        if (mContentFrameListener.mFirstTex) {
            mContentFrameListener.mSurfaceTexture.updateTexImage();
            mContentFrameListener.mFirstTex = false;
        }

        nativeDrawFrame(mNativeVrShell);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Normally, touch event is dispatched to presentation view only if the phone is paired with
        // a Cardboard viewer. This is annoying when we just want to quickly verify a Cardboard
        // behavior. This allows us to trigger cardboard trigger event without pair to a Cardboard.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_VR_SHELL_DEV)
                && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            nativeOnTriggerEvent(mNativeVrShell);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onResume() {
        super.onResume();
        mGlSurfaceView.onResume();
        if (mNativeVrShell != 0) {
            // Refreshing the viewer profile accesses disk, so we need to temporarily allow disk
            // reads. The GVR team promises this will be fixed when they launch.
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
            try {
                nativeOnResume(mNativeVrShell);
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mGlSurfaceView.onPause();
        if (mNativeVrShell != 0) {
            nativeOnPause(mNativeVrShell);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (mNativeVrShell != 0) {
            // Ensure our GL thread is stopped before we destroy the native VR Shell.
            mGlSurfaceView.onPause();
            nativeDestroy(mNativeVrShell);
            mNativeVrShell = 0;
        }
        if (mContentFrameListener != null && mContentFrameListener.mSurfaceTexture != null) {
            mContentFrameListener.mSurfaceTexture.release();
        }
        if (mUiFrameListener != null && mUiFrameListener.mSurfaceTexture != null) {
            mUiFrameListener.mSurfaceTexture.release();
        }
        restoreContentWindow();
        mUiContents.destroy();
    }

    @Override
    public void pause() {
        onPause();
    }

    @Override
    public void resume() {
        onResume();
    }

    @Override
    public void teardown() {
        shutdown();
    }

    @Override
    public void setWebVrModeEnabled(boolean enabled) {
        nativeSetWebVrMode(mNativeVrShell, enabled);
    }

    @Override
    public FrameLayout getContainer() {
        return this;
    }

    @Override
    public void setCloseButtonListener(Runnable runner) {
        getUiLayout().setCloseButtonListener(runner);
    }

    /**
     * Create a new GLES11Ext.GL_TEXTURE_EXTERNAL_OES texture handle.
     * @return New texture handle.
     */
    private int createExternalTextureHandle() {
        int[] textureDataHandle = new int[1];
        glGenTextures(1, textureDataHandle, 0);
        if (textureDataHandle[0] != 0) {
            glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureDataHandle[0]);
            glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            return textureDataHandle[0];
        } else {
            throw new RuntimeException("Error generating texture handle.");
        }
    }

    private native long nativeInit(WebContents contentWebContents, long nativeContentWindowAndroid,
            WebContents uiWebContents, long nativeUiWindowAndroid);
    private native void nativeSetDelegate(long nativeVrShell, VrShellDelegate delegate);
    private native void nativeGvrInit(long nativeVrShell, long nativeGvrApi);
    private native void nativeDestroy(long nativeVrShell);
    private native void nativeInitializeGl(
            long nativeVrShell, int contentTextureHandle, int uiTextureHandle);
    private native void nativeDrawFrame(long nativeVrShell);
    private native void nativeOnTriggerEvent(long nativeVrShell);
    private native void nativeOnPause(long nativeVrShell);
    private native void nativeOnResume(long nativeVrShell);
    private native void nativeContentSurfaceChanged(
            long nativeVrShell, int width, int height, Surface surface);
    private native void nativeUiSurfaceChanged(
            long nativeVrShell, int width, int height, Surface surface);
    private native void nativeUpdateCompositorLayers(long nativeVrShell);
    private native void nativeSetWebVrMode(long nativeVrShell, boolean enabled);
}
