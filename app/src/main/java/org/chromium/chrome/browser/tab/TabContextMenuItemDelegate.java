// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.contextmenu.ContextMenuItemDelegate;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.util.UrlUtilities;
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
    public static final String PAGESPEED_PASSTHROUGH_HEADERS =
            "Chrome-Proxy: pass-through\nCache-Control: no-cache";

    private final Clipboard mClipboard;
    private final Tab mTab;

    /**
     * Builds a {@link TabContextMenuItemDelegate} instance.
     */
    public TabContextMenuItemDelegate(Tab tab) {
        mTab = tab;
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
    public boolean isOpenInOtherWindowSupported() {
        return MultiWindowUtils.getInstance().isOpenInOtherWindowSupported(mTab.getActivity());
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
        mClipboard.setText(text);
    }

    @Override
    public void onOpenInOtherWindow(String url, Referrer referrer) {
        TabDelegate tabDelegate = new TabDelegate(mTab.isIncognito());
        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setReferrer(referrer);
        tabDelegate.createTabInOtherWindow(loadUrlParams, mTab.getActivity(), mTab.getParentId());
    }

    @Override
    public void onOpenInNewTab(String url, Referrer referrer) {
        RecordUserAction.record("MobileNewTabOpened");
        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setReferrer(referrer);
        Tab newTab = mTab.getTabModelSelector().openNewTab(
                loadUrlParams, TabLaunchType.FROM_LONGPRESS_BACKGROUND, mTab, isIncognito());

        // {@code newTab} is null in document mode. Do not record metrics for document mode.
        if (mTab.getTabUma() != null && newTab != null) {
            mTab.getTabUma().onBackgroundTabOpenedFromContextMenu(newTab);
        }
    }

    @Override
    public void onReloadLoFiImages() {
        mTab.reloadLoFiImages();
    }

    @Override
    public void onLoadOriginalImage() {
        mTab.loadOriginalImage();
    }

    @Override
    public void onOpenInNewIncognitoTab(String url) {
        RecordUserAction.record("MobileNewTabOpened");
        mTab.getTabModelSelector().openNewTab(new LoadUrlParams(url),
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
    public void onOpenImageInNewTab(String url, Referrer referrer) {
        boolean useOriginal = isSpdyProxyEnabledForUrl(url);
        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setVerbatimHeaders(useOriginal ? PAGESPEED_PASSTHROUGH_HEADERS : null);
        loadUrlParams.setReferrer(referrer);
        mTab.getActivity().getTabModelSelector().openNewTab(loadUrlParams,
                TabLaunchType.FROM_LONGPRESS_BACKGROUND, mTab, isIncognito());
    }

    @Override
    public void onOpenInChrome(String linkUrl, String pageUrl) {
        Intent chromeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl));
        chromeIntent.setPackage(mTab.getApplicationContext().getPackageName());
        chromeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        boolean activityStarted = false;
        if (pageUrl != null) {
            try {
                URI pageUri = URI.create(pageUrl);
                if (UrlUtilities.isInternalScheme(pageUri)) {
                    IntentHandler.startChromeLauncherActivityForTrustedIntent(
                            chromeIntent, mTab.getApplicationContext());
                    activityStarted = true;
                }
            } catch (IllegalArgumentException ex) {
                // Ignore the exception for creating the URI and launch the intent
                // without the trusted intent extras.
            }
        }

        if (!activityStarted) {
            Context context = mTab.getActivity();
            if (context == null) context = mTab.getApplicationContext();
            context.startActivity(chromeIntent);
            activityStarted = true;
        }
    }

    @Override
    public void onSavePageLater(String linkUrl) {
        OfflinePageBridge.getForProfile(mTab.getProfile())
                .savePageLater(linkUrl, "async_loading", true /* userRequested */);
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
