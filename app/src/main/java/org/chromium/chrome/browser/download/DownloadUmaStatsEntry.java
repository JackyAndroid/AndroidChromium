// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import org.chromium.base.Log;

/**
 * SharedPreferences entries for for helping report UMA stats. A download may require several
 * browser sessions to complete, we need to store them in SharedPreferences in case browser is
 * killed.
 */
public class DownloadUmaStatsEntry {
    private static final String TAG = "DownloadUmaStats";
    public final String id;
    public final long downloadStartTime;
    public final boolean useDownloadManager;
    public int numInterruptions;
    public boolean isPaused;

    DownloadUmaStatsEntry(String id, long downloadStartTime, int numInterruptions,
            boolean isPaused, boolean useDownloadManager) {
        this.id = id;
        this.downloadStartTime = downloadStartTime;
        this.numInterruptions = numInterruptions;
        this.isPaused = isPaused;
        this.useDownloadManager = useDownloadManager;
    }

    /**
     * Parse the UMA entry from a String object in SharedPrefs.
     *
     * @param sharedPrefString String from SharedPreference.
     * @return a DownloadUmaStatsEntry object.
     */
    static DownloadUmaStatsEntry parseFromString(String sharedPrefString) {
        String[] values = sharedPrefString.split(",", 5);
        if (values.length == 5) {
            try {
                boolean useDownloadManager = "1".equals(values[0]);
                boolean isPaused = "1".equals(values[1]);
                long downloadStartTime = Long.parseLong(values[2]);
                int numInterruptions = Integer.parseInt(values[3]);
                String id = values[4];
                return new DownloadUmaStatsEntry(
                        id, downloadStartTime, numInterruptions, isPaused, useDownloadManager);
            } catch (NumberFormatException nfe) {
                Log.w(TAG, "Exception while parsing UMA entry:" + sharedPrefString);
            }
        }
        return null;
    }

    /**
     * @return a string for the DownloadUmaStatsEntry instance to be inserted into SharedPrefs.
     */
    String getSharedPreferenceString() {
        return (useDownloadManager ? "1" : "0") + "," + (isPaused ? "1" : "0") + ","
                + downloadStartTime + "," + numInterruptions + "," + id;
    }

    /**
     * Build a DownloadItem from this object.
     * @return a DownloadItem built from this object.
     */
    DownloadItem buildDownloadItem() {
        DownloadItem item = new DownloadItem(useDownloadManager, null);
        item.setStartTime(downloadStartTime);
        if (useDownloadManager) {
            item.setSystemDownloadId(Long.parseLong(id));
        } else {
            DownloadInfo info = new DownloadInfo.Builder().setDownloadGuid(id).build();
            item.setDownloadInfo(info);
        }
        return item;
    }
}
