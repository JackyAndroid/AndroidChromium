// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A wrapper for Android DownloadManager to provide utility functions.
 */
public class DownloadManagerDelegate {
    private static final String TAG = "DownloadDelegate";
    private static final long INVALID_SYSTEM_DOWNLOAD_ID = -1;
    private static final String DOWNLOAD_ID_MAPPINGS_FILE_NAME = "download_id_mappings";
    private static final Object sLock = new Object();
    protected final Context mContext;

    public DownloadManagerDelegate(Context context) {
        mContext = context;
    }

    /**
     * Inserts a new download ID mapping into the SharedPreferences
     * @param downloadId system download ID from Android DownloadManager.
     * @param downloadGuid Download GUID.
     */
    private void addDownloadIdMapping(long downloadId, String downloadGuid) {
        synchronized (sLock) {
            SharedPreferences sharedPrefs = getSharedPreferences();
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putLong(downloadGuid, downloadId);
            editor.apply();
        }
    }

    /**
     * Removes a download Id mapping from the SharedPreferences given the download GUID.
     * @param guid Download GUID.
     * @return the Android DownloadManager's download ID that is removed, or
     *         INVALID_SYSTEM_DOWNLOAD_ID if it is not found.
     */
    private long removeDownloadIdMapping(String downloadGuid) {
        long downloadId = INVALID_SYSTEM_DOWNLOAD_ID;
        synchronized (sLock) {
            SharedPreferences sharedPrefs = getSharedPreferences();
            downloadId = sharedPrefs.getLong(downloadGuid, INVALID_SYSTEM_DOWNLOAD_ID);
            if (downloadId != INVALID_SYSTEM_DOWNLOAD_ID) {
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.remove(downloadGuid);
                editor.apply();
            }
        }
        return downloadId;
    }

    /**
     * Lazily retrieve the SharedPreferences when needed. Since download operations are not very
     * frequent, no need to load all SharedPreference entries into a hashmap in the memory.
     * @return the SharedPreferences instance.
     */
    private SharedPreferences getSharedPreferences() {
        return ContextUtils.getApplicationContext().getSharedPreferences(
                DOWNLOAD_ID_MAPPINGS_FILE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * @see android.app.DownloadManager#addCompletedDownload(String, String, boolean, String,
     * String, long, boolean)
     */
    protected long addCompletedDownload(String fileName, String description, String mimeType,
            String path, long length, String originalUrl, String referer, String downloadGuid) {
        DownloadManager manager =
                (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
        boolean useSystemNotification = !notificationManager.areNotificationsEnabled();
        long downloadId = -1;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            Class<?> c = manager.getClass();
            try {
                Class[] args = {String.class, String.class, boolean.class, String.class,
                        String.class, long.class, boolean.class, Uri.class, Uri.class};
                Method method = c.getMethod("addCompletedDownload", args);
                // OriginalUri has to be null or non-empty.
                Uri originalUri = TextUtils.isEmpty(originalUrl) ? null : Uri.parse(originalUrl);
                Uri refererUri = TextUtils.isEmpty(referer) ? null : Uri.parse(referer);
                downloadId = (Long) method.invoke(manager, fileName, description, true, mimeType,
                        path, length, useSystemNotification, originalUri, refererUri);
            } catch (SecurityException e) {
                Log.e(TAG, "Cannot access the needed method.");
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "Cannot find the needed method.");
            } catch (InvocationTargetException e) {
                Log.e(TAG, "Error calling the needed method.");
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Error accessing the needed method.");
            }
        } else {
            downloadId = manager.addCompletedDownload(fileName, description, true, mimeType, path,
                    length, useSystemNotification);
        }
        addDownloadIdMapping(downloadId, downloadGuid);
        return downloadId;
    }

    /**
     * Removes a download from Android DownloadManager.
     * @param downloadGuid The GUID of the download.
     */
    void removeCompletedDownload(String downloadGuid) {
        long downloadId = removeDownloadIdMapping(downloadGuid);
        if (downloadId != INVALID_SYSTEM_DOWNLOAD_ID) {
            DownloadManager manager =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            manager.remove(downloadId);
        }
    }

