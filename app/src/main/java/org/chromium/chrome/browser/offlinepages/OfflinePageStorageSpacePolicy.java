// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import java.util.List;

/**
 * Manages the storage space policy for offline pages.
 */
public class OfflinePageStorageSpacePolicy {
    /**
     * Minimal total size of all pages, before a header will be shown to offer freeing up space.
     */
    private static final long MINIMUM_TOTAL_SIZE_BYTES = 10 * (1 << 20); // 10MB
    /**
     * Minimal size of pages to clean up, before a header will be shown to offer freeing up space.
     */
    private static final long MINIMUM_CLEANUP_SIZE_BYTES = 5 * (1 << 20); // 5MB

    private OfflinePageBridge mOfflinePageBridge;

    /**
     * @param offlinePageBridge An object to access offline page functionality.
     */
    public OfflinePageStorageSpacePolicy(OfflinePageBridge offlinePageBridge) {
        assert offlinePageBridge != null;
        mOfflinePageBridge = offlinePageBridge;
    }

    /** @return Whether there exists offline pages that could be cleaned up to make space. */
    public boolean hasPagesToCleanUp() {
        return getSizeOfPagesToCleanUp() > 0;
    }

    /**
     * @return Whether the header should be shown in saved pages view to inform user of total used
     * storage and offer freeing up space.
     */
    public boolean shouldShowStorageSpaceHeader() {
        return getSizeOfAllPages() > MINIMUM_TOTAL_SIZE_BYTES
                && getSizeOfPagesToCleanUp() > MINIMUM_CLEANUP_SIZE_BYTES;
    }

    /** @return Total size, in bytes, of all saved pages. */
    public long getSizeOfAllPages() {
        return getTotalSize(mOfflinePageBridge.getAllPages());
    }

    private long getSizeOfPagesToCleanUp() {
        return getTotalSize(mOfflinePageBridge.getPagesToCleanUp());
    }

    private long getTotalSize(List<OfflinePageItem> offlinePages) {
        long totalSize = 0;
        for (OfflinePageItem page : offlinePages) {
            totalSize += page.getFileSize();
        }
        return totalSize;
    }
}
