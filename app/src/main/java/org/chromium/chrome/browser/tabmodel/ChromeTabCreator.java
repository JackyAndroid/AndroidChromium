// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import android.content.Intent;
import android.text.TextUtils;

import org.chromium.base.SysUtils;
import org.chromium.base.TraceEvent;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.AsyncTabCreationParams;
import org.chromium.chrome.browser.tabmodel.document.AsyncTabCreationParamsManager;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.components.service_tab_launcher.ServiceTabLauncher;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;

/**
 * This class creates various kinds of new tabs and adds them to the right {@link TabModel}.
 */
public class ChromeTabCreator extends TabCreatorManager.TabCreator {

    private final ChromeActivity mActivity;
    private final WindowAndroid mNativeWindow;
    private final TabModelOrderController mOrderController;
    private final TabPersistentStore mTabSaver;
    private final boolean mIncognito;

    private TabModel mTabModel;
    private TabContentManager mTabContentManager;

    public ChromeTabCreator(ChromeActivity activity, WindowAndroid nativeWindow,
            TabModelOrderController orderController, TabPersistentStore tabSaver,
            boolean incognito) {
        mActivity = activity;
        mNativeWindow = nativeWindow;
        mOrderController = orderController;
        mTabSaver = tabSaver;
        mIncognito = incognito;
    }

    @Override
    public boolean createsTabsAsynchronously() {
        return false;
    }

    /**
     * Creates a new tab and posts to UI.
     * @param loadUrlParams parameters of the url load.
     * @param type Information about how the tab was launched.
     * @param parent the parent tab, if present.
     * @return The new tab.
     */
    @Override
    public Tab createNewTab(LoadUrlParams loadUrlParams, TabModel.TabLaunchType type,
            Tab parent) {
        return createNewTab(loadUrlParams, type, parent, null);
    }

    /**
     * Creates a new tab and posts to UI.
     * @param loadUrlParams parameters of the url load.
     * @param type Information about how the tab was launched.
     * @param parent the parent tab, if present.
     * @param intent the source of the url if it isn't null.
     * @return The new tab.
     */
    private Tab createNewTab(LoadUrlParams loadUrlParams, TabModel.TabLaunchType type,
            Tab parent, Intent intent) {
        // If parent is in the same tab model, place the new tab next to it.
        int position = TabModel.INVALID_TAB_INDEX;
        int index = mTabModel.indexOf(parent);
        if (index != TabModel.INVALID_TAB_INDEX) position = index + 1;

        return createNewTab(loadUrlParams, type, parent, position, intent);
    }