    /**
     * Interface for returning the query result when it completes.
     */
    public interface DownloadQueryCallback {
        /**
         * Callback function to return query result.
         * @param result Query result from android DownloadManager.
         * @param showNotifications Whether to show status notifications.
         */
        public void onQueryCompleted(DownloadQueryResult result, boolean showNotifications);
    }

    /**
     * Result for querying the Android DownloadManager.
     */
    static class DownloadQueryResult {
        public final DownloadItem item;
        public final int downloadStatus;
        public final long downloadTimeInMilliseconds;
        public final long bytesDownloaded;
        public final boolean canResolve;
        public final int failureReason;

        DownloadQueryResult(DownloadItem item, int downloadStatus, long downloadTimeInMilliseconds,
                long bytesDownloaded, boolean canResolve, int failureReason) {
            this.item = item;
            this.downloadStatus = downloadStatus;
            this.downloadTimeInMilliseconds = downloadTimeInMilliseconds;
            this.canResolve = canResolve;
            this.bytesDownloaded = bytesDownloaded;
            this.failureReason = failureReason;
        }
    }

    /**
     * Query the Android DownloadManager for download status.
     * @param downloadItem Download item to query.
     * @param showNotifications Whether to show status notifications.
     * @param callback Callback to be notified when query completes.
     */
    void queryDownloadResult(
            DownloadItem downloadItem, boolean showNotifications, DownloadQueryCallback callback) {
        DownloadQueryTask task = new DownloadQueryTask(downloadItem, showNotifications, callback);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Async task to query download status from Android DownloadManager
     */
    private class DownloadQueryTask extends AsyncTask<Void, Void, DownloadQueryResult> {
        private final DownloadItem mDownloadItem;
        private final boolean mShowNotifications;
        private final DownloadQueryCallback mCallback;

        public DownloadQueryTask(DownloadItem downloadItem, boolean showNotifications,
                DownloadQueryCallback callback) {
            mDownloadItem = downloadItem;
            mShowNotifications = showNotifications;
            mCallback = callback;
        }

        @Override
        public DownloadQueryResult doInBackground(Void... voids) {
            DownloadManager manager =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor c = manager.query(
                    new DownloadManager.Query().setFilterById(mDownloadItem.getSystemDownloadId()));
            if (c == null) {
                return new DownloadQueryResult(mDownloadItem,
                        DownloadManagerService.DOWNLOAD_STATUS_CANCELLED, 0, 0, false, 0);
            }
            long bytesDownloaded = 0;
            boolean canResolve = false;
            int downloadStatus = DownloadManagerService.DOWNLOAD_STATUS_IN_PROGRESS;
            int failureReason = 0;
            long lastModifiedTime = 0;
            if (c.moveToNext()) {
                int statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    downloadStatus = DownloadManagerService.DOWNLOAD_STATUS_COMPLETE;
                    if (mShowNotifications) {
                        canResolve = DownloadManagerService.isOMADownloadDescription(
                                mDownloadItem.getDownloadInfo())
                                || DownloadManagerService.canResolveDownloadItem(
                                        mContext, mDownloadItem, false);
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    downloadStatus = DownloadManagerService.DOWNLOAD_STATUS_FAILED;
                    failureReason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                }
                lastModifiedTime =
                        c.getLong(c.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP));
                bytesDownloaded =
                        c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            } else {
                downloadStatus = DownloadManagerService.DOWNLOAD_STATUS_CANCELLED;
            }
            c.close();
            long totalTime = Math.max(0, lastModifiedTime - mDownloadItem.getStartTime());
            return new DownloadQueryResult(mDownloadItem, downloadStatus, totalTime,
                    bytesDownloaded, canResolve, failureReason);
        }

        @Override
        protected void onPostExecute(DownloadQueryResult result) {
            mCallback.onQueryCompleted(result, mShowNotifications);
        }
    }

    static Uri getContentUriFromDownloadManager(Context context, long downloadId) {
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri contentUri = null;
        try {
            contentUri = manager.getUriForDownloadedFile(downloadId);
        } catch (SecurityException e) {
            Log.e(TAG, "unable to get content URI from DownloadManager");
        }
        return contentUri;
    }
}
