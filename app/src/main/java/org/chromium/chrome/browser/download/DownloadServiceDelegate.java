// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

/** Interface for classes implementing concrete implementation of UI behavior. */
public interface DownloadServiceDelegate {
    /**
     * Called to cancel a download.
     * @param downloadGuid GUID of the download.
     * @param isOffTheRecord Whether the download is off the record.
     * @param isNotificationDismissed Whether cancel is caused by dismissing the notification.
     */
    void cancelDownload(String downloadGuid, boolean isOffTheRecord,
            boolean isNotificationDismissed);

    /**
     * Called to pause a download.
     * @param downloadGuid GUID of the download.
     * @param isOffTheRecord Whether the download is off the record.
     */
    void pauseDownload(String downloadGuid, boolean isOffTheRecord);

    /**
     * Called to resume a paused download.
     * @param item Download item to resume.
     * @param hasUserGesture Whether the resumption is triggered by user gesture.
     * TODO(fgorski): Update the interface to not require download item.
     */
    void resumeDownload(DownloadItem item, boolean hasUserGesture);

    /** Called to destroy the delegate, in case it needs to be destroyed. */
    void destroyServiceDelegate();
}
