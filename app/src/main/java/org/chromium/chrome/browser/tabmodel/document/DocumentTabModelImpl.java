// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.google.protobuf.nano.MessageNano;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.document.DocumentMetricIds;
import org.chromium.chrome.browser.document.IncognitoNotificationManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelJniBridge;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelInfo.DocumentEntry;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelInfo.DocumentList;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.content_public.browser.WebContents;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maintains a list of Tabs displayed when Chrome is running in document-mode.
 */
public class DocumentTabModelImpl extends TabModelJniBridge implements DocumentTabModel {
    private static final String TAG = "DocumentTabModel";

    @VisibleForTesting
    public static final String PREF_PACKAGE = "com.google.android.apps.chrome.document";

    @VisibleForTesting
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

    /** Delegate that provides Tabs to the DocumentTabModel. */
    private final TabCreatorManager mTabCreatorManager;

    /** ID of a Tab whose state should be loaded immediately, if it belongs to this TabList. */
    private final int mPrioritizedTabId;

    /** List of observers watching for a particular loading state. */
    private final ObserverList<InitializationObserver> mInitializationObservers;

    /** List of observers watching the TabModel. */
    private final ObserverList<TabModelObserver> mObservers;

    /** Context to use. */
    private final Context mContext;

    /** Current loading status. */
    private int mCurrentState;

    /** ID of the last tab that was shown to the user. */
    private int mLastShownTabId = Tab.INVALID_TAB_ID;

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
        super(isIncognito);
        mActivityDelegate = activityDelegate;
        mStorageDelegate = storageDelegate;
        mTabCreatorManager = tabCreatorManager;
        mPrioritizedTabId = prioritizedTabId;
        mContext = context;

        mCurrentState = STATE_UNINITIALIZED;
        mTabIdList = new ArrayList<Integer>();
        mEntryMap = new SparseArray<Entry>();
        mHistoricalTabs = new ArrayList<Integer>();
        mInitializationObservers = new ObserverList<InitializationObserver>();
        mObservers = new ObserverList<TabModelObserver>();

        SharedPreferences prefs = mContext.getSharedPreferences(PREF_PACKAGE, Context.MODE_PRIVATE);
        mLastShownTabId = prefs.getInt(
                isIncognito() ? PREF_LAST_SHOWN_TAB_ID_INCOGNITO : PREF_LAST_SHOWN_TAB_ID_REGULAR,
                Tab.INVALID_TAB_ID);

