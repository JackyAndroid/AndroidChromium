// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.Manifest.permission;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.ui.base.WindowAndroid;

/**
 * Java counterpart of android DownloadController.
 *
 * Its a singleton class instantiated by the C++ DownloadController.
 */
public class DownloadController {
    private static final String LOGTAG = "DownloadController";
    private static final DownloadController sInstance = new DownloadController();

    /**
     * Class for notifying the application that download has completed.
     */
    public interface DownloadNotificationService {
        /**
         * Notify the host application that a download is finished.
         * @param downloadInfo Information about the completed download.
         */
        void onDownloadCompleted(final DownloadInfo downloadInfo);

        /**
         * Notify the host application that a download is in progress.
         * @param downloadInfo Information about the in-progress download.
         */
        void onDownloadUpdated(final DownloadInfo downloadInfo);

        /**
         * Notify the host application that a download is cancelled.
         * @param downloadInfo Information about the cancelled download.
         */
        void onDownloadCancelled(final DownloadInfo downloadInfo);

        /**
         * Notify the host application that a download is interrupted.
         * @param downloadInfo Information about the completed download.
         * @param isAutoResumable Download can be auto resumed when network becomes available.
         */
        void onDownloadInterrupted(final DownloadInfo downloadInfo, boolean isAutoResumable);
    }

    private static DownloadNotificationService sDownloadNotificationService;

    @CalledByNative
    public static DownloadController getInstance() {
        return sInstance;
    }

    private DownloadController() {
        nativeInit();
    }

    public static void setDownloadNotificationService(DownloadNotificationService service) {
        sDownloadNotificationService = service;
    }

    /**
     * Notifies the download delegate that a download completed and passes along info about the
     * download. This can be either a POST download or a GET download with authentication.
     */
    @CalledByNative
    private void onDownloadCompleted(String url, String mimeType, String filename, String path,
            long contentLength, String downloadGuid, String originalUrl, String refererUrl,
            boolean hasUserGesture) {
        if (sDownloadNotificationService == null) return;
        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                .setUrl(url)
                .setMimeType(mimeType)
                .setFileName(filename)
                .setFilePath(path)
                .setContentLength(contentLength)
                .setDescription(filename)
                .setDownloadGuid(downloadGuid)
                .setOriginalUrl(originalUrl)
                .setReferer(refererUrl)
                .setHasUserGesture(hasUserGesture)
                .build();
        sDownloadNotificationService.onDownloadCompleted(downloadInfo);
    }

    /**
     * Notifies the download delegate that a download completed and passes along info about the
     * download. This can be either a POST download or a GET download with authentication.
     */
    @CalledByNative
    private void onDownloadInterrupted(String url, String mimeType, String filename, String path,
            long contentLength, String downloadGuid, boolean isResumable, boolean isAutoResumable,
            boolean isOffTheRecord) {
        if (sDownloadNotificationService == null) return;
        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                .setUrl(url)
                .setMimeType(mimeType)
                .setFileName(filename)
                .setFilePath(path)
                .setContentLength(contentLength)
                .setDescription(filename)
                .setDownloadGuid(downloadGuid)
                .setIsResumable(isResumable)
                .setIsOffTheRecord(isOffTheRecord)
                .build();
        sDownloadNotificationService.onDownloadInterrupted(downloadInfo, isAutoResumable);
    }

    /**
     * Called when a download was cancelled.
     * @param notificationId Notification Id of the download item.
     * @param downloadGuid GUID of the download item.
     */
    @CalledByNative
    private void onDownloadCancelled(String downloadGuid) {
        if (sDownloadNotificationService == null) return;
        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                .setDownloadGuid(downloadGuid)
                .build();
        sDownloadNotificationService.onDownloadCancelled(downloadInfo);
    }

    /**
     * Notifies the download delegate about progress of a download. Downloads that use Chrome
     * network stack use custom notification to display the progress of downloads.
     */
    @CalledByNative
    private void onDownloadUpdated(String url, String mimeType, String filename, String path,
            long contentLength, String downloadGuid, int percentCompleted, long timeRemainingInMs,
            boolean hasUserGesture, boolean isPaused, boolean isOffTheRecord) {
        if (sDownloadNotificationService == null) return;
        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                .setUrl(url)
                .setMimeType(mimeType)
                .setFileName(filename)
                .setFilePath(path)
                .setContentLength(contentLength)
                .setDescription(filename)
                .setDownloadGuid(downloadGuid)
                .setPercentCompleted(percentCompleted)
                .setTimeRemainingInMillis(timeRemainingInMs)
                .setHasUserGesture(hasUserGesture)
                .setIsPaused(isPaused)
                .setIsOffTheRecord(isOffTheRecord)
                .build();
        sDownloadNotificationService.onDownloadUpdated(downloadInfo);
    }


    /**
     * Returns whether file access is allowed.
     *
     * @param windowAndroid WindowAndroid to access file system.
     * @return true if allowed, or false otherwise.
     */
    @CalledByNative
    private boolean hasFileAccess(WindowAndroid windowAndroid) {
        return windowAndroid.hasPermission(permission.WRITE_EXTERNAL_STORAGE);
    }

    /**
     * Notify the results of a file access request.
     * @param callbackId The ID of the callback.
     * @param granted Whether access was granted.
     */
    public void onRequestFileAccessResult(long callbackId, boolean granted) {
        nativeOnRequestFileAccessResult(callbackId, granted);
    }

    // native methods
    private native void nativeInit();
    private native void nativeOnRequestFileAccessResult(long callbackId, boolean granted);
}
