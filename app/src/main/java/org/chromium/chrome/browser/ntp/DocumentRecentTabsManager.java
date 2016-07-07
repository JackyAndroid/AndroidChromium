// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.document.DocumentUtils;
import org.chromium.chrome.browser.ntp.ForeignSessionHelper.ForeignSession;
import org.chromium.chrome.browser.ntp.ForeignSessionHelper.ForeignSessionTab;
import org.chromium.chrome.browser.ntp.RecentlyClosedBridge.RecentlyClosedTab;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegate;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModel;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelImpl;
import org.chromium.ui.WindowOpenDisposition;

import java.util.ArrayList;
import java.util.List;

/**
 * ChromeHome specific version of RecentTabsManager that allows opening new DocumentActivities
 * instead of tabs.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class DocumentRecentTabsManager extends RecentTabsManager {
    /**
     * The number of ms to delay opening a new tab, so that we have time to hide dialog before
     * the screenshot of the current page is taken.
     */
    private static final int NEW_TAB_DELAY_MS = 150;
    private final Activity mActivity;
    private final List<CurrentlyOpenTab> mCurrentlyOpenTabs;
    private final DocumentTabModel mTabModel;
    private final DocumentTabModel.InitializationObserver mUpdateOpenTabsObserver;
    private TabModelObserver mTabModelObserver;
    private Dialog mDialog;

    private boolean mShowingAllInCurrentTabs;

    /**
     * @param activity Activity that should be used to launch intents.
     */
    public DocumentRecentTabsManager(Tab tab, Activity activity) {
        super(tab, tab.getProfile().getOriginalProfile(), activity);
        mActivity = activity;
        mCurrentlyOpenTabs = new ArrayList<CurrentlyOpenTab>();
        mTabModel =
                ChromeApplication.getDocumentTabModelSelector().getModel(tab.isIncognito());
        mUpdateOpenTabsObserver = new DocumentTabModel.InitializationObserver(mTabModel) {
                @Override
                public boolean isSatisfied(int currentState) {
                    return currentState >= DocumentTabModelImpl.STATE_FULLY_LOADED;
                }

                @Override
                public boolean isCanceled() {
                    return mActivity.isDestroyed() || mActivity.isFinishing();
                }

                @Override
                protected void runImmediately() {
                    updateCurrentlyOpenTabsWhenDatabaseReady();
                }
        };
        mTabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didAddTab(Tab tab, TabLaunchType type) {
                updateCurrentlyOpenTabsWhenDatabaseReady();
            }

            @Override
            public void didCloseTab(Tab tab) {
                updateCurrentlyOpenTabsWhenDatabaseReady();
            }
        };
        mTabModel.addObserver(mTabModelObserver);
        updateCurrentlyOpenTabs();
    }

    /**
     * @param dialog Dialog that displays the RecentTabsPage and will be dismissed when
     *               a link is opened.
     */
    public void setDialog(Dialog dialog) {
        mDialog = dialog;
    }

    @Override
    public void destroy() {
        super.destroy();
        mTabModel.removeObserver(mTabModelObserver);
    }

    @Override
    public void openForeignSessionTab(final ForeignSession session, final ForeignSessionTab tab,
            int windowDisposition) {
        // Hide the dialog for screenshot. We don't want to dismiss yet because that will destroy
        // the NativePage objects we need in the delayed runnable.
        if (mDialog != null) mDialog.hide();
        ThreadUtils.postOnUiThreadDelayed(new Runnable() {
            @Override
            public void run() {
                DocumentRecentTabsManager.super.openForeignSessionTab(
                        session, tab, WindowOpenDisposition.NEW_FOREGROUND_TAB);
                if (mDialog != null) mDialog.dismiss();
            }
        }, NEW_TAB_DELAY_MS);
    }

    @Override
    public void openRecentlyClosedTab(final RecentlyClosedTab tab, int windowDisposition) {
        // Hide the dialog for screenshot. We don't want to dismiss yet because that will destroy
        // the NativePage objects we need in the delayed runnable.
        if (mDialog != null) mDialog.hide();
        ThreadUtils.postOnUiThreadDelayed(new Runnable() {
            @Override
            public void run() {
                DocumentRecentTabsManager.super.openRecentlyClosedTab(
                        tab, WindowOpenDisposition.NEW_FOREGROUND_TAB);
                if (mDialog != null) mDialog.dismiss();
            }
        }, NEW_TAB_DELAY_MS);
    }

    @Override
    public void openHistoryPage() {
        if (mDialog != null) mDialog.dismiss();
        super.openHistoryPage();
    }

    @Override
    public List<CurrentlyOpenTab> getCurrentlyOpenTabs() {
        return mCurrentlyOpenTabs;
    }

    @Override
    public void setCurrentlyOpenTabsShowAll(boolean showingAll) {
        mShowingAllInCurrentTabs = showingAll;
        postUpdate();
    }

    @Override
    public boolean isCurrentlyOpenTabsShowingAll() {
        return mShowingAllInCurrentTabs;
    }

    @Override
    public void closeTab(CurrentlyOpenTab tab) {
        Tab currentTab =
                ChromeApplication.getDocumentTabModelSelector().getCurrentTab();
        Tab tabOject = TabModelUtils.getTabById(mTabModel, tab.getTabId());
        mTabModel.closeTab(tabOject);
        if (currentTab.getId() == tabOject.getId()) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_MAIN);
            intent.setPackage(mActivity.getPackageName());
            mActivity.startActivity(intent);
        }
        postUpdate();
    }

    @Override
    protected void updateCurrentlyOpenTabs() {
        mUpdateOpenTabsObserver.runWhenReady();
    }

    private void updateCurrentlyOpenTabsWhenDatabaseReady() {
        final int currentTabId = ActivityDelegate.getTabIdFromIntent(mActivity.getIntent());

        ActivityManager am = (ActivityManager) mActivity.getSystemService(
                Activity.ACTIVITY_SERVICE);
        List<ActivityManager.AppTask> taskList = am.getAppTasks();
        mCurrentlyOpenTabs.clear();
        for (int i = 0; i < taskList.size(); i++) {
            RecentTaskInfo taskInfo = DocumentUtils.getTaskInfoFromTask(taskList.get(i));
            if (taskInfo == null) continue;

            final Intent baseIntent = taskInfo.baseIntent;
            final int tabId = ActivityDelegate.getTabIdFromIntent(baseIntent);
            String url = mTabModel.getCurrentUrlForDocument(tabId);
            if (TextUtils.isEmpty(url) || url.startsWith(UrlConstants.CHROME_NATIVE_SCHEME)) {
                continue;
            }

            CharSequence description = taskInfo.description;
            String title = description != null ?  description.toString() : "";

            final Runnable startNewDocument = new Runnable() {
                @Override
                public void run() {
                    Intent newIntent = Tab.createBringTabToFrontIntent(tabId);
                    newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    mActivity.startActivity(newIntent);
                }
            };

            Runnable onClickRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mDialog != null) mDialog.dismiss();
                    if (currentTabId != tabId) {
                        ThreadUtils.postOnUiThreadDelayed(startNewDocument, NEW_TAB_DELAY_MS);
                    }
                }
            };
            mCurrentlyOpenTabs.add(new CurrentlyOpenTab(tabId, url, title, onClickRunnable));
        }
    }
}
