// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.content.Intent;

import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.document.DocumentMetricIds;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;

/**
 * Data that will be used later when a Tab is opened via an intent. Often only the necessary
 * subset of the data will be set. All data is removed once the Tab finishes initializing.
 */
public class AsyncTabCreationParams {
    /** Parameters used for opening a URL in the new Tab. */
    private final LoadUrlParams mLoadUrlParams;

    /** The original intent. Set only by the {@link ChromeLauncherActivity}. */
    private final Intent mOriginalIntent;

    /** WebContents object to initialize the Tab with. Set only by the {@link TabDelegate}. */
    private final WebContents mWebContents;

    /** The tab launch request ID from the {@link ChromeServiceTabLauncher}. **/
    private final Integer mRequestId;

    /** How to start a {@link DocumentTab}. */
    private int mDocumentLaunchMode = ChromeLauncherActivity.LAUNCH_MODE_FOREGROUND;

    /** What caused a {@link DocumentTab} to be created. */
    private int mDocumentStartedBy = DocumentMetricIds.STARTED_BY_UNKNOWN;

    /** Whether or not the {@link WebContents} should be initially hidden. */
    private boolean mIsInitiallyHidden;

    /** Create parameters for creating a Tab asynchronously. */
    public AsyncTabCreationParams(LoadUrlParams loadUrlParams) {
        this(loadUrlParams, null, null, null);
    }

    /** Called by {@see ChromeLauncherActivity} when clobbering DocumentTabs. */
    public AsyncTabCreationParams(LoadUrlParams loadUrlParams, Intent originalIntent) {
        this(loadUrlParams, originalIntent, null, null);
        assert originalIntent != null;
    }

    /** Called by {@see TabDelegate} for creating new a Tab with a pre-existing WebContents. */
    public AsyncTabCreationParams(LoadUrlParams loadUrlParams, WebContents webContents) {
        this(loadUrlParams, null, webContents, null);
        assert webContents != null;
    }

    /** Called by {@see ChromeServiceTabLauncher} to create tabs via service workers. */
    public AsyncTabCreationParams(LoadUrlParams loadUrlParams, Integer requestId) {
        this(loadUrlParams, null, null, requestId);
        assert requestId != null;
    }

    public void setDocumentLaunchMode(int launchMode) {
        mDocumentLaunchMode = launchMode;
    }

    public void setDocumentStartedBy(int startedBy) {
        mDocumentStartedBy = startedBy;
    }

    public int getDocumentLaunchMode() {
        return mDocumentLaunchMode;
    }

    public int getDocumentStartedBy() {
        return mDocumentStartedBy;
    }

    public LoadUrlParams getLoadUrlParams() {
        return mLoadUrlParams;
    }

    public Intent getOriginalIntent() {
        return mOriginalIntent;
    }

    public Integer getRequestId() {
        return mRequestId;
    }

    public WebContents getWebContents() {
        return mWebContents;
    }

    public void setIsInitiallyHidden(boolean initiallyHidden) {
        mIsInitiallyHidden = initiallyHidden;
    }

    public boolean isInitiallyHidden() {
        return mIsInitiallyHidden;
    }

    private AsyncTabCreationParams(LoadUrlParams loadUrlParams, Intent originalIntent,
            WebContents webContents, Integer requestId) {
        assert loadUrlParams != null;

        // These parameters are set in very, very specific and exclusive circumstances.
        if (originalIntent != null) assert webContents == null && requestId == null;
        if (webContents != null) assert originalIntent == null && requestId == null;
        if (requestId != null) assert originalIntent == null && webContents == null;

        mLoadUrlParams = loadUrlParams;
        mRequestId = requestId;
        mWebContents = webContents;
        mOriginalIntent = originalIntent;
    }
}