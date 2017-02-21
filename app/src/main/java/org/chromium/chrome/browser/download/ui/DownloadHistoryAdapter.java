// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.ComponentName;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadItem;
import org.chromium.chrome.browser.download.ui.BackendProvider.DownloadDelegate;
import org.chromium.chrome.browser.download.ui.BackendProvider.OfflinePageDelegate;
import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper.DownloadItemWrapper;
import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper.OfflinePageItemWrapper;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi.DownloadUiObserver;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadBridge;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadItem;
import org.chromium.chrome.browser.widget.DateDividedAdapter;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.content_public.browser.DownloadState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Bridges the user's download history and the UI used to display it. */
public class DownloadHistoryAdapter extends DateDividedAdapter implements DownloadUiObserver {

    /** See {@link #findItemIndex}. */
    private static final int INVALID_INDEX = -1;

    /**
     * Externally deleted items that have been removed from downloads history.
     * Shared across instances.
     */
    private static Map<String, Boolean> sExternallyDeletedItems = new HashMap<>();

    /**
     * Externally deleted off-the-record items that have been removed from downloads history.
     * Shared across instances.
     */
    private static Map<String, Boolean> sExternallyDeletedOffTheRecordItems = new HashMap<>();

    /**
     * The number of DownloadHistoryAdapater instances in existence that have been initialized.
     */
    private static final AtomicInteger sNumInstancesInitialized = new AtomicInteger();

    private final List<DownloadItemWrapper> mDownloadItems = new ArrayList<>();
    private final List<DownloadItemWrapper> mDownloadOffTheRecordItems = new ArrayList<>();
    private final List<OfflinePageItemWrapper> mOfflinePageItems = new ArrayList<>();
    private final List<DownloadHistoryItemWrapper> mFilteredItems = new ArrayList<>();
    private final FilePathsToDownloadItemsMap mFilePathsToItemsMap =
            new FilePathsToDownloadItemsMap();

    private final ComponentName mParentComponent;
    private final boolean mShowOffTheRecord;
    private final LoadingStateDelegate mLoadingDelegate;

    private BackendProvider mBackendProvider;
    private OfflinePageDownloadBridge.Observer mOfflinePageObserver;
    private int mFilter = DownloadFilter.FILTER_ALL;

    private boolean mAllDownloadItemsRetrieved;
    private boolean mAllOffTheRecordDownloadItemsRetrieved;
    private boolean mAllOfflinePagesRetrieved;

    DownloadHistoryAdapter(boolean showOffTheRecord, ComponentName parentComponent) {
        mShowOffTheRecord = showOffTheRecord;
        mParentComponent = parentComponent;
        mLoadingDelegate = new LoadingStateDelegate(mShowOffTheRecord);

        // Using stable IDs allows the RecyclerView to animate changes.
        setHasStableIds(true);
    }

    public void initialize(BackendProvider provider) {
        mBackendProvider = provider;

        // Get all regular and (if necessary) off the record downloads.
        DownloadDelegate downloadManager = getDownloadDelegate();
        downloadManager.addDownloadHistoryAdapter(this);
        downloadManager.getAllDownloads(false);
        if (mShowOffTheRecord) downloadManager.getAllDownloads(true);

        initializeOfflinePageBridge();

        sNumInstancesInitialized.getAndIncrement();
    }

