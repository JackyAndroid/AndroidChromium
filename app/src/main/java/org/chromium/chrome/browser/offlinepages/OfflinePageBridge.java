// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.pm.PackageManager;

import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.BookmarksBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.components.offlinepages.DeletePageResult;
import org.chromium.components.offlinepages.SavePageResult;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Access gate to C++ side offline pages functionalities.
 */
@JNINamespace("offline_pages::android")
public final class OfflinePageBridge {

    private long mNativeOfflinePageBridge;
    private boolean mIsNativeOfflinePageModelLoaded;
    private final ObserverList<OfflinePageModelObserver> mObservers =
            new ObserverList<OfflinePageModelObserver>();

    /** Whether the offline pages feature is enabled. */
    private static Boolean sIsEnabled;

    /**
     * Callback used to saving an offline page.
     */
    public interface SavePageCallback {
        /**
         * Delivers result of saving a page.
         *
         * @param savePageResult Result of the saving. Uses
         *     {@see org.chromium.components.offlinepages.SavePageResult} enum.
         * @param url URL of the saved page.
         * @see OfflinePageBridge#savePage()
         */
        @CalledByNative("SavePageCallback")
        void onSavePageDone(int savePageResult, String url);
    }

    /**
     * Callback used to deleting an offline page.
     */
    public interface DeletePageCallback {
        /**
         * Delivers result of deleting a page.
         *
         * @param deletePageResult Result of deleting the page. Uses
         *     {@see org.chromium.components.offlinepages.DeletePageResult} enum.
         * @see OfflinePageBridge#deletePage()
         */
        @CalledByNative("DeletePageCallback")
        void onDeletePageDone(int deletePageResult);
    }

    /**
     * Base observer class listeners to be notified of changes to the offline page model.
     */
    public abstract static class OfflinePageModelObserver {
        /**
         * Called when the native side of offline pages is loaded and now in usable state.
         */
        public void offlinePageModelLoaded() {}

        /**
         * Called when the native side of offline pages is changed due to adding, removing or
         * update an offline page.
         */
        public void offlinePageModelChanged() {}

        /**
         * Called when an offline page is deleted. This can be called as a result of
         * #checkOfflinePageMetadata().
         * @param bookmarkId A bookmark ID of the deleted offline page.
         */
        public void offlinePageDeleted(BookmarkId bookmarkId) {}
    }

    private static int getFreeSpacePercentage() {
        return (int) (1.0 * OfflinePageUtils.getFreeSpaceInBytes()
                / OfflinePageUtils.getTotalSpaceInBytes() * 100);
    }

    private static int getFreeSpaceMB() {
        return (int) (OfflinePageUtils.getFreeSpaceInBytes() / (1024 * 1024));
    }

    /**
     * Creates offline pages bridge for a given profile.
     */
    @VisibleForTesting
    public OfflinePageBridge(Profile profile) {
        mNativeOfflinePageBridge = nativeInit(profile);
    }

    /**
     * Returns true if the offline pages feature is enabled.
     */
    public static boolean isEnabled() {
        ThreadUtils.assertOnUiThread();
        if (sIsEnabled == null) {
            // Enhanced bookmarks feature should also be enabled.
            sIsEnabled = nativeIsOfflinePagesEnabled()
                    && BookmarksBridge.isEnhancedBookmarksEnabled();
        }
        return sIsEnabled;
    }

    /**
     * @return True if an offline copy of the given URL can be saved.
     */
    public static boolean canSavePage(String url) {
        return nativeCanSavePage(url);
    }

    /**
     * Destroys native offline pages bridge. It should be called during
     * destruction to ensure proper cleanup.
     */
    public void destroy() {
        assert mNativeOfflinePageBridge != 0;
        nativeDestroy(mNativeOfflinePageBridge);
        mIsNativeOfflinePageModelLoaded = false;
        mNativeOfflinePageBridge = 0;
    }

