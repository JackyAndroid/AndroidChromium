// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.app.Activity;
import android.content.Context;
import android.util.SparseArray;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.document.DocumentUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelJniBridge;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.content_public.browser.WebContents;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintains a list of Tabs displayed when Chrome is running in document-mode.
 */
public class DocumentTabModelImpl extends TabModelJniBridge implements DocumentTabModel {
    private static final String TAG = "DocumentTabModel";

    public static final String PREF_PACKAGE = "com.google.android.apps.chrome.document";
    public static final String PREF_LAST_SHOWN_TAB_ID_REGULAR = "last_shown_tab_id.regular";
    public static final String PREF_LAST_SHOWN_TAB_ID_INCOGNITO = "last_shown_tab_id.incognito";

    /** TabModel is uninitialized. */
    public static final int STATE_UNINITIALIZED = 0;

    /** Begin parsing the tasks from Recents and loading persisted state. */
    public static final int STATE_READ_RECENT_TASKS_START = 1;

    /** Done parsing the tasks from Recents and loading persisted state. */
    public static final int STATE_READ_RECENT_TASKS_END = 2;

    /** Begin loading the current/prioritized tab state synchronously. */
    public static final int STATE_LOAD_CURRENT_TAB_STATE_START = 3;

    /** Finish loading the current/prioritized tab state synchronously. */
    public static final int STATE_LOAD_CURRENT_TAB_STATE_END = 4;

    /** Begin reading TabStates from storage for background tabs. */
    public static final int STATE_LOAD_TAB_STATE_BG_START = 5;

    /** Done reading TabStates from storage for background tabs. */
    public static final int STATE_LOAD_TAB_STATE_BG_END = 6;

    /** Begin deserializing the TabState.  Requires the native library. */
    public static final int STATE_DESERIALIZE_START = 7;

    /** Done deserializing the TabState. */
    public static final int STATE_DESERIALIZE_END = 8;

    /** Begin parsing the historical tabs. */
    public static final int STATE_DETERMINE_HISTORICAL_TABS_START = 9;

    /** Done parsing the historical tabs. */
    public static final int STATE_DETERMINE_HISTORICAL_TABS_END = 10;

    /** Clean out old TabState files. */
    public static final int STATE_CLEAN_UP_OBSOLETE_TABS = 11;

    /** TabModel is fully ready to use. */
    public static final int STATE_FULLY_LOADED = 12;

    /** List of known tabs. */
    private final ArrayList<Integer> mTabIdList;

    /** Stores an entry for each DocumentActivity that is alive.  Keys are document IDs. */
    private final SparseArray<Entry> mEntryMap;

    /**
     * Stores tabIds which have been removed from the ActivityManager while Chrome was not alive.
     * It is cleared after restoration has been finished.
     */
    private final List<Integer> mHistoricalTabs;

    /** Delegate for working with the ActivityManager. */
    private final ActivityDelegate mActivityDelegate;

    /** Delegate for working with the filesystem. */
    private final StorageDelegate mStorageDelegate;

    /** Context to use. */
    private final Context mContext;

    /** Current loading status. */
    private int mCurrentState;

    /** ID of the last tab that was shown to the user. */
    private int mLastShownTabId = Tab.INVALID_TAB_ID;

    /**
     * Pre-load shared prefs to avoid being blocked on the
     * disk access async task in the future.
     */
    public static void warmUpSharedPrefs(Context context) {
        context.getSharedPreferences(PREF_PACKAGE, Context.MODE_PRIVATE);
    }

    /**
     * Construct a DocumentTabModel.
     * @param activityDelegate Delegate to use for accessing the ActivityManager.
     * @param storageDelegate Delegate to use for accessing persistent storage.
     * @param tabCreatorManager Used to create Tabs.
     * @param isIncognito Whether or not the TabList is managing incognito tabs.
     * @param prioritizedTabId ID of the tab to prioritize when loading.
     * @param context Context to use for accessing SharedPreferences.
     */
    public DocumentTabModelImpl(ActivityDelegate activityDelegate, StorageDelegate storageDelegate,
            TabCreatorManager tabCreatorManager, boolean isIncognito, int prioritizedTabId,
            Context context) {
        super(isIncognito, false);
        mActivityDelegate = activityDelegate;
        mStorageDelegate = storageDelegate;
        mContext = context;

        mCurrentState = STATE_UNINITIALIZED;
        mTabIdList = new ArrayList<Integer>();
        mEntryMap = new SparseArray<Entry>();
        mHistoricalTabs = new ArrayList<Integer>();

        mLastShownTabId = DocumentUtils.getLastShownTabIdFromPrefs(mContext, isIncognito());

        // Restore the tab list.
        setCurrentState(STATE_READ_RECENT_TASKS_START);
        mStorageDelegate.restoreTabEntries(
                isIncognito, activityDelegate, mEntryMap, mTabIdList, mHistoricalTabs);
        setCurrentState(STATE_READ_RECENT_TASKS_END);
    }

    public StorageDelegate getStorageDelegate() {
        return mStorageDelegate;
    }

    /**
     * Finds the index of the given Tab ID.
     * @param tabId ID of the Tab to find.
     * @return Index of the tab, or -1 if it couldn't be found.
     */
    private int indexOf(int tabId) {
        return mTabIdList.indexOf(tabId);
    }

