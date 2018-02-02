// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.text.TextUtils;

import java.util.ArrayList;

/**
 * Stores a List of DownloadHistoryItemWrappers for a particular download backend.
 */
public abstract class BackendItems extends ArrayList<DownloadHistoryItemWrapper> {
    /** See {@link #findItemIndex}. */
    public static final int INVALID_INDEX = -1;

    /** Whether or not the list has been initialized. */
    private boolean mIsInitialized;

    /**
     * Determines how many bytes are occupied by completed downloads.
     * @return Total size of completed downloads in bytes.
     */
    public long getTotalBytes() {
        long totalSize = 0;
        for (DownloadHistoryItemWrapper item : this) {
            if (!item.isComplete()) continue;
            totalSize += item.getFileSize();
        }
        return totalSize;
    }

    /**
     * Filters out items that are displayed in this list for the current filter.
     * TODO(dfalcantara): Show all non-cancelled downloads.
     *
     * @param filterType    Filter to use.
     * @param filteredItems List for appending items that match the filter.
     */
    public void filter(int filterType, BackendItems filteredItems) {
        for (DownloadHistoryItemWrapper item : this) {
            if (!item.isComplete()) continue;

            if (item.getFilterType() == filterType || filterType == DownloadFilter.FILTER_ALL) {
                filteredItems.add(item);
            }
        }
    }

    /**
     * Search for an existing entry with the given ID.
     * @param guid GUID of the entry.
     * @return The index of the item, or INVALID_INDEX if it couldn't be found.
     */
    public int findItemIndex(String guid) {
        for (int i = 0; i < size(); i++) {
            if (TextUtils.equals(get(i).getId(), guid)) return i;
        }
        return INVALID_INDEX;
    }

    /**
     * Removes the item matching the given guid.
     * @param guid GUID of the download to remove.
     * @return Item that was removed, or null if the item wasn't found.
     */
    public DownloadHistoryItemWrapper removeItem(String guid) {
        int index = findItemIndex(guid);
        if (index == INVALID_INDEX) return null;
        return remove(index);
    }

    public boolean isInitialized() {
        return mIsInitialized;
    }

    public void setIsInitialized() {
        mIsInitialized = true;
    }
}
