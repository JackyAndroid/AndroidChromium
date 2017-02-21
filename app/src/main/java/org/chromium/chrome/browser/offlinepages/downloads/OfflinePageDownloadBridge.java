// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.downloads;

import android.content.ComponentName;
import android.support.annotation.Nullable;

import org.chromium.base.ContextUtils;
import org.chromium.base.ObserverList;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadItem;
import org.chromium.chrome.browser.download.DownloadServiceDelegate;
import org.chromium.chrome.browser.download.ui.BackendProvider.OfflinePageDelegate;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.AsyncTabCreationParams;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.content_public.browser.LoadUrlParams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves as an interface between Download Home UI and offline page related items that are to be
 * displayed in the downloads UI.
 */
@JNINamespace("offline_pages::android")
public class OfflinePageDownloadBridge implements DownloadServiceDelegate, OfflinePageDelegate {
    /**
     * Base observer class for notifications on changes to the offline page related download items.
     */
    public abstract static class Observer {
        /**
         * Indicates that the bridge is loaded and consumers can call GetXXX methods on it.
         * If the bridge is loaded at the time the observer is being added, the Loaded event will be
         * dispatched immediately.
         */
        public void onItemsLoaded() {}

        /**
         * Event fired when an new item was added.
         * @param item A newly added download item.
         */
        public void onItemAdded(OfflinePageDownloadItem item) {}

        /**
         * Event fired when an item was deleted
         * @param guid A GUID of the deleted download item.
         */
        public void onItemDeleted(String guid) {}

        /**
         * Event fired when an new item was updated.
         * @param item A newly updated download item.
         */
        public void onItemUpdated(OfflinePageDownloadItem item) {}
    }

    private static boolean sIsTesting = false;
    private final ObserverList<Observer> mObservers = new ObserverList<Observer>();
    private long mNativeOfflinePageDownloadBridge;
    private boolean mIsLoaded;

    /**
     * Gets DownloadServiceDelegate that is suitable for interacting with offline download items.
     */
    public static DownloadServiceDelegate getDownloadServiceDelegate() {
        return new OfflinePageDownloadBridge(Profile.getLastUsedProfile());
    }

    public OfflinePageDownloadBridge(Profile profile) {
        mNativeOfflinePageDownloadBridge = sIsTesting ? 0L : nativeInit(profile);
    }

    /** Destroys the native portion of the bridge. */
    @Override
    public void destroy() {
        if (mNativeOfflinePageDownloadBridge != 0) {
            nativeDestroy(mNativeOfflinePageDownloadBridge);
            mNativeOfflinePageDownloadBridge = 0;
            mIsLoaded = false;
        }
        mObservers.clear();
    }

    /**
     * Add an observer of offline download items changes.
     * @param observer The observer to be added.
     */
    @Override
    public void addObserver(Observer observer) {
        mObservers.addObserver(observer);
        if (mIsLoaded) {
            observer.onItemsLoaded();
        }
    }

    /**
     * Remove an observer of offline download items changes.
     * @param observer The observer to be removed.
     */
    @Override
    public void removeObserver(Observer observer) {
        mObservers.removeObserver(observer);
    }

    /** @return all of the download items related to offline pages. */
    @Override
    public List<OfflinePageDownloadItem> getAllItems() {
        List<OfflinePageDownloadItem> items = new ArrayList<>();
        nativeGetAllItems(mNativeOfflinePageDownloadBridge, items);
        return items;
    }

    /**
     * Gets a download item related to the provided GUID.
     * @param guid a GUID of the item to get.
     * @return download item related to the offline page identified by GUID.
     */
    public OfflinePageDownloadItem getItem(String guid) {
        return nativeGetItemByGuid(mNativeOfflinePageDownloadBridge, guid);
    }

    @Override
    public void cancelDownload(String downloadGuid, boolean isOffTheRecord,
            boolean isNotificationDismissed) {
        nativeCancelDownload(mNativeOfflinePageDownloadBridge, downloadGuid);
    }

    @Override
    public void pauseDownload(String downloadGuid, boolean isOffTheRecord) {
        nativePauseDownload(mNativeOfflinePageDownloadBridge, downloadGuid);
    }

    @Override
    public void resumeDownload(DownloadItem item, boolean hasUserGesture) {
        if (hasUserGesture) {
            nativeResumeDownload(mNativeOfflinePageDownloadBridge, item.getId());
        }
    }

    /**
     * Schedules deletion of the offline page identified by the GUID.
     * If the item is still in the process of download, the download is canceled.
     * Actual cancel and/or deletion happens asynchronously, Observer is notified when it's done.
     * @param guid a GUID of the item to delete.
     */
    @Override
    public void deleteItem(String guid) {
        nativeDeleteItemByGuid(mNativeOfflinePageDownloadBridge, guid);
    }

