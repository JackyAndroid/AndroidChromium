// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.overlays;

import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.scene_layer.SceneOverlayLayer;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.ui.resources.ResourceManager;

import java.util.List;

/**
 * An interface which positions the actual tabs and adds additional UI to the them.
 */
public interface SceneOverlay {
    /**
     * Updates and gets a {@link SceneOverlayLayer} that represents an scene overlay.
     *
     * @param layerTitleCache A layer title cache.
     * @param resourceManager A resource manager.
     * @param yOffset Current top controls offset in dp.
     * @return A {@link SceneOverlayLayer} that represents an scene overlay.
     * Or {@code null} if this {@link SceneOverlay} doesn't have a tree.
     */
    SceneOverlayLayer getUpdatedSceneOverlayTree(LayerTitleCache layerTitleCache,
            ResourceManager resourceManager, float yOffset);

    /**
     * @return The {@link EventFilter} that processes events for this {@link SceneOverlay}.
     */
    EventFilter getEventFilter();

    /**
     * Called when the viewport size of the screen changes.
     * @param width                  The new width of the viewport available in dp.
     * @param height                 The new height of the viewport available in dp.
     * @param visibleViewportOffsetY The visible viewport Y offset in dp.
     * @param orientation            The new orientation.
     */
    void onSizeChanged(float width, float height, float visibleViewportOffsetY, int orientation);

    /**
     * @param views A list of virtual views representing compositor rendered views.
     */
    void getVirtualViews(List<VirtualView> views);

    /**
     * Helper-specific updates. Cascades the values updated by the animations and flings.
     * @param time The current time of the app in ms.
     * @param dt   The delta time between update frames in ms.
     * @return     Whether the updating is done.
     */
    boolean updateOverlay(long time, long dt);

    /**
     * Notify the a title has changed.
     *
     * @param tabId     The id of the tab that has changed.
     * @param title     The new title.
     */
    void tabTitleChanged(int tabId, String title);

    /**
     * Called when the active {@link TabModel} switched (e.g. standard -> incognito).
     * @param incognito Whether or not the new active model is incognito.
     */
    void tabModelSwitched(boolean incognito);

    /**
     * Called when a tab get selected.
     * @param time      The current time of the app in ms.
     * @param incognito Whether or not the affected model was incognito.
     * @param id        The id of the selected tab.
     * @param prevId    The id of the previously selected tab.
     */
    void tabSelected(long time, boolean incognito, int id, int prevId);

    /**
     * Called when a tab has been moved in the tabModel.
     * @param time      The current time of the app in ms.
     * @param incognito Whether or not the affected model was incognito.
     * @param id        The id of the Tab.
     * @param oldIndex  The old index of the tab in the {@link TabModel}.
     * @param newIndex  The new index of the tab in the {@link TabModel}.
     */
    void tabMoved(long time, boolean incognito, int id, int oldIndex, int newIndex);

    /**
     * Called when a tab is being closed. When called, the closing tab will not
     * be part of the model.
     * @param time      The current time of the app in ms.
     * @param incognito Whether or not the affected model was incognito.
     * @param id        The id of the tab being closed.
     */
    void tabClosed(long time, boolean incognito, int id);

    /**
     * Called when a tab close has been undone and the tab has been restored.
     * @param time      The current time of the app in ms.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito
     */
    void tabClosureCancelled(long time, boolean incognito, int id);

    /**
     * Called when a tab is created from the top left button.
     * @param time      The current time of the app in ms.
     * @param incognito Whether or not the affected model was incognito.
     * @param id        The id of the newly created tab.
     * @param prevId    The id of the source tab.
     * @param selected  Whether the tab will be selected.
     */
    void tabCreated(long time, boolean incognito, int id, int prevId, boolean selected);

    /**
     * Called when a tab has started loading.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito.
     */
    void tabPageLoadStarted(int id, boolean incognito);

    /**
     * Called when a tab has finished loading.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito.
     */
    void tabPageLoadFinished(int id, boolean incognito);

    /**
     * Called when a tab has started loading resources.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito.
     */
    void tabLoadStarted(int id, boolean incognito);

    /**
     * Called when a tab has stopped loading resources.
     * @param id        The id of the Tab.
     * @param incognito True if the tab is incognito.
     */
    void tabLoadFinished(int id, boolean incognito);
}
