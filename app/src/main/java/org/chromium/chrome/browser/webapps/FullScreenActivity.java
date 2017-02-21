// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerDocument;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabUma.TabCreationState;
import org.chromium.chrome.browser.tabmodel.SingleTabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.widget.ControlContainer;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContentsObserver;

import java.io.File;

/**
 * Base class for task-focused activities that need to display web content in a nearly UI-less
 * Chrome (InfoBars still appear).
 *
 * This is vaguely analogous to a WebView, but in Chrome. Example applications that might use this
 * Activity would be webapps and streaming media activities - anything where user interaction with
 * the regular browser's UI is either unnecessary or undesirable.
 * Subclasses can override {@link #createUI()} if they need something more exotic.
 */
@SuppressFBWarnings("URF_UNREAD_FIELD")
public abstract class FullScreenActivity extends ChromeActivity {
    protected static final String BUNDLE_TAB_ID = "tabId";
    protected static final String BUNDLE_TAB_URL = "tabUrl";
    private static final String TAG = "FullScreenActivity";

    private Tab mTab;
    private WebContentsObserver mWebContentsObserver;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void preInflationStartup() {
        super.preInflationStartup();

        setTabCreators(createTabDelegate(false), createTabDelegate(true));
        setTabModelSelector(new SingleTabModelSelector(this, false, false) {
            @Override
            public Tab openNewTab(LoadUrlParams loadUrlParams, TabLaunchType type, Tab parent,
                    boolean incognito) {
                getTabCreator(incognito).createNewTab(loadUrlParams, type, parent);
                return null;
            }
        });
    }

    /** Creates TabDelegates for opening new Tabs. */
    protected TabDelegate createTabDelegate(boolean incognito) {
        return new TabDelegate(incognito);
    }

    @Override
    public void finishNativeInitialization() {
        mTab = createTab();
        getTabModelSelector().setTab(mTab);
        mTab.show(TabSelectionType.FROM_NEW);

        ControlContainer controlContainer = (ControlContainer) findViewById(R.id.control_container);
        initializeCompositorContent(new LayoutManagerDocument(getCompositorViewHolder()),
                (View) controlContainer, (ViewGroup) findViewById(android.R.id.content),
                controlContainer);

        getActivityTab().setFullscreenManager(getFullscreenManager());
        super.finishNativeInitialization();
    }

    @Override
    protected void initializeToolbar() { }

    @Override
    public SingleTabModelSelector getTabModelSelector() {
        return (SingleTabModelSelector) super.getTabModelSelector();
    }

    @Override
    public final Tab getActivityTab() {
        return mTab;
    }

    /**
     * Creates the {@link Tab} used by the FullScreenActivity.
     * If the {@code savedInstanceState} exists, then the user did not intentionally close the app
     * by swiping it away in the recent tasks list.  In that case, we try to restore the tab from
     * disk.
     */
    private Tab createTab() {
        Tab tab = null;
        boolean unfreeze = false;

        int tabId = Tab.INVALID_TAB_ID;
        String tabUrl = null;
        if (getSavedInstanceState() != null) {
            tabId = getSavedInstanceState().getInt(BUNDLE_TAB_ID, Tab.INVALID_TAB_ID);
            tabUrl = getSavedInstanceState().getString(BUNDLE_TAB_URL);
        }

        if (tabId != Tab.INVALID_TAB_ID && tabUrl != null && getActivityDirectory() != null) {
            // Restore the tab.
            TabState tabState = TabState.restoreTabState(getActivityDirectory(), tabId);
            tab = new Tab(tabId, Tab.INVALID_TAB_ID, false, this, getWindowAndroid(),
                    TabLaunchType.FROM_RESTORE,
                    TabCreationState.FROZEN_ON_RESTORE, tabState);
            unfreeze = true;
        }

        if (tab == null) {
            tab = new Tab(Tab.INVALID_TAB_ID, Tab.INVALID_TAB_ID, false, this, getWindowAndroid(),
                    TabLaunchType.FROM_CHROME_UI, null, null);
        }

        tab.initialize(null, getTabContentManager(), createTabDelegateFactory(), false, unfreeze);
        mWebContentsObserver = new WebContentsObserver(tab.getWebContents()) {
            @Override
            public void didCommitProvisionalLoadForFrame(
                    long frameId, boolean isMainFrame, String url, int transitionType) {
                if (isMainFrame) {
                    // Notify the renderer to permanently hide the top controls since they do
                    // not apply to fullscreen content views.
                    getActivityTab().updateTopControlsState(
                            getActivityTab().getTopControlsStateConstraints(), true);
                }
            }
        };
        return tab;
    }

    /**
     * @return {@link TabDelegateFactory} to be used while creating the associated {@link Tab}.
     */
    protected TabDelegateFactory createTabDelegateFactory() {
        return new FullScreenDelegateFactory();
    }

    /**
     * @return {@link File} pointing at a directory specific for this class.
     */
    protected File getActivityDirectory() {
        return null;
    }

    @Override
    protected boolean handleBackPressed() {
        if (mTab == null) return false;
        if (mTab.canGoBack()) {
            mTab.goBack();
            return true;
        }
        return false;
    }

    @Override
    public void onCheckForUpdate(boolean updateAvailable) {
    }
}
