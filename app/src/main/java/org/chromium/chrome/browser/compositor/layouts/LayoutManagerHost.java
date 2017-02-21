// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.content.Context;
import android.view.View;

import org.chromium.chrome.browser.compositor.TitleCache;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.content.browser.ContentViewCore;

/**
 * This is the minimal interface of the host view from the layout side.
 * Any of these functions may be called on the GL thread.
 */
public interface LayoutManagerHost {
    /**
     * If set to true, the time it takes for ContentView to become ready will be
     * logged to the screen.
     */
    static final boolean LOG_CHROME_VIEW_SHOW_TIME = false;

    /**
     * Requests a refresh of the visuals.
     */
    void requestRender();

    /**
     * @return The Android context of the host view.
     */
    Context getContext();

    /**
     * @see View#getWidth()
     * @return The width of the host view.
     */
    int getWidth();

    /**
     * @see View#getHeight()
     * @return The height of the host view.
     */
    int getHeight();

    /**
     * @return The associated {@link LayoutRenderHost} to be used from the GL Thread.
     */
    LayoutRenderHost getLayoutRenderHost();

    /**
     * Sets the visibility of the content overlays.
     * @param show True if the content overlays should be shown.
     */
    void setContentOverlayVisibility(boolean show);

    /**
     * @return The {@link TitleCache} to use to store title bitmaps.
     */
    TitleCache getTitleCache();

    /**
     * @return The manager in charge of handling fullscreen changes.
     */
    ChromeFullscreenManager getFullscreenManager();

    /**
     * Called when a new {@link ContentViewCore} has been added to the list of current visible
     * {@link ContentViewCore}s.  While this {@link ContentViewCore} might not be drawing its
     * contents at this time, it needs to be sized appropriately.
     * @param content The {@link ContentViewCore} that was added to the current list of visible
     *                {@link ContentViewCore}s.
     */
    void onContentViewCoreAdded(ContentViewCore content);

    /**
     * Called when the currently visible content has been changed.
     */
    void onContentChanged();

    /**
     * Hides the the keyboard if it was opened for the ContentView.
     * @param postHideTask A task to run after the keyboard is done hiding and the view's
     *         layout has been updated.  If the keyboard was not shown, the task will run
     *         immediately.
     */
    void hideKeyboard(Runnable postHideTask);
}
