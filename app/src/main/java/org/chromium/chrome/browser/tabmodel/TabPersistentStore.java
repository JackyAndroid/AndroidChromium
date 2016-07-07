// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import android.content.Context;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseIntArray;

import org.chromium.base.ImportantFileWriterAndroid;
import org.chromium.base.Log;
import org.chromium.base.StreamUtil;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.LoadUrlParams;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * This class handles saving and loading tab state from the persistent storage.
 */
public class TabPersistentStore extends TabPersister {
    private static final String TAG = "tabmodel";

    /**
     * The current version of the saved state file.
     * Version 4: In addition to the tab's ID, save the tab's last URL.
     * Version 5: In addition to the total tab count, save the incognito tab count.
     */
    private static final int SAVED_STATE_VERSION = 5;

    private static final String BASE_STATE_FOLDER = "tabs";

    /** The name of the file where the state is saved. */
    @VisibleForTesting
    public static final String SAVED_STATE_FILE = "tab_state";

    /** Prevents two copies of the Migration task from being created. */
    private static final Object MIGRATION_LOCK = new Object();

    /** Prevents race conditions when setting the sBaseStateDirectory. */
    private static final Object BASE_STATE_DIRECTORY_LOCK = new Object();

    /**
     * Callback interface to use while reading the persisted TabModelSelector info from disk.
     */
    public static interface OnTabStateReadCallback {
        /**
         * To be called as the details about a persisted Tab are read from the TabModelSelector's
         * persisted data.
         * @param index                  The index out of all tabs for the current tab read.
         * @param id                     The id for the current tab read.
         * @param url                    The url for the current tab read.
         * @param isIncognito            Whether the Tab is definitely Incognito, or null if it
         *                               couldn't be determined because we didn't know how many
         *                               Incognito tabs were saved out.
         * @param isStandardActiveIndex  Whether the current tab read is the normal active tab.
         * @param isIncognitoActiveIndex Whether the current tab read is the incognito active tab.
         */
        void onDetailsRead(int index, int id, String url, Boolean isIncognito,
                boolean isStandardActiveIndex, boolean isIncognitoActiveIndex);
    }

    /**
     * Alerted at various stages of operation.
     */
    public static interface TabPersistentStoreObserver {
        /**
         * To be called when the file containing the initial information about the TabModels has
         * been loaded.
         * @param tabCountAtStartup How many tabs there are in the TabModels.
         */
        void onInitialized(int tabCountAtStartup);

        /**
         * Called when details about a Tab are read from the metadata file.
         */
        void onDetailsRead(int index, int id, String url,
                boolean isStandardActiveIndex, boolean isIncognitoActiveIndex);

        /**
         * To be called when the TabStates have all been loaded.
         * @param context Context used by the TabPersistentStore.
         */
        void onStateLoaded(Context context);

        /**
         * Called when the metadata file has been saved out asynchronously.
         * This currently does not get called when the metadata file is saved out on the UI thread.
         */
        void onMetadataSavedAsynchronously();
    }

    private static FileMigrationTask sMigrationTask = null;
    private static File sBaseStateDirectory;

    private final TabModelSelector mTabModelSelector;
    private final TabCreatorManager mTabCreatorManager;
    private final Context mContext;
    private final int mSelectorIndex;
    private final TabPersistentStoreObserver mObserver;
    private final Object mSaveListLock = new Object();

    private TabContentManager mTabContentManager;

    private final Deque<Tab> mTabsToSave;
    private final Deque<TabRestoreDetails> mTabsToRestore;

    private LoadTabTask mLoadTabTask;
    private SaveTabTask mSaveTabTask;
    private SaveListTask mSaveListTask;

    private boolean mDestroyed;
    private boolean mCancelNormalTabLoads = false;
    private boolean mCancelIncognitoTabLoads = false;

    private File mStateDirectory;

    // Keys are the original tab indexes, values are the tab ids.
    private SparseIntArray mNormalTabsRestored;
    private SparseIntArray mIncognitoTabsRestored;


