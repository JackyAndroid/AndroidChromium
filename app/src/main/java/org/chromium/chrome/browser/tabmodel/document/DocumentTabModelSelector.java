// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.document.DocumentUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabIdManager;
import org.chromium.chrome.browser.tabmodel.IncognitoTabModel.IncognitoTabModelDelegate;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorBase;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * Deprecated.  Kept around only for migration.
 *
 * Stores DocumentTabModels for Chrome Activities running in Document-mode.
 * Also manages the transfer of data from one DocumentActivity to another, e.g. WebContents that are
 * created by one Activity but need to be loaded in another Tab.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class DocumentTabModelSelector extends TabModelSelectorBase implements TabCreatorManager {
    public static final String PREF_PACKAGE = "com.google.android.apps.chrome.document";
    public static final String PREF_IS_INCOGNITO_SELECTED = "is_incognito_selected";

    /**
     * Overrides Delegates used by the DocumentTabModels.
     */
    private static final Object STORAGE_DELEGATE_FOR_TESTS_LOCK = new Object();
    private static StorageDelegate sStorageDelegateForTests;

    /**
     * ID of the Tab to prioritize when initializing the TabState.
     */
    private static int sPrioritizedTabId = Tab.INVALID_TAB_ID;

    /**
     * Interacts with DocumentActivities.
     */
    private final ActivityDelegate mActivityDelegate;

    /**
     * Interacts with the file system.
     */
    private final StorageDelegate mStorageDelegate;

    /**
     * Creates new Tabs.
     */
    private final TabDelegate mRegularTabDelegate;
    private final TabDelegate mIncognitoTabDelegate;

    /**
     * TabModel that keeps track of regular tabs. This is always not null.
     */
    private final DocumentTabModel mRegularTabModel;

    /**
     * TabModel that keeps track of incognito tabs. This may be null if no incognito tabs exist.
     */
    private final IncognitoDocumentTabModel mIncognitoTabModel;

    /**
     * If the TabModels haven't been initialized yet, prioritize the correct one to load the Tab.
     * @param prioritizedTabId ID of the tab to prioritize.
     */
    public static void setPrioritizedTabId(int prioritizedTabId) {
        sPrioritizedTabId = prioritizedTabId;
    }

    public DocumentTabModelSelector(ActivityDelegate activityDelegate,
            StorageDelegate storageDelegate, TabDelegate regularTabDelegate,
            TabDelegate incognitoTabDelegate) {
        mActivityDelegate = activityDelegate;
        mStorageDelegate =
                sStorageDelegateForTests == null ? storageDelegate : sStorageDelegateForTests;
        mRegularTabDelegate = regularTabDelegate;
        mIncognitoTabDelegate = incognitoTabDelegate;

        final Context context = ContextUtils.getApplicationContext();
        mRegularTabModel = new DocumentTabModelImpl(
                mActivityDelegate, mStorageDelegate, this, false, sPrioritizedTabId, context);
        mIncognitoTabModel = new IncognitoDocumentTabModel(new IncognitoTabModelDelegate() {
            @Override
            public TabModel createTabModel() {
                DocumentTabModel incognitoModel = new DocumentTabModelImpl(mActivityDelegate,
                        mStorageDelegate, DocumentTabModelSelector.this, true, sPrioritizedTabId,
                        context);
                return incognitoModel;
            }

            @Override
            public boolean doIncognitoTabsExist() {
                // TODO(dfalcantara): Devices in document mode do not trigger the TabWindowManager.
                //                    Revisit this when we have a Samsung L multi-instance device.
                return mIncognitoTabModel.getCount() > 0;
            }
        }, mActivityDelegate);
        initializeTabIdCounter();

        // Re-select the previously selected TabModel.
        SharedPreferences prefs = context.getSharedPreferences(PREF_PACKAGE, Context.MODE_PRIVATE);
        boolean startIncognito = prefs.getBoolean(PREF_IS_INCOGNITO_SELECTED, false);
        initialize(startIncognito, mRegularTabModel, mIncognitoTabModel);
    }

    @Override
    public TabDelegate getTabCreator(boolean incognito) {
        return incognito ? mIncognitoTabDelegate : mRegularTabDelegate;
    }

    private void initializeTabIdCounter() {
        int biggestId = getLargestTaskIdFromRecents();
        biggestId = getMaxTabId(mRegularTabModel, biggestId);
        biggestId = getMaxTabId(mIncognitoTabModel, biggestId);
        TabIdManager.getInstance().incrementIdCounterTo(biggestId + 1);
    }

    private int getMaxTabId(DocumentTabModel tabModel, int min) {
        int biggestId = min;
        int numTabs = tabModel.getCount();
        for (int tabIndex = 0; tabIndex < numTabs; tabIndex++) {
            biggestId = Math.max(biggestId, tabModel.getTabAt(tabIndex).getId());
        }
        return biggestId;
    }

    private int getLargestTaskIdFromRecents() {
        int biggestId = Tab.INVALID_TAB_ID;
        Context context = ContextUtils.getApplicationContext();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
            RecentTaskInfo info = DocumentUtils.getTaskInfoFromTask(task);
            if (info == null) continue;
            biggestId = Math.max(biggestId, info.persistentId);
        }
        return biggestId;
    }

    @Override
    public Tab openNewTab(LoadUrlParams loadUrlParams, TabLaunchType type, Tab parent,
            boolean incognito) {
        TabDelegate delegate = getTabCreator(incognito);
        delegate.createNewTab(loadUrlParams, type, parent);
        return null;
    }

    @Override
    public void selectModel(boolean incognito) {
        super.selectModel(incognito);

        Context context = ContextUtils.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREF_PACKAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_IS_INCOGNITO_SELECTED, incognito);
        editor.apply();
    }

    @Override
    public DocumentTabModel getModel(boolean incognito) {
        return (DocumentTabModel) super.getModel(incognito);
    }

    @Override
    public DocumentTabModel getModelForTabId(int id) {
        return (DocumentTabModel) super.getModelForTabId(id);
    }

    /**
     * Overrides the regular StorageDelegate in the constructor.  MUST be called before the
     * DocumentTabModelSelector instance is created to take effect.
     */
    @VisibleForTesting
    public static void setStorageDelegateForTests(StorageDelegate delegate) {
        synchronized (STORAGE_DELEGATE_FOR_TESTS_LOCK) {
            sStorageDelegateForTests = delegate;
        }
    }
}
