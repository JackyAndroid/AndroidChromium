// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.view.ContextMenu;

import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;

/**
 * An implementation of the {@link TabObserver} which has empty implementations of all methods.
 */
public class EmptyTabObserver implements TabObserver {

    @Override
    public void onShown(Tab tab) { }

    @Override
    public void onHidden(Tab tab) { }

    @Override
    public void onClosingStateChanged(Tab tab, boolean closing) { }

    @Override
    public void onDestroyed(Tab tab) { }

    @Override
    public void onContentChanged(Tab tab) { }

    @Override
    public void onOverlayContentViewCoreAdded(Tab tab, ContentViewCore content) { }

    @Override
    public void onOverlayContentViewCoreRemoved(Tab tab, ContentViewCore content) { }

    @Override
    public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) { }

    @Override
    public void onPageLoadStarted(Tab tab, String url) { }

    @Override
    public void onPageLoadFinished(Tab tab) { }

    @Override
    public void onPageLoadFailed(Tab tab, int errorCode) { }

    @Override
    public void onFaviconUpdated(Tab tab) { }

    @Override
    public void onTitleUpdated(Tab tab) { }

    @Override
    public void onUrlUpdated(Tab tab) { }

    @Override
    public void onSSLStateUpdated(Tab tab) { }

    @Override
    public void onCrash(Tab tab, boolean sadTabShown) { }

    @Override
    public void onWebContentsSwapped(Tab tab, boolean didStartLoad, boolean didFinishLoad) { }

    @Override
    public void onContextMenuShown(Tab tab, ContextMenu menu) { }

    @Override
    public void onContextualActionBarVisibilityChanged(Tab tab, boolean visible) { }

    @Override
    public void onWebContentsInstantSupportDisabled() { }

    @Override
    public void onLoadStarted(Tab tab, boolean toDifferentDocument) { }

    @Override
    public void onLoadStopped(Tab tab, boolean toDifferentDocument) { }

    @Override
    public void onLoadProgressChanged(Tab tab, int progress) { }

    @Override
    public void onUpdateUrl(Tab tab, String url) { }

    @Override
    public void onToggleFullscreenMode(Tab tab, boolean enable) { }

    @Override
    public void onDidFailLoad(Tab tab, boolean isProvisionalLoad, boolean isMainFrame,
            int errorCode, String description, String failingUrl) { }

    @Override
    public void onDidStartProvisionalLoadForFrame(Tab tab, long frameId, long parentFrameId,
            boolean isMainFrame, String validatedUrl, boolean isErrorPage,
            boolean isIframeSrcdoc) { }

    @Override
    public void onDidCommitProvisionalLoadForFrame(Tab tab, long frameId, boolean isMainFrame,
            String url, int transitionType) { }

    @Override
    public void onDidNavigateMainFrame(Tab tab, String url, String baseUrl,
            boolean isNavigationToDifferentPage, boolean isFragmentNavigation, int statusCode) { }

    @Override
    public void didFirstVisuallyNonEmptyPaint(Tab tab) { }

    @Override
    public void onDidChangeThemeColor(Tab tab, int color) { }

    @Override
    public void onDidAttachInterstitialPage(Tab tab) { }

    @Override
    public void onDidDetachInterstitialPage(Tab tab) { }

    @Override
    public void onDidStartNavigationToPendingEntry(Tab tab, String url) { }

    @Override
    public void onBackgroundColorChanged(Tab tab, int color) { }

    @Override
    public void webContentsCreated(Tab tab, WebContents sourceWebContents, long openerRenderFrameId,
            String frameName, String targetUrl, WebContents newWebContents) { }
}
