// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerDocument;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.SingleTabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.widget.ControlContainer;
import org.chromium.content_public.browser.LoadUrlParams;

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
public abstract class FullScreenActivity extends ChromeActivity
        implements FullScreenActivityTab.TopControlsVisibilityDelegate {
    private FullScreenActivityTab mTab;

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
        mTab = FullScreenActivityTab.create(
                this, getWindowAndroid(), getActivityDirectory(), getSavedInstanceState(), this);
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
    public final FullScreenActivityTab getActivityTab() {
        return mTab;
    }

    /**
     * @return {@link File} pointing at a directory specific for this class.
     */
    protected File getActivityDirectory() {
        return null;
    }

    // Implements {@link FullScreenActivityTab.TopControlsVisibilityDelegate}.
    @Override
    public boolean shouldShowTopControls(String url, int securityLevel) {
        return false;
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
}