    /** Called when the user's download history has been gathered. */
    public void onAllDownloadsRetrieved(List<DownloadItem> result, boolean isOffTheRecord) {
        if (isOffTheRecord && !mShowOffTheRecord) return;

        if ((!isOffTheRecord && mAllDownloadItemsRetrieved)
                || (isOffTheRecord && mAllOffTheRecordDownloadItemsRetrieved)) {
            return;
        }
        if (!isOffTheRecord) {
            mAllDownloadItemsRetrieved = true;
        } else {
            mAllOffTheRecordDownloadItemsRetrieved = true;
        }

        mLoadingDelegate.updateLoadingState(
                isOffTheRecord ? LoadingStateDelegate.OFF_THE_RECORD_HISTORY_LOADED
                        : LoadingStateDelegate.DOWNLOAD_HISTORY_LOADED);

        List<DownloadItemWrapper> list = getDownloadItemList(isOffTheRecord);
        list.clear();
        int[] mItemCounts = new int[DownloadFilter.FILTER_BOUNDARY];

        for (DownloadItem item : result) {
            DownloadItemWrapper wrapper = createDownloadItemWrapper(item, isOffTheRecord);

            // TODO(twellington): The native downloads service should remove externally deleted
            //                    downloads rather than passing them to Java.
            if (getExternallyDeletedItemsMap(isOffTheRecord).containsKey(wrapper.getId())) {
                continue;
            } else if (wrapper.hasBeenExternallyRemoved()) {
                removeExternallyDeletedItem(wrapper, isOffTheRecord);
            } else {
                list.add(wrapper);
                mItemCounts[wrapper.getFilterType()]++;
                mFilePathsToItemsMap.addItem(wrapper);
            }
        }

        if (!isOffTheRecord) recordDownloadCountHistograms(mItemCounts);

        onItemsRetrieved();
    }

    /** Called when the user's offline page history has been gathered. */
    private void onAllOfflinePagesRetrieved(List<OfflinePageDownloadItem> result) {
        if (mAllOfflinePagesRetrieved) return;
        mAllOfflinePagesRetrieved = true;

        mLoadingDelegate.updateLoadingState(LoadingStateDelegate.OFFLINE_PAGE_LOADED);
        mOfflinePageItems.clear();
        for (OfflinePageDownloadItem item : result) {
            OfflinePageItemWrapper wrapper = createOfflinePageItemWrapper(item);
            mOfflinePageItems.add(wrapper);
            mFilePathsToItemsMap.addItem(wrapper);
        }

        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.OfflinePage",
                result.size());