    /**
     * Creates a new tab and posts to UI.
     * @param loadUrlParams parameters of the url load.
     * @param type Information about how the tab was launched.
     * @param parent the parent tab, if present.
     * @param position the requested position (index in the tab model)
     * @param intent the source of the url if it isn't null.
     * @return The new tab.
     */
    private Tab createNewTab(LoadUrlParams loadUrlParams, TabModel.TabLaunchType type,
            Tab parent, int position, Intent intent) {
        try {
            TraceEvent.begin("ChromeTabCreator.createNewTab");
            int parentId = parent != null ? parent.getId() : Tab.INVALID_TAB_ID;

            // Sanitize the url.
            loadUrlParams.setUrl(UrlUtilities.fixupUrl(loadUrlParams.getUrl()));
            loadUrlParams.setTransitionType(getTransitionType(type));

            // Check if the tab is being created asynchronously.
            int assignedTabId = intent == null ? Tab.INVALID_TAB_ID : IntentUtils.safeGetIntExtra(
                    intent, IntentHandler.EXTRA_TAB_ID, Tab.INVALID_TAB_ID);
            AsyncTabCreationParams asyncParams =
                    AsyncTabCreationParamsManager.remove(assignedTabId);
            WebContents webContents = asyncParams == null ? null : asyncParams.getWebContents();

            boolean openInForeground = mOrderController.willOpenInForeground(type, mIncognito)
                    || webContents != null;
            TabDelegateFactory delegateFactory = new TabDelegateFactory();
            Tab tab;
            if (webContents != null) {
                // A WebContents was passed through the Intent.  Create a new Tab to hold it.
                Intent parentIntent = IntentUtils.safeGetParcelableExtra(
                        intent, IntentHandler.EXTRA_PARENT_INTENT);
                parentId = IntentUtils.safeGetIntExtra(
                        intent, IntentHandler.EXTRA_PARENT_TAB_ID, parentId);

                assert TabModelUtils.getTabIndexById(mTabModel, assignedTabId)
                        == TabModel.INVALID_TAB_INDEX;
                tab = Tab.createLiveTab(assignedTabId, mActivity, mIncognito,
                        mNativeWindow, type, parentId, !openInForeground);
                tab.initialize(webContents, mTabContentManager, delegateFactory, !openInForeground);
                tab.setParentIntent(parentIntent);
                webContents.resumeLoadingCreatedWebContents();
            } else if (!openInForeground && SysUtils.isLowEndDevice()) {
                // On low memory devices the tabs opened in background are not loaded automatically
                // to preserve resources (cpu, memory, strong renderer binding) for the foreground
                // tab.
                // TODO(dfalcantara): Fallback Tabs created when the TabState couldn't be restored
                //                    on startup should go through this path, as well, but there's
                //                    currently no way to pipe that information to this function.
                tab = Tab.createTabForLazyLoad(mActivity, mIncognito, mNativeWindow, type,
                        parentId, loadUrlParams);
                tab.initialize(null, mTabContentManager, delegateFactory, !openInForeground);
                mTabSaver.addTabToSaveQueue(tab);
            } else {
                webContents =
                        WarmupManager.getInstance().hasPrerenderedUrl(loadUrlParams.getUrl())
                        ? WarmupManager.getInstance().takePrerenderedWebContents() : null;

                tab = Tab.createLiveTab(Tab.INVALID_TAB_ID, mActivity, mIncognito,
                        mNativeWindow, type, parentId, !openInForeground);
                tab.initialize(webContents, mTabContentManager, delegateFactory, !openInForeground);
                tab.loadUrl(loadUrlParams);
            }
            tab.getTabRedirectHandler().updateIntent(intent);

            if (intent != null && intent.hasExtra(ServiceTabLauncher.LAUNCH_REQUEST_ID_EXTRA)) {
                ServiceTabLauncher.onWebContentsForRequestAvailable(
                        intent.getIntExtra(ServiceTabLauncher.LAUNCH_REQUEST_ID_EXTRA, 0),
                        tab.getWebContents());
            }

            mTabModel.addTab(tab, position, type);
            return tab;
        } finally {
            TraceEvent.end("ChromeTabCreator.createNewTab");
        }
    }

    @Override
    public boolean createTabWithWebContents(WebContents webContents, int parentId,
            TabLaunchType type, String url) {
        // The parent tab was already closed.  Do not open child tabs.
        if (mTabModel.isClosurePending(parentId)) return false;

        // If parent is in the same tab model, place the new tab next to it.
        int position = TabModel.INVALID_TAB_INDEX;
        int index = TabModelUtils.getTabIndexById(mTabModel, parentId);
        if (index != TabModel.INVALID_TAB_INDEX) position = index + 1;

        boolean openInForeground = mOrderController.willOpenInForeground(type, mIncognito);
        Tab tab = Tab.createLiveTab(Tab.INVALID_TAB_ID, mActivity, mIncognito,
                mNativeWindow, type, parentId, !openInForeground);
        tab.initialize(
                webContents, mTabContentManager, new TabDelegateFactory(), !openInForeground);
        mTabModel.addTab(tab, position, type);
        return true;
    }

    @Override
    public Tab launchUrl(String url, TabModel.TabLaunchType type) {
        return launchUrl(url, type, null, 0);
    }

    /**
     * Creates a new tab and loads the specified URL in it. This is a convenience method for
     * {@link #createNewTab} with the default {@link LoadUrlParams} and no parent tab.
     *
     * @param url the URL to open.
     * @param type the type of action that triggered that launch. Determines how the tab is opened
     *             (for example, in the foreground or background).
     * @param intent the source of url if it isn't null.
     * @param intentTimestamp the time the intent was received.
     * @return the created tab.
     */
    public Tab launchUrl(String url, TabModel.TabLaunchType type, Intent intent,
            long intentTimestamp) {
        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setIntentReceivedTimestamp(intentTimestamp);
        return createNewTab(loadUrlParams, type, null, intent);
    }