    /**
     * Adds an observer to offline page model changes.
     * @param observer The observer to be added.
     */
    @VisibleForTesting
    public void addObserver(OfflinePageModelObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes an observer to offline page model changes.
     * @param observer The observer to be removed.
     */
    @VisibleForTesting
    public void removeObserver(OfflinePageModelObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * @return Gets all available offline pages. Requires that the model is already loaded.
     */
    @VisibleForTesting
    public List<OfflinePageItem> getAllPages() {
        assert mIsNativeOfflinePageModelLoaded;
        List<OfflinePageItem> result = new ArrayList<OfflinePageItem>();
        nativeGetAllPages(mNativeOfflinePageBridge, result);
        return result;
    }

    /**
     * Gets an offline page associated with a provided bookmark ID.
     *
     * @param bookmarkId Id of the bookmark associated with an offline page.
     * @return An {@link OfflinePageItem} matching the bookmark Id or <code>null</code> if none
     * exist.
     */
    @VisibleForTesting
    public OfflinePageItem getPageByBookmarkId(BookmarkId bookmarkId) {
        return nativeGetPageByBookmarkId(mNativeOfflinePageBridge, bookmarkId.getId());
    }

    /**
     * Saves the web page loaded into web contents offline.
     *
     * @param webContents Contents of the page to save.
     * @param bookmarkId Id of the bookmark related to the offline page.
     * @param windowAndroid The Android window used to access the file system.
     * @param callback Interface that contains a callback.
     * @see SavePageCallback
     */
    @VisibleForTesting
    public void savePage(final WebContents webContents, final BookmarkId bookmarkId,
            WindowAndroid windowAndroid, final SavePageCallback callback) {
        assert mIsNativeOfflinePageModelLoaded;
        RecordHistogram.recordEnumeratedHistogram(
                "OfflinePages.SavePage.FreeSpacePercentage", getFreeSpacePercentage(), 101);
        RecordHistogram.recordCustomCountHistogram(
                "OfflinePages.SavePage.FreeSpaceMB", getFreeSpaceMB(), 1, 500000, 50);

        if (windowAndroid == null) {
            callback.onSavePageDone(SavePageResult.CONTENT_UNAVAILABLE, webContents.getUrl());
        } else if (OfflinePageUtils.hasFileAccessPermission(windowAndroid)) {
            nativeSavePage(mNativeOfflinePageBridge, callback, webContents, bookmarkId.getId());
        } else {
            PermissionCallback permissionCallback = new PermissionCallback() {
                @Override
                public void onRequestPermissionsResult(String[] permission, int[] grantResults) {
                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        nativeSavePage(mNativeOfflinePageBridge, callback, webContents,
                                bookmarkId.getId());
                    } else {
                        callback.onSavePageDone(SavePageResult.CANCELLED, webContents.getUrl());
                    }
                }
            };

            OfflinePageUtils.requestFileAccessPermission(windowAndroid, permissionCallback);
        }
    }

    /**
     * Marks that an offline page related to a specified bookmark has been accessed.
     *
     * @param bookmarkId Bookmark ID for which the offline copy will be deleted.
     */
    public void markPageAccessed(BookmarkId bookmarkId) {
        assert mIsNativeOfflinePageModelLoaded;
        nativeMarkPageAccessed(mNativeOfflinePageBridge, bookmarkId.getId());
    }

    /**
     * Deletes an offline page related to a specified bookmark.
     *
     * @param bookmarkId Bookmark ID for which the offline copy will be deleted.
     * @param windowAndroid The Android window used to access the file system.
     * @param callback Interface that contains a callback.
     * @see DeletePageCallback
     */
    @VisibleForTesting
    public void deletePage(final BookmarkId bookmarkId, WindowAndroid windowAndroid,
            final DeletePageCallback callback) {
        assert mIsNativeOfflinePageModelLoaded;
        assert windowAndroid != null;
        RecordHistogram.recordEnumeratedHistogram(
                "OfflinePages.DeletePage.FreeSpacePercentage", getFreeSpacePercentage(), 101);
        RecordHistogram.recordCustomCountHistogram(
                "OfflinePages.DeletePage.FreeSpaceMB", getFreeSpaceMB(), 1, 500000, 50);

        if (OfflinePageUtils.hasFileAccessPermission(windowAndroid)) {
            nativeDeletePage(mNativeOfflinePageBridge, callback, bookmarkId.getId());
        } else {
            PermissionCallback permissionCallback = new PermissionCallback() {
                @Override
                public void onRequestPermissionsResult(String[] permission, int[] grantResults) {
                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        nativeDeletePage(mNativeOfflinePageBridge, callback, bookmarkId.getId());
                    } else {
                        callback.onDeletePageDone(DeletePageResult.DEVICE_FAILURE);
                    }
                }
            };

            OfflinePageUtils.requestFileAccessPermission(windowAndroid, permissionCallback);
        }
    }

