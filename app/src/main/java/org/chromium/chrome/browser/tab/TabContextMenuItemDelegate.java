// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.Intent;
import android.net.Uri;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.contextmenu.ContextMenuItemDelegate;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.Clipboard;
import org.chromium.ui.base.PageTransition;

import java.net.URI;
import java.util.Locale;

/**
 * A default {@link ContextMenuItemDelegate} that supports the context menu functionality in Tab.
 */
public class TabContextMenuItemDelegate implements ContextMenuItemDelegate {
    private final Clipboard mClipboard;
    private final Tab mTab;
    private final ChromeActivity mActivity;

    /**
     * Builds a {@link TabContextMenuItemDelegate} instance.
     */
    public TabContextMenuItemDelegate(Tab tab, ChromeActivity activity) {
        mTab = tab;
        mActivity = activity;
        mClipboard = new Clipboard(mTab.getApplicationContext());
    }

    @Override
    public boolean isIncognito() {
        return mTab.isIncognito();
    }

    @Override
    public boolean isIncognitoSupported() {
        return PrefServiceBridge.getInstance().isIncognitoModeEnabled();
    }

    @Override
    public boolean isDataReductionProxyEnabledForURL(String url) {
        return isSpdyProxyEnabledForUrl(url);
    }

    @Override
    public boolean startDownload(String url, boolean isLink) {
        return !isLink || !mTab.shouldInterceptContextMenuDownload(url);
    }

    @Override
    public void onSaveToClipboard(String text, int clipboardType) {
        mClipboard.setText(text, text);
    }

    @Override
    public void onOpenInNewTab(String url, Referrer referrer) {
        RecordUserAction.record("MobileNewTabOpened");
        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setReferrer(referrer);
        mActivity.getTabModelSelector().openNewTab(loadUrlParams,
                TabLaunchType.FROM_LONGPRESS_BACKGROUND, mTab, isIncognito());
    }

    @Override
    public void onReloadDisableLoFi() {
        mTab.reloadDisableLoFi();
    }

    @Override
    public void onLoadOriginalImage() {
        mTab.loadOriginalImage();
    }

    @Override
    public void onOpenInNewIncognitoTab(String url) {
        RecordUserAction.record("MobileNewTabOpened");
        mActivity.getTabModelSelector().openNewTab(new LoadUrlParams(url),
                TabLaunchType.FROM_LONGPRESS_FOREGROUND, mTab, true);
    }

    @Override
    public String getPageUrl() {
        return mTab.getUrl();
    }

    @Override
    public void onOpenImageUrl(String url, Referrer referrer) {
        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setTransitionType(PageTransition.LINK);
        loadUrlParams.setReferrer(referrer);
        mTab.loadUrl(loadUrlParams);
    }

    @Override
    public void onOpenInChrome(String linkUrl, String pageUrl) {
        Intent chromeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl));
        chromeIntent.setPackage(mActivity.getPackageName());
        chromeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        boolean activityStarted = false;
        if (pageUrl != null) {
            try {
                URI pageUri = URI.create(pageUrl);
                if (UrlUtilities.isInternalScheme(pageUri)) {
                    IntentHandler.startChromeLauncherActivityForTrustedIntent(
                            chromeIntent, mActivity);
                    activityStarted = true;
                }
            } catch (IllegalArgumentException ex) {
                // Ignore the exception for creating the URI and launch the intent
                // without the trusted intent extras.
            }
        }

        if (!activityStarted) {
            mActivity.startActivity(chromeIntent);
            activityStarted = true;
        }
    }

    /**
     * Checks if spdy proxy is enabled for input url.
     * @param url Input url to check for spdy setting.
     * @return true if url is enabled for spdy proxy.
    */
    private boolean isSpdyProxyEnabledForUrl(String url) {
        if (DataReductionProxySettings.getInstance().isDataReductionProxyEnabled()
                && url != null && !url.toLowerCase(Locale.US).startsWith("https://")
                && !isIncognito()) {
            return true;
        }
        return false;
    }
}