    /**
     * Creates an instance of a TabPersistentStore.
     * @param modelSelector The {@link TabModelSelector} to restore to and save from.
     * @param selectorIndex The index that represents which sub folder to pull and save state to.
     *                      This is used when there can be more than one TabModelSelector.
     * @param context       A Context instance.
     * @param tabCreatorManager The {@link TabCreatorManager} to use.
     * @param observer      Notified when the TabPersistentStore has completed tasks.
     */
    public TabPersistentStore(TabModelSelector modelSelector, int selectorIndex, Context context,
            TabCreatorManager tabCreatorManager, TabPersistentStoreObserver observer) {
        mTabModelSelector = modelSelector;
        mContext = context;
        mTabCreatorManager = tabCreatorManager;
        mTabsToSave = new ArrayDeque<Tab>();
        mTabsToRestore = new ArrayDeque<TabRestoreDetails>();
        mSelectorIndex = selectorIndex;
        mObserver = observer;
        createMigrationTask();
    }

    private final void createMigrationTask() {
        synchronized (MIGRATION_LOCK) {
            if (sMigrationTask == null) {
                sMigrationTask = new FileMigrationTask();
                sMigrationTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            }
        }
    }

    @Override
    public File getStateDirectory() {
        if (mStateDirectory == null) {
            mStateDirectory = getStateDirectory(mContext, mSelectorIndex);
        }
        return mStateDirectory;
    }

    /**
     * Sets where the base state directory is.  If overridding this value, set it before the
     * instance's mStateDirectory field is initialized.
     */
    @VisibleForTesting
    public static void setBaseStateDirectory(File directory) {
        synchronized (BASE_STATE_DIRECTORY_LOCK) {
            sBaseStateDirectory = directory;
        }
    }

    /**
     * @return Folder that all metadata for the ChromeTabbedActivity TabModels should be located.
     *         Each subdirectory stores info about different instances of ChromeTabbedActivity.
     */
    public static File getBaseStateDirectory(Context context) {
        if (sBaseStateDirectory == null) {
            setBaseStateDirectory(context.getDir(BASE_STATE_FOLDER, Context.MODE_PRIVATE));
        }
        return sBaseStateDirectory;
    }

    /**
     * The folder where the state should be saved to.
     * @param context A Context instance.
     * @param index   The TabModelSelector index.
     * @return        A file representing the directory that contains the TabModelSelector state.
     */
    public static File getStateDirectory(Context context, int index) {
        File file = new File(getBaseStateDirectory(context), Integer.toString(index));
        if (!file.exists() && !file.mkdirs()) Log.e(TAG, "Failed to create state folder: " + file);
        return file;
    }

    /**
     * Waits for the task that migrates all state files to their new location to finish.
     */
    @VisibleForTesting
    public static void waitForMigrationToFinish() throws InterruptedException, ExecutionException {
        assert sMigrationTask != null : "The migration should be initialized by now.";
        sMigrationTask.get();
    }

    private static void logSaveException(Exception e) {
        Log.w(TAG, "Error while saving tabs state; will attempt to continue...", e);
    }

    /**
     * Sets the {@link TabContentManager} to use.
     * @param cache The {@link TabContentManager} to use.
     */
    public void setTabContentManager(TabContentManager cache) {
        mTabContentManager = cache;
    }