    /**
     * Deletes offline pages based on the list of provided bookamrk IDs. Calls the callback
     * when operation is complete. Requires that the model is already loaded.
     *
     * @param bookmarkIds A list of bookmark IDs for which the offline pages will be deleted.
     * @param callback A callback that will be called once operation is completed.
     */
    public void deletePages(List<BookmarkId> bookmarkIds, DeletePageCallback callback) {
        assert mIsNativeOfflinePageModelLoaded;
        long[] ids = new long[bookmarkIds.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = bookmarkIds.get(i).getId();
        }
        nativeDeletePages(mNativeOfflinePageBridge, callback, ids);
    }

    /**
     * Whether or not the underlying offline page model is loaded.
     */
    public boolean isOfflinePageModelLoaded() {
        return mIsNativeOfflinePageModelLoaded;
    }

    /**
     * @return Gets a list of pages that will be removed to clean up storage.  Requires that the
     *     model is already loaded.
     */
    public List<OfflinePageItem> getPagesToCleanUp() {
        assert mIsNativeOfflinePageModelLoaded;
        List<OfflinePageItem> result = new ArrayList<OfflinePageItem>();
        nativeGetPagesToCleanUp(mNativeOfflinePageBridge, result);
        return result;
    }

    /**
     * Starts a check of offline page metadata, e.g. are all offline copies present.
     */
    public void checkOfflinePageMetadata() {
        nativeCheckMetadataConsistency(mNativeOfflinePageBridge);
    }

    @CalledByNative
    private void offlinePageModelLoaded() {
        mIsNativeOfflinePageModelLoaded = true;
        for (OfflinePageModelObserver observer : mObservers) {
            observer.offlinePageModelLoaded();
        }
    }

    @CalledByNative
    private void offlinePageModelChanged() {
        for (OfflinePageModelObserver observer : mObservers) {
            observer.offlinePageModelChanged();
        }
    }

    @CalledByNative
    private void offlinePageDeleted(long bookmarkId) {
        BookmarkId id = new BookmarkId(bookmarkId, BookmarkType.NORMAL);
        for (OfflinePageModelObserver observer : mObservers) {
            observer.offlinePageDeleted(id);
        }
    }

    @CalledByNative
    private static void createOfflinePageAndAddToList(List<OfflinePageItem> offlinePagesList,
            String url, long bookmarkId, String offlineUrl, long fileSize, int accessCount,
            long lastAccessTimeMs) {
        offlinePagesList.add(createOfflinePageItem(
                url, bookmarkId, offlineUrl, fileSize, accessCount, lastAccessTimeMs));
    }

    @CalledByNative
    private static OfflinePageItem createOfflinePageItem(String url, long bookmarkId,
            String offlineUrl, long fileSize, int accessCount, long lastAccessTimeMs) {
        return new OfflinePageItem(
                url, bookmarkId, offlineUrl, fileSize, accessCount, lastAccessTimeMs);
    }

    private static native boolean nativeIsOfflinePagesEnabled();
    private static native boolean nativeCanSavePage(String url);

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeOfflinePageBridge);
    private native void nativeGetAllPages(
            long nativeOfflinePageBridge, List<OfflinePageItem> offlinePages);
    private native OfflinePageItem nativeGetPageByBookmarkId(
            long nativeOfflinePageBridge, long bookmarkId);
    private native void nativeSavePage(long nativeOfflinePageBridge, SavePageCallback callback,
            WebContents webContents, long bookmarkId);
    private native void nativeMarkPageAccessed(long nativeOfflinePageBridge, long bookmarkId);
    private native void nativeDeletePage(long nativeOfflinePageBridge,
            DeletePageCallback callback, long bookmarkId);
    private native void nativeDeletePages(
            long nativeOfflinePageBridge, DeletePageCallback callback, long[] bookmarkIds);
    private native void nativeGetPagesToCleanUp(
            long nativeOfflinePageBridge, List<OfflinePageItem> offlinePages);
    private native void nativeCheckMetadataConsistency(long nativeOfflinePageBridge);
}
