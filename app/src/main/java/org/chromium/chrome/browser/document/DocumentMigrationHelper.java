// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.app.ActivityManager.TaskDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ImportantFileWriterAndroid;
import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ApplicationLifetime;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.compositor.layouts.content.ContentOffsetProvider;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager.DecompressThumbnailCallback;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.ntp.NativePageFactory;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabIdManager;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabPersistentStore;
import org.chromium.chrome.browser.tabmodel.TabPersistentStore.OnTabStateReadCallback;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegate;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegateImpl;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModel;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModel.Entry;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModel.InitializationObserver;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelImpl;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelSelector;
import org.chromium.chrome.browser.tabmodel.document.OffTheRecordDocumentTabModel;
import org.chromium.chrome.browser.tabmodel.document.StorageDelegate;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The class that carries out migration of tab states from/to document mode.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class DocumentMigrationHelper {
    private static final String TAG = "cr.DocumentMigration";

    public static final int FINALIZE_MODE_NO_ACTION = 0;
    public static final int FINALIZE_MODE_FINISH_ACTIVITY = 1;
    public static final int FINALIZE_MODE_RESTART_APP = 2;

    private static final List<MigrationImageCallback> sMigrationCallbacks =
            new ArrayList<MigrationImageCallback>();

    private static class MigrationTabStateReadCallback implements OnTabStateReadCallback {
        private int mSelectedTabId = Tab.INVALID_TAB_ID;

        @Override
        public void onDetailsRead(int index, int id, String url, Boolean isIncognito,
                boolean isStandardActiveIndex, boolean isIncognitoActiveIndex) {
            TabIdManager.getInstance().incrementIdCounterTo(id + 1);
            if (!isStandardActiveIndex) return;
            // If the current tab read is the active standard tab, set the last used
            // tab pref with the id, so that when document mode starts we show that
            // tab first.
            mSelectedTabId = id;
        }

        public int getSelectedTabId() {
            return mSelectedTabId;
        }
    }

    /**
     * Stores a list of "tasks" that are meant to be returned by the ActivityManager for migration.
     * The tasks are inserted manually during the migration from classic mode to document mode.
     */
    private static class MigrationActivityDelegate extends ActivityDelegateImpl {
        private final List<Entry> mEntries;
        private final int mSelectedTabId;

        private MigrationActivityDelegate(List<Entry> entries, int selectedTabId) {
            super(DocumentActivity.class, IncognitoDocumentActivity.class);
            mEntries = entries;
            mSelectedTabId = selectedTabId;
        }

        public int getSelectedTabId() {
            return mSelectedTabId;
        }

        @Override
        public boolean isValidActivity(boolean isIncognito, Intent intent) {
            return true;
        }

        @Override
        public List<Entry> getTasksFromRecents(boolean isIncognito) {
            // We need to have our own list here, since these entries have not actually been
            // created in Recents yet.
            return mEntries;
        }
    }

    private static class MigrationTabCreatorManager implements TabCreatorManager {
        TabDelegate mRegularTabCreator = new TabDelegate(false);
        TabDelegate mIncognitoTabCreator = new TabDelegate(true);

        @Override
        public TabDelegate getTabCreator(boolean incognito) {
            return incognito ? mIncognitoTabCreator : mRegularTabCreator;
        }
    }

    private static class MigrationTabModel extends DocumentTabModelImpl {
        private final SparseArray<String> mTitleList;

        /**
         * Constucts a {@link DocumentTabModel} to be used for migration.
         * @param activityDelegate The delegate that has the tabs to be migrated.
         * @param storageDelegate Delegate that interacts with the file system.
         */
        MigrationTabModel(MigrationActivityDelegate activityDelegate,
                StorageDelegate storageDelegate) {
            super(activityDelegate, storageDelegate, new MigrationTabCreatorManager(), false,
                    Tab.INVALID_TAB_ID, ApplicationStatus.getApplicationContext());
            startTabStateLoad();
            mTitleList = new SparseArray<String>();
            setLastShownId(activityDelegate.getSelectedTabId());
        }

        /**
         * Returns the display title for the Document with the given ID.
         * @param tabId The ID for the document to return the url for.
         * @return The display title for the entry if it was found, null otherwise.
         */
        public String getTitleForDocument(int tabId) {
            String title = mTitleList.get(tabId);
            return TextUtils.isEmpty(title) ? "" : title;
        }

        @Override
        protected boolean shouldStartDeserialization(int currentState) {
            return currentState == STATE_LOAD_TAB_STATE_BG_END;
        }

        @Override
        protected void updateEntryInfoFromTabState(Entry entry, TabState tabState) {
            super.updateEntryInfoFromTabState(entry, tabState);
            mTitleList.put(entry.tabId, tabState.getDisplayTitleFromState());
        }
    }

    private static class MigrationImageCallback
            implements FaviconImageCallback, DecompressThumbnailCallback {
        private final AtomicInteger mCompletedCount;
        private final Activity mActivity;
        private final TabContentManager mTabContentManager;
        private final MigrationTabModel mTabModel;
        private final int mTabId;
        private final String mUrl;
        private final String mTitle;
        private final int mFinalizeMode;

        private Bitmap mIcon;

        public MigrationImageCallback(final AtomicInteger completedCount, final Activity activity,
                final TabContentManager tabContentManager, final MigrationTabModel tabModel,
                final int tabId, final String url, final String title, final int finalizeMode) {
            mCompletedCount = completedCount;
            mActivity = activity;
            mTabContentManager = tabContentManager;
            mTabModel = tabModel;
            mTabId = tabId;
            mUrl = url;
            mTitle = title;
            mFinalizeMode = finalizeMode;
        }

        @Override
        public void onFaviconAvailable(final Bitmap favicon, String iconUrl) {
            mIcon = new DocumentActivityIcon(mActivity).getBitmap(mUrl, favicon);
            mTabContentManager.getThumbnailForId(mTabId, this);
        }

        @Override
        public void onFinishGetBitmap(Bitmap bitmap) {
            // Even if the favicon or thumbnail are null, we still add the AppTask: the framework
            // handles null favicons and addAppTask() below handles null thumbnails.
            if (!NativePageFactory.isNativePageUrl(mUrl, false)
                    && !mUrl.startsWith(UrlConstants.CHROME_SCHEME)) {
                addAppTask(mActivity, mTabId, mTabModel.getTabStateForDocument(mTabId), mUrl,
                        mTitle, mIcon, bitmap);
            }

            if (mCompletedCount.incrementAndGet() == mTabModel.getCount()) {
                mTabContentManager.destroy();
                finalizeMigration(mActivity, mFinalizeMode);
            }
        }
    }

    /**
     * Migrates all tab state to classic mode and creates a tab model file using the current
     * {@link DocumentTabModel} instances.
     * @param activity The activity to be finished after migration if necessary.
     * @param finalizeMode The mode in which the migration should be finalized.
     */
    public static void migrateTabsFromDocumentToClassic(final Activity activity,
            int finalizeMode) {
        Context context = ApplicationStatus.getApplicationContext();
        // Before migration we remove all incognito tabs and also remove the
        // tabs that can not be reached through the {@link DocumentTabModel} instances.
        List<Integer> tabIdsToRemove = new ArrayList<Integer>();

        DocumentTabModelImpl normalTabModel = (DocumentTabModelImpl)
                ChromeApplication.getDocumentTabModelSelector().getModel(false);
        OffTheRecordDocumentTabModel incognitoTabModel = (OffTheRecordDocumentTabModel)
                ChromeApplication.getDocumentTabModelSelector().getModel(true);

        // TODO(yusufo): Clean up this logic.
        for (int i = 0; i < incognitoTabModel.getCount(); i++) {
            tabIdsToRemove.add(incognitoTabModel.getTabAt(i).getId());
        }

        ActivityManager am =
                (ActivityManager) context.getSystemService(Activity.ACTIVITY_SERVICE);
        List<AppTask> taskList = am.getAppTasks();
        for (int i = 0; i < taskList.size(); i++) {
            Intent intent = DocumentUtils.getBaseIntentFromTask(taskList.get(i));
            int id = ActivityDelegate.getTabIdFromIntent(intent);
            if (id == Tab.INVALID_TAB_ID) continue;
            if (tabIdsToRemove.contains(id)) taskList.get(i).finishAndRemoveTask();
        }
        incognitoTabModel.updateRecentlyClosed();

        File migratedFolder = TabPersistentStore.getStateDirectory(context, 0);
        String tabStatefileName = new File(migratedFolder,
                TabPersistentStore.SAVED_STATE_FILE).getAbsolutePath();

        // All the TabStates (incognito or not) live in the same directory.
        File[] allTabs = normalTabModel.getStorageDelegate().getStateDirectory().listFiles();
        try {
            if (allTabs != null) {
                for (int i = 0; i < allTabs.length; i++) {
                    String fileName = allTabs[i].getName();
                    Pair<Integer, Boolean> tabInfo = TabState.parseInfoFromFilename(fileName);
                    if (tabInfo == null) continue;
                    int tabId = tabInfo.first;

                    // Also remove the tab state file for the closed tabs.
                    boolean success;
                    if (!tabIdsToRemove.contains(tabId)) {
                        success = allTabs[i].renameTo(new File(migratedFolder, fileName));
                    } else {
                        success = allTabs[i].delete();
                    }

                    if (!success) Log.e(TAG, "Failed to move/delete file for tab ID: " + tabId);
                }
            }

            if (normalTabModel.getCount() != 0) {
                byte[] listData;
                listData = TabPersistentStore.serializeTabModelSelector(
                        ChromeApplication.getDocumentTabModelSelector(), null);
                ImportantFileWriterAndroid.writeFileAtomically(tabStatefileName, listData);
            }
        } catch (IOException e) {
            Log.e(TAG , "IO exception during tab migration, tab state might not restore correctly");
        }
        finalizeMigration(activity, finalizeMode);
    }

    /**
     * Migrates all tab state to document mode and creates tasks for each currently open tab.
     * @param activity Activity to be used while launching the tasks.
     * @param finalizeMode The mode in which the migration should be finalized.
     */
    public static void migrateTabsFromClassicToDocument(
            final Activity activity, final int finalizeMode) {
        StorageDelegate storageDelegate = new StorageDelegate();
        MigrationActivityDelegate activityDelegate =
                createActivityDelegateWithTabsToMigrate(storageDelegate, activity);
        final MigrationTabModel normalTabModel =
                new MigrationTabModel(activityDelegate, storageDelegate);

        InitializationObserver observer = new InitializationObserver(normalTabModel) {
            @Override
            protected void runImmediately() {
                addAppTasksFromFiles(activity, normalTabModel, finalizeMode);
            }

            @Override
            public boolean isSatisfied(int currentState) {
                return currentState == DocumentTabModelImpl.STATE_DESERIALIZE_END;
            }

            @Override
            public boolean isCanceled() {
                return false;
            }
        };

        observer.runWhenReady();
    }

    /**
     * Migrate tabs saved in classic mode to document mode for an upgrade. This doesn't restart
     * the app process but only finishes the {@link ChromeLauncherActivity} it was being called
     * with.
     * @param activity The activity to use for carrying out and finalizing the migration.
     * @param finalizeMode The mode in which the migration should be finalized.
     * @return Whether any tabs will be migrated.
     */
    public static boolean migrateTabsToDocumentForUpgrade(Activity activity,
            int finalizeMode) {
        // Allow StrictMode violations here as state file read/write is on the critical path. This
        // is also a one-time migration, so optimizing further does not improve daily experience.
        // See http://crbug.com/493157 for more context.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();
        try {
            ChromePreferenceManager.getInstance(activity).setAttemptedMigrationOnUpgrade();

            File[] fileList = TabPersistentStore.getStateDirectory(activity, 0).listFiles();
            if (fileList == null || fileList.length == 0
                    || (fileList.length == 1
                    && fileList[0].getName().equals(TabPersistentStore.SAVED_STATE_FILE))) {
                return false;
            }

            migrateTabsFromClassicToDocument(activity, finalizeMode);
            return true;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private static void finalizeMigration(Activity activity, final int mode) {
        sMigrationCallbacks.clear();

        switch(mode) {
            case FINALIZE_MODE_NO_ACTION:
                return;
            case FINALIZE_MODE_FINISH_ACTIVITY:
                activity.finishAndRemoveTask();
                return;
            case FINALIZE_MODE_RESTART_APP:
                ApplicationLifetime.terminate(true);
                return;
            default:
                assert false;
        }
    }

    private static MigrationActivityDelegate createActivityDelegateWithTabsToMigrate(
            StorageDelegate storageDelegate, Activity activity) {
        File migratedFolder = storageDelegate.getStateDirectory();
        if (!migratedFolder.exists() && !migratedFolder.mkdir()) {
            Log.e(TAG, "Failed to create folder: " + migratedFolder.getAbsolutePath());
        }

        // Create maps for all tabs that will be used during TabModel initialization.
        final List<Entry> normalEntryMap = new ArrayList<Entry>();

        File currentFolder = null;
        MigrationTabStateReadCallback callback = new MigrationTabStateReadCallback();
        for (int currentSelectorIndex = 0;
                (currentFolder = getNextNonEmptyFolder(activity, currentSelectorIndex)) != null;
                ++currentSelectorIndex) {
            File[] allTabs = TabPersistentStore
                    .getStateDirectory(activity, currentSelectorIndex).listFiles();
            try {
                TabPersistentStore.readSavedStateFile(currentFolder, callback);
            } catch (IOException e) {
                Log.e(TAG, "IO Exception while trying to get the last used tab id");
            }

            if (allTabs != null) {
                for (int i = 0; i < allTabs.length; i++) {
                    // Move tab state file to the document side folder.
                    String fileName = allTabs[i].getName();
                    Pair<Integer, Boolean> tabInfo = TabState.parseInfoFromFilename(fileName);
                    if (tabInfo == null) continue;

                    boolean success;
                    if (tabInfo.second) {
                        success = allTabs[i].delete();
                    } else {
                        success = allTabs[i].renameTo(new File(migratedFolder, fileName));
                        normalEntryMap.add(new Entry(tabInfo.first, UrlConstants.NTP_URL));
                    }

                    if (!success) Log.e(TAG, "Failed to move/delete file: " + fileName);
                }
            }
        }

        return new MigrationActivityDelegate(normalEntryMap, callback.getSelectedTabId());
    }

    private static File getNextNonEmptyFolder(Activity activity, int currentSelectorIndex) {
        File folder = TabPersistentStore.getStateDirectory(activity, currentSelectorIndex);
        File[] files = folder.listFiles();
        return (files == null || files.length == 0) ? null : folder;
    }

    private static void addAppTasksFromFiles(final Activity activity,
            final MigrationTabModel tabModel, final int finalizeMode) {
        assert sMigrationCallbacks.size() == 0;

        if (tabModel.getCount() == 0) {
            finalizeMigration(activity, finalizeMode);
            return;
        }

        final AtomicInteger completedCount = new AtomicInteger();
        final TabContentManager contentManager =
                new TabContentManager(activity, new ContentOffsetProvider() {
                    @Override
                    public int getOverlayTranslateY() {
                        return 0;
                    }
                }, DeviceClassManager.enableSnapshots());
        FaviconHelper faviconHelper = new FaviconHelper();
        for (int i = 0; i < tabModel.getCount(); i++) {
            final int tabId = tabModel.getTabAt(i).getId();
            String currentUrl = tabModel.getCurrentUrlForDocument(tabId);
            String currentTitle = tabModel.getTitleForDocument(tabId);

            // Use placeholders if we can't find anything for url and title.
            if (TextUtils.isEmpty(currentUrl)) currentUrl = UrlConstants.NTP_URL;
            if (TextUtils.isEmpty(currentTitle)
                    && !NativePageFactory.isNativePageUrl(currentUrl, false)) {
                currentTitle = UrlUtilities.getDomainAndRegistry(currentUrl, false);
            }
            final String url = currentUrl;
            final String title = currentTitle;

            MigrationImageCallback imageCallback = new MigrationImageCallback(completedCount,
                    activity, contentManager, tabModel, tabId, url, title, finalizeMode);
            faviconHelper.getLocalFaviconImageForURL(
                    Profile.getLastUsedProfile().getOriginalProfile(),
                    url, 0, imageCallback);
            sMigrationCallbacks.add(imageCallback);
        }
    }

    private static void addAppTask(Activity activity, int tabId, TabState tabState,
            String currentUrl, String title, Bitmap favicon, Bitmap bitmap) {
        if (tabId == ActivityDelegate.getTabIdFromIntent(activity.getIntent())) return;
        // Create intent and taskDescription.
        Intent intent = new Intent(Intent.ACTION_VIEW,
                DocumentTabModelSelector.createDocumentDataString(tabId, currentUrl));
        intent.setClassName(activity, ChromeLauncherActivity.getDocumentClassName(false));
        intent.putExtra(IntentHandler.EXTRA_PRESERVE_TASK, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        ActivityManager am =
                (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);

        Bitmap thumbnail = Bitmap.createBitmap(am.getAppTaskThumbnailSize().getWidth(),
                am.getAppTaskThumbnailSize().getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(thumbnail);
        if (bitmap == null) {
            canvas.drawColor(Color.WHITE);
        } else {
            float scale = Math.max(
                    (float) thumbnail.getWidth() / bitmap.getWidth(),
                    (float) thumbnail.getHeight() / bitmap.getHeight());
            canvas.scale(scale, scale);
            canvas.drawBitmap(bitmap, 0, 0, null);
        }
        TaskDescription taskDescription = new TaskDescription(title, favicon,
                ApiCompatibilityUtils.getColor(activity.getResources(),
                        R.color.default_primary_color));
        am.addAppTask(activity, intent, taskDescription, thumbnail);
        Entry entry = new Entry(tabId, tabState);
        DocumentTabModelImpl tabModel = (DocumentTabModelImpl) ChromeApplication
                .getDocumentTabModelSelector().getModel(false);
        tabModel.addEntryForMigration(entry);
    }


    /**
     * Migrates tabs with state to and from document mode.
     * @param toDocumentMode Whether the user is opting out. If true the migration is from Document
     *               to Classic mode.
     * @param activity The activity to use for launching intent if needed.
     * @param terminate Whether the application process should be terminated after the migration.
     */
    public static void migrateTabs(boolean toDocumentMode, final Activity activity,
            boolean terminate) {
        // Allowing StrictMode violations as above. See http://crbug.com/493157 for context.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();
        try {
            int terminateMode =
                    terminate ? FINALIZE_MODE_RESTART_APP : FINALIZE_MODE_FINISH_ACTIVITY;
            if (toDocumentMode) {
                migrateTabsFromClassicToDocument(activity, terminateMode);
            } else {
                migrateTabsFromDocumentToClassic(activity, terminateMode);
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }
}
