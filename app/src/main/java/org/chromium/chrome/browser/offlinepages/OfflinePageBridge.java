// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import org.chromium.base.Callback;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Access gate to C++ side offline pages functionalities.
 */
@JNINamespace("offline_pages::android")
public class OfflinePageBridge {
    public static final String BOOKMARK_NAMESPACE = "bookmark";
    public static final String SHARE_NAMESPACE = "share";

    /**
     * Retrieves the OfflinePageBridge for the given profile, creating it the first time
     * getForProfile is called for a given profile.  Must be called on the UI thread.
     *
     * @param profile The profile associated with the OfflinePageBridge to get.
     */
    public static OfflinePageBridge getForProfile(Profile profile) {
        ThreadUtils.assertOnUiThread();

        return nativeGetOfflinePageBridgeForProfile(profile);
    }

    private long mNativeOfflinePageBridge;
    private boolean mIsNativeOfflinePageModelLoaded;
    private final ObserverList<OfflinePageModelObserver> mObservers =
            new ObserverList<OfflinePageModelObserver>();

    /** Whether an offline sub-feature is enabled or not. */
    private static Boolean sOfflineBookmarksEnabled;
    private static Boolean sBackgroundLoadingEnabled;
    private static Boolean sIsPageSharingEnabled;

    /**
     * Callback used when saving an offline page.
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
        void onSavePageDone(int savePageResult, String url, long offlineId);
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
         * @param offlineId The offline ID of the deleted offline page.
         * @param clientId The client supplied ID of the deleted offline page.
         */
        public void offlinePageDeleted(long offlineId, ClientId clientId) {}
    }

    /**
     * Creates an offline page bridge for a given profile.
     * Accessible by the package for testability.
     */
    @VisibleForTesting
    OfflinePageBridge(long nativeOfflinePageBridge) {
        mNativeOfflinePageBridge = nativeOfflinePageBridge;
    }

    /**
     * Called by the native OfflinePageBridge so that it can cache the new Java OfflinePageBridge.
     */
    @CalledByNative
    private static OfflinePageBridge create(long nativeOfflinePageBridge) {
        return new OfflinePageBridge(nativeOfflinePageBridge);
    }

    /**
     * @return True if saving bookmarked pages for offline viewing is enabled.
     */
    public static boolean isOfflineBookmarksEnabled() {
        ThreadUtils.assertOnUiThread();
        if (sOfflineBookmarksEnabled == null) {
            sOfflineBookmarksEnabled = nativeIsOfflineBookmarksEnabled();
        }
        return sOfflineBookmarksEnabled;
    }

    /**
     * @return True if offline pages sharing is enabled.
     */
    @VisibleForTesting
    public static boolean isPageSharingEnabled() {
        ThreadUtils.assertOnUiThread();
        if (sIsPageSharingEnabled == null) {
            sIsPageSharingEnabled = nativeIsPageSharingEnabled();
        }
        return sIsPageSharingEnabled;
    }

    /**
     * @return True if an offline copy of the given URL can be saved.
     */
    public static boolean canSavePage(String url) {
        return nativeCanSavePage(url);
    }

    /**
     * Adds an observer to offline page model changes.
     * @param observer The observer to be added.
     */
    public void addObserver(OfflinePageModelObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes an observer to offline page model changes.
     * @param observer The observer to be removed.
     */
    public void removeObserver(OfflinePageModelObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Gets all available offline pages, returning results via the provided callback.
     *
     * @param callback The callback to run when the operation completes.
     */
    @VisibleForTesting
    void getAllPages(final Callback<List<OfflinePageItem>> callback) {
        List<OfflinePageItem> result = new ArrayList<>();
        nativeGetAllPages(mNativeOfflinePageBridge, result, callback);
    }

    /**
     * Gets the offline pages associated with the provided client IDs.
     *
     * @param clientIds Client's IDs associated with offline pages.
     * @return A list of {@link OfflinePageItem} matching the provided IDs, or an empty list if none
     * exist.
     */
    @VisibleForTesting
    public void getPagesByClientIds(
            final List<ClientId> clientIds, final Callback<List<OfflinePageItem>> callback) {
        String[] namespaces = new String[clientIds.size()];
        String[] ids = new String[clientIds.size()];

        for (int i = 0; i < clientIds.size(); i++) {
            namespaces[i] = clientIds.get(i).getNamespace();
            ids[i] = clientIds.get(i).getId();
        }

        List<OfflinePageItem> result = new ArrayList<>();
        nativeGetPagesByClientId(mNativeOfflinePageBridge, result, namespaces, ids, callback);
    }

    /**
     * Gets all the URLs in the request queue.
     *
     * @return A list of {@link SavePageRequest} representing all the queued requests.
     */
    @VisibleForTesting
    public void getRequestsInQueue(Callback<SavePageRequest[]> callback) {
        nativeGetRequestsInQueue(mNativeOfflinePageBridge, callback);
    }

    private static class RequestsRemovedCallback {
        private Callback<List<RequestRemovedResult>> mCallback;

        public RequestsRemovedCallback(Callback<List<RequestRemovedResult>> callback) {
            mCallback = callback;
        }

        @CalledByNative("RequestsRemovedCallback")
        public void onResult(long[] resultIds, int[] resultCodes) {
            assert resultIds.length == resultCodes.length;

            List<RequestRemovedResult> results = new ArrayList<>();
            for (int i = 0; i < resultIds.length; i++) {
                results.add(new RequestRemovedResult(resultIds[i], resultCodes[i]));
            }

            mCallback.onResult(results);
        }
    }

    /**
     * Contains a result for a remove page request.
     */
    public static class RequestRemovedResult {
        private long mRequestId;
        private int mUpdateRequestResult;

        public RequestRemovedResult(long requestId, int requestResult) {
            mRequestId = requestId;
            mUpdateRequestResult = requestResult;
        }

        /** Request ID as found in the SavePageRequest. */
        public long getRequestId() {
            return mRequestId;
        }

        /** {@see org.chromium.components.offlinepages.background.UpdateRequestResult} enum. */
        public int getUpdateRequestResult() {
            return mUpdateRequestResult;
        }
    }

    /**
     * Removes SavePageRequests from the request queue.
     *
     * The callback will be called with |null| in the case that the queue is unavailable.  This can
     * happen in incognito, for example.
     *
     * @param requestIds The IDs of the requests to remove.
     * @param callback Called when the removal is done, with the SavePageRequest objects that were
     *     actually removed.
     */
    public void removeRequestsFromQueue(
            List<Long> requestIdList, Callback<List<RequestRemovedResult>> callback) {
        long[] requestIds = new long[requestIdList.size()];
        for (int i = 0; i < requestIdList.size(); i++) {
            requestIds[i] = requestIdList.get(i).longValue();
        }
        nativeRemoveRequestsFromQueue(
                mNativeOfflinePageBridge, requestIds, new RequestsRemovedCallback(callback));
    }

    /**
     * Get the offline page associated with the provided offline URL.
     *
     * @param onlineUrl URL of the page.
     * @param tabId Android tab ID.
     * @param callback callback to pass back the
     * matching {@link OfflinePageItem} if found. Will pass back null if not.
     */
    public void selectPageForOnlineUrl(String onlineUrl, int tabId,
            Callback<OfflinePageItem> callback) {
        nativeSelectPageForOnlineUrl(mNativeOfflinePageBridge, onlineUrl, tabId, callback);
    }

    /**
     * Get the offline page associated with the provided offline ID.
     *
     * @param offlineId ID of the offline page.
     * @param callback callback to pass back the
     * matching {@link OfflinePageItem} if found. Will pass back <code>null</code> if not.
     */
    public void getPageByOfflineId(final long offlineId, final Callback<OfflinePageItem> callback) {
        nativeGetPageByOfflineId(mNativeOfflinePageBridge, offlineId, callback);
    }

    /**
     * Saves the web page loaded into web contents offline.
     *
     * @param webContents Contents of the page to save.
     * @param ClientId of the bookmark related to the offline page.
     * @param callback Interface that contains a callback. This may be called synchronously, e.g.
     * if the web contents is already destroyed.
     * @see SavePageCallback
     */
    public void savePage(final WebContents webContents, final ClientId clientId,
            final SavePageCallback callback) {
        assert mIsNativeOfflinePageModelLoaded;
        assert webContents != null;

        nativeSavePage(mNativeOfflinePageBridge, callback, webContents, clientId.getNamespace(),
                clientId.getId());
    }

    /**
     * Save the given URL as an offline page when the network becomes available.
     *
     * The page is marked as not having been saved by the user.  Use the 3-argument form to specify
     * a user request.
     *
     * @param url The given URL to save for later.
     * @param clientId The client ID for the offline page to be saved later.
     */
    @VisibleForTesting
    public void savePageLater(String url, ClientId clientId) {
        savePageLater(url, clientId, true);
    }

    /**
     * Save the given URL as an offline page when the network becomes available.
     *
     * @param url The given URL to save for later.
     * @param clientId The client ID for the offline page to be saved later.
     * @param userRequested Whether this request should be prioritized because the user explicitly
     *     requested it.
     */
    public void savePageLater(final String url, final ClientId clientId, boolean userRequested) {
        nativeSavePageLater(mNativeOfflinePageBridge, url, clientId.getNamespace(),
                clientId.getId(), userRequested);
    }

    /**
     * Save the given URL as an offline page when the network becomes available with a randomly
     * generated clientId in the given namespace.
     *
     * @param url The given URL to save for later.
     * @param namespace The namespace for the offline page to be saved later.
     * @param userRequested Whether this request should be prioritized because the user explicitly
     *                      requested it.
     */
    public void savePageLater(final String url, final String namespace, boolean userRequested) {
        ClientId clientId = ClientId.createGuidClientIdForNamespace(namespace);
        savePageLater(url, clientId, true /* userRequested */);
    }

    /**
     * Deletes an offline page related to a specified bookmark.
     *
     * @param clientId Client ID for which the offline copy will be deleted.
     * @param callback Interface that contains a callback.
     */
    @VisibleForTesting
    public void deletePage(final ClientId clientId, Callback<Integer> callback) {
        assert mIsNativeOfflinePageModelLoaded;
        ArrayList<ClientId> ids = new ArrayList<ClientId>();
        ids.add(clientId);

        deletePagesByClientId(ids, callback);
    }

    /**
     * Deletes offline pages based on the list of provided client IDs. Calls the callback
     * when operation is complete. Requires that the model is already loaded.
     *
     * @param clientIds A list of Client IDs for which the offline pages will be deleted.
     * @param callback A callback that will be called once operation is completed.
     */
    public void deletePagesByClientId(List<ClientId> clientIds, Callback<Integer> callback) {
        String[] namespaces = new String[clientIds.size()];
        String[] ids = new String[clientIds.size()];

        for (int i = 0; i < clientIds.size(); i++) {
            namespaces[i] = clientIds.get(i).getNamespace();
            ids[i] = clientIds.get(i).getId();
        }

        nativeDeletePagesByClientId(mNativeOfflinePageBridge, namespaces, ids, callback);
    }

    /**
     * Whether or not the underlying offline page model is loaded.
     */
    public boolean isOfflinePageModelLoaded() {
        return mIsNativeOfflinePageModelLoaded;
    }

    /**
     * Retrieves the extra request header to reload the offline page.
     * @param webContents Contents of the page to reload.
     * @return The extra request header string.
     */
    public String getOfflinePageHeaderForReload(WebContents webContents) {
        return nativeGetOfflinePageHeaderForReload(mNativeOfflinePageBridge, webContents);
    }

    /**
     * @return True if an offline preview is being shown.
     * @param webContents Contents of the page to check.
     */
    public boolean isShowingOfflinePreview(WebContents webContents) {
        return nativeIsShowingOfflinePreview(mNativeOfflinePageBridge, webContents);
    }

    private static class CheckPagesExistOfflineCallbackInternal {
        private Callback<Set<String>> mCallback;

        CheckPagesExistOfflineCallbackInternal(Callback<Set<String>> callback) {
            mCallback = callback;
        }

        @CalledByNative("CheckPagesExistOfflineCallbackInternal")
        public void onResult(String[] results) {
            Set<String> resultSet = new HashSet<>();
            Collections.addAll(resultSet, results);
            mCallback.onResult(resultSet);
        }
    }

    /**
     * Returns via callback any urls in <code>urls</code> for which there exist offline pages.
     *
     * TODO(http://crbug.com/598006): Add metrics for preventing UI jank.
     */
    public void checkPagesExistOffline(Set<String> urls, Callback<Set<String>> callback) {
        String[] urlArray = urls.toArray(new String[urls.size()]);

        CheckPagesExistOfflineCallbackInternal callbackInternal =
                new CheckPagesExistOfflineCallbackInternal(callback);
        nativeCheckPagesExistOffline(mNativeOfflinePageBridge, urlArray, callbackInternal);
    }

    @VisibleForTesting
    static void setOfflineBookmarksEnabledForTesting(boolean enabled) {
        sOfflineBookmarksEnabled = enabled;
    }

    @CalledByNative
    void offlinePageModelLoaded() {
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

    /**
     * Removes references to the native OfflinePageBridge when it is being destroyed.
     */
    @CalledByNative
    private void offlinePageBridgeDestroyed() {
        ThreadUtils.assertOnUiThread();
        assert mNativeOfflinePageBridge != 0;

        mIsNativeOfflinePageModelLoaded = false;
        mNativeOfflinePageBridge = 0;

        // TODO(dewittj): Add a model destroyed method to the observer interface.
        mObservers.clear();
    }

    @CalledByNative
    void offlinePageDeleted(long offlineId, ClientId clientId) {
        for (OfflinePageModelObserver observer : mObservers) {
            observer.offlinePageDeleted(offlineId, clientId);
        }
    }

    @CalledByNative
    private static void createOfflinePageAndAddToList(List<OfflinePageItem> offlinePagesList,
            String url, long offlineId, String clientNamespace, String clientId, String filePath,
            long fileSize, long creationTime, int accessCount, long lastAccessTimeMs) {
        offlinePagesList.add(createOfflinePageItem(url, offlineId, clientNamespace, clientId,
                filePath, fileSize, creationTime, accessCount, lastAccessTimeMs));
    }

    @CalledByNative
    private static OfflinePageItem createOfflinePageItem(String url, long offlineId,
            String clientNamespace, String clientId, String filePath, long fileSize,
            long creationTime, int accessCount, long lastAccessTimeMs) {
        return new OfflinePageItem(url, offlineId, clientNamespace, clientId, filePath,
                fileSize, creationTime, accessCount, lastAccessTimeMs);
    }

    @CalledByNative
    private static ClientId createClientId(String clientNamespace, String id) {
        return new ClientId(clientNamespace, id);
    }

    private static native boolean nativeIsOfflineBookmarksEnabled();
    private static native boolean nativeIsPageSharingEnabled();
    private static native boolean nativeCanSavePage(String url);
    private static native OfflinePageBridge nativeGetOfflinePageBridgeForProfile(Profile profile);

    @VisibleForTesting
    native void nativeGetAllPages(long nativeOfflinePageBridge, List<OfflinePageItem> offlinePages,
            final Callback<List<OfflinePageItem>> callback);
    private native void nativeCheckPagesExistOffline(long nativeOfflinePageBridge, Object[] urls,
            CheckPagesExistOfflineCallbackInternal callback);
    @VisibleForTesting
    native void nativeGetRequestsInQueue(
            long nativeOfflinePageBridge, Callback<SavePageRequest[]> callback);
    @VisibleForTesting
    native void nativeRemoveRequestsFromQueue(
            long nativeOfflinePageBridge, long[] requestIds, RequestsRemovedCallback callback);
    @VisibleForTesting
    native void nativeGetPageByOfflineId(
            long nativeOfflinePageBridge, long offlineId, Callback<OfflinePageItem> callback);
    @VisibleForTesting
    native void nativeGetPagesByClientId(long nativeOfflinePageBridge, List<OfflinePageItem> result,
            String[] namespaces, String[] ids, Callback<List<OfflinePageItem>> callback);
    @VisibleForTesting
    native void nativeDeletePagesByClientId(long nativeOfflinePageBridge, String[] namespaces,
            String[] ids, Callback<Integer> callback);
    private native void nativeSelectPageForOnlineUrl(
            long nativeOfflinePageBridge, String onlineUrl, int tabId,
            Callback<OfflinePageItem> callback);
    private native void nativeSavePage(long nativeOfflinePageBridge, SavePageCallback callback,
            WebContents webContents, String clientNamespace, String clientId);
    private native void nativeSavePageLater(long nativeOfflinePageBridge, String url,
            String clientNamespace, String clientId, boolean userRequested);
    private native String nativeGetOfflinePageHeaderForReload(
            long nativeOfflinePageBridge, WebContents webContents);
    private native boolean nativeIsShowingOfflinePreview(
            long nativeOfflinePageBridge, WebContents webContents);
}
