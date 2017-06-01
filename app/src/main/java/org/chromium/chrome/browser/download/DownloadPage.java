// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.Activity;
import android.view.View;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BasicNativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi;
import org.chromium.chrome.browser.tab.Tab;

/**
 * Native page for managing downloads handled through Chrome.
 */
public class DownloadPage extends BasicNativePage {
    private ActivityStateListener mActivityStateListener;

    private DownloadManagerUi mManager;
    private String mTitle;

    /**
     * Create a new instance of the downloads page.
     * @param activity The activity to get context and manage fragments.
     * @param tab The tab to load urls.
     */
    public DownloadPage(Activity activity, Tab tab) {
        super(activity, tab);
    }

    @Override
    protected void initialize(Activity activity, final Tab tab) {
        ThreadUtils.assertOnUiThread();

        mManager = new DownloadManagerUi(activity, tab.isIncognito(), activity.getComponentName());
        mManager.setBasicNativePage(this);
        mTitle = activity.getString(R.string.download_manager_ui_all_downloads);

        // #destroy() unregisters the ActivityStateListener to avoid checking for externally removed
        // downloads after the downloads page is closed. This requires each DownloadPage to have its
        // own ActivityStateListener. If multiple tabs are showing the downloads page, multiple
        // requests to check for externally removed downloads will be issued when the activity is
        // resumed.
        mActivityStateListener = new ActivityStateListener() {
            @Override
            public void onActivityStateChange(Activity activity, int newState) {
                if (newState == ActivityState.RESUMED) {
                    DownloadUtils.checkForExternallyRemovedDownloads(
                            mManager.getBackendProvider(), tab.isIncognito());
                }
            }
        };
        ApplicationStatus.registerStateListenerForActivity(mActivityStateListener, activity);
    }

    @Override
    public View getView() {
        return mManager.getView();
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getHost() {
        return UrlConstants.DOWNLOADS_HOST;
    }

    @Override
    public void updateForUrl(String url) {
        super.updateForUrl(url);
        mManager.updateForUrl(url);
    }

    @Override
    public void destroy() {
        mManager.onDestroyed();
        mManager = null;
        ApplicationStatus.unregisterActivityStateListener(mActivityStateListener);
        super.destroy();
    }
}
