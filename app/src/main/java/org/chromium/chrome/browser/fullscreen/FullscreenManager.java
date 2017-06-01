// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.fullscreen;

import android.view.View;
import android.view.Window;

import org.chromium.chrome.browser.fullscreen.FullscreenHtmlApiHandler.FullscreenHtmlApiDelegate;
import org.chromium.chrome.browser.tab.Tab;

/**
 * Manages the basic fullscreen functionality required by a Tab.
 */
// TODO(tedchoc): Remove Tab's requirement on the fullscreen tokens to slim down the API of this
//                class.
public abstract class FullscreenManager {
    public static final int INVALID_TOKEN = -1;

    private final FullscreenHtmlApiHandler mHtmlApiHandler;
    private boolean mOverlayVideoMode;
    private Tab mTab;

    /**
     * Constructs the basic ChromeTab oriented FullscreenManager.
     *
     * @param window Top-level window to turn to fullscreen.
     */
    public FullscreenManager(Window window) {
        mHtmlApiHandler = new FullscreenHtmlApiHandler(window, createApiDelegate());
    }

    /**
     * @return The delegate that will handle the embedder specific requirements of the
     *         fullscreen API handler.
     */
    protected abstract FullscreenHtmlApiDelegate createApiDelegate();

    /**
     * @return The handler for managing interactions with the HTML fullscreen API.
     */
    protected FullscreenHtmlApiHandler getHtmlApiHandler() {
        return mHtmlApiHandler;
    }

    /**
     * @return The height of the top controls in pixels.
     */
    public abstract int getTopControlsHeight();

    /**
     * @return The offset of the content from the top of the screen.
     */
    public abstract float getContentOffset();

    /**
     * Tells the fullscreen manager a ContentVideoView is created below the contents.
     * @param enabled Whether to enter or leave overlay video mode.
     */
    public void setOverlayVideoMode(boolean enabled) {
        mOverlayVideoMode = enabled;
    }

    /**
     * @return Check whether ContentVideoView is shown.
     */
    public boolean isOverlayVideoMode() {
        return mOverlayVideoMode;
    }

    /**
     * Updates the positions of the browser controls and content to the default non fullscreen
     * values.
     */
    public abstract void setPositionsForTabToNonFullscreen();

    /**
     * Updates the positions of the browser controls and content based on the desired position of
     * the current tab.
     *
     * @param topControlsOffset The Y offset of the top controls.
     * @param bottomControlsOffset The Y offset of the bottom controls.
     * @param topContentOffset The Y offset for the content.
     */
    public abstract void setPositionsForTab(float topControlsOffset, float bottomControlsOffset,
            float topContentOffset);

    /**
     * Updates the current ContentView's children and any popups with the correct offsets based on
     * the current fullscreen state.
     */
    public abstract void updateContentViewChildrenState();

    /**
     * Sets the currently selected tab for fullscreen.
     */
    public void setTab(Tab tab) {
        if (mTab == tab) return;

        // Remove the fullscreen manager from the old tab before setting the new tab.
        if (mTab != null) mTab.setFullscreenManager(null);

        mTab = tab;

        // Initialize the new tab with the correct fullscreen manager reference.
        if (mTab != null) mTab.setFullscreenManager(this);
    }

    /**
     * @return The currently selected tab for fullscreen.
     */
    public Tab getTab() {
        return mTab;
    }

    /**
     * Enters or exits persistent fullscreen mode.  In this mode, the browser controls will be
     * permanently hidden until this mode is exited.
     *
     * @param enabled Whether to enable persistent fullscreen mode.
     */
    public void setPersistentFullscreenMode(boolean enabled) {
        mHtmlApiHandler.setPersistentFullscreenMode(enabled);

        Tab tab = getTab();
        if (tab != null) {
            tab.updateFullscreenEnabledState();
        }
    }

    /**
     * @return Whether the application is in persistent fullscreen mode.
     * @see #setPersistentFullscreenMode(boolean)
     */
    public boolean getPersistentFullscreenMode() {
        return mHtmlApiHandler.getPersistentFullscreenMode();
    }

    /**
     * Notified when the system UI visibility for the current ContentView has changed.
     * @param visibility The updated UI visibility.
     * @see View#getSystemUiVisibility()
     */
    public void onContentViewSystemUiVisibilityChange(int visibility) {
        mHtmlApiHandler.onContentViewSystemUiVisibilityChange(visibility);
    }

    /**
     * Ensure the proper system UI flags are set after the window regains focus.
     * @see android.app.Activity#onWindowFocusChanged(boolean)
     */
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        mHtmlApiHandler.onWindowFocusChanged(hasWindowFocus);
    }

    /**
     * Called when scrolling state of the ContentView changed.
     */
    public void onContentViewScrollingStateChanged(boolean scrolling) {}
}