    public void saveState() {
        // Temporarily allowing disk access. TODO: Fix. See http://b/5518024
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            // The list of tabs should be saved first in case our activity is terminated early.
            // Explicitly toss out any existing SaveListTask because they only save the TabModel as
            // it looked when the SaveListTask was first created.
            if (mSaveListTask != null) mSaveListTask.cancel(true);
            try {
                saveListToFile(serializeTabMetadata());
            } catch (IOException e) {
                logSaveException(e);
            }

            // Add current tabs to save because they did not get a save signal yet.
            Tab currentStandardTab = TabModelUtils.getCurrentTab(mTabModelSelector.getModel(false));
            if (currentStandardTab != null && !mTabsToSave.contains(currentStandardTab)
                    && currentStandardTab.isTabStateDirty()
                    // For content URI, the read permission granted to an activity is not
                    // persistent.
                    && !isTabUrlContentScheme(currentStandardTab)) {
                mTabsToSave.addLast(currentStandardTab);
            }
            Tab currentIncognitoTab = TabModelUtils.getCurrentTab(mTabModelSelector.getModel(true));
            if (currentIncognitoTab != null && !mTabsToSave.contains(currentIncognitoTab)
                    && currentIncognitoTab.isTabStateDirty()
                    && !isTabUrlContentScheme(currentIncognitoTab)) {
                mTabsToSave.addLast(currentIncognitoTab);
            }
            // Wait for the current tab to save.
            if (mSaveTabTask != null) {
                // Cancel calls get() to wait for this to finish internally if it has to.
                // The issue is it may assume it cancelled the task, but the task still actually
                // wrote the state to disk.  That's why we have to check mStateSaved here.
                if (mSaveTabTask.cancel(false) && !mSaveTabTask.mStateSaved) {
                    // The task was successfully cancelled.  We should try to save this state again.
                    Tab cancelledTab = mSaveTabTask.mTab;
                    if (!mTabsToSave.contains(cancelledTab)
                            && cancelledTab.isTabStateDirty()
                            && !isTabUrlContentScheme(cancelledTab)) {
                        mTabsToSave.addLast(cancelledTab);
                    }
                }

                mSaveTabTask = null;
            }

            // Synchronously save any remaining unsaved tabs (hopefully very few).
            for (Tab tab : mTabsToSave) {
                int id = tab.getId();
                boolean incognito = tab.isIncognito();
                try {
                    TabState state = tab.getState();
                    if (state != null) {
                        TabState.saveState(
                                openTabStateOutputStream(id, incognito), state, incognito);
                    }
                } catch (IOException e) {
                    logSaveException(e);
                } catch (OutOfMemoryError e) {
                    Log.w(TAG, "Out of memory error while attempting to save tab state.  Erasing.");
                    deleteTabState(id, incognito);
                }
            }
            mTabsToSave.clear();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Restore saved state. Must be called before any tabs are added to the list.
     *
     * @return The next tab ID to use for new tabs.
     */
    public int loadState() {
        try {
            waitForMigrationToFinish();
        } catch (InterruptedException e) {
            // Ignore these exceptions, we'll do the best we can.
        } catch (ExecutionException e) {
            // Ignore these exceptions, we'll do the best we can.
        }

        mCancelNormalTabLoads = false;
        mCancelIncognitoTabLoads = false;
        mNormalTabsRestored = new SparseIntArray();
        mIncognitoTabsRestored = new SparseIntArray();
        int nextId = 0;
        try {
            nextId = loadStateInternal();
        } catch (Exception e) {
            // Catch generic exception to prevent a corrupted state from crashing the app
            // at startup.
            Log.d(TAG, "loadState exception: " + e.toString(), e);
        }

        // As everything is loaded asynchronously user actions can create a tab that has ids
        // pointing to old files and not deleted fast enough. This makes sure to delete everything
        // that we are sure not to use.
        // This assumes that the app will only create tab with id at and above nextId.
        cleanupPersistentDataAtAndAboveId(nextId);

        if (mObserver != null) mObserver.onInitialized(mTabsToRestore.size());
        return nextId;
    }

    /**
     * Restore tab state.  Tab state is loaded asynchronously, other than the active tab which
     * can be forced to load synchronously.
     *
     * @param setActiveTab If true the last active tab given in the saved state is loaded
     *                     synchronously and set as the current active tab. If false all tabs are
     *                     loaded asynchronously.
     */
    public void restoreTabs(boolean setActiveTab) {
        if (setActiveTab) {
            // Restore and select the active tab, which is first in the restore list.
            // If the active tab can't be restored, restore and select another tab. Otherwise, the
            // tab model won't have a valid index and the UI will break. http://crbug.com/261378
            while (!mTabsToRestore.isEmpty()
                    && mNormalTabsRestored.size() == 0
                    && mIncognitoTabsRestored.size() == 0) {
                TabRestoreDetails tabToRestore = mTabsToRestore.removeFirst();
                restoreTab(tabToRestore, true);
            }
        }
        loadNextTab();
    }

    /**
     * If a tab is being restored with the given url, then restore the tab in a frozen state
     * synchronously.
     */
    public void restoreTabStateForUrl(String url) {
        restoreTabStateInternal(url, Tab.INVALID_TAB_ID);
    }

    /**
     * If a tab is being restored with the given id, then restore the tab in a frozen state
     * synchronously.
     */
    public void restoreTabStateForId(int id) {
        restoreTabStateInternal(null, id);
    }

    private void restoreTabStateInternal(String url, int id) {
        TabRestoreDetails tabToRestore = null;
        if (mLoadTabTask != null) {
            if ((url == null && mLoadTabTask.mTabToRestore.id == id)
                    || (url != null && TextUtils.equals(mLoadTabTask.mTabToRestore.url, url))) {
                // Steal the task of restoring the tab from the active load tab task.
                mLoadTabTask.cancel(false);
                tabToRestore = mLoadTabTask.mTabToRestore;
                loadNextTab();  // Queue up async task to load next tab after we're done here.
            }
        }

        if (tabToRestore == null) {
            if (url == null) {
                tabToRestore = getTabToRestoreById(id);
            } else {
                tabToRestore = getTabToRestoreByUrl(url);
            }
        }

        if (tabToRestore != null) {
            mTabsToRestore.remove(tabToRestore);
            restoreTab(tabToRestore, false);
        }
    }

    private void restoreTab(TabRestoreDetails tabToRestore, boolean setAsActive) {
        // As we do this in startup, and restoring the active tab's state is critical, we permit
        // this read.
        // TODO(joth): An improved solution would be to pre-read the files on a background and
        // block here waiting for that task to complete only if needed. See http://b/5518170
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            TabState state = TabState.restoreTabState(getStateDirectory(), tabToRestore.id);
            restoreTab(tabToRestore, state, setAsActive);
        } catch (Exception e) {
            // Catch generic exception to prevent a corrupted state from crashing the app
            // at startup.
            Log.d(TAG, "loadTabs exception: " + e.toString(), e);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private void restoreTab(
            TabRestoreDetails tabToRestore, TabState tabState, boolean setAsActive) {
        // If we don't have enough information about the Tab, bail out.
        boolean isIncognito = isIncognitoTabBeingRestored(tabToRestore, tabState);
        if (tabState == null) {
            if (tabToRestore.isIncognito == null) {
                Log.w(TAG, "Failed to restore tab: not enough info about its type was available.");
                return;
            } else if (isIncognito) {
                Log.i(TAG, "Failed to restore Incognito tab: its TabState could not be restored.");
                return;
            }
        }

        TabModel model = mTabModelSelector.getModel(isIncognito);
        SparseIntArray restoredTabs = isIncognito ? mIncognitoTabsRestored : mNormalTabsRestored;
        int restoredIndex = 0;
        if (restoredTabs.size() > 0
                && tabToRestore.originalIndex > restoredTabs.keyAt(restoredTabs.size() - 1)) {
            // Restore at end if our index is greater than all restored tabs.
            restoredIndex = restoredTabs.size();
        } else {
             // Otherwise try to find the tab we should restore before, if any.
            for (int i = 0; i < restoredTabs.size(); i++) {
                if (restoredTabs.keyAt(i) > tabToRestore.originalIndex) {
                    Tab nextTabByIndex = TabModelUtils.getTabById(model, restoredTabs.valueAt(i));
                    restoredIndex = nextTabByIndex != null ? model.indexOf(nextTabByIndex) : -1;
                    break;
                }
            }
        }

        if (tabState != null) {
            mTabCreatorManager.getTabCreator(isIncognito).createFrozenTab(
                    tabState, tabToRestore.id, restoredIndex);
        } else {
            Log.w(TAG, "Failed to restore TabState; creating Tab with last known URL.");
            Tab fallbackTab = mTabCreatorManager.getTabCreator(isIncognito).createNewTab(
                    new LoadUrlParams(tabToRestore.url), TabModel.TabLaunchType.FROM_LINK, null);
            model.moveTab(fallbackTab.getId(), restoredIndex);
        }

        if (setAsActive) {
            TabModelUtils.setIndex(model, TabModelUtils.getTabIndexById(model, tabToRestore.id));
        }
        restoredTabs.put(tabToRestore.originalIndex, tabToRestore.id);
    }

    /**
     * @return Number of restored tabs on cold startup.
     */
    public int getRestoredTabCount() {
        return mTabsToRestore.size();
    }

    public void clearState() {
        deleteFileAsync(SAVED_STATE_FILE);
        cleanupPersistentData();
        onStateLoaded();
    }

    /**
     * Clears all the encrypted data from the disk.
     * Most likely called when we lost the encryption key.
     */
    public void clearEncryptedState() {
        cleanupAllEncryptedPersistentData();
    }

    /**
     * Cancels loading of {@link Tab}s from disk from saved state. This is useful if the user
     * does an action which impacts all {@link Tab}s, not just the ones currently loaded into
     * the model. For example, if the user tries to close all {@link Tab}s, we need don't want
     * to restore old {@link Tab}s anymore.
     *
     * @param incognito Whether or not to ignore incognito {@link Tab}s or normal
     *                  {@link Tab}s as they are being restored.
     */
    public void cancelLoadingTabs(boolean incognito) {
        if (incognito) {
            mCancelIncognitoTabLoads = true;
        } else {
            mCancelNormalTabLoads = true;
        }
    }

    public void addTabToSaveQueue(Tab tab) {
        if (!mTabsToSave.contains(tab) && tab.isTabStateDirty() && !isTabUrlContentScheme(tab)) {
            mTabsToSave.addLast(tab);
        }
        saveNextTab();
    }

    public void removeTabFromQueues(Tab tab) {
        mTabsToSave.remove(tab);
        mTabsToRestore.remove(getTabToRestoreById(tab.getId()));

        if (mLoadTabTask != null && mLoadTabTask.mTabToRestore.id == tab.getId()) {
            mLoadTabTask.cancel(false);
            mLoadTabTask = null;
            loadNextTab();
        }

        if (mSaveTabTask != null && mSaveTabTask.mId == tab.getId()) {
            mSaveTabTask.cancel(false);
            mSaveTabTask = null;
            saveNextTab();
        }

        cleanupPersistentData(tab.getId(), tab.isIncognito());
    }

    private TabRestoreDetails getTabToRestoreByUrl(String url) {
        for (TabRestoreDetails tabBeingRestored : mTabsToRestore) {
            if (TextUtils.equals(tabBeingRestored.url, url)) {
                return tabBeingRestored;
            }
        }
        return null;
    }

    private TabRestoreDetails getTabToRestoreById(int id) {
        for (TabRestoreDetails tabBeingRestored : mTabsToRestore) {
            if (tabBeingRestored.id == id) {
                return tabBeingRestored;
            }
        }
        return null;
    }

    public void destroy() {
        mDestroyed = true;
        if (mLoadTabTask != null) mLoadTabTask.cancel(true);
        mTabsToSave.clear();
        mTabsToRestore.clear();
        if (mSaveTabTask != null) mSaveTabTask.cancel(false);
        if (mSaveListTask != null) mSaveListTask.cancel(true);
    }

    private void cleanupPersistentData(int id, boolean incognito) {
        deleteFileAsync(TabState.getTabStateFilename(id, incognito));
        // No need to forward that event to the tab content manager as this is already
        // done as part of the standard tab removal process.
    }

    private byte[] serializeTabMetadata() throws IOException {
        List<TabRestoreDetails> tabsToRestore = new ArrayList<TabRestoreDetails>();

        // The metadata file may be being written out before all of the Tabs have been restored.
        // Save that information out, as well.
        if (mLoadTabTask != null) tabsToRestore.add(mLoadTabTask.mTabToRestore);
        for (TabRestoreDetails details : mTabsToRestore) {
            tabsToRestore.add(details);
        }

        return serializeTabModelSelector(mTabModelSelector, tabsToRestore);
    }

    /**
     * Serializes {@code selector} to a byte array, copying out the data pertaining to tab ordering
     * and selected indices.
     * @param selector      The {@link TabModelSelector} to serialize.
     * @param tabsToRestore Tabs that are in the process of being restored.
     * @return              {@code byte[]} containing the serialized state of {@code selector}.
     */
    @VisibleForTesting
    public static byte[] serializeTabModelSelector(TabModelSelector selector,
            List<TabRestoreDetails> tabsToRestore) throws IOException {
        ThreadUtils.assertOnUiThread();

        TabModel incognitoList = selector.getModel(true);
        TabModel standardList = selector.getModel(false);

        // Determine how many Tabs there are, including those not yet been added to the TabLists.
        int numAlreadyLoaded = incognitoList.getCount() + standardList.getCount();
        int numStillBeingLoaded = tabsToRestore == null ? 0 : tabsToRestore.size();
        int numTabsTotal = numStillBeingLoaded + numAlreadyLoaded;

        // Save the index file containing the list of tabs to restore.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeInt(SAVED_STATE_VERSION);
        stream.writeInt(numTabsTotal);
        stream.writeInt(incognitoList.getCount());
        stream.writeInt(incognitoList.index());
        stream.writeInt(standardList.index() + incognitoList.getCount());
        Log.d(TAG, "Serializing tab lists; counts: " + standardList.getCount()
                + ", " + incognitoList.getCount()
                + ", " + (tabsToRestore == null ? 0 : tabsToRestore.size()));

        // Save incognito state first, so when we load, if the incognito files are unreadable
        // we can fall back easily onto the standard selected tab.
        for (int i = 0; i < incognitoList.getCount(); i++) {
            stream.writeInt(incognitoList.getTabAt(i).getId());
            stream.writeUTF(incognitoList.getTabAt(i).getUrl());
        }
        for (int i = 0; i < standardList.getCount(); i++) {
            stream.writeInt(standardList.getTabAt(i).getId());
            stream.writeUTF(standardList.getTabAt(i).getUrl());
        }

        // Write out information about the tabs that haven't finished being loaded.
        // We shouldn't have to worry about Tab duplication because the tab details are processed
        // only on the UI Thread.
        if (tabsToRestore != null) {
            for (TabRestoreDetails details : tabsToRestore) {
                stream.writeInt(details.id);
                stream.writeUTF(details.url);
            }
        }

        stream.close();
        return output.toByteArray();
    }

    private void saveListToFile(byte[] listData) {
        synchronized (mSaveListLock) {
            // Save the index file containing the list of tabs to restore.
            String fileName = new File(getStateDirectory(), SAVED_STATE_FILE).getAbsolutePath();
            ImportantFileWriterAndroid.writeFileAtomically(fileName, listData);
        }
    }

    /**
     * Load the saved state of the tab model. No tabs will be restored until you call
     * {@link #restoreTabs(boolean)}. Must be called before any tabs are added to the list.
     *
     * @throws IOException
     */
    @VisibleForTesting
    public int loadStateInternal() throws IOException {
        // Temporarily allowing disk access. TODO: Fix. See http://crbug.com/473357
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            assert  mTabModelSelector.getModel(true).getCount() == 0;
            assert  mTabModelSelector.getModel(false).getCount() == 0;
            int maxId = 0;

            File[] folders = getBaseStateDirectory(mContext).listFiles();
            if (folders == null) return maxId;

            File stateFolder = getStateDirectory();
            for (File folder : folders) {
                assert folder.isDirectory();
                if (!folder.isDirectory()) continue;
                boolean readDir = folder.equals(stateFolder);
                final Deque<TabRestoreDetails> restoreList = readDir ? mTabsToRestore : null;
                final boolean isIncognitoSelected = mTabModelSelector.isIncognitoSelected();

                // TODO(dfalcantara): Store the max tab ID in a shared preference so that it can be
                //                    shared with all of the other modes that Chrome runs in and to
                //                    avoid reading in all of the possible app_tabs subdirectories.
                int curId = readSavedStateFile(folder, new OnTabStateReadCallback() {
                    @Override
                    public void onDetailsRead(int index, int id, String url, Boolean isIncognito,
                            boolean isStandardActiveIndex, boolean isIncognitoActiveIndex) {
                        // If we're not trying to build the restore list skip the build part.
                        // We've already read all the state for this entry from the input stream.
                        if (restoreList == null) return;

                        // Note that incognito tab may not load properly so we may need to use
                        // the current tab from the standard model.
                        // This logic only works because we store the incognito indices first.
                        TabRestoreDetails details =
                                new TabRestoreDetails(id, index, isIncognito, url);

                        if ((isIncognitoActiveIndex && isIncognitoSelected)
                                || (isStandardActiveIndex && !isIncognitoSelected)) {
                            // Active tab gets loaded first
                            restoreList.addFirst(details);
                        } else {
                            restoreList.addLast(details);
                        }

                        if (mObserver != null) {
                            mObserver.onDetailsRead(
                                    index, id, url, isStandardActiveIndex, isIncognitoActiveIndex);
                        }
                    }
                });
                maxId = Math.max(maxId, curId);
            }
            return maxId;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    public static int readSavedStateFile(File folder, OnTabStateReadCallback callback)
            throws IOException {
        DataInputStream stream = null;
        // As we do this in startup, and restoring the tab state is critical to
        // initializing all other state, we permit this one read.
        // TODO(joth): An improved solution would be to pre-read the files on a background and
        // block here waiting for that task to complete only if needed. See http://b/5518170
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            File stateFile = new File(folder, SAVED_STATE_FILE);
            if (!stateFile.exists()) return 0;

            stream = new DataInputStream(new BufferedInputStream(new FileInputStream(stateFile)));

            int nextId = 0;
            boolean skipUrlRead = false;
            boolean skipIncognitoCount = false;
            final int version = stream.readInt();
            if (version != SAVED_STATE_VERSION) {
                // We don't support restoring Tab data from before M18.
                if (version < 3) return 0;

                // Older versions are missing newer data.
                if (version < 5) skipIncognitoCount = true;
                if (version < 4) skipUrlRead = true;
            }

            final int count = stream.readInt();
            final int incognitoCount = skipIncognitoCount ? -1 : stream.readInt();
            final int incognitoActiveIndex = stream.readInt();
            final int standardActiveIndex = stream.readInt();
            if (count < 0 || incognitoActiveIndex >= count || standardActiveIndex >= count) {
                throw new IOException();
            }

            for (int i = 0; i < count; i++) {
                int id = stream.readInt();
                String tabUrl = skipUrlRead ? "" : stream.readUTF();
                if (id >= nextId) nextId = id + 1;

                Boolean isIncognito = (incognitoCount < 0) ? null : i < incognitoCount;
                callback.onDetailsRead(i, id, tabUrl, isIncognito,
                        i == standardActiveIndex, i == incognitoActiveIndex);
            }
            return nextId;
        } finally {
            StreamUtil.closeQuietly(stream);
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private void saveNextTab() {
        if (mSaveTabTask != null) return;
        if (!mTabsToSave.isEmpty()) {
            Tab tab = mTabsToSave.removeFirst();
            mSaveTabTask = new SaveTabTask(tab);
            mSaveTabTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        } else {
            saveTabListAsynchronously();
        }
    }

    /**
     * Kick off an AsyncTask to save the current list of Tabs.
     */
    public void saveTabListAsynchronously() {
        if (mSaveListTask != null) mSaveListTask.cancel(true);
        mSaveListTask = new SaveListTask();
        mSaveListTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    private class SaveTabTask extends AsyncTask<Void, Void, Void> {
        Tab mTab;
        int mId;
        TabState mState;
        boolean mEncrypted;
        boolean mStateSaved = false;

        SaveTabTask(Tab tab) {
            mTab = tab;
            mId = tab.getId();
            mEncrypted = tab.isIncognito();
        }

        @Override
        protected void onPreExecute() {
            if (mDestroyed || isCancelled()) return;
            mState = mTab.getState();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            mStateSaved = saveTabState(mId, mEncrypted, mState);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (mDestroyed || isCancelled()) return;
            if (mStateSaved) mTab.setIsTabStateDirty(false);
            mSaveTabTask = null;
            saveNextTab();
        }
    }

    private class SaveListTask extends AsyncTask<Void, Void, Void> {
        byte[] mListData;

        @Override
        protected void onPreExecute() {
            if (mDestroyed || isCancelled()) return;
            try {
                mListData = serializeTabMetadata();
            } catch (IOException e) {
                mListData = null;
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (mListData == null || isCancelled()) return null;
            saveListToFile(mListData);
            mListData = null;
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (mDestroyed || isCancelled()) return;

            if (mSaveListTask == this) {
                mSaveListTask = null;
                if (mObserver != null) mObserver.onMetadataSavedAsynchronously();
            }
        }
    }

    private void onStateLoaded() {
        if (mObserver != null) mObserver.onStateLoaded(mContext);
    }

    private void loadNextTab() {
        if (mDestroyed) return;

        if (mTabsToRestore.isEmpty()) {
            mNormalTabsRestored = null;
            mIncognitoTabsRestored = null;
            cleanupPersistentData();
            onStateLoaded();
            mLoadTabTask = null;
            Log.d(TAG, "Loaded tab lists; counts: " + mTabModelSelector.getModel(false).getCount()
                    + "," + mTabModelSelector.getModel(true).getCount());
        } else {
            TabRestoreDetails tabToRestore = mTabsToRestore.removeFirst();
            mLoadTabTask = new LoadTabTask(tabToRestore);
            mLoadTabTask.execute();
        }
    }

    private void cleanupPersistentData() {
        String[] files = getStateDirectory().list();
        if (files != null) {
            for (String file : files) {
                Pair<Integer, Boolean> data = TabState.parseInfoFromFilename(file);
                if (data != null) {
                    TabModel model = mTabModelSelector.getModel(data.second);
                    if (TabModelUtils.getTabById(model, data.first) == null) {
                        deleteFileAsync(file);
                    }
                }
            }
        }

        if (mTabContentManager != null) {
            mTabContentManager.cleanupPersistentData(mTabModelSelector);
        }
    }

    private void cleanupPersistentDataAtAndAboveId(int minForbiddenId)  {
        String[] files = getStateDirectory().list();
        if (files != null) {
            for (String file : files) {
                Pair<Integer, Boolean> data = TabState.parseInfoFromFilename(file);
                if (data != null && data.first >= minForbiddenId) {
                    deleteFileAsync(file);
                }
            }
        }

        if (mTabContentManager != null) {
            mTabContentManager.cleanupPersistentDataAtAndAboveId(minForbiddenId);
        }
    }

    private void cleanupAllEncryptedPersistentData() {
        String[] files = getStateDirectory().list();
        if (files != null) {
            for (String file : files) {
                if (file.startsWith(TabState.SAVED_TAB_STATE_FILE_PREFIX_INCOGNITO)) {
                    deleteFileAsync(file);
                }
            }
        }
    }

    private void deleteFileAsync(final String file) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                File stateFile = new File(getStateDirectory(), file);
                if (stateFile.exists()) {
                    if (!stateFile.delete()) Log.e(TAG, "Failed to delete file: " + stateFile);
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        // Explicitly serializing file mutations (save & delete) to ensure they occur in order.
    }

    private class LoadTabTask extends AsyncTask<Void, Void, TabState> {

        public final TabRestoreDetails mTabToRestore;

        public LoadTabTask(TabRestoreDetails tabToRestore) {
            mTabToRestore = tabToRestore;
        }

        @Override
        protected TabState doInBackground(Void... voids) {
            if (mDestroyed || isCancelled()) return null;
            try {
                return TabState.restoreTabState(getStateDirectory(), mTabToRestore.id);
            } catch (Exception e) {
                Log.w(TAG, "Unable to read state: " + e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(TabState tabState) {
            if (mDestroyed || isCancelled()) return;

            boolean isIncognito = isIncognitoTabBeingRestored(mTabToRestore, tabState);
            boolean isLoadCancelled = (isIncognito && mCancelIncognitoTabLoads)
                    || (!isIncognito && mCancelNormalTabLoads);
            if (!isLoadCancelled) restoreTab(mTabToRestore, tabState, false);

            loadNextTab();
        }
    }

    private static final class TabRestoreDetails {

        public final int id;
        public final int originalIndex;
        public final String url;
        public final Boolean isIncognito;

        public TabRestoreDetails(int id, int originalIndex, Boolean isIncognito, String url) {
            this.id = id;
            this.originalIndex = originalIndex;
            this.url = url;
            this.isIncognito = isIncognito;
        }
    }

    private class FileMigrationTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            File oldFolder = mContext.getFilesDir();
            File newFolder = getStateDirectory();
            // If we already have files here just return.
            File[] newFiles = newFolder.listFiles();
            if (newFiles != null && newFiles.length > 0) return null;

            File modelFile = new File(oldFolder, SAVED_STATE_FILE);
            if (modelFile.exists()) {
                if (!modelFile.renameTo(new File(newFolder, SAVED_STATE_FILE))) {
                    Log.e(TAG, "Failed to rename file: " + modelFile);
                }
            }

            File[] files = oldFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (TabState.parseInfoFromFilename(file.getName()) != null) {
                        if (!file.renameTo(new File(newFolder, file.getName()))) {
                            Log.e(TAG, "Failed to rename file: " + file);
                        }
                    }
                }
            }

            return null;
        }
    }

    private boolean isTabUrlContentScheme(Tab tab) {
        String url = tab.getUrl();
        return url != null && url.startsWith("content");
    }

    /**
     * Determines if a Tab being restored is definitely an Incognito Tab.
     *
     * This function can fail to determine if a Tab is incognito if not enough data about the Tab
     * was successfully saved out.
     *
     * @return True if the tab is definitely Incognito, false if it's not or if it's undecideable.
     */
    private static boolean isIncognitoTabBeingRestored(
            TabRestoreDetails tabDetails, TabState tabState) {
        if (tabState != null) {
            // The Tab's previous state was completely restored.
            return tabState.isIncognito();
        } else if (tabDetails.isIncognito != null) {
            // The TabState couldn't be restored, but we have some information about the tab.
            return tabDetails.isIncognito;
        } else {
            // The tab's type is undecideable.
            return false;
        }
    }
}
