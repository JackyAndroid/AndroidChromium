// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import android.graphics.Bitmap;

import org.chromium.base.ObserverList.RewindableIterator;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tab.TabUma;
import org.chromium.chrome.browser.tab.TabUma.TabCreationState;
import org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroid;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegate;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

/**
 * A Tab child class with Chrome documents specific functionality.
 */
public class DocumentTab extends Tab {
    /**
     * Observer class with extra calls specific to Chrome Documents
     */
    public static class DocumentTabObserver extends EmptyTabObserver {
        /**
         * Called when a Favicon is received for the current document.
         * @param image The favicon image that was received.
         */
        protected void onFaviconReceived(Bitmap image) { }
    }

    private boolean mDidRestoreState;

    // Whether this document tab was constructed from passed-in web contents pointer.
    private boolean mCreatedFromWebContents;

    private final DocumentActivity mActivity;

    /**
     * Standard constructor for the document tab.
     * @param activity The document activity that will hold on to this tab.
     * @param incognito Whether the tab is incognito.
     * @param windowAndroid The window that this tab should be using.
     * @param url The url to load on creation.
     * @param parentTabId The id of the parent tab.
     * @param initiallyHidden Whether or not the {@link WebContents} should be initially hidden.
     */
    private DocumentTab(DocumentActivity activity, boolean incognito, WindowAndroid windowAndroid,
            String url, int parentTabId, boolean initiallyHidden) {
        super(ActivityDelegate.getTabIdFromIntent(activity.getIntent()), parentTabId, incognito,
                activity, windowAndroid, TabLaunchType.FROM_EXTERNAL_APP, null, null);
        mActivity = activity;
        initialize(url, null, activity.getTabContentManager(), false, initiallyHidden);
    }

    /**
     * Constructor for document tab from a frozen state.
     * @param activity The document activity that will hold on to this tab.
     * @param incognito Whether the tab is incognito.
     * @param windowAndroid The window that this tab should be using.
     * @param url The url to load on creation.
     * @param tabState The {@link TabState} the tab will be recreated from.
     * @param parentTabId The id of the parent tab.
     */
    private DocumentTab(DocumentActivity activity, boolean incognito,
            WindowAndroid windowAndroid, String url, TabState tabState, int parentTabId) {
        super(ActivityDelegate.getTabIdFromIntent(activity.getIntent()), parentTabId, incognito,
                activity, windowAndroid, TabLaunchType.FROM_RESTORE,  null, tabState);
        mActivity = activity;
        initialize(url, null, activity.getTabContentManager(), true, false);
    }

    /**
     * Constructor for tab opened via JS.
     * @param activity The document activity that will hold on to this tab.
     * @param incognito Whether the tab is incognito.
     * @param windowAndroid The window that this tab should be using.
     * @param url The url to load on creation.
     * @param parentTabId The id of the parent tab.
     * @param webContents An optional {@link WebContents} object to use.
     */
    private DocumentTab(DocumentActivity activity, boolean incognito,
            WindowAndroid windowAndroid, String url, int parentTabId, WebContents webContents) {
        super(ActivityDelegate.getTabIdFromIntent(activity.getIntent()), parentTabId, incognito,
                activity, windowAndroid, TabLaunchType.FROM_LONGPRESS_FOREGROUND, null, null);
        mActivity = activity;
        initialize(url, webContents, activity.getTabContentManager(), false, false);
        mCreatedFromWebContents = true;
    }

    @Override
    protected void initContentViewCore(WebContents webContents) {
        super.initContentViewCore(webContents);
        getContentViewCore().setFullscreenRequiredForOrientationLock(false);
    }