        // Restore the tab list.
        setCurrentState(STATE_READ_RECENT_TASKS_START);
        mStorageDelegate.restoreTabEntries(
                isIncognito, activityDelegate, mEntryMap, mTabIdList, mHistoricalTabs);
        setCurrentState(STATE_READ_RECENT_TASKS_END);
    }

    @Override
    public void initializeNative() {
        if (!isNativeInitialized()) super.initializeNative();
        deserializeTabStatesAsync();
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
    public boolean setLastShownId(int id) {
        if (mLastShownTabId == id) return false;

        int previousTabId = mLastShownTabId;
        mLastShownTabId = id;

        String prefName =
                isIncognito() ? PREF_LAST_SHOWN_TAB_ID_INCOGNITO : PREF_LAST_SHOWN_TAB_ID_REGULAR;
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_PACKAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(prefName, id);
        editor.apply();

        // TODO(dfalcantara): Figure out how to fire the correct type of TabSelectionType, which is
        //                    quite hard to do in Document-mode from where we call this.
        for (TabModelObserver obs : mObservers) {
            obs.didSelectTab(
                    TabModelUtils.getCurrentTab(this), TabSelectionType.FROM_USER, previousTabId);
        }

        return true;
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
        if (index < 0 || index >= getCount()) return;
        int tabId = mTabIdList.get(index);
        mActivityDelegate.moveTaskToFront(isIncognito(), tabId);
        setLastShownId(tabId);
    }

    @Override
    public boolean closeTabAt(int index) {
        ThreadUtils.assertOnUiThread();
        if (index < 0 || index >= getCount()) return false;

        Tab tab = getTabAt(index);
        for (TabModelObserver obs : mObservers) obs.willCloseTab(tab, false);

        int tabId = tab.getId();
        Entry entry = mEntryMap.get(tabId);
        if (!isIncognito() && entry != null && entry.getTabState() != null) {
            entry.getTabState().contentsState.createHistoricalTab();
        }

        mActivityDelegate.finishAndRemoveTask(isIncognito(), tabId);
        mTabIdList.remove(index);
        mEntryMap.remove(tabId);

        for (TabModelObserver obs : mObservers) obs.didCloseTab(tab);
        return true;
    }

    @Override
    public boolean closeTab(Tab tab) {
        return closeTab(tab, false, false, false);
    }

    @Override
    public boolean closeTab(Tab tabToClose, boolean animate, boolean uponExit, boolean canUndo) {
        // The tab should be destroyed by the DocumentActivity that owns it.
        return closeTabAt(indexOf(tabToClose.getId()));
    }

    @Override
    protected TabDelegate getTabCreator(boolean incognito) {
        return (TabDelegate) mTabCreatorManager.getTabCreator(incognito);
    }

    @Override
    protected boolean createTabWithWebContents(
            boolean isIncognito, WebContents webContents, int parentTabId) {
        // Tabs created along this pathway are currently only created via JNI, which includes
        // session restore tabs.  Differs from TabModelImpl because we explicitly open tabs in the
        // foreground -- opening tabs in affiliated mode is disallowed by ChromeLauncherActivity
        // when a WebContents has already been created.
        return getTabCreator(isIncognito).createTabWithWebContents(
                webContents, parentTabId, TabLaunchType.FROM_LONGPRESS_FOREGROUND,
                webContents.getUrl(), DocumentMetricIds.STARTED_BY_CHROME_HOME_RECENT_TABS);
    }

    @Override
    protected boolean isSessionRestoreInProgress() {
        return mCurrentState < STATE_FULLY_LOADED;
    }

    /**
     * Adds the Tab ID at the given index.
     * @param index Where to add the ID.
     * @param tabId ID to add.
     */
    private void addTabId(int index, int tabId) {
        assert tabId != Tab.INVALID_TAB_ID;
        if (mTabIdList.contains(tabId)) return;
        mTabIdList.add(index, tabId);
    }

    @Override
    public String getInitialUrlForDocument(int tabId) {
        Entry entry = mEntryMap.get(tabId);
        return entry == null ? null : entry.initialUrl;
    }

    @Override
    public String getCurrentUrlForDocument(int tabId) {
        Entry entry = mEntryMap.get(tabId);
        return entry == null ? null : entry.currentUrl;
    }

    @Override
    public boolean isTabStateReady(int tabId) {
        Entry entry = mEntryMap.get(tabId);
        return entry == null ? true : entry.isTabStateReady;
    }

    @Override
    public TabState getTabStateForDocument(int tabId) {
        Entry entry = mEntryMap.get(tabId);
        return entry == null ? null : entry.getTabState();
    }

    @Override
    public boolean hasEntryForTabId(int tabId) {
        return mEntryMap.get(tabId) != null;
    }

    @Override
    public boolean isRetargetable(int tabId) {
        Entry entry = mEntryMap.get(tabId);
        return entry == null ? false : !entry.canGoBack;
    }

    @Override
    public void addInitializationObserver(InitializationObserver observer) {
        ThreadUtils.assertOnUiThread();
        mInitializationObservers.addObserver(observer);
    }

    @Override
    public void updateRecentlyClosed() {
        ThreadUtils.assertOnUiThread();
        List<Entry> current = mActivityDelegate.getTasksFromRecents(isIncognito());
        Set<Integer> removed = new HashSet<Integer>();
        for (int i = 0; i < mEntryMap.size(); i++) {
            int tabId = mEntryMap.keyAt(i);
            if (isTabIdInEntryList(current, tabId)
                    || mActivityDelegate.isTabAssociatedWithNonDestroyedActivity(
                            isIncognito(), tabId)) {
                continue;
            }
            removed.add(tabId);
        }

        for (Integer tabId : removed) {
            closeTabAt(indexOf(tabId));
        }
    }

    @Override
    public void updateEntry(Intent intent, Tab tab) {
        assert mActivityDelegate.isValidActivity(isIncognito(), intent);

        int id = ActivityDelegate.getTabIdFromIntent(intent);
        if (id == Tab.INVALID_TAB_ID) return;

        Entry currentEntry = mEntryMap.get(id);
        String currentUrl = tab.getUrl();
        boolean canGoBack = tab.canGoBack();
        TabState state = tab.getState();
        if (currentEntry != null
                && currentEntry.tabId == id
                && TextUtils.equals(currentEntry.currentUrl, currentUrl)
                && currentEntry.canGoBack == canGoBack
                && currentEntry.getTabState() == state
                && !tab.isTabStateDirty()) {
            return;
        }

        if (currentEntry == null) {
            currentEntry = new Entry(id, ActivityDelegate.getInitialUrlForDocument(intent));
            mEntryMap.put(id, currentEntry);
        }
        currentEntry.isDirty = true;
        currentEntry.currentUrl = currentUrl;
        currentEntry.canGoBack = canGoBack;
        currentEntry.setTabState(state);

        // TODO(dfalcantara): This is different from how the normal Tab determines when to save its
        // state, but this can't be fixed because we cann't hold onto Tabs in this class.
        tab.setIsTabStateDirty(false);

        if (currentEntry.placeholderTab != null) {
            if (currentEntry.placeholderTab.isInitialized()) currentEntry.placeholderTab.destroy();
            currentEntry.placeholderTab = null;
        }

        writeGeneralDataToStorageAsync();
        writeTabStatesToStorageAsync();
    }

    @Override
    public int getCurrentInitializationStage() {
        return mCurrentState;
    }

    /**
     * Add an entry to the entry map for migration purposes.
     * @param entry The entry to be added.
     *
     * TODO(dfalcantara): Reduce visibility once DocumentMigrationHelper is upstreamed.
     */
    public void addEntryForMigration(Entry entry) {
        addTabId(getCount(), entry.tabId);
        if (mEntryMap.indexOfKey(entry.tabId) >= 0) return;
        mEntryMap.put(entry.tabId, entry);
    }

    // TODO(mariakhomenko): we no longer need prioritized tab id in constructor, shift it here.
    @Override
    public void startTabStateLoad() {
        if (mCurrentState != STATE_READ_RECENT_TASKS_END) return;
        setCurrentState(STATE_LOAD_CURRENT_TAB_STATE_START);
        // Immediately try loading the requested tab.
        if (mPrioritizedTabId != Tab.INVALID_TAB_ID) {
            Entry entry = mEntryMap.get(mPrioritizedTabId);
            if (entry != null) {
                entry.setTabState(
                        mStorageDelegate.restoreTabState(mPrioritizedTabId, isIncognito()));
                entry.isTabStateReady = true;
            }
        }
        setCurrentState(STATE_LOAD_CURRENT_TAB_STATE_END);
        loadTabStatesAsync();
    }

    private void loadTabStatesAsync() {
        new AsyncTask<Void, Void, Void>() {
            private final List<Entry> mEntries = new ArrayList<Entry>(getCount());

            @Override
            public void onPreExecute() {
                setCurrentState(STATE_LOAD_TAB_STATE_BG_START);
                for (int i = 0; i < getCount(); i++) {
                    mEntries.add(new Entry(getTabAt(i).getId()));
                }
            }

            @Override
            public Void doInBackground(Void... params) {
                for (Entry entry : mEntries) {
                    if (mPrioritizedTabId == entry.tabId) continue;
                    entry.setTabState(
                            mStorageDelegate.restoreTabState(entry.tabId, isIncognito()));
                    entry.isTabStateReady = true;
                }

                return null;
            }

            @Override
            public void onPostExecute(Void result) {
                for (Entry pair : mEntries) {
                    Entry entry = mEntryMap.get(pair.tabId);
                    if (entry == null) continue;

                    if (entry.getTabState() == null) entry.setTabState(pair.getTabState());
                    entry.isTabStateReady = true;
                }

                setCurrentState(STATE_LOAD_TAB_STATE_BG_END);
                deserializeTabStatesAsync();
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    private void deserializeTabStatesAsync() {
        if (!shouldStartDeserialization(mCurrentState)) return;

        new AsyncTask<Void, Void, Void>() {
            private final List<Entry> mCachedEntries = new ArrayList<Entry>(mEntryMap.size());

            @Override
            public void onPreExecute() {
                setCurrentState(STATE_DESERIALIZE_START);

                for (int i = 0; i < mEntryMap.size(); i++) {
                    Entry entry = mEntryMap.valueAt(i);
                    if (entry.getTabState() == null) continue;
                    mCachedEntries.add(new Entry(entry.tabId, entry.getTabState()));
                }
            }

            @Override
            public Void doInBackground(Void... params) {
                for (Entry entry : mCachedEntries) {
                    TabState tabState = entry.getTabState();
                    updateEntryInfoFromTabState(entry, tabState);
                }
                return null;
            }

            @Override
            public void onPostExecute(Void result) {
                for (Entry pair : mCachedEntries) {
                    Entry realEntry = mEntryMap.get(pair.tabId);
                    if (realEntry == null || realEntry.currentUrl != null) continue;
                    realEntry.currentUrl = pair.currentUrl;
                }

                setCurrentState(STATE_DESERIALIZE_END);
                if (isNativeInitialized()) {
                    broadcastSessionRestoreComplete();
                    loadHistoricalTabsAsync();
                }
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Call for extending classes to override for getting additional information for an entry from
     * the tab state when it is deserialized.
     * @param entry The {@link Entry} currently being processed
     * @param tabState The {@link TabState} that has been deserialized for the entry.
     */
    protected void updateEntryInfoFromTabState(Entry entry, TabState tabState) {
        entry.currentUrl = tabState.getVirtualUrlFromState();
    }

    /**
     * Checks whether initialization should move to the deserialization step.
     * @param currentState Current initialization stage.
     * @return Whether to proceed or not.
     */
    protected boolean shouldStartDeserialization(int currentState) {
        return isNativeInitialized() && currentState == STATE_LOAD_TAB_STATE_BG_END;
    }

    private void loadHistoricalTabsAsync() {
        new AsyncTask<Void, Void, Void>() {
            private Set<Integer> mHistoricalTabsForBackgroundThread;
            private List<Entry> mEntries;

            @Override
            public void onPreExecute() {
                setCurrentState(STATE_DETERMINE_HISTORICAL_TABS_START);
                mHistoricalTabsForBackgroundThread = new HashSet<Integer>(mHistoricalTabs.size());
                mHistoricalTabsForBackgroundThread.addAll(mHistoricalTabs);
                mEntries = new ArrayList<Entry>(mHistoricalTabsForBackgroundThread.size());
            }

            @Override
            public Void doInBackground(Void... params) {
                for (Integer tabId : mHistoricalTabsForBackgroundThread) {
                    // Read the saved state, then delete the file.
                    TabState state = mStorageDelegate.restoreTabState(tabId, isIncognito());
                    mEntries.add(new Entry(tabId, state));
                    mStorageDelegate.deleteTabState(tabId, isIncognito());
                }

                return null;
            }

            @Override
            public void onPostExecute(Void result) {
                for (Entry entry : mEntries) {
                    if (entry.getTabState() == null) continue;
                    entry.getTabState().contentsState.createHistoricalTab();
                }
                mHistoricalTabs.clear();
                setCurrentState(STATE_DETERMINE_HISTORICAL_TABS_END);
                cleanUpObsoleteTabStatesAsync();
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Clears the folder of TabStates that correspond to missing tasks.
     */
    private void cleanUpObsoleteTabStatesAsync() {
        new AsyncTask<Void, Void, Void>() {
            private List<Entry> mCurrentTabs;

            @Override
            protected void onPreExecute() {
                setCurrentState(STATE_CLEAN_UP_OBSOLETE_TABS);
                mCurrentTabs = mActivityDelegate.getTasksFromRecents(isIncognito());
            }

            @Override
            protected Void doInBackground(Void... voids) {
                File stateDirectory = mStorageDelegate.getStateDirectory();
                String[] files = stateDirectory.list();
                if (files == null) return null;

                for (final String fileName : files) {
                    Pair<Integer, Boolean> tabInfo = TabState.parseInfoFromFilename(fileName);
                    if (tabInfo == null) continue;

                    int tabId = tabInfo.first;
                    boolean incognito = tabInfo.second;
                    if (incognito != isIncognito() || isTabIdInEntryList(mCurrentTabs, tabId)) {
                        continue;
                    }

                    boolean success = new File(stateDirectory, fileName).delete();
                    if (!success) Log.w(TAG, "Failed to delete: " + fileName);
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                setCurrentState(STATE_FULLY_LOADED);
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Save out a tiny file with minimal information required for retargeting.
     */
    private void writeGeneralDataToStorageAsync() {
        if (isIncognito()) return;

        new AsyncTask<Void, Void, Void>() {
            private DocumentList mList;

            @Override
            protected void onPreExecute() {
                List<DocumentEntry> entriesList = new ArrayList<DocumentEntry>();
                for (int i = 0; i < getCount(); i++) {
                    Entry entry = mEntryMap.get(getTabAt(i).getId());
                    if (entry == null) continue;

                    DocumentEntry docEntry = new DocumentEntry();
                    docEntry.tabId = entry.tabId;
                    docEntry.canGoBack = entry.canGoBack;

                    entriesList.add(docEntry);
                }
                mList = new DocumentList();
                mList.entries = entriesList.toArray(new DocumentEntry[entriesList.size()]);
            }

            @Override
            protected Void doInBackground(Void... params) {
                mStorageDelegate.writeTaskFileBytes(isIncognito(), MessageNano.toByteArray(mList));
                return null;
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Write out all of the TabStates.
     */
    private void writeTabStatesToStorageAsync() {
        new AsyncTask<Void, Void, Void>() {
            private final SparseArray<TabState> mStatesToWrite = new SparseArray<TabState>();

            @Override
            protected void onPreExecute() {
                for (int i = 0; i < mEntryMap.size(); i++) {
                    Entry entry = mEntryMap.valueAt(i);
                    if (!entry.isDirty || entry.getTabState() == null) continue;
                    mStatesToWrite.put(entry.tabId, entry.getTabState());
                }
            }

            @Override
            protected Void doInBackground(Void... voids) {
                for (int i = 0; i < mStatesToWrite.size(); i++) {
                    int tabId = mStatesToWrite.keyAt(i);
                    mStorageDelegate.saveTabState(tabId, isIncognito(), mStatesToWrite.valueAt(i));
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                for (int i = 0; i < mStatesToWrite.size(); i++) {
                    int tabId = mStatesToWrite.keyAt(i);
                    Entry entry = mEntryMap.get(tabId);
                    if (entry == null) continue;
                    entry.isDirty = false;
                }
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    private void setCurrentState(int newState) {
        ThreadUtils.assertOnUiThread();
        assert mCurrentState == newState - 1;
        mCurrentState = newState;

        for (InitializationObserver observer : mInitializationObservers) {
            if (observer.isCanceled()) {
                Log.w(TAG, "Observer alerted after canceled: " + observer);
                mInitializationObservers.removeObserver(observer);
            } else if (observer.isSatisfied(mCurrentState)) {
                observer.runWhenReady();
                mInitializationObservers.removeObserver(observer);
            }
        }
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
        for (int i = getCount() - 1; i >= 0; i--) closeTabAt(i);
        if (isIncognito()) IncognitoNotificationManager.dismissIncognitoNotification();
    }

    @Override
    public void moveTab(int id, int newIndex) {
        newIndex = MathUtils.clamp(newIndex, 0, getCount());
        int curIndex = TabModelUtils.getTabIndexById(this, id);
        if (curIndex == INVALID_TAB_INDEX || curIndex == newIndex || curIndex + 1 == newIndex) {
            return;
        }

        mTabIdList.remove(curIndex);
        addTabId(newIndex, id);

        Tab tab = getTabAt(curIndex);
        if (tab == null) return;
        for (TabModelObserver obs : mObservers) obs.didMoveTab(tab, newIndex, curIndex);
    }

    @Override
    public void destroy() {
        super.destroy();
        mInitializationObservers.clear();
        mObservers.clear();
    }

    @Override
    public void addTab(Intent intent, Tab tab) {
        if (tab.getId() == Tab.INVALID_TAB_ID
                || ActivityDelegate.getTabIdFromIntent(intent) != tab.getId()) {
            return;
        }

        int parentIndex = indexOf(tab.getParentId());
        int index = parentIndex == -1 ? getCount() : parentIndex + 1;
        addTab(tab, index, tab.getLaunchType());
        updateEntry(intent, tab);
    }

    @Override
    public void addTab(Tab tab, int index, TabLaunchType type) {
        // TODO(dfalcantara): Prevent this method from being called directly instead of going
        //                    through addTab(Intent intent, Tab tab).
        if (tab.getId() == Tab.INVALID_TAB_ID) return;

        for (TabModelObserver obs : mObservers) obs.willAddTab(tab, type);

        if (index == TabModel.INVALID_TAB_INDEX) {
            addTabId(getCount(), tab.getId());
        } else {
            addTabId(index, tab.getId());
        }

        tabAddedToModel(tab);
        for (TabModelObserver obs : mObservers) obs.didAddTab(tab, type);
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
        mObservers.addObserver(observer);
    }

    @Override
    public void removeObserver(TabModelObserver observer) {
        mObservers.removeObserver(observer);
    }

    private static boolean isTabIdInEntryList(List<Entry> entries, int tabId) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).tabId == tabId) return true;
        }
        return false;
    }
}
