// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.ComponentName;
import android.support.annotation.Nullable;

import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadBridge;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadItem;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;

import java.util.List;

/**
 * Provides classes that need to be interacted with by the {@link DownloadHistoryAdapter}.
 */
public interface BackendProvider {

    /** Interacts with the Downloads backend. */
    public static interface DownloadDelegate {
        /** See {@link DownloadManagerService#addDownloadHistoryAdapter}. */
        void addDownloadHistoryAdapter(DownloadHistoryAdapter adapter);

        /** See {@link DownloadManagerService#removeDownloadHistoryAdapter}. */
        void removeDownloadHistoryAdapter(DownloadHistoryAdapter adapter);

        /** See {@link DownloadManagerService#getAllDownloads}. */
        void getAllDownloads(boolean isOffTheRecord);

        /** See {@link DownloadManagerService#checkForExternallyRemovedDownloads}. */
        void checkForExternallyRemovedDownloads(boolean isOffTheRecord);

        /** See {@link DownloadManagerService#removeDownload}. */
        void removeDownload(String guid, boolean isOffTheRecord);

        /** See {@link DownloadManagerService#isDownloadOpenableInBrowser}. */
        boolean isDownloadOpenableInBrowser(String guid, boolean isOffTheRecord, String mimeType);
    }

    /** Interacts with the Offline Pages backend. */
    public static interface OfflinePageDelegate {
        /** See {@link OfflinePageDownloadBridge#addObserver}. */
        void addObserver(OfflinePageDownloadBridge.Observer observer);

        /** See {@link OfflinePageDownloadBridge#removeObserver}. */
        void removeObserver(OfflinePageDownloadBridge.Observer observer);

        /** See {@link OfflinePageDownloadBridge#getAllItems}. */
        List<OfflinePageDownloadItem> getAllItems();

        /** See {@link OfflinePageDownloadBridge#openItem}. */
        void openItem(String guid, @Nullable ComponentName componentName);

        /** See {@link OfflinePageDownloadBridge#deleteItem}. */
        void deleteItem(String guid);

        /** See {@link OfflinePageDownloadBridge#destroy}. */
        void destroy();
    }

    /** Returns the {@link DownloadDelegate} that works with the Downloads backend. */
    DownloadDelegate getDownloadDelegate();

    /** Returns the {@link OfflinePageDelegate} that works with the Offline Pages backend. */
    OfflinePageDelegate getOfflinePageBridge();

    /** Returns the {@link ThumbnailProvider} that gets thumbnails for files. */
    ThumbnailProvider getThumbnailProvider();

    /** Returns the {@link SelectionDelegate} that tracks selected items. */
    SelectionDelegate<DownloadHistoryItemWrapper> getSelectionDelegate();

    /** Destroys the BackendProvider. */
    void destroy();
}