    /**
     * Initializes the tab with native web contents.
     * @param url The url to use for looking up potentially pre-rendered web contents.
     * @param webContents Optionally, a pre-created web contents.
     * @param unfreeze Whether we want to initialize the tab from tab state.
     * @param initiallyHidden Whether or not the {@link WebContents} should be initially hidden.
     */
    private void initialize(String url, WebContents webContents,
            TabContentManager tabContentManager, boolean unfreeze, boolean initiallyHidden) {
        if (!unfreeze && webContents == null) {
            webContents = WarmupManager.getInstance().hasPrerenderedUrl(url)
                    ? WarmupManager.getInstance().takePrerenderedWebContents()
                    : WebContentsFactory.createWebContents(isIncognito(), initiallyHidden);
        }
        initialize(webContents, tabContentManager, new TabDelegateFactory() {
            @Override
            public TabWebContentsDelegateAndroid createWebContentsDelegate(
                    Tab tab, ChromeActivity activity) {
                return new DocumentTabWebContentsDelegateAndroid(DocumentTab.this, mActivity);
            }
        }, initiallyHidden);
        if (unfreeze) mDidRestoreState = unfreezeContents();

        getView().requestFocus();
    }

    @Override
    public void onFaviconAvailable(Bitmap image) {
        super.onFaviconAvailable(image);
        if (image == null) return;
        RewindableIterator<TabObserver> observers = getTabObservers();
        while (observers.hasNext()) {
            TabObserver observer = observers.next();
            if (observer instanceof DocumentTabObserver) {
                ((DocumentTabObserver) observer).onFaviconReceived(image);
            }
        }
    }

    /**
     * A web contents delegate for handling opening new windows in Document mode.
     */
    public class DocumentTabWebContentsDelegateAndroid extends TabWebContentsDelegateAndroid {
        public DocumentTabWebContentsDelegateAndroid(Tab tab, ChromeActivity activity) {
            super(tab, activity);
        }

        /**
         * TODO(dfalcantara): Remove this when DocumentActivity.getTabModelSelector()
         *                    can return a TabModelSelector that activateContents() can use.
         */
        @Override
        protected TabModel getTabModel() {
            return ChromeApplication.getDocumentTabModelSelector().getModel(isIncognito());
        }
    }

    /**
     * @return Whether or not the tab's state was restored.
     */
    public boolean didRestoreState() {
        return mDidRestoreState;
    }

    /**
     * @return Whether this tab was created using web contents passed to it.
     */
    public boolean isCreatedWithWebContents() {
        return mCreatedFromWebContents;
    }

    /**
     * Create a DocumentTab.
     * @param activity The activity the tab will be residing in.
     * @param incognito Whether the tab is incognito.
     * @param window The window the activity is using.
     * @param url The url that should be displayed by the tab.
     * @param webContents A {@link WebContents} object.
     * @param tabState State that was previously persisted to disk for the Tab.
     * @return The created {@link DocumentTab}.
     * @param initiallyHidden Whether or not the {@link WebContents} should be initially hidden.
     */
    static DocumentTab create(DocumentActivity activity, boolean incognito, WindowAndroid window,
            String url, WebContents webContents, TabState tabState, boolean initiallyHidden) {
        int parentTabId = activity.getIntent().getIntExtra(
                IntentHandler.EXTRA_PARENT_TAB_ID, Tab.INVALID_TAB_ID);
        if (webContents != null) {
            DocumentTab tab = new DocumentTab(
                    activity, incognito, window, url, parentTabId, webContents);
            webContents.resumeLoadingCreatedWebContents();
            return tab;
        }

        if (tabState == null) {
            return new DocumentTab(activity, incognito, window, url, parentTabId, initiallyHidden);
        } else {
            return new DocumentTab(activity, incognito, window, "", tabState, parentTabId);
        }
    }

    @Override
    public void onActivityStart() {
        // DocumentActivity#onResumeWithNative() will call Tab.show(), and so we don't need to call
        // it at this point.
        onActivityStartInternal(false /* showNow */);
    }

    @VisibleForTesting
    public DocumentActivity getActivity() {
        return mActivity;
    }

    /**
     * A helper function to create TabUma and set it to the tab.
     * @param creationState In what state the tab was created.
     */
    public void initializeTabUma(TabCreationState creationState) {
        setTabUma(new TabUma(this, creationState,
                mActivity.getTabModelSelector().getModel(mActivity.isIncognito())));
    }
}
