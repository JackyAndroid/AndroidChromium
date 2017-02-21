// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.scene_layer;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.resources.ResourceManager;

/**
 * A SceneLayer to render layers for Reader Mode.
 */
@JNINamespace("android")
public class ReaderModeSceneLayer extends SceneOverlayLayer {
    /** Pointer to native ReaderModeSceneLayer. */
    private long mNativePtr;

    /** If the scene layer has been initialized. */
    private boolean mIsInitialized;

    /** The conversion multiple from dp to px. */
    private final float mDpToPx;

    /**
     * @param dpToPx The conversion multiple from dp to px for the device.
     */
    public ReaderModeSceneLayer(float dpToPx) {
        mDpToPx = dpToPx;
    }

    /**
     * Update the scene layer to draw an OverlayPanel.
     * @param resourceManager Manager to get view and image resources.
     * @param panel The OverlayPanel to render.
     * @param barTextViewId The ID of the view containing the Reader Mode text.
     * @param barTextOpacity The opacity of the text specified by {@code barTextViewId}.
     */
    public void update(ResourceManager resourceManager, OverlayPanel panel, int barTextViewId,
            float barTextOpacity) {
        // Don't try to update the layer if not initialized or showing.
        if (resourceManager == null || !panel.isShowing()) return;

        if (!mIsInitialized) {
            nativeCreateReaderModeLayer(mNativePtr, resourceManager);
            // TODO(mdjones): Rename contextual search resources below to be generic to overlay
            // panels.
            nativeSetResourceIds(mNativePtr,
                    barTextViewId,
                    R.drawable.contextual_search_bar_background,
                    R.drawable.contextual_search_bar_shadow,
                    R.drawable.infobar_mobile_friendly,
                    R.drawable.btn_close);
            mIsInitialized = true;
        }

        WebContents panelWebContents = panel.getContentViewCore() != null
                ? panel.getContentViewCore().getWebContents() : null;

        nativeUpdate(mNativePtr,
                mDpToPx,
                panel.getBasePageBrightness(),
                panel.getBasePageY() * mDpToPx,
                panelWebContents,
                panel.getOffsetX() * mDpToPx,
                panel.getOffsetY() * mDpToPx,
                panel.getWidth() * mDpToPx,
                panel.getHeight() * mDpToPx,
                panel.getBarMarginSide() * mDpToPx,
                panel.getBarHeight() * mDpToPx,
                barTextOpacity,
                panel.isBarBorderVisible(),
                panel.getBarBorderHeight() * mDpToPx,
                panel.getBarShadowVisible(),
                panel.getBarShadowOpacity());
    }

    @Override
    public void setContentTree(SceneLayer contentTree) {
        nativeSetContentTree(mNativePtr, contentTree);
    }

    /**
     * Hide the layer tree; for use if the panel is not being shown.
     */
    public void hideTree() {
        if (!mIsInitialized) return;
        nativeHideTree(mNativePtr);
    }

    @Override
    protected void initializeNative() {
        if (mNativePtr == 0) {
            mNativePtr = nativeInit();
        }
        assert mNativePtr != 0;
    }

    /**
     * Destroys this object and the corresponding native component.
     */
    @Override
    public void destroy() {
        super.destroy();
        mIsInitialized = false;
        mNativePtr = 0;
    }

    private native long nativeInit();
    private native void nativeCreateReaderModeLayer(
            long nativeReaderModeSceneLayer,
            ResourceManager resourceManager);
    private native void nativeSetContentTree(
            long nativeReaderModeSceneLayer,
            SceneLayer contentTree);
    private native void nativeHideTree(
            long nativeReaderModeSceneLayer);
    private native void nativeSetResourceIds(
            long nativeReaderModeSceneLayer,
            int barTextResourceId,
            int barBackgroundResourceId,
            int barShadowResourceId,
            int panelIconResourceId,
            int closeIconResourceId);
    private native void nativeUpdate(
            long nativeReaderModeSceneLayer,
            float dpToPx,
            float basePageBrightness,
            float basePageYOffset,
            WebContents webContents,
            float panelX,
            float panelY,
            float panelWidth,
            float panelHeight,
            float barMarginSide,
            float barHeight,
            float textOpacity,
            boolean barBorderVisible,
            float barBorderHeight,
            boolean barShadowVisible,
            float barShadowOpacity);
}
