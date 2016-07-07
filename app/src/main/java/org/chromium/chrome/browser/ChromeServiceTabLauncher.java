// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;

import org.chromium.chrome.browser.document.DocumentMetricIds;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.AsyncTabCreationParams;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.components.service_tab_launcher.ServiceTabLauncher;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.PageTransition;

/**
 * Service Tab Launcher implementation for Chrome. Provides the ability for Android Services
 * running in Chrome to launch tabs, without having access to an activity.
 *
 * This class is referred to from the ServiceTabLauncher implementation in Chromium using a
 * meta-data value in the Android manifest file. The ServiceTabLauncher class has more
 * documentation about why this is necessary.
 *
 * TODO(peter): after upstreaming, merge this with ServiceTabLauncher and remove reflection calls
 *              in ServiceTabLauncher.
 */
public class ChromeServiceTabLauncher extends ServiceTabLauncher {
    @Override
    public void launchTab(Context context, int requestId, boolean incognito, String url,
                          int disposition, String referrerUrl, int referrerPolicy,
                          String extraHeaders, byte[] postData) {
        // TODO(peter): Determine the intent source based on the |disposition| with which the
        // tab is being launched. Right now this is gated by a check in the native implementation.
        int intentSource = DocumentMetricIds.STARTED_BY_WINDOW_OPEN;

        LoadUrlParams loadUrlParams = new LoadUrlParams(url, PageTransition.LINK);
        loadUrlParams.setPostData(postData);
        loadUrlParams.setVerbatimHeaders(extraHeaders);
        loadUrlParams.setReferrer(new Referrer(referrerUrl, referrerPolicy));

        AsyncTabCreationParams asyncParams = new AsyncTabCreationParams(loadUrlParams, requestId);
        asyncParams.setDocumentStartedBy(intentSource);

        TabDelegate tabDelegate = new TabDelegate(incognito);
        tabDelegate.createNewTab(
                asyncParams, TabLaunchType.FROM_MENU_OR_OVERVIEW, Tab.INVALID_TAB_ID);
    }
}
