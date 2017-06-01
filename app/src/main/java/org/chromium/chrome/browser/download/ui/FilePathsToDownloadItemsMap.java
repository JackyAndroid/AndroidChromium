// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multiple download items may reference the same location on disk. This class maintains a mapping
 * of file paths to download items that reference the file path.
 * TODO(twellington): remove this class after the backend handles duplicate removal.
 */
class FilePathsToDownloadItemsMap {
    private final Map<String, ArrayList<DownloadHistoryItemWrapper>> mMap = new HashMap<>();

    /**
     * Adds a DownloadHistoryItemWrapper to the map. This method does not check whether the item
     * already exists in the map. If an item is being updated, use {@link #replaceItem}.
     * @param wrapper The item to add to the map.
     */
    void addItem(DownloadHistoryItemWrapper wrapper) {
        if (!mMap.containsKey(wrapper.getFilePath())) {
            mMap.put(wrapper.getFilePath(), new ArrayList<DownloadHistoryItemWrapper>());
        }
        mMap.get(wrapper.getFilePath()).add(wrapper);
    }

    /**
     * Replaces a DownloadHistoryItemWrapper using the item's ID. Does nothing if the item does not
     * exist in the map.
     * @param wrapper The item to replace in the map
     */
    void replaceItem(DownloadHistoryItemWrapper wrapper) {
        ArrayList<DownloadHistoryItemWrapper> matchingItems = mMap.get(wrapper.getFilePath());
        if (matchingItems == null) return;

        for (int i = 0; i < matchingItems.size(); i++) {
            if (matchingItems.get(i).getId().equals(wrapper.getId())
                    && matchingItems.get(i).isOffTheRecord() == wrapper.isOffTheRecord()) {
                matchingItems.set(i, wrapper);
            }
        }
    }

    /**
     * Removes a DownloadHistoryItemWrapper from the map. Does nothing if the item does not exist in
     * the map.
     * @param wrapper The item to remove from the map.
     */
    void removeItem(DownloadHistoryItemWrapper wrapper) {
        ArrayList<DownloadHistoryItemWrapper> matchingItems = mMap.get(wrapper.getFilePath());
        if (matchingItems == null) return;

        for (int i = 0; i < matchingItems.size(); i++) {
            if (!matchingItems.get(i).equals(wrapper)) continue;

            // If this is the only DownloadHistoryItemWrapper that references the file path,
            // remove the file path from the map.
            if (matchingItems.size() == 1) {
                mMap.remove(wrapper.getFilePath());
            } else {
                matchingItems.remove(i);
            }

            return;
        }
    }

    /**
     * Gets all DownloadHistoryItemWrappers that point to the same path in the user's storage.
     * @param filePath The file path used to retrieve items.
     * @return DownloadHistoryItemWrappers associated with filePath.
     */
    List<DownloadHistoryItemWrapper> getItemsForFilePath(String filePath) {
        return mMap.get(filePath);
    }
}