    @Override
    public void destroyServiceDelegate() {
        destroy();
    }

    /**
     * 'Opens' the offline page identified by the GUID.
     * This is done by creating a new tab and navigating it to the saved local snapshot.
     * No automatic redirection is happening based on the connection status.
     * If the item with specified GUID is not found or can't be opened, nothing happens.
     * @param guid          GUID of the item to open.
     * @param componentName If specified, targets a specific Activity to open the offline page in.
     */
    @Override
    public void openItem(String guid, @Nullable ComponentName componentName) {
        OfflinePageDownloadItem item = getItem(guid);
        if (item == null) return;

        LoadUrlParams params = new LoadUrlParams(item.getUrl());
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-Chrome-offline", "persist=1 reason=download id="
                        + Long.toString(nativeGetOfflineIdByGuid(
                                  mNativeOfflinePageDownloadBridge, guid)));
        params.setExtraHeaders(headers);
        AsyncTabCreationParams asyncParams = componentName == null
                ? new AsyncTabCreationParams(params)
                : new AsyncTabCreationParams(params, componentName);
        final TabDelegate tabDelegate = new TabDelegate(false);
        tabDelegate.createNewTab(asyncParams, TabLaunchType.FROM_CHROME_UI, Tab.INVALID_TAB_ID);
    }

    /**
     * Starts download of the page currently open in the specified Tab.
     * If tab's contents are not yet loaded completely, we'll wait for it
     * to load enough for snapshot to be reasonable. If the Chrome is made
     * background and killed, the background request remains that will
     * eventually load the page in background and obtain its offline
     * snapshot.
     * @param tab a tab contents of which will be saved locally.
     */
    public void startDownload(Tab tab) {
        nativeStartDownload(mNativeOfflinePageDownloadBridge, tab,
                ContextUtils.getApplicationContext().getString(R.string.menu_downloads));
    }

    /**
     * Method to ensure that the bridge is created for tests without calling the native portion of
     * initialization.
     * @param isTesting flag indicating whether the constructor will initialize native code.
     */
    static void setIsTesting(boolean isTesting) {
        sIsTesting = isTesting;
    }

    /**
     * Waits for the download items to get loaded and opens the offline page identified by the GUID.
     * @param GUID of the item to open.
     */
    public static void openDownloadedPage(final String guid) {
        final OfflinePageDownloadBridge bridge =
                new OfflinePageDownloadBridge(Profile.getLastUsedProfile());
        bridge.addObserver(
                new Observer() {
                    @Override
                    public void onItemsLoaded() {
                        bridge.openItem(guid, null);
                        bridge.destroyServiceDelegate();
                    }
                });
    }

    @CalledByNative
    void downloadItemsLoaded() {
        mIsLoaded = true;

        for (Observer observer : mObservers) {
            observer.onItemsLoaded();
        }
    }

    @CalledByNative
    void downloadItemAdded(OfflinePageDownloadItem item) {
        assert item != null;

        for (Observer observer : mObservers) {
            observer.onItemAdded(item);
        }
    }

    @CalledByNative
    void downloadItemDeleted(String guid) {
        for (Observer observer : mObservers) {
            observer.onItemDeleted(guid);
        }
    }

    @CalledByNative
    void downloadItemUpdated(OfflinePageDownloadItem item) {
        assert item != null;

        for (Observer observer : mObservers) {
            observer.onItemUpdated(item);
        }
    }

    @CalledByNative
    static void createDownloadItemAndAddToList(List<OfflinePageDownloadItem> list, String guid,
            String url, String title, String targetPath, long startTimeMs, long totalBytes) {
        list.add(createDownloadItem(guid, url, title, targetPath, startTimeMs, totalBytes));
    }

    @CalledByNative
    static OfflinePageDownloadItem createDownloadItem(
            String guid, String url, String title, String targetPath,
            long startTimeMs, long totalBytes) {
        return new OfflinePageDownloadItem(guid, url, title, targetPath, startTimeMs, totalBytes);
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeOfflinePageDownloadBridge);
    native void nativeGetAllItems(
            long nativeOfflinePageDownloadBridge, List<OfflinePageDownloadItem> items);
    native OfflinePageDownloadItem nativeGetItemByGuid(
            long nativeOfflinePageDownloadBridge, String guid);
    native void nativeCancelDownload(long nativeOfflinePageDownloadBridge, String guid);
    native void nativePauseDownload(long nativeOfflinePageDownloadBridge, String guid);
    native void nativeResumeDownload(long nativeOfflinePageDownloadBridge, String guid);
    native void nativeDeleteItemByGuid(long nativeOfflinePageDownloadBridge, String guid);
    native long nativeGetOfflineIdByGuid(long nativeOfflinePageDownloadBridge, String guid);
    native void nativeStartDownload(
            long nativeOfflinePageDownloadBridge, Tab tab, String downloadsLabel);
}
