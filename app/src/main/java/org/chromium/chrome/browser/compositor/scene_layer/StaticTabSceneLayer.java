// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.scene_layer;

import android.graphics.Rect;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;

/**
 * A SceneLayer to render a static tab.
 */
@JNINamespace("chrome::android")
public class StaticTabSceneLayer extends SceneLayer {
    // NOTE: If you use SceneLayer's native pointer here, the JNI generator will try to
    // downcast using reinterpret_cast<>. We keep a separate pointer to avoid it.
    private long mNativePtr;

    private final int mResToolbarControlContainer;

    public StaticTabSceneLayer(int resToolbarControlContainer) {
        mResToolbarControlContainer = resToolbarControlContainer;
    }

    /**
     * Update {@link StaticTabSceneLayer} with the given parameters.
     *
     * @param dpToPx            The ratio of dp to px.
     * @param contentViewport   The viewport of the content.
     * @param layerTitleCache   The LayerTitleCache.
     * @param tabContentManager The TabContentManager.
     * @param fullscreenManager The FullscreenManager.
     * @param layoutTab         The LayoutTab.
     */
    public void update(float dpToPx, Rect contentViewport, LayerTitleCache layerTitleCache,
            TabContentManager tabContentManager, ChromeFullscreenManager fullscreenManager,
            LayoutTab layoutTab) {
        if (layoutTab == null) {
            return;
        }

        float contentOffset =
                fullscreenManager != null ? fullscreenManager.getContentOffset() : 0.f;

        // TODO(dtrainor, clholgat): remove "* dpToPx" once the native part is fully supporting dp.
        nativeUpdateTabLayer(mNativePtr, contentViewport.left, contentViewport.top,
                contentViewport.width(), contentViewport.height(), tabContentManager,
                layoutTab.getId(), mResToolbarControlContainer, layoutTab.canUseLiveTexture(),
                layoutTab.getBackgroundColor(), layoutTab.getRenderX() * dpToPx,
                layoutTab.getRenderY() * dpToPx, layoutTab.getScaledContentWidth() * dpToPx,
                layoutTab.getScaledContentHeight() * dpToPx, contentOffset,
                layoutTab.getStaticToViewBlend(), layoutTab.getSaturation(),
                layoutTab.getBrightness());
    }

    /**
     * Set the given sceneLayer as content along with {@link StaticTabSceneLayer}'s own.
     *
     * @param sceneLayer
     */
    // TODO(pedrosimonetti): Remove this once contextual search is moved into an overlay.
    public void setContentSceneLayer(SceneLayer sceneLayer) {
        nativeSetContentSceneLayer(mNativePtr, sceneLayer);
    }

    @Override
    protected void initializeNative() {
        if (mNativePtr == 0) {
            mNativePtr = nativeInit();
        }
        assert mNativePtr != 0;
    }

    @Override
    public void destroy() {
        super.destroy();
        mNativePtr = 0;
    }

    private native long nativeInit();
    private native void nativeUpdateTabLayer(long nativeStaticTabSceneLayer, float contentViewportX,
            float contentViewportY, float contentViewportWidth, float contentViewportHeight,
            TabContentManager tabContentManager, int id, int toolbarResourceId,
            boolean canUseLiveLayer, int backgroundColor, float x, float y, float width,
            float height, float contentOffsetY, float staticToViewBlend, float saturation,
            float brightness);
    private native void nativeSetContentSceneLayer(
            long nativeStaticTabSceneLayer, SceneLayer sceneLayer);
}
