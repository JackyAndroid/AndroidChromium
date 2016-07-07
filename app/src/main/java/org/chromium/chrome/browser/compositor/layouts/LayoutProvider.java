// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.graphics.Rect;
import android.graphics.RectF;

import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.ui.resources.ResourceManager;

/**
 * Interface that give access the active layout. This is useful to isolate the renderer of
 * all the layout logic.
 * Called from the GL thread.
 */
public interface LayoutProvider {
    /**
     * @return The layout to be rendered. The caller may not keep a reference of that value
     * internally because the value may change without notice.
     */
    Layout getActiveLayout();

    /**
     * @param rect RectF instance to be used to store the result and return. If null, it uses a new
     *             RectF instance.
     * @return The rectangle of the layout in its View in dp.
     */
    RectF getViewportDp(RectF rect);

    /**
     * @param rect Rect instance to be used to store the result and return. If null, it uses a new
     *             Rect instance.
     * @return The rectangle of the layout in its View in pixels.
     */
    Rect getViewportPixel(Rect rect);

    /**
     * @return The manager in charge of handling fullscreen changes.
     */
    ChromeFullscreenManager getFullscreenManager();

   /**
     * Build a {@link SceneLayer} for the active layout if it hasn't already been built, and update
     * it and return it.
     *
     * @param contentViewport   A viewport in which to display content.
     * @param layerTitleCache   A layer title cache.
     * @param tabContentManager A tab content manager.
     * @param resourceManager   A resource manager.
     * @param fullscreenManager A fullscreen manager.
     * @return                  A {@link SceneLayer} that represents the content for this
     *                          {@link Layout}.
     */
    SceneLayer getUpdatedActiveSceneLayer(Rect viewport, Rect contentViewport,
            LayerTitleCache layerTitleCache, TabContentManager tabContentManager,
            ResourceManager resourceManager, ChromeFullscreenManager fullscreenManager);
}