    @Override
    public int index() {
        if (getCount() == 0) return TabList.INVALID_TAB_INDEX;
        int indexOfLastId = indexOf(mLastShownTabId);
        if (indexOfLastId != -1) return indexOfLastId;

        // The previous Tab is gone; select a Tab based on MRU ordering.
        List<Entry> tasks = mActivityDelegate.getTasksFromRecents(isIncognito());
        if (tasks.size() == 0) return TabList.INVALID_TAB_INDEX;

        for (int i = 0; i < tasks.size(); i++) {
            int lastKnownId = tasks.get(i).tabId;
            int indexOfMostRecentlyUsedId = indexOf(lastKnownId);
            if (indexOfMostRecentlyUsedId != -1) return indexOfMostRecentlyUsedId;
        }

        return TabList.INVALID_TAB_INDEX;
    }

    @Override
    public int indexOf(Tab tab) {
        if (tab == null) return Tab.INVALID_TAB_ID;
        return indexOf(tab.getId());
    }

    @Override
    public int getCount() {
        return mTabIdList.size();
    }

    @Override
    public boolean isClosurePending(int tabId) {
        return false;
    }

    @Override
    public Tab getTabAt(int index) {
        if (index < 0 || index >= getCount()) return null;

        // Return a live tab if the corresponding DocumentActivity is currently alive.
        int tabId = mTabIdList.get(index);
        List<WeakReference<Activity>> activities = ApplicationStatus.getRunningActivities();
        for (WeakReference<Activity> activityRef : activities) {
            Activity activity = activityRef.get();
            if (!(activity instanceof DocumentActivity)
                    || !mActivityDelegate.isValidActivity(isIncognito(), activity.getIntent())) {
                continue;
            }

            Tab tab = ((DocumentActivity) activity).getActivityTab();
            int documentId = tab == null ? Tab.INVALID_TAB_ID : tab.getId();
            if (documentId == tabId) return tab;
        }

        // Try to create a Tab that will hold the Tab's info.
        Entry entry = mEntryMap.get(tabId);
        assert entry != null;

        // If a tab has already been initialized, use that.
        if (entry.placeholderTab != null && entry.placeholderTab.isInitialized()) {
            return entry.placeholderTab;
        }

        // Create a frozen Tab if we are capable, or if the previous Tab is just a placeholder.
        if (entry.getTabState() != null && isNativeInitialized()
                && (entry.placeholderTab == null || !entry.placeholderTab.isInitialized())) {
            entry.placeholderTab = getTabCreator(isIncognito()).createFrozenTab(
                    entry.getTabState(), entry.tabId, TabModel.INVALID_TAB_INDEX);
            entry.placeholderTab.initializeNative();
        }

        // Create a placeholder Tab that just has the ID.
        if (entry.placeholderTab == null) {
            entry.placeholderTab = new Tab(tabId, isIncognito(), null);
        }

        return entry.placeholderTab;
    }

    @Override
    public void setIndex(int index, TabSelectionType type) {
    }

    @Override
    protected boolean closeTabAt(int index) {
        return false;
    }

    @Override
    public boolean closeTab(Tab tab) {
        return false;
    }

    @Override
    public boolean closeTab(Tab tabToClose, boolean animate, boolean uponExit, boolean canUndo) {
        return false;
    }

    @Override
    protected TabDelegate getTabCreator(boolean incognito) {
        return null;
    }

    @Override
    protected boolean createTabWithWebContents(Tab parent, boolean isIncognito,
            WebContents webContents, int parentTabId) {
        return false;
    }

    @Override
    protected boolean isSessionRestoreInProgress() {
        return mCurrentState < STATE_FULLY_LOADED;
    }

    @Override
    public String getInitialUrlForDocument(int tabId) {
        Entry entry = mEntryMap.get(tabId);
        return entry == null ? null : entry.initialUrl;
    }

    private void setCurrentState(int newState) {
        ThreadUtils.assertOnUiThread();
        assert mCurrentState == newState - 1;
        mCurrentState = newState;
    }

    @Override
    public Tab getNextTabIfClosed(int id) {
        // Tab may not necessarily exist.
        return null;
    }

    @Override
    public void closeAllTabs() {
        closeAllTabs(true, false);
    }

    @Override
    public void closeAllTabs(boolean allowDelegation, boolean uponExit) {
    }

    @Override
    public void moveTab(int id, int newIndex) {
        assert false;
    }

    @Override
    public void addTab(Tab tab, int index, TabLaunchType type) {
        assert false;
    }

    @Override
    public void removeTab(Tab tab) {
        assert false;
    }

    @Override
    public boolean supportsPendingClosures() {
        return false;
    }

    @Override
    public void commitAllTabClosures() {
    }

    @Override
    public void commitTabClosure(int tabId) {
    }

    @Override
    public void cancelTabClosure(int tabId) {
    }

    @Override
    public TabList getComprehensiveModel() {
        return this;
    }

    @Override
    public void addObserver(TabModelObserver observer) {
    }

    @Override
    public void removeObserver(TabModelObserver observer) {
    }

    @Override
    public void openMostRecentlyClosedTab() {
    }
}
