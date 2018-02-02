// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.graphics.Bitmap;
import android.text.TextUtils;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.components.security_state.ConnectionSecurityLevel;

import java.util.List;

/**
 * Helper that updates the Android task description given the state of the current tab.
 *
 * <p>
 * The task description is what is shown in Android's Overview/Recents screen for each entry.
 */
public class ActivityTabTaskDescriptionHelper {
    private final int mDefaultThemeColor;
    private final ChromeActivity mActivity;
    private final TabModelSelector mTabModelSelector;

    private final ActivityTaskDescriptionIconGenerator mIconGenerator;
    private final FaviconHelper mFaviconHelper;

    private final TabModelSelectorObserver mTabModelSelectorObserver;
    private final TabModelSelectorTabModelObserver mTabModelObserver;
    private final TabObserver mTabObserver;

    private Bitmap mLargestFavicon;
    private Tab mCurrentTab;

    /**
     * Constructs a task description helper for the given activity.
     *
     * @param activity The activity whose descriptions should be updated.
     * @param defaultThemeColor The default theme color to be used if the tab does not override it.
     */
    public ActivityTabTaskDescriptionHelper(ChromeActivity activity, int defaultThemeColor) {
        mActivity = activity;
        mDefaultThemeColor = defaultThemeColor;

        mTabModelSelector = mActivity.getTabModelSelector();

        mIconGenerator = new ActivityTaskDescriptionIconGenerator(activity);
        mFaviconHelper = new FaviconHelper();

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onWebContentsSwapped(Tab tab, boolean didStartLoad, boolean didFinishLoad) {
                if (!didStartLoad) return;
                resetIcon();
            }

            @Override
            public void onFaviconUpdated(Tab tab, Bitmap icon) {
                if (icon == null) return;
                updateFavicon(icon);
            }

            @Override
            public void onUrlUpdated(Tab tab) {
                updateTaskDescription();
            }

            @Override
            public void onTitleUpdated(Tab tab) {
                updateTaskDescription();
            }

            @Override
            public void onSSLStateUpdated(Tab tab) {
                if (hasSecurityWarningOrError(tab)) resetIcon();
            }

            @Override
            public void onDidNavigateMainFrame(Tab tab, String url, String baseUrl,
                    boolean isNavigationToDifferentPage, boolean isFragmentNavigation,
                    int statusCode) {
                if (!isNavigationToDifferentPage) return;
                mLargestFavicon = null;
                updateTaskDescription();
            }

            @Override
            public void onLoadStopped(Tab tab, boolean toDifferentDocument) {
                updateTaskDescription();
            }

            @Override
            public void onDidChangeThemeColor(Tab tab, int color) {
                updateTaskDescription();
            }

            @Override
            public void onDidAttachInterstitialPage(Tab tab) {
                resetIcon();
            }

            @Override
            public void onDidDetachInterstitialPage(Tab tab) {
                resetIcon();
            }

            private boolean hasSecurityWarningOrError(Tab tab) {
                int securityLevel = tab.getSecurityLevel();
                return securityLevel == ConnectionSecurityLevel.DANGEROUS
                        || securityLevel == ConnectionSecurityLevel.SECURITY_WARNING
                        || securityLevel
                        == ConnectionSecurityLevel.SECURE_WITH_POLICY_INSTALLED_CERT;
            }
        };

        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                refreshSelectedTab();
            }
        };

        mTabModelObserver = new TabModelSelectorTabModelObserver(mTabModelSelector) {
            @Override
            public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
                refreshSelectedTab();
            }

            @Override
            public void tabClosureUndone(Tab tab) {
                refreshSelectedTab();
            }

            @Override
            public void didCloseTab(int tabId, boolean incognito) {
                refreshSelectedTab();
            }

            @Override
            public void tabRemoved(Tab tab) {
                refreshSelectedTab();
            }

            @Override
            public void tabPendingClosure(Tab tab) {
                refreshSelectedTab();
            }

            @Override
            public void allTabsPendingClosure(List<Integer> tabIds) {
                refreshSelectedTab();
            }
        };

        mTabModelSelector.addObserver(mTabModelSelectorObserver);
        refreshSelectedTab();
    }

    private void resetIcon() {
        mLargestFavicon = null;
        updateTaskDescription();
    }

    private void updateFavicon(Bitmap favicon) {
        if (favicon == null) return;
        if (mLargestFavicon == null || favicon.getWidth() > mLargestFavicon.getWidth()
                || favicon.getHeight() > mLargestFavicon.getHeight()) {
            mLargestFavicon = favicon;
            updateTaskDescription();
        }
    }

    private void updateTaskDescription() {
        if (mCurrentTab == null) {
            updateTaskDescription(null, null);
            return;
        }

        if (NewTabPage.isNTPUrl(mCurrentTab.getUrl()) && !mCurrentTab.isIncognito()) {
            // NTP needs a new color in recents, but uses the default application title and icon
            updateTaskDescription(null, null);
            return;
        }

        String label = mCurrentTab.getTitle();
        String domain = UrlUtilities.getDomainAndRegistry(mCurrentTab.getUrl(), false);
        if (TextUtils.isEmpty(label)) {
            label = domain;
        }
        if (mLargestFavicon == null && TextUtils.isEmpty(label)) {
            updateTaskDescription(null, null);
            return;
        }

        Bitmap bitmap = null;
        if (!mCurrentTab.isIncognito()) {
            bitmap = mIconGenerator.getBitmap(mCurrentTab.getUrl(), mLargestFavicon);
        }

        updateTaskDescription(label, bitmap);
    }

    /**
     * Update the task description with the specified icon and label.
     *
     * <p>
     * This is only publicly visible to allow activities to set this early during initialization
     * prior to the tab's being available.
     *
     * @param label The text to use in the task description.
     * @param icon The icon to use in the task description.
     */
    public void updateTaskDescription(String label, Bitmap icon) {
        int color = mDefaultThemeColor;
        if (mCurrentTab != null && !mCurrentTab.isDefaultThemeColor()) {
            color = mCurrentTab.getThemeColor();
        }
        ApiCompatibilityUtils.setTaskDescription(mActivity, label, icon, color);
    }

    private void refreshSelectedTab() {
        Tab tab = mTabModelSelector.getCurrentTab();
        if (mCurrentTab == tab) return;

        if (mCurrentTab != null) mCurrentTab.removeObserver(mTabObserver);

        mCurrentTab = tab;
        if (mCurrentTab != null) {
            mCurrentTab.addObserver(mTabObserver);

            final String currentUrl = mCurrentTab.getUrl();
            mFaviconHelper.getLocalFaviconImageForURL(
                    mCurrentTab.getProfile(), mCurrentTab.getUrl(), 0,
                    new FaviconHelper.FaviconImageCallback() {
                        @Override
                        public void onFaviconAvailable(Bitmap image, String iconUrl) {
                            if (mCurrentTab == null
                                    || !TextUtils.equals(currentUrl, mCurrentTab.getUrl())) {
                                return;
                            }

                            updateFavicon(image);
                        }
                    });
        }
        updateTaskDescription();
    }

    /**
     * Destroys all dependent components of the task description helper.
     */
    public void destroy() {
        mFaviconHelper.destroy();

        if (mCurrentTab != null) {
            mCurrentTab.removeObserver(mTabObserver);
        }

        mTabModelSelector.removeObserver(mTabModelSelectorObserver);
        mTabModelObserver.destroy();
    }
}
