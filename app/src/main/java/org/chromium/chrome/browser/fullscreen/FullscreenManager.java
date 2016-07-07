// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.fullscreen;

import android.view.View;
import android.view.Window;

import org.chromium.chrome.browser.fullscreen.FullscreenHtmlApiHandler.FullscreenHtmlApiDelegate;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

/**
 * Manages the basic fullscreen functionality required by a Tab.
 */
// TODO(tedchoc): Remove Tab's requirement on the fullscreen tokens to slim down the API of this
//                class.
public abstract class FullscreenManager {
    public static final int INVALID_TOKEN = -1;

    private final TabModelSelector mModelSelector;
    private final FullscreenHtmlApiHandler mHtmlApiHandler;
    private boolean mOverlayVideoMode;

    /**
     * Constructs the basic ChromeTab oriented FullscreenManager.
     *
     * @param window Top-level window to turn to fullscreen.
     * @param modelSelector The model selector providing access to the current tab.
     */
    public FullscreenManager(Window window, TabModelSelector modelSelector) {
        mModelSelector = modelSelector;
        mHtmlApiHandler = new FullscreenHtmlApiHandler(window, createApiDelegate());
        mOverlayVideoMode = false;
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
     * @return The selector for accessing the current Tab.
     */
    protected TabModelSelector getTabModelSelector() {
        return mModelSelector;
    }

    /**
     * Trigger a temporary showing of the top controls.
     */
    public abstract void showControlsTransient();

    /**
     * Trigger a permanent showing of the top controls until requested otherwise.
     *
     * @return The token that determines whether the requester still needs persistent controls to
     *         be present on the screen.
     * @see #hideControlsPersistent(int)
     */
    public abstract int showControlsPersistent();

    /**
     * Same behavior as {@link #showControlsPersistent()} but also handles removing a previously
     * requested token if necessary.
     *
     * @param oldToken The old fullscreen token to be cleared.
     * @return The fullscreen token as defined in {@link #showControlsPersistent()}.
     */
    public abstract int showControlsPersistentAndClearOldToken(int oldToken);

    /**
     * Notify the manager that the top controls are no longer required for the given token.
     *
     * @param token The fullscreen token returned from {@link #showControlsPersistent()}.
     */
    public abstract void hideControlsPersistent(int token);

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
     * Updates the positions of the top controls and content to the default non fullscreen
     * values.
     */
    public abstract void setPositionsForTabToNonFullscreen();

    /**
     * Updates the positions of the top controls and content based on the desired position of
     * the current tab.
     *
     * @param controlsOffset The Y offset of the top controls.
     * @param contentOffset The Y offset for the content.
     */
    public abstract void setPositionsForTab(float controlsOffset, float contentOffset);

    /**
     * Updates the current ContentView's children and any popups with the correct offsets based on
     * the current fullscreen state.
     */
    public abstract void updateContentViewChildrenState();

    /**
     * Enters or exits persistent fullscreen mode.  In this mode, the top controls will be
     * permanently hidden until this mode is exited.
     *
     * @param enabled Whether to enable persistent fullscreen mode.
     */
    public void setPersistentFullscreenMode(boolean enabled) {
        mHtmlApiHandler.setPersistentFullscreenMode(enabled);

        Tab tab = mModelSelector.getCurrentTab();
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
