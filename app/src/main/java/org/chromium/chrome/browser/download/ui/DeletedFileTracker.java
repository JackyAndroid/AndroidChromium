// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper.DownloadItemWrapper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks items that have been removed from downloads history because they were deleted externally.
 * For use solely by the {@link DownloadHistoryAdapter}.
 *
 * TODO(dfalcantara): Make this class unnecessary.
 */
class DeletedFileTracker {
    private final Set<String> mRegularItems = new HashSet<>();
    private final Set<String> mIncognitoItems = new HashSet<>();
    private final AtomicInteger mNumInstances = new AtomicInteger();

    /** Called when a new {@link DownloadHistoryAdapter} is tracking deleted downloads. */
    void incrementInstanceCount() {
        mNumInstances.getAndIncrement();
    }

    /** Called when a {@link DownloadHistoryAdapter} is no longer traking deleted downloads. */
    void decrementInstanceCount() {
        if (mNumInstances.decrementAndGet() == 0) {
            // If there is no interest, clear out the maps so that they stop taking up space.
            mRegularItems.clear();
            mIncognitoItems.clear();
        }
    }

    /** Add a new item to the tracker. */
    void add(DownloadHistoryItemWrapper wrapper) {
        if (!(wrapper instanceof DownloadItemWrapper)) return;
        Set<String> items = wrapper.isOffTheRecord() ? mIncognitoItems : mRegularItems;
        items.add(wrapper.getId());
    }

    /** Checks if an item is in the tracker. */
    boolean contains(DownloadHistoryItemWrapper wrapper) {
        if (!(wrapper instanceof DownloadItemWrapper)) return false;
        Set<String> items = wrapper.isOffTheRecord() ? mIncognitoItems : mRegularItems;
        return items.contains(wrapper.getId());
    }
}