    /**
     * Opens the specified URL into a tab, potentially reusing a tab. Typically if a user opens
     * several link from the same application, we reuse the same tab so as to not open too many
     * tabs.
     * @param url the URL to open
     * @param referer The referer url if provided, null otherwise.
     * @param headers HTTP headers to send alongside the URL.
     * @param appId the ID of the application that triggered that URL navigation.
     * @param forceNewTab whether the URL should be opened in a new tab. If false, an existing tab
     *                    already opened by the same app will be reused.
     * @param intent the source of url if it isn't null.
     * @param intentTimestamp the time the intent was received.
     * @return the tab the URL was opened in, could be a new tab or a reused one.
     */
    public Tab launchUrlFromExternalApp(String url, String referer, String headers,
            String appId, boolean forceNewTab, Intent intent, long intentTimestamp) {
        assert !mIncognito;
        boolean isLaunchedFromChrome = TextUtils.equals(appId, mActivity.getPackageName());
        if (forceNewTab && !isLaunchedFromChrome) {
            // We don't associate the tab with that app ID, as it is assumed that if the
            // application wanted to open this tab as a new tab, it probably does not want it
            // reused either.
            LoadUrlParams loadUrlParams = new LoadUrlParams(url);
            loadUrlParams.setIntentReceivedTimestamp(intentTimestamp);
            loadUrlParams.setVerbatimHeaders(headers);
            if (referer != null) {
                loadUrlParams.setReferrer(new Referrer(referer, Referrer.REFERRER_POLICY_DEFAULT));
            }
            return createNewTab(loadUrlParams, TabLaunchType.FROM_EXTERNAL_APP, null, intent);
        }

        if (appId == null) {
            // If we have no application ID, we use a made-up one so that these tabs can be
            // reused.
            appId = TabModelImpl.UNKNOWN_APP_ID;
        }
        // Let's try to find an existing tab that was started by that app.
        for (int i = 0; i < mTabModel.getCount(); i++) {
            Tab tab = mTabModel.getTabAt(i);
            if (appId.equals(tab.getAppAssociatedWith())) {
                // We don't reuse the tab, we create a new one at the same index instead.
                // Reusing a tab would require clearing the navigation history and clearing the
                // contents (we would not want the previous content to show).
                LoadUrlParams loadUrlParams = new LoadUrlParams(url);
                loadUrlParams.setIntentReceivedTimestamp(intentTimestamp);
                Tab newTab = createNewTab(
                        loadUrlParams, TabLaunchType.FROM_EXTERNAL_APP, null, i, intent);
                newTab.setAppAssociatedWith(appId);
                mTabModel.closeTab(tab, false, false, false);
                return newTab;
            }
        }

        // No tab for that app, we'll have to create a new one.
        Tab tab = launchUrl(url, TabLaunchType.FROM_EXTERNAL_APP, intent, intentTimestamp);
        tab.setAppAssociatedWith(appId);
        return tab;
    }

    @Override
    public Tab createFrozenTab(TabState state, int id, int index) {
        Tab tab = Tab.createFrozenTabFromState(
                id, mActivity, state.isIncognito(), mNativeWindow, state.parentId, state);
        boolean selectTab = mOrderController.willOpenInForeground(TabLaunchType.FROM_RESTORE,
                state.isIncognito());
        tab.initialize(null, mTabContentManager, new TabDelegateFactory(), !selectTab);
        assert state.isIncognito() == mIncognito;
        mTabModel.addTab(tab, index, TabLaunchType.FROM_RESTORE);
        return tab;
    }

    /**
     * @param type Type of the tab launch.
     * @return The page transition type constant.
     */
    private static int getTransitionType(TabLaunchType type) {
        switch (type) {
            case FROM_LINK:
            case FROM_EXTERNAL_APP:
                return PageTransition.LINK | PageTransition.FROM_API;
            case FROM_MENU_OR_OVERVIEW:
            case FROM_LONGPRESS_FOREGROUND:
            case FROM_LONGPRESS_BACKGROUND:
            case FROM_KEYBOARD:
                return PageTransition.AUTO_TOPLEVEL;
            default:
                assert false;
                return PageTransition.LINK;
        }
    }

    /**
     * Sets the tab model and tab content manager to use.
     * @param model   The new {@link TabModel} to use.
     * @param manager The new {@link TabContentManager} to use.
     */
    public void setTabModel(TabModel model, TabContentManager manager) {
        mTabModel = model;
        mTabContentManager = manager;
    }

}
