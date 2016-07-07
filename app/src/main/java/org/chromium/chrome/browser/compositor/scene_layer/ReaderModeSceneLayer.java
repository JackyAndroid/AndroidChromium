// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.scene_layer;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.dom_distiller.ReaderModePanel;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.resources.ResourceManager;

/**
 * A SceneLayer to render layers for ReaderModeLayout.
 */
@JNINamespace("chrome::android")
public class ReaderModeSceneLayer extends SceneLayer {

    // NOTE: If you use SceneLayer's native pointer here, the JNI generator will try to
    // downcast using reinterpret_cast<>. We keep a separate pointer to avoid it.
    private long mNativePtr;

    private final float mDpToPx;

    public ReaderModeSceneLayer(float dpToPx) {
        mDpToPx = dpToPx;
    }

    /**
     * Update reader mode's layer tree using the parameters.
     *
     * @param panel
     * @param resourceManager
     */
    public void update(ReaderModePanel panel, ResourceManager resourceManager) {
        if (panel == null) return;
        if (!panel.isShowing()) return;

        nativeUpdateReaderModeLayer(mNativePtr,
                R.drawable.reader_mode_bar_background, R.id.reader_mode_view,
                panel.didFirstNonEmptyDistilledPaint() ? panel.getDistilledContentViewCore() : null,
                panel.getPanelY() * mDpToPx,
                panel.getWidth() * mDpToPx,
                panel.getMarginTop() * mDpToPx,
                panel.getPanelHeight() * mDpToPx,
                panel.getDistilledContentY() * mDpToPx,
                panel.getDistilledHeight() * mDpToPx,
                panel.getX() * mDpToPx,
                panel.getTextOpacity(),
                panel.getReaderModeHeaderBackgroundColor(),
                resourceManager);
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
        mNativePtr = 0;
    }

    private native long nativeInit();
    private native void nativeUpdateReaderModeLayer(long nativeReaderModeSceneLayer,
            int panelBackgroundResourceId, int panelTextResourceId,
            ContentViewCore readerModeContentViewCore,
            float panelY, float panelWidth, float panelMarginTop, float panelHeight,
            float distilledY, float distilledHeight,
            float x,
            float panelTextOpacity, int headerBackgroundColor,
            ResourceManager resourceManager);
}