        onItemsRetrieved();
    }

    /**
     * Should be called when download items or offline pages have been retrieved.
     */
    private void onItemsRetrieved() {
        if (mLoadingDelegate.isLoaded()) {
            recordTotalDownloadCountHistogram();
            filter(mLoadingDelegate.getPendingFilter());
        }
    }

    /** Returns the total size of all non-deleted downloaded items. */
    public long getTotalDownloadSize() {
        long totalSize = 0;
        for (DownloadHistoryItemWrapper wrapper : mDownloadItems) {
            totalSize += wrapper.getFileSize();
        }
        for (DownloadHistoryItemWrapper wrapper : mDownloadOffTheRecordItems) {
            totalSize += wrapper.getFileSize();
        }
        for (DownloadHistoryItemWrapper wrapper : mOfflinePageItems) {
            totalSize += wrapper.getFileSize();
        }
        return totalSize;
    }

    @Override
    protected int getTimedItemViewResId() {
        return R.layout.download_date_view;
    }

    @Override
    public ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.download_item_view, parent, false);
        ((DownloadItemView) v).setSelectionDelegate(getSelectionDelegate());
        return new DownloadHistoryItemViewHolder(v);
    }

    @Override
    public void bindViewHolderForTimedItem(ViewHolder current, TimedItem timedItem) {
        final DownloadHistoryItemWrapper item = (DownloadHistoryItemWrapper) timedItem;

        DownloadHistoryItemViewHolder holder = (DownloadHistoryItemViewHolder) current;
        holder.displayItem(mBackendProvider, item);
    }

    /**
     * Updates the list when new information about a download comes in.
     */
    public void onDownloadItemUpdated(DownloadItem item, boolean isOffTheRecord, int state) {
        if (isOffTheRecord && !mShowOffTheRecord) return;

        // The adapter currently only cares about completion events.
        if (state != DownloadState.COMPLETE) return;

        List<DownloadItemWrapper> list = getDownloadItemList(isOffTheRecord);
        int index = findItemIndex(list, item.getId());

        DownloadItemWrapper wrapper = createDownloadItemWrapper(item, isOffTheRecord);

        // If an externally deleted item has already been removed from the history service, it
        // shouldn't be removed again.
        if (getExternallyDeletedItemsMap(isOffTheRecord).containsKey(wrapper.getId())) return;

        if (wrapper.hasBeenExternallyRemoved()) {
            removeExternallyDeletedItem(wrapper, isOffTheRecord);
            return;
        }

        if (index == INVALID_INDEX) {
            // Add a new entry.
            list.add(wrapper);
            mFilePathsToItemsMap.addItem(wrapper);
        } else {
            DownloadItemWrapper previousWrapper = list.get(index);
            // If the previous item was selected, the updated item should be selected as well.
            if (getSelectionDelegate().isItemSelected(previousWrapper)) {
                getSelectionDelegate().toggleSelectionForItem(previousWrapper);
                getSelectionDelegate().toggleSelectionForItem(wrapper);
            }
            // Update the old one.
            list.set(index, wrapper);
            mFilePathsToItemsMap.replaceItem(wrapper);
        }

        filter(mFilter);
    }

    /**
     * Removes the DownloadItem with the given ID.
     * @param guid           ID of the DownloadItem that has been removed.
     * @param isOffTheRecord True if off the record, false otherwise.
     */
    public void onDownloadItemRemoved(String guid, boolean isOffTheRecord) {
        if (isOffTheRecord && !mShowOffTheRecord) return;
        if (removeItemFromList(getDownloadItemList(isOffTheRecord), guid)) {
            filter(mFilter);
        }
    }

    @Override
    public void onFilterChanged(int filter) {
        if (mLoadingDelegate.isLoaded()) {
            filter(filter);
        } else {
            // On tablets, this method might be called before anything is loaded. In this case,
            // cache the filter, and wait till the backends are loaded.
            mLoadingDelegate.setPendingFilter(filter);
        }
    }

    @Override
    public void onManagerDestroyed() {
        getDownloadDelegate().removeDownloadHistoryAdapter(this);
        getOfflinePageBridge().removeObserver(mOfflinePageObserver);

        // If there are no more instances, clear out externally deleted items maps so that they stop
        // taking up space.
        if (sNumInstancesInitialized.decrementAndGet() == 0) {
            sExternallyDeletedItems.clear();
            sExternallyDeletedOffTheRecordItems.clear();
        }
    }

    /**
     * @param items The items to remove from this adapter. This should be used to remove items
     *              from the adapter during deletions.
     */
    void removeItemsFromAdapter(List<DownloadHistoryItemWrapper> items) {
        for (DownloadHistoryItemWrapper item : items) {
            if (item instanceof DownloadItemWrapper) {
                getDownloadItemList(item.isOffTheRecord()).remove(item);
            } else {
                mOfflinePageItems.remove(item);
            }
            mFilePathsToItemsMap.removeItem(item);
        }
        filter(mFilter);
    }

    /**
     * @param items The items to add to this adapter. This should be used to add items back to the
     *              adapter when undoing deletions.
     */
    void addItemsToAdapter(List<DownloadHistoryItemWrapper> items) {
        for (DownloadHistoryItemWrapper item : items) {
            if (item instanceof DownloadItemWrapper) {
                getDownloadItemList(item.isOffTheRecord()).add((DownloadItemWrapper) item);
            } else {
                mOfflinePageItems.add((OfflinePageItemWrapper) item);
            }
            mFilePathsToItemsMap.addItem(item);
        }
        filter(mFilter);
    }

    /**
     * Gets all DownloadHistoryItemWrappers that point to the same path in the user's storage.
     * @param filePath The file path used to retrieve items.
     * @return DownloadHistoryItemWrappers associated with filePath.
     */
    List<DownloadHistoryItemWrapper> getItemsForFilePath(String filePath) {
        return mFilePathsToItemsMap.getItemsForFilePath(filePath);
    }

    private DownloadDelegate getDownloadDelegate() {
        return mBackendProvider.getDownloadDelegate();
    }

    private OfflinePageDelegate getOfflinePageBridge() {
        return mBackendProvider.getOfflinePageBridge();
    }

    private SelectionDelegate<DownloadHistoryItemWrapper> getSelectionDelegate() {
        return mBackendProvider.getSelectionDelegate();
    }

    /** Filters the list of downloads to show only files of a specific type. */
    private void filter(int filterType) {
        mFilter = filterType;
        mFilteredItems.clear();
        if (filterType == DownloadFilter.FILTER_ALL) {
            mFilteredItems.addAll(mDownloadItems);
            mFilteredItems.addAll(mDownloadOffTheRecordItems);
            mFilteredItems.addAll(mOfflinePageItems);
        } else {
            for (DownloadHistoryItemWrapper item : mDownloadItems) {
                if (item.getFilterType() == filterType) mFilteredItems.add(item);
            }

            for (DownloadHistoryItemWrapper item : mDownloadOffTheRecordItems) {
                if (item.getFilterType() == filterType) mFilteredItems.add(item);
            }

            if (filterType == DownloadFilter.FILTER_PAGE) {
                for (DownloadHistoryItemWrapper item : mOfflinePageItems) mFilteredItems.add(item);
            }
        }

        loadItems(mFilteredItems);
    }

    private void initializeOfflinePageBridge() {
        mOfflinePageObserver = new OfflinePageDownloadBridge.Observer() {
            @Override
            public void onItemsLoaded() {
                onAllOfflinePagesRetrieved(getOfflinePageBridge().getAllItems());
            }

            @Override
            public void onItemAdded(OfflinePageDownloadItem item) {
                OfflinePageItemWrapper wrapper = createOfflinePageItemWrapper(item);
                mOfflinePageItems.add(wrapper);
                mFilePathsToItemsMap.addItem(wrapper);
                updateFilter();
            }

            @Override
            public void onItemDeleted(String guid) {
                if (removeItemFromList(mOfflinePageItems, guid)) updateFilter();
            }

            @Override
            public void onItemUpdated(OfflinePageDownloadItem item) {
                int index = findItemIndex(mOfflinePageItems, item.getGuid());
                if (index != INVALID_INDEX) {
                    OfflinePageItemWrapper wrapper = createOfflinePageItemWrapper(item);
                    mOfflinePageItems.set(index, wrapper);
                    mFilePathsToItemsMap.replaceItem(wrapper);
                    updateFilter();
                }
            }

            /** Re-filter the items if needed. */
            private void updateFilter() {
                if (mFilter == DownloadFilter.FILTER_ALL || mFilter == DownloadFilter.FILTER_PAGE) {
                    filter(mFilter);
                }
            }
        };
        getOfflinePageBridge().addObserver(mOfflinePageObserver);
    }

    private List<DownloadItemWrapper> getDownloadItemList(boolean isOffTheRecord) {
        return isOffTheRecord ? mDownloadOffTheRecordItems : mDownloadItems;
    }

    /**
     * Search for an existing entry for the {@link DownloadHistoryItemWrapper} with the given ID.
     * @param list List to search through.
     * @param guid GUID of the entry.
     * @return The index of the item, or INVALID_INDEX if it couldn't be found.
     */
    private <T extends DownloadHistoryItemWrapper> int findItemIndex(List<T> list, String guid) {
        for (int i = 0; i < list.size(); i++) {
            if (TextUtils.equals(list.get(i).getId(), guid)) return i;
        }
        return INVALID_INDEX;
    }

    /**
     * Removes the item matching the given |guid|.
     * @param list List of the users downloads of a specific type.
     * @param guid GUID of the download to remove.
     * @return True if something was removed, false otherwise.
     */
    private <T extends DownloadHistoryItemWrapper> boolean removeItemFromList(
            List<T> list, String guid) {
        int index = findItemIndex(list, guid);
        if (index != INVALID_INDEX) {
            T wrapper = list.remove(index);
            mFilePathsToItemsMap.removeItem(wrapper);

            if (getSelectionDelegate().isItemSelected(wrapper)) {
                getSelectionDelegate().toggleSelectionForItem(wrapper);
            }
            return true;
        }
        return false;
    }

    private DownloadItemWrapper createDownloadItemWrapper(
            DownloadItem item, boolean isOffTheRecord) {
        return new DownloadItemWrapper(item, isOffTheRecord, mBackendProvider, mParentComponent);
    }

    private OfflinePageItemWrapper createOfflinePageItemWrapper(OfflinePageDownloadItem item) {
        return new OfflinePageItemWrapper(item, mBackendProvider, mParentComponent);
    }

    private void recordDownloadCountHistograms(int[] itemCounts) {
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Audio",
                itemCounts[DownloadFilter.FILTER_AUDIO]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Document",
                itemCounts[DownloadFilter.FILTER_DOCUMENT]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Image",
                itemCounts[DownloadFilter.FILTER_IMAGE]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Other",
                itemCounts[DownloadFilter.FILTER_OTHER]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Video",
                itemCounts[DownloadFilter.FILTER_VIDEO]);
    }

    private void recordTotalDownloadCountHistogram() {
        // The total count intentionally leaves out incognito downloads. This should be revisited
        // if/when incognito downloads are persistently available in downloads home.
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Total",
                mDownloadItems.size() + mOfflinePageItems.size());
    }

    private void removeExternallyDeletedItem(DownloadItemWrapper wrapper, boolean isOffTheRecord) {
        getExternallyDeletedItemsMap(isOffTheRecord).put(wrapper.getId(), true);
        wrapper.remove();
        mFilePathsToItemsMap.removeItem(wrapper);
        RecordUserAction.record("Android.DownloadManager.Item.ExternallyDeleted");
    }

    private Map<String, Boolean> getExternallyDeletedItemsMap(boolean isOffTheRecord) {
        return isOffTheRecord ? sExternallyDeletedOffTheRecordItems : sExternallyDeletedItems;
    }

    /**
     * Determines when the data from all of the backends has been loaded.
     * <p>
     * TODO(ianwen): add a timeout mechanism to either the DownloadLoadingDelegate or to the
     * backend so that if it takes forever to load one of the backend, users are still able to see
     * the other two.
     */
    private static class LoadingStateDelegate {
        public static final int DOWNLOAD_HISTORY_LOADED = 0b001;
        public static final int OFF_THE_RECORD_HISTORY_LOADED = 0b010;
        public static final int OFFLINE_PAGE_LOADED = 0b100;

        private static final int ALL_LOADED = 0b111;

        private int mState;
        private int mPendingFilter = DownloadFilter.FILTER_ALL;

        /**
         * @param offTheRecord Whether this delegate needs to consider incognito.
         */
        public LoadingStateDelegate(boolean offTheRecord) {
            // If we don't care about incognito, mark it as loaded.
            mState = offTheRecord ? 0 : OFF_THE_RECORD_HISTORY_LOADED;
        }

        /**
         * Tells this delegate one of the three backends has been loaded.
         */
        public void updateLoadingState(int flagToUpdate) {
            mState |= flagToUpdate;
        }

        /**
         * @return Whether all backends are loaded.
         */
        public boolean isLoaded() {
            return mState == ALL_LOADED;
        }

        /**
         * Caches a filter for when the backends have loaded.
         */
        public void setPendingFilter(int filter) {
            mPendingFilter = filter;
        }

        /**
         * @return The cached filter. If there are no such filter, fall back to
         *         {@link DownloadFilter#FILTER_ALL}.
         */
        public int getPendingFilter() {
            return mPendingFilter;
        }
    }
}
