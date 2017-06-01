// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;

import java.util.UUID;

/**
 * Class representing the download information stored in SharedPreferences to construct a
 * download notification.
 */
public class DownloadSharedPreferenceEntry {
    private static final String TAG = "DownloadEntry";

    // Current version of the DownloadSharedPreferenceEntry. When changing the SharedPreference,
    // we need to change the version number too.
    @VisibleForTesting
    static final int VERSION = 3;
    public static final int ITEM_TYPE_DOWNLOAD = 1;
    public static final int ITEM_TYPE_OFFLINE_PAGE = 2;

    public final int notificationId;
    public final boolean isOffTheRecord;  // Whether the download is public (non incognito).
    public boolean canDownloadWhileMetered;
    public final String fileName;
    public final String downloadGuid;
    public final int itemType;

    static final DownloadSharedPreferenceEntry INVALID_ENTRY =
            new DownloadSharedPreferenceEntry(-1, false, false, null, "", ITEM_TYPE_DOWNLOAD);

    DownloadSharedPreferenceEntry(int notificationId, boolean isOffTheRecord,
            boolean canDownloadWhileMetered, String guid, String fileName, int itemType) {
        this.notificationId = notificationId;
        this.isOffTheRecord = isOffTheRecord;
        this.canDownloadWhileMetered = canDownloadWhileMetered;
        this.downloadGuid = guid;
        this.fileName = fileName;
        this.itemType = itemType;
    }

    /**
     * Parse the pending notification from a String object in SharedPrefs.
     *
     * @param sharedPrefString String from SharedPreference, containing the notification ID, GUID,
     *        file name, whether it is resumable and whether download started on a metered network.
     * @return a DownloadSharedPreferenceEntry object.
     */
    static DownloadSharedPreferenceEntry parseFromString(String sharedPrefString) {
        String versionString = sharedPrefString.substring(0, sharedPrefString.indexOf(","));
        // Ignore all SharedPreference entries that has an invalid version for now.
        int version = 0;
        try {
            version = Integer.parseInt(versionString);
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "Exception while parsing pending download:" + sharedPrefString);
        }
        if (version <= 0 || version > 3) return INVALID_ENTRY;

        // Expected number of items for version 1 and 2 is 6, version 3 is 7.
        int expectedItemsNumber = (version == 3 ? 7 : 6);
        String[] values = sharedPrefString.split(",", expectedItemsNumber);
        if (values.length != expectedItemsNumber) return INVALID_ENTRY;

        // Index == 0 is used for version, therefor we start from 1.
        int currentIndex = 1;
        int id = 0;
        int itemType = ITEM_TYPE_DOWNLOAD;
        try {
            id = Integer.parseInt(values[currentIndex++]);
            if (version > 2) {
                itemType = Integer.parseInt(values[currentIndex++]);
            }
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "Exception while parsing pending download:" + sharedPrefString);
            return INVALID_ENTRY;
        }
        if (itemType != ITEM_TYPE_DOWNLOAD && itemType != ITEM_TYPE_OFFLINE_PAGE) {
            return INVALID_ENTRY;
        }

        boolean isOffTheRecord = (version >= 2) ? "1".equals(values[currentIndex])
                                                : "0".equals(values[currentIndex]);
        ++currentIndex;

        boolean canDownloadWhileMetered = "1".equals(values[currentIndex++]);

        String guid = values[currentIndex++];
        if (!isValidGUID(guid)) return INVALID_ENTRY;

        String fileName = values[currentIndex++];

        return new DownloadSharedPreferenceEntry(
                id, isOffTheRecord, canDownloadWhileMetered, guid, fileName, itemType);
    }

    /**
     * @return a string for the DownloadSharedPreferenceEntry instance to be inserted into
     *         SharedPrefs.
     */
    String getSharedPreferenceString() {
        return VERSION + "," + notificationId + "," + itemType + "," + (isOffTheRecord ? "1" : "0")
                + "," + (canDownloadWhileMetered ? "1" : "0") + "," + downloadGuid + "," + fileName;
    }

    /**
     * Check if a string is a valid GUID. GUID is RFC 4122 compliant, it should have format
     * xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.
     * TODO(qinmin): move this to base/.
     * @return true if the string is a valid GUID, or false otherwise.
     */
    static boolean isValidGUID(String guid) {
        if (guid == null) return false;
        try {
            // Java UUID class doesn't check the length of the string. Need to convert it back to
            // string so that we can validate the length of the original string.
            UUID uuid = UUID.fromString(guid);
            String uuidString = uuid.toString();
            return guid.equalsIgnoreCase(uuidString);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isOfflinePage() {
        return itemType == ITEM_TYPE_OFFLINE_PAGE;
    }

    /**
     * Build a download item from this object.
     */
    DownloadItem buildDownloadItem() {
        DownloadInfo info = new DownloadInfo.Builder()
                .setDownloadGuid(downloadGuid)
                .setFileName(fileName)
                .setIsOffTheRecord(isOffTheRecord)
                .build();
        return new DownloadItem(false, info);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DownloadSharedPreferenceEntry)) {
            return false;
        }
        final DownloadSharedPreferenceEntry other = (DownloadSharedPreferenceEntry) object;
        return TextUtils.equals(downloadGuid, other.downloadGuid)
                && TextUtils.equals(fileName, other.fileName)
                && notificationId == other.notificationId
                && itemType == other.itemType
                && isOffTheRecord == other.isOffTheRecord
                && canDownloadWhileMetered == other.canDownloadWhileMetered;
    }

    @Override
    public int hashCode() {
        int hash = 31;
        hash = 37 * hash + (isOffTheRecord ? 1 : 0);
        hash = 37 * hash + (canDownloadWhileMetered ? 1 : 0);
        hash = 37 * hash + notificationId;
        hash = 37 * hash + itemType;
        hash = 37 * hash + downloadGuid.hashCode();
        hash = 37 * hash + fileName.hashCode();
        return hash;
    }
}
