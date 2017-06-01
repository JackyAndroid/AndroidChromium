// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.downloads;

/** Class representing offline page or save page request to downloads UI. */
public class OfflinePageDownloadItem {
    private final String mUrl;
    private final String mTitle;
    private final String mGuid;
    private final String mTargetPath;
    private final long mStartTimeMs;
    private final long mTotalBytes;

    public OfflinePageDownloadItem(
            String guid, String url, String title, String targetPath,
            long startTimeMs, long totalBytes) {
        mGuid = guid;
        mUrl = url;
        mTitle = title;
        mTargetPath = targetPath;
        mStartTimeMs = startTimeMs;
        mTotalBytes = totalBytes;
    }

    /** @return GUID identifying the item. */
    public String getGuid() {
        return mGuid;
    }

    /** @return URL related to the item. */
    public String getUrl() {
        return mUrl;
    }

    /** @return Title of the page. */
    public String getTitle() {
        return mTitle;
    }

    /** @return Path to the offline item on the disk. */
    // TODO(fgorski): Title would be more meaningful to show in the Download UI, where the local
    // path is shown right now.
    public String getTargetPath() {
        return mTargetPath;
    }

    /** @return Start time of the item, corresponding to when the offline page was saved. */
    public long getStartTimeMs() {
        return mStartTimeMs;
    }

    /** @return Size of the offline archive in bytes. */
    public long getTotalBytes() {
        return mTotalBytes;
    }
}
