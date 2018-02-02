// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.evaluation;

import org.chromium.base.Callback;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.offlinepages.ClientId;
import org.chromium.chrome.browser.offlinepages.OfflinePageItem;
import org.chromium.chrome.browser.offlinepages.SavePageRequest;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Class used for offline page evaluation testing tools.
 */
@JNINamespace("offline_pages::android")
public class OfflinePageEvaluationBridge {
    /**
     * Observer class for notifications on changes to save page requests or offline page model which
     * are used for testing.
     */
    public abstract static class OfflinePageEvaluationObserver {
        /**
         * Event fired when the offline page model is loaded.
         */
        public void offlinePageModelLoaded() {}

        /**
         * Event fired when a new request is added.
         * @param request The newly added save page request.
         */
        public void savePageRequestAdded(SavePageRequest request) {}

        /**
         * Event fired when a request is completed.
         * @param request The completed request.
         * @param status The status of the completion, see
         * org.chromium.components.offlinepages.BackgroundSavePageResult.
         */
        public void savePageRequestCompleted(SavePageRequest request, int status) {}

        /**
         * Event fired when a new request is changed.
         * @param request The changed request.
         */
        public void savePageRequestChanged(SavePageRequest request) {}
    }

    /**
     * Get the instance of the evaluation bridge.
     * @param profile The profile used to get bridge.
     * @param useEvaluationScheduler True if using the evaluation scheduler instead of the
     * GCMNetworkManager one.
     */
    public static OfflinePageEvaluationBridge getForProfile(
            Profile profile, boolean useEvaluationScheduler) {
        ThreadUtils.assertOnUiThread();
        return nativeGetBridgeForProfile(profile, useEvaluationScheduler);
    }

    private long mNativeOfflinePageEvaluationBridge;
    private boolean mIsOfflinePageModelLoaded;
    private ObserverList<OfflinePageEvaluationObserver> mObservers =
            new ObserverList<OfflinePageEvaluationObserver>();

    /**
     * Creates an offline page evalutaion bridge for a given profile.
     */
    OfflinePageEvaluationBridge(long nativeOfflinePageEvaluationBridge) {
        mNativeOfflinePageEvaluationBridge = nativeOfflinePageEvaluationBridge;
    }

    /**
     * Called by the native OfflinePageEvaluationBridge.
     */
    @CalledByNative
    private static OfflinePageEvaluationBridge create(long nativeOfflinePageEvaluationBridge) {
        return new OfflinePageEvaluationBridge(nativeOfflinePageEvaluationBridge);
    }

