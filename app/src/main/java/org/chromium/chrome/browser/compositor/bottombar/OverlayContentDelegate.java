// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar;

import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler;
import org.chromium.components.navigation_interception.NavigationParams;
import org.chromium.content.browser.ContentViewCore;

/**
 * An base class for tracking events on the overlay panel.
 * TODO(mdjones): Rename to OverlayPanelContentDelegate.
 */
public class OverlayContentDelegate {

    /**
     * Called when the panel's ContentViewCore navigates in the main frame.
     * @param url The URL being navigated to.
     * @param isExternalUrl Whether the URL is different from the initially loaded URL.
     */
    public void onMainFrameLoadStarted(String url, boolean isExternalUrl) {}

    /**
     * Called when a page navigation results in an error page.
     * @param url The URL that caused the failure.
     * @param isExternalUrl Whether the URL is different from the initially loaded URL.
     * @param isFailure Whether the loaded page is an error page.
     */
    public void onMainFrameNavigation(String url, boolean isExternalUrl, boolean isFailure) {}

    /**
     * Called when content started loading in the panel.
     * @param url The URL that is loading.
     */
    public void onContentLoadStarted(String url) {}

    /**
     * Called when the panel content has finished loading.
     */
    public void onContentLoadFinished() {}

    /**
     * Called when the navigation entry has been committed.
     */
    public void onNavigationEntryCommitted() {}

    /**
     * Determine if a particular navigation should be intercepted.
     * @param externalNavHandler External navigation handler for the activity the panel is in.
     * @param navigationParams The navigation params for the current navigation.
     * @return True if the navigation should be intercepted.
     */
    public boolean shouldInterceptNavigation(ExternalNavigationHandler externalNavHandler,
            NavigationParams navigationParams) {
        return true;
    }

    /**
     * Allows the delegate to intercept the loading of a URL.
     * If the loading is intercepted, the OverlayPanelContent will not load the URL when
     * {@link OverlayPanelContent#loadUrl} is called. Instead, it is up to the delegate to load it.
     * This allows, for example, passing custom HTTP headers when loading a URL.
     * @param overlayContentViewCore The Overlay.
     * @param url The URL to load.
     * @return Whether the load has been intercepted.
     */
    public boolean handleInterceptLoadUrl(ContentViewCore overlayContentViewCore, String url) {
        return false;
    }

    // ============================================================================================
    // ContentViewCore related events.
    // ============================================================================================

    /**
     * Called then the content visibility is changed.
     * @param isVisible True if the content is visible.
     */
    public void onVisibilityChanged(boolean isVisible) {}

    /**
     * Called once the ContentViewCore has been seen.
     */
    public void onContentViewSeen() {}

    /**
     * Called once the ContentViewCore has been created and set up completely.
     * @param contentViewCore The contentViewCore that was created.
     */
    public void onContentViewCreated(ContentViewCore contentViewCore) {}

    /**
     * Called once the ContentViewCore has been destroyed.
     */
    public void onContentViewDestroyed() {}
}
