// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;

import org.chromium.base.StreamUtil;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabUma.TabCreationState;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A tab that will be used for FullScreenActivity. See {@link FullScreenActivity} for more.
 */
@SuppressFBWarnings("URF_UNREAD_FIELD")
public class FullScreenActivityTab extends Tab {
    private static final String TAG = "FullScreenActivityTab";

    /**
     * A delegate to determine top controls visibility.
     */
    public interface TopControlsVisibilityDelegate {
        /**
         * Determines whether top controls should be shown.
         *
         * @param uri The URI to display.
         * @param securityLevel Security level of the Tab.
         * @return Whether the URL bar should be visible or not.
         */
        boolean shouldShowTopControls(String uri, int securityLevel);
    }

    static final String BUNDLE_TAB_ID = "tabId";
    static final String BUNDLE_TAB_URL = "tabUrl";

    private WebContentsObserver mObserver;
    private TopControlsVisibilityDelegate mTopControlsVisibilityDelegate;

    private FullScreenActivityTab(ChromeActivity activity, WindowAndroid window,
            TopControlsVisibilityDelegate topControlsVisibilityDelegate) {
        super(INVALID_TAB_ID, INVALID_TAB_ID, false, activity, window,
                TabLaunchType.FROM_MENU_OR_OVERVIEW, null, null);
        initializeFullScreenActivityTab(
                activity.getTabContentManager(), false, topControlsVisibilityDelegate);
    }

    private FullScreenActivityTab(int id, ChromeActivity activity, WindowAndroid window,
            TabState state, TopControlsVisibilityDelegate topControlsVisibilityDelegate) {
        super(id, Tab.INVALID_TAB_ID, false, activity, window, TabLaunchType.FROM_RESTORE,
                TabCreationState.FROZEN_ON_RESTORE, state);
        initializeFullScreenActivityTab(
                activity.getTabContentManager(), true, topControlsVisibilityDelegate);
    }

    private void initializeFullScreenActivityTab(TabContentManager tabContentManager,
            boolean unfreeze, TopControlsVisibilityDelegate topControlsVisibilityDelegate) {
        initialize(null, tabContentManager, new FullScreenDelegateFactory(), false);
        if (unfreeze) unfreezeContents();
        mObserver = createWebContentsObserver();
        mTopControlsVisibilityDelegate = topControlsVisibilityDelegate;
    }

    /**
     * Saves the state of the tab out to the {@link Bundle}.
     */
    void saveInstanceState(Bundle outState) {
        outState.putInt(BUNDLE_TAB_ID, getId());
        outState.putString(BUNDLE_TAB_URL, getUrl());
    }

    /**
     * @return WebContentsObserver that watches for changes.
     */
    private WebContentsObserver createWebContentsObserver() {
        return new WebContentsObserver(getWebContents()) {
            @Override
            public void didCommitProvisionalLoadForFrame(
                    long frameId, boolean isMainFrame, String url, int transitionType) {
                if (isMainFrame) {
                    // Notify the renderer to permanently hide the top controls since they do
                    // not apply to fullscreen content views.
                    updateTopControlsState(getTopControlsStateConstraints(),
                            getTopControlsStateConstraints(), true);
                }
            }
        };
    }

    @Override
    protected void initContentViewCore(WebContents webContents) {
        super.initContentViewCore(webContents);
        getContentViewCore().setFullscreenRequiredForOrientationLock(false);
    }

    /**
     * Loads the given {@code url}.
     * @param url URL to load.
     */
    public void loadUrl(String url) {
        loadUrl(new LoadUrlParams(url, PageTransition.AUTO_TOPLEVEL));
    }

    /**
     * Saves the tab data out to a file.
     */
    void saveState(File activityDirectory) {
        File tabFile = getTabFile(activityDirectory, getId());

        FileOutputStream foutput = null;
        // Temporarily allowing disk access while fixing. TODO: http://crbug.com/525781
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();
        try {
            foutput = new FileOutputStream(tabFile);
            TabState.saveState(foutput, getState(), false);
        } catch (FileNotFoundException exception) {
            Log.e(TAG, "Failed to save out tab state.", exception);
        } catch (IOException exception) {
            Log.e(TAG, "Failed to save out tab state.", exception);
        } finally {
            StreamUtil.closeQuietly(foutput);
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * @return {@link File} pointing at the tab state for this Activity.
     */
    private static File getTabFile(File activityDirectory, int tabId) {
        return new File(activityDirectory, TabState.getTabStateFilename(tabId, false));
    }

    /**
     * Creates the {@link FullScreenActivityTab} used by the FullScreenActivity.
     * If the {@code savedInstanceState} exists, then the user did not intentionally close the app
     * by swiping it away in the recent tasks list.  In that case, we try to restore the tab from
     * disk.
     * @param activity Activity that will own the Tab.
     * @param directory Directory associated with the Activity.  Null implies tab state isn't saved.
     * @param savedInstanceState Bundle saved out when the app was killed by Android.  May be null.
     * @param topControlsVisibilityDelegate Delegate to determine top controls visibility.
     * @return {@link FullScreenActivityTab} for the Activity.
     */
    public static FullScreenActivityTab create(ChromeActivity activity, WindowAndroid window,
            File directory, Bundle savedInstanceState,
            TopControlsVisibilityDelegate topControlsVisibilityDelegate) {
        FullScreenActivityTab tab = null;

        int tabId = Tab.INVALID_TAB_ID;
        String tabUrl = null;
        if (savedInstanceState != null) {
            tabId = savedInstanceState.getInt(BUNDLE_TAB_ID, INVALID_TAB_ID);
            tabUrl = savedInstanceState.getString(BUNDLE_TAB_URL);
        }

        if (tabId != Tab.INVALID_TAB_ID && tabUrl != null && directory != null) {
            FileInputStream stream = null;
            try {
                // Restore the tab.
                stream = new FileInputStream(getTabFile(directory, tabId));
                TabState tabState = TabState.readState(stream, false);
                tab = new FullScreenActivityTab(
                        tabId, activity, window, tabState, topControlsVisibilityDelegate);
            } catch (FileNotFoundException exception) {
                Log.e(TAG, "Failed to restore tab state.", exception);
            } catch (IOException exception) {
                Log.e(TAG, "Failed to restore tab state.", exception);
            } finally {
                StreamUtil.closeQuietly(stream);
            }
        }

        if (tab == null) {
            // Create a new tab.
            tab = new FullScreenActivityTab(activity, window, topControlsVisibilityDelegate);
        }

        return tab;
    }

    @Override
    protected boolean isHidingTopControlsEnabled() {
        if (getFullscreenManager() == null) return true;
        if (getFullscreenManager().getPersistentFullscreenMode()) return true;
        if (mTopControlsVisibilityDelegate == null) return false;
        return !mTopControlsVisibilityDelegate.shouldShowTopControls(getUrl(), getSecurityLevel());
    }

    @Override
    public boolean isShowingTopControlsEnabled() {
        // On webapp activity and embedd content view activity, it's either hiding or showing.
        // Users cannot change the visibility state by sliding it in or out.
        return !isHidingTopControlsEnabled();
    }
}