    /**
     * Add an observer of the evaluation events.
     */
    public void addObserver(OfflinePageEvaluationObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Remove an observer of evaluation events.
     */
    public void removeObserver(OfflinePageEvaluationObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Gets all pages in offline page model.
     * @param callback The callback would be invoked after the action completes and return with a
     *                 list of pages.
     */
    public void getAllPages(final Callback<List<OfflinePageItem>> callback) {
        List<OfflinePageItem> result = new ArrayList<>();
        nativeGetAllPages(mNativeOfflinePageEvaluationBridge, result, callback);
    }

    /**
     * Saves a url as offline page async.
     * @param url The url of the web page.
     * @param namespace The namespace to which the page belongs.
     * @param userRequest True if it's user-requested page.
     */
    public void savePageLater(final String url, final String namespace, boolean userRequested) {
        ClientId clientId = ClientId.createGuidClientIdForNamespace(namespace);
        nativeSavePageLater(mNativeOfflinePageEvaluationBridge, url, namespace, clientId.getId(),
                userRequested);
    }

    /**
     * Forces request coordinator to process the requests in the queue.
     * @param callback The callback would be invoked after the operation completes.
     * @return True if processing starts successfully and callback is expected to be called, false
     * otherwise.
     */
    public boolean pushRequestProcessing(final Callback<Boolean> callback) {
        return nativePushRequestProcessing(mNativeOfflinePageEvaluationBridge, callback);
    }

    /**
     * Gets all requests in the queue.
     * @param callback The callback would be invoked with a list of requests which are in the queue.
     */
    public void getRequestsInQueue(Callback<SavePageRequest[]> callback) {
        nativeGetRequestsInQueue(mNativeOfflinePageEvaluationBridge, callback);
    }

    /**
     * Removes requests from the queue by request ids.
     * @param requestIds The list of request ids to be deleted.
     * @param callback The callback would be invoked with number of successfully deleted ids.
     */
    public void removeRequestsFromQueue(List<Long> requestIds, Callback<Integer> callback) {
        long[] ids = new long[requestIds.size()];
        for (int i = 0; i < requestIds.size(); i++) {
            ids[i] = requestIds.get(i);
        }
        nativeRemoveRequestsFromQueue(mNativeOfflinePageEvaluationBridge, ids, callback);
    }

    /**
     * @return True if the offline page model has fully loaded.
     */
    public boolean isOfflinePageModelLoaded() {
        return mIsOfflinePageModelLoaded;
    }

    @CalledByNative
    void savePageRequestAdded(SavePageRequest request) {
        for (OfflinePageEvaluationObserver observer : mObservers) {
            observer.savePageRequestAdded(request);
        }
    }

    @CalledByNative
    void savePageRequestCompleted(SavePageRequest request, int status) {
        for (OfflinePageEvaluationObserver observer : mObservers) {
            observer.savePageRequestCompleted(request, status);
        }
    }

    @CalledByNative
    void savePageRequestChanged(SavePageRequest request) {
        for (OfflinePageEvaluationObserver observer : mObservers) {
            observer.savePageRequestChanged(request);
        }
    }

    @CalledByNative
    void offlinePageModelLoaded() {
        mIsOfflinePageModelLoaded = true;
        for (OfflinePageEvaluationObserver observer : mObservers) {
            observer.offlinePageModelLoaded();
        }
    }

    @CalledByNative
    private void offlinePageEvaluationBridgeDestroyed() {
        ThreadUtils.assertOnUiThread();
        assert mNativeOfflinePageEvaluationBridge != 0;

        mNativeOfflinePageEvaluationBridge = 0;
        mIsOfflinePageModelLoaded = false;

        mObservers.clear();
    }

    @CalledByNative
    private static void createOfflinePageAndAddToList(List<OfflinePageItem> offlinePagesList,
            String url, long offlineId, String clientNamespace, String clientId, String filePath,
            long fileSize, long creationTime, int accessCount, long lastAccessTimeMs) {
        offlinePagesList.add(createOfflinePageItem(url, offlineId, clientNamespace, clientId,
                filePath, fileSize, creationTime, accessCount, lastAccessTimeMs));
    }

    private static OfflinePageItem createOfflinePageItem(String url, long offlineId,
            String clientNamespace, String clientId, String filePath, long fileSize,
            long creationTime, int accessCount, long lastAccessTimeMs) {
        return new OfflinePageItem(url, offlineId, clientNamespace, clientId, filePath, fileSize,
                creationTime, accessCount, lastAccessTimeMs);
    }

    private static native OfflinePageEvaluationBridge nativeGetBridgeForProfile(
            Profile profile, boolean useEvaluationScheduler);

    private native void nativeGetAllPages(long nativeOfflinePageEvaluationBridge,
            List<OfflinePageItem> offlinePages, final Callback<List<OfflinePageItem>> callback);
    private native void nativeSavePageLater(long nativeOfflinePageEvaluationBridge, String url,
            String clientNamespace, String clientId, boolean userRequested);
    private native boolean nativePushRequestProcessing(
            long nativeOfflinePageEvaluationBridge, Callback<Boolean> callback);
    private native void nativeGetRequestsInQueue(
            long nativeOfflinePageEvaluationBridge, final Callback<SavePageRequest[]> callback);
    private native void nativeRemoveRequestsFromQueue(long nativeOfflinePageEvaluationBridge,
            long[] requestIds, final Callback<Integer> callback);
}
