// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.Pair;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.content.browser.DownloadController;
import org.chromium.content.browser.DownloadInfo;
import org.chromium.ui.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chrome implementation of the DownloadNotificationService interface. This class is responsible for
 * keeping track of which downloads are in progress. It generates updates for progress of downloads
 * and handles cleaning up of interrupted progress notifications.
 */
public class DownloadManagerService extends BroadcastReceiver implements
        DownloadController.DownloadNotificationService {
    private static final String TAG = "cr.DownloadService";
    private static final String DOWNLOAD_NOTIFICATION_IDS = "DownloadNotificationIds";
    private static final String DOWNLOAD_DIRECTORY = "Download";
    protected static final String PENDING_OMA_DOWNLOADS = "PendingOMADownloads";
    private static final String UNKNOWN_MIME_TYPE = "application/unknown";
    private static final long UPDATE_DELAY_MILLIS = 1000;
    private static final long INVALID_DOWNLOAD_ID = -1L;
    private static final int UNKNOWN_DOWNLOAD_STATUS = -1;
    // Set will be more expensive to initialize, so use an ArrayList here.
    private static final List<String> MIME_TYPES_TO_OPEN = new ArrayList<String>(Arrays.asList(
            OMADownloadHandler.OMA_DOWNLOAD_DESCRIPTOR_MIME,
            "application/pdf",
            "application/x-x509-ca-cert",
            "application/x-x509-user-cert",
            "application/x-x509-server-cert",
            "application/x-pkcs12",
            "application/application/x-pem-file",
            "application/pkix-cert",
            "application/x-wifi-config"));

    private static DownloadManagerService sDownloadManagerService;

    private final SharedPreferences mSharedPrefs;
    private final ConcurrentHashMap<Integer, DownloadProgress> mDownloadProgressMap =
            new ConcurrentHashMap<Integer, DownloadProgress>(4, 0.75f, 2);

    private final DownloadNotifier mDownloadNotifier;
    // Delay between UI updates.
    private final long mUpdateDelayInMillis;

    // Flag to track if we need to post a task to update download notifications.
    private final AtomicBoolean mIsUIUpdateScheduled;
    private final Handler mHandler;
    private final Context mContext;

    private final LongSparseArray<DownloadInfo> mPendingDownloads =
            new LongSparseArray<DownloadInfo>();
    private OMADownloadHandler mOMADownloadHandler;
    private DownloadSnackbarController mDownloadSnackbarController;

    /**
     * Enum representing status of a download.
     */
    private enum DownloadStatus {
        IN_PROGRESS,
        COMPLETE,
        FAILED
    }

    /**
     * Class representing progress of a download.
     */
    private static class DownloadProgress {
        final long mStartTimeInMillis;
        volatile DownloadInfo mDownloadInfo;
        volatile DownloadStatus mDownloadStatus;

        DownloadProgress(long startTimeInMillis, DownloadInfo downloadInfo,
                DownloadStatus downloadStatus) {
            mStartTimeInMillis = startTimeInMillis;
            mDownloadInfo = downloadInfo;
            mDownloadStatus = downloadStatus;
        }
    }

    /**
     * Class representing an OMA download entry to be stored in SharedPrefs.
     */
    @VisibleForTesting
    protected static class OMAEntry {
        final long mDownloadId;
        final String mInstallNotifyURI;

        OMAEntry(long downloadId, String installNotifyURI) {
            mDownloadId = downloadId;
            mInstallNotifyURI = installNotifyURI;
        }

        /**
         * Parse OMA entry from the SharedPrefs String
         * TODO(qinmin): use a file instead of SharedPrefs to store the OMA entry.
         *
         * @param entry String contains the OMA information.
         * @return an OMAEntry object.
         */
        @VisibleForTesting
        static OMAEntry parseOMAEntry(String entry) {
            int index = entry.indexOf(",");
            long downloadId = Long.parseLong(entry.substring(0, index));
            return new OMAEntry(downloadId, entry.substring(index + 1));
        }

        /**
         * Generates a string for an OMA entry to be inserted into the SharedPrefs.
         * TODO(qinmin): use a file instead of SharedPrefs to store the OMA entry.
         *
         * @return a String representing the download entry.
         */
        String generateSharedPrefsString() {
            return String.valueOf(mDownloadId) + "," + mInstallNotifyURI;
        }
    }

    /**
     * Creates DownloadManagerService.
     */
    @SuppressFBWarnings("LI_LAZY_INIT") // Findbugs doesn't see this is only UI thread.
    public static DownloadManagerService getDownloadManagerService(final Context context) {
        ThreadUtils.assertOnUiThread();
        assert context == context.getApplicationContext();
        if (sDownloadManagerService == null) {
            sDownloadManagerService = new DownloadManagerService(context,
                    new SystemDownloadNotifier(context),  new Handler(), UPDATE_DELAY_MILLIS);
        }
        return sDownloadManagerService;
    }

    /**
     * For tests only: sets the DownloadManagerService.
     * @param service An instance of DownloadManagerService.
     * @return Null or a currently set instance of DownloadManagerService.
     */
    @VisibleForTesting
    public static DownloadManagerService setDownloadManagerService(DownloadManagerService service) {
        ThreadUtils.assertOnUiThread();
        DownloadManagerService prev = sDownloadManagerService;
        sDownloadManagerService = service;
        return prev;
    }

    @VisibleForTesting
    protected DownloadManagerService(Context context,
            DownloadNotifier downloadNotifier,
            Handler handler,
            long updateDelayInMillis) {
        mContext = context;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context
                .getApplicationContext());
        mDownloadNotifier = downloadNotifier;
        mUpdateDelayInMillis = updateDelayInMillis;
        mHandler = handler;
        mIsUIUpdateScheduled = new AtomicBoolean(false);
        mOMADownloadHandler = new OMADownloadHandler(context);
        mDownloadSnackbarController = new DownloadSnackbarController(context);
    }

    @Override
    public void onDownloadCompleted(final DownloadInfo downloadInfo) {
        DownloadStatus status = DownloadStatus.COMPLETE;
        if (!downloadInfo.isSuccessful() || downloadInfo.getContentLength() == 0) {
            status = DownloadStatus.FAILED;
        }
        updateDownloadProgress(downloadInfo, status);
        scheduleUpdateIfNeeded();
    }

    @Override
    public void onDownloadUpdated(final DownloadInfo downloadInfo) {
        updateDownloadProgress(downloadInfo, DownloadStatus.IN_PROGRESS);
        scheduleUpdateIfNeeded();
    }

    /**
     * Clear any pending notifications for incomplete downloads by reading them from shared prefs.
     * When Clank is restarted it clears any old notifications for incomplete downloads.
     */
    public void clearPendingDownloadNotifications() {
        if (mSharedPrefs.contains(DOWNLOAD_NOTIFICATION_IDS)) {
            Set<String> downloadIds = getStoredDownloadInfo(DOWNLOAD_NOTIFICATION_IDS);
            for (String id : downloadIds) {
                int notificationId = parseNotificationId(id);
                if (notificationId > 0) {
                    mDownloadNotifier.cancelNotification(notificationId);
                    Log.w(TAG, "Download failed: Cleared download id:" + id);
                }
            }
            mSharedPrefs.edit().remove(DOWNLOAD_NOTIFICATION_IDS).apply();
        }
        if (mSharedPrefs.contains(PENDING_OMA_DOWNLOADS)) {
            Set<String> omaDownloads = getStoredDownloadInfo(PENDING_OMA_DOWNLOADS);
            for (String omaDownload : omaDownloads) {
                OMAEntry entry = OMAEntry.parseOMAEntry(omaDownload);
                clearPendingOMADownload(entry.mDownloadId, entry.mInstallNotifyURI);
            }
        }
    }

    /**
     * Async task to clear the pending OMA download from SharedPrefs and inform
     * the OMADownloadHandler about download status.
     */
    private class ClearPendingOMADownloadTask extends
            AsyncTask<Void, Void, Pair<Integer, Boolean>> {
        private DownloadInfo mDownloadInfo;
        private final long mDownloadId;
        private final String mInstallNotifyURI;
        private int mFailureReason;

        public ClearPendingOMADownloadTask(long downloadId, String installNotifyURI) {
            mDownloadId = downloadId;
            mInstallNotifyURI = installNotifyURI;
            mDownloadInfo = mPendingDownloads.get(downloadId);
        }

        @Override
        public Pair<Integer, Boolean> doInBackground(Void...voids) {
            DownloadManager manager =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor c = manager.query(new DownloadManager.Query().setFilterById(mDownloadId));
            int statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int reasonIndex = c.getColumnIndex(DownloadManager.COLUMN_REASON);
            int filenameIndex = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME);
            int status = DownloadManager.STATUS_FAILED;
            Boolean canResolve = false;
            if (c.moveToNext()) {
                status = c.getInt(statusIndex);
                String path = c.getString(filenameIndex);
                String fileName = TextUtils.isEmpty(path) ? null : new File(path).getName();
                if (mDownloadInfo == null) {
                    // Chrome has been killed, reconstruct a DownloadInfo.
                    mDownloadInfo = new DownloadInfo.Builder()
                            .setFileName(fileName)
                            .setDownloadId((int) mDownloadId)
                            .setDescription(c.getString(
                                    c.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)))
                            .setMimeType(c.getString(
                                    c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)))
                            .setContentLength(Long.parseLong(c.getString(
                                    c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))))
                            .build();
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    mDownloadInfo = DownloadInfo.Builder.fromDownloadInfo(mDownloadInfo)
                            .setFileName(fileName)
                            .build();
                    canResolve = canResolveDownloadItem(mContext, mDownloadId);
                } else if (status == DownloadManager.STATUS_FAILED) {
                    mFailureReason = c.getInt(reasonIndex);
                    manager.remove(mDownloadId);
                }
            }
            c.close();
            return Pair.create(status, canResolve);
        }

        @Override
        protected void onPostExecute(Pair<Integer, Boolean> result) {
            if (result.first == DownloadManager.STATUS_SUCCESSFUL) {
                mOMADownloadHandler.onDownloadCompleted(mDownloadInfo, mInstallNotifyURI);
                removeOMADownloadFromSharedPrefs(mDownloadId);
                mDownloadSnackbarController.onDownloadSucceeded(
                        mDownloadInfo, mDownloadId, result.second);
            } else if (result.first == DownloadManager.STATUS_FAILED) {
                mOMADownloadHandler.onDownloadFailed(
                        mDownloadInfo, mFailureReason, mInstallNotifyURI);
                removeOMADownloadFromSharedPrefs(mDownloadId);
                onDownloadFailed(mDownloadInfo.getFileName());
            }
        }
    }

    /**
     * Clear pending OMA downloads for a particular download ID.
     *
     * @param downloadId Download identifier.
     * @param info Information about the download.
     * @param installNotifyURI URI to notify after installation.
     */
    private void clearPendingOMADownload(long downloadId, String installNotifyURI) {
        ClearPendingOMADownloadTask task = new ClearPendingOMADownloadTask(
                downloadId, installNotifyURI);
        task.execute();
    }

    /**
     * Parse the notification ID from a String object.
     *
     * @param id String containing the notification ID.
     * @return notification ID.
     */
    private static int parseNotificationId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "Exception while parsing download id:" + id);
            return -1;
        }
    }

    /**
     * Broadcast that a download was successful.
     * @param downloadInfo info about the download.
     */
    protected void broadcastDownloadSuccessful(DownloadInfo downloadInfo) {}

    /**
     * Gets download information from SharedPrefs.
     * @param type Type of the information to retrieve.
     * @return download information saved to the SharedPrefs for the given type.
     */
    @VisibleForTesting
    protected Set<String> getStoredDownloadInfo(String type) {
        return new HashSet<String>(mSharedPrefs.getStringSet(
                type, new HashSet<String>()));
    }

    /**
     * Removes a donwload Id from SharedPrefs.
     * @param downloadId ID to be removed.
     */
    private void removeDownloadIdFromSharedPrefs(int downloadId) {
        Set<String> downloadIds = getStoredDownloadInfo(DOWNLOAD_NOTIFICATION_IDS);
        String id = Integer.toString(downloadId);
        if (downloadIds.contains(id)) {
            downloadIds.remove(id);
            storeDownloadInfo(DOWNLOAD_NOTIFICATION_IDS, downloadIds);
        }
    }

    /**
     * Add download ID to SharedPrefs.
     * @param downloadId ID to be stored.
     */
    private void addDownloadIdToSharedPrefs(int downloadId) {
        Set<String> downloadIds = getStoredDownloadInfo(DOWNLOAD_NOTIFICATION_IDS);
        downloadIds.add(Integer.toString(downloadId));
        storeDownloadInfo(DOWNLOAD_NOTIFICATION_IDS, downloadIds);
    }

    /**
     * Add OMA download info to SharedPrefs.
     * @param omaInfo OMA download information to save.
     */
    @VisibleForTesting
    protected void addOMADownloadToSharedPrefs(String omaInfo) {
        Set<String> omaDownloads = getStoredDownloadInfo(PENDING_OMA_DOWNLOADS);
        omaDownloads.add(omaInfo);
        storeDownloadInfo(PENDING_OMA_DOWNLOADS, omaDownloads);
    }

    /**
     * Remove OMA download info from SharedPrefs.
     * @param downloadId ID to be removed.
     */
    private void removeOMADownloadFromSharedPrefs(long downloadId) {
        Set<String> omaDownloads = getStoredDownloadInfo(PENDING_OMA_DOWNLOADS);
        for (String omaDownload : omaDownloads) {
            OMAEntry entry = OMAEntry.parseOMAEntry(omaDownload);
            if (entry.mDownloadId == downloadId) {
                omaDownloads.remove(omaDownload);
                storeDownloadInfo(PENDING_OMA_DOWNLOADS, omaDownloads);
                return;
            }
        }
    }

    /**
     * Check if a download ID is in OMA SharedPrefs.
     * @param downloadId Download identifier to check.
     * @param true if it is in the SharedPrefs, or false otherwise.
     */
    private boolean isDownloadIdInOMASharedPrefs(long downloadId) {
        Set<String> omaDownloads = getStoredDownloadInfo(PENDING_OMA_DOWNLOADS);
        for (String omaDownload : omaDownloads) {
            OMAEntry entry = OMAEntry.parseOMAEntry(omaDownload);
            if (entry.mDownloadId == downloadId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stores download information to shared preferences. The information can be
     * either pending download IDs, or pending OMA downloads.
     *
     * @param type Type of the information.
     * @param downloadInfo Information to be saved.
     */
    private void storeDownloadInfo(String type, Set<String> downloadInfo) {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        if (downloadInfo.isEmpty()) {
            editor.remove(type);
        } else {
            editor.putStringSet(type, downloadInfo);
        }
        editor.apply();
    }

    /**
     * Updates notifications for all current downloads. Should not be called from UI thread.
     *
     * @return A map that maps all download info to their corresponding download IDs in the
     *         download manager and whether the download can be resolved. If a download fails,
     *         its download ID is INVALID_DOWNLOAD_ID and the launching intent is null.
     */
    private Map<DownloadInfo, Pair<Long, Boolean>> updateAllNotifications() {
        assert !ThreadUtils.runningOnUiThread();
        Map<DownloadInfo, Pair<Long, Boolean>> completionMap =
                new HashMap<DownloadInfo, Pair<Long, Boolean>>();
        for (DownloadProgress progress : mDownloadProgressMap.values()) {
            if (progress != null) {
                switch (progress.mDownloadStatus) {
                    case COMPLETE:
                        removeProgressNotificationForDownload(progress.mDownloadInfo
                                .getDownloadId());
                        long downloadId = addCompletedDownload(progress.mDownloadInfo);
                        if (downloadId == INVALID_DOWNLOAD_ID) {
                            completionMap.put(
                                    progress.mDownloadInfo,
                                    Pair.create(INVALID_DOWNLOAD_ID, false));
                            mDownloadNotifier.notifyDownloadFailed(progress.mDownloadInfo);
                        } else {
                            boolean canResolve = canResolveDownloadItem(mContext, downloadId);
                            completionMap.put(
                                    progress.mDownloadInfo, Pair.create(downloadId, canResolve));
                            mDownloadNotifier.notifyDownloadSuccessful(
                                    progress.mDownloadInfo,
                                    getLaunchIntentFromDownloadId(mContext, downloadId));
                            broadcastDownloadSuccessful(progress.mDownloadInfo);
                        }
                        break;
                    case FAILED:
                        removeProgressNotificationForDownload(progress.mDownloadInfo
                                .getDownloadId());
                        mDownloadNotifier.notifyDownloadFailed(progress.mDownloadInfo);
                        completionMap.put(
                                progress.mDownloadInfo, Pair.create(INVALID_DOWNLOAD_ID, false));
                        Log.w(TAG, "Download failed: " + progress.mDownloadInfo.getFilePath());
                        break;
                    case IN_PROGRESS:
                        mDownloadNotifier.notifyDownloadProgress(progress.mDownloadInfo,
                                progress.mStartTimeInMillis);
                }
            }
        }
        return completionMap;
    }

    /**
     * Add a completed download into DownloadManager.
     *
     * @param downloadInfo Information of the downloaded file.
     * @return download ID if the download is added to the DownloadManager, or
     *         INVALID_DOWNLOAD_ID otherwise.
     */
    protected long addCompletedDownload(DownloadInfo downloadInfo) {
        String mimeType = downloadInfo.getMimeType();
        if (TextUtils.isEmpty(mimeType)) mimeType = UNKNOWN_MIME_TYPE;
        String description = downloadInfo.getDescription();
        if (TextUtils.isEmpty(description)) description = downloadInfo.getFileName();
        DownloadManager manager =
                (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = INVALID_DOWNLOAD_ID;
        try {
            downloadId = manager.addCompletedDownload(
                    downloadInfo.getFileName(), description, true, mimeType,
                    downloadInfo.getFilePath(), downloadInfo.getContentLength(), false);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Failed to add the download item to DownloadManager: ", e);
            if (downloadInfo.getFilePath() != null) {
                File file = new File(downloadInfo.getFilePath());
                if (!file.delete()) {
                    Log.w(TAG, "Failed to remove the unsucessful download");
                }
            }
            return INVALID_DOWNLOAD_ID;
        }
        return downloadId;
    }

    /**
     * Handle auto opennable files after download completes.
     *
     * @param info Information of the downloaded file.
     * @param downloadId Download identifier issued by the android DownloadManager.
     */
    private void handleAutoOpenAfterDownload(DownloadInfo info, long downloadId) {
        if (OMADownloadHandler.OMA_DOWNLOAD_DESCRIPTOR_MIME.equalsIgnoreCase(info.getMimeType())) {
            mOMADownloadHandler.handleOMADownload(info, downloadId);
            return;
        }
        openDownloadedContent(downloadId);
    }

    /**
     * Schedule an update if there is no update scheduled.
     */
    private void scheduleUpdateIfNeeded() {
        if (mIsUIUpdateScheduled.compareAndSet(false, true)) {
            Runnable updateTask = new Runnable() {
                @Override
                public void run() {
                    new AsyncTask<Void, Void, Map<DownloadInfo, Pair<Long, Boolean>>>() {
                        @Override
                        public Map<DownloadInfo, Pair<Long, Boolean>> doInBackground(
                                Void... params) {
                            return updateAllNotifications();
                        }

                        @Override
                        protected void onPostExecute(
                                Map<DownloadInfo, Pair<Long, Boolean>> result) {
                            for (Map.Entry<DownloadInfo, Pair<Long, Boolean>> entry :
                                    result.entrySet()) {
                                DownloadInfo info = entry.getKey();
                                long downloadId = entry.getValue().first;
                                if (downloadId == INVALID_DOWNLOAD_ID) {
                                    onDownloadFailed(info.getFileName());
                                } else {
                                    boolean canResolve = entry.getValue().second;
                                    if (canResolve && shouldOpenAfterDownload(info)) {
                                        handleAutoOpenAfterDownload(info, downloadId);
                                    } else {
                                        mDownloadSnackbarController.onDownloadSucceeded(
                                                info, downloadId, canResolve);
                                    }
                                }
                            }
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    mIsUIUpdateScheduled.set(false);
                }
            };
            mHandler.postDelayed(updateTask, mUpdateDelayInMillis);
        }
    }

    /**
     * Cancel the progress notification of download and clear any cached information about this
     * download.
     *
     * @param downloadId Download Identifier.
     */
    void removeProgressNotificationForDownload(int downloadId) {
        mDownloadProgressMap.remove(downloadId);
        mDownloadNotifier.cancelNotification(downloadId);
        removeDownloadIdFromSharedPrefs(downloadId);
    }

    /**
     * Updates the progress of a download.
     *
     * @param downloadInfo Information about the download.
     * @param status Status of the download.
     */
    private void updateDownloadProgress(DownloadInfo downloadInfo, DownloadStatus status) {
        assert downloadInfo.hasDownloadId();
        int downloadId = downloadInfo.getDownloadId();
        DownloadProgress progress = mDownloadProgressMap.get(downloadId);
        if (progress == null) {
            progress = new DownloadProgress(System.currentTimeMillis(), downloadInfo,
                    status);
            if (status == DownloadStatus.IN_PROGRESS) {
                // A new in-progress download, add an entry to shared prefs to make sure
                // to clear the notification.
                addDownloadIdToSharedPrefs(downloadId);
            }
            mDownloadProgressMap.putIfAbsent(downloadId, progress);
        } else {
            progress.mDownloadStatus = status;
            progress.mDownloadInfo = downloadInfo;
        }
    }

    /**
     * Sets the download handler for OMA downloads, for testing purpose.
     *
     * @param omaDownloadHandler Download handler for OMA contents.
     */
    @VisibleForTesting
    protected void setOMADownloadHandler(OMADownloadHandler omaDownloadHandler) {
        mOMADownloadHandler = omaDownloadHandler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) return;
        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,
                INVALID_DOWNLOAD_ID);
        if (downloadId == INVALID_DOWNLOAD_ID) return;
        boolean isPendingOMADownload = mOMADownloadHandler.isPendingOMADownload(downloadId);
        boolean isInOMASharedPrefs = isDownloadIdInOMASharedPrefs(downloadId);
        if (isPendingOMADownload || isInOMASharedPrefs) {
            clearPendingOMADownload(downloadId, null);
            mPendingDownloads.remove(downloadId);
            return;
        }
        DownloadInfo info = mPendingDownloads.get(downloadId);
        if (info != null) {
            DownloadCompletionTask task = new DownloadCompletionTask(info, downloadId);
            task.execute();
            mPendingDownloads.remove(downloadId);
            if (mPendingDownloads.size() == 0) {
                mContext.unregisterReceiver(this);
            }
        }
    }

    /**
     * Async task to handle completed downloads.
     */
    private class DownloadCompletionTask extends AsyncTask<Void, Void, Pair<Integer, Boolean>> {
        private final long mDownloadId;
        private final DownloadInfo mDownloadInfo;

        public DownloadCompletionTask(DownloadInfo info, long downloadId) {
            mDownloadInfo = info;
            mDownloadId = downloadId;
        }

        @Override
        public Pair<Integer, Boolean> doInBackground(Void...voids) {
            final DownloadManager manager =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor c = manager.query(new DownloadManager.Query().setFilterById(mDownloadId));
            int status = UNKNOWN_DOWNLOAD_STATUS;
            boolean canResolve = false;
            if (c.moveToNext()) {
                status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    canResolve = canResolveDownloadItem(mContext, mDownloadId);
                }
            }
            c.close();
            return Pair.create(status, canResolve);
        }

        @Override
        protected void onPostExecute(Pair<Integer, Boolean> result) {
            switch (result.first) {
                case DownloadManager.STATUS_SUCCESSFUL:
                    if (shouldOpenAfterDownload(mDownloadInfo) && result.second) {
                        handleAutoOpenAfterDownload(mDownloadInfo, mDownloadId);
                    } else {
                        mDownloadSnackbarController.onDownloadSucceeded(
                                mDownloadInfo, mDownloadId, result.second);
                    }
                    break;
                case DownloadManager.STATUS_FAILED:
                    // TODO(qinmin): retrieve the failure reason from DownloadManager.
                    onDownloadFailed(mDownloadInfo.getFileName());
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Sends the download request to Android download manager. If |notifyCompleted| is true,
     * a notification will be sent to the user once download is complete and the downloaded
     * content will be saved to the public directory on external storage. Otherwise, the
     * download will be saved in the app directory and user will not get any notifications
     * after download completion.
     *
     * @param info Download information about the download.
     * @param notifyCompleted Whether to notify the user after Downloadmanager completes the
     *                        download.
     */
    public void enqueueDownloadManagerRequest(final DownloadInfo info, boolean notifyCompleted) {
        EnqueueDownloadRequestTask task = new EnqueueDownloadRequestTask(info);
        task.execute(notifyCompleted);
    }

    /**
     * Async task to enqueue a download request into DownloadManager.
     */
    private class EnqueueDownloadRequestTask extends
            AsyncTask<Boolean, Void, Boolean> {
        private long mDownloadId;
        private DownloadInfo mDownloadInfo;

        public EnqueueDownloadRequestTask(DownloadInfo downloadInfo) {
            mDownloadInfo = downloadInfo;
        }

        @Override
        public Boolean doInBackground(Boolean... booleans) {
            boolean notifyCompleted = booleans[0];
            Uri uri = Uri.parse(mDownloadInfo.getUrl());
            DownloadManager.Request request;
            try {
                request = new DownloadManager.Request(uri);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Cannot download non http or https scheme");
                return false;
            }

            request.setMimeType(mDownloadInfo.getMimeType());
            try {
                if (notifyCompleted) {
                    // Set downloaded file destination to /sdcard/Download or, should it be
                    // set to one of several Environment.DIRECTORY* dirs depending on mimetype?
                    request.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS, mDownloadInfo.getFileName());
                } else {
                    File dir = new File(mContext.getExternalFilesDir(null), DOWNLOAD_DIRECTORY);
                    if (dir.mkdir() || dir.isDirectory()) {
                        File file = new File(dir, mDownloadInfo.getFileName());
                        request.setDestinationUri(Uri.fromFile(file));
                    } else {
                        Log.e(TAG, "Cannot create download directory");
                        return false;
                    }
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot create download directory");
                return false;
            }

            if (notifyCompleted) {
                // Let this downloaded file be scanned by MediaScanner - so that it can
                // show up in Gallery app, for example.
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            } else {
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE);
            }
            String description = mDownloadInfo.getDescription();
            if (TextUtils.isEmpty(description)) {
                description = mDownloadInfo.getFileName();
            }
            request.setDescription(description);
            request.addRequestHeader("Cookie", mDownloadInfo.getCookie());
            request.addRequestHeader("Referer", mDownloadInfo.getReferer());
            request.addRequestHeader("User-Agent", mDownloadInfo.getUserAgent());

            DownloadManager manager =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            try {
                mDownloadId = manager.enqueue(request);
            } catch (IllegalArgumentException e) {
                // See crbug.com/143499 for more details.
                Log.e(TAG, "Download failed: " + e);
                return false;
            } catch (RuntimeException e) {
                // See crbug.com/490442 for more details.
                Log.e(TAG, "Failed to create target file on the external storage: " + e);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            boolean isPendingOMADownload =
                    mOMADownloadHandler.isPendingOMADownload(mDownloadInfo.getDownloadId());
            if (!result) {
                onDownloadFailed(mDownloadInfo.getFileName());
                if (isPendingOMADownload) {
                    mOMADownloadHandler.onDownloadFailed(
                            mDownloadInfo, DownloadManager.ERROR_UNKNOWN, null);
                }
                return;
            }
            Toast.makeText(mContext, R.string.download_pending, Toast.LENGTH_SHORT).show();
            if (isPendingOMADownload) {
                // A new downloadId is generated, needs to update the OMADownloadHandler
                // about this.
                mDownloadInfo = mOMADownloadHandler.updateDownloadInfo(
                        mDownloadInfo, mDownloadId);
                // TODO(qinmin): use a file instead of shared prefs to save the
                // OMA information in case chrome is killed. This will allow us to
                // save more information like cookies and user agent.
                String notifyUri = mOMADownloadHandler.getInstallNotifyInfo(mDownloadId);
                if (!TextUtils.isEmpty(notifyUri)) {
                    OMAEntry entry = new OMAEntry(mDownloadId, notifyUri);
                    addOMADownloadToSharedPrefs(entry.generateSharedPrefsString());
                }
            }
            if (mPendingDownloads.size() == 0) {
                mContext.registerReceiver(DownloadManagerService.this,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
            mPendingDownloads.put(mDownloadId, mDownloadInfo);
        }
    }

    /**
     * Determines if the download should be immediately opened after
     * downloading.
     *
     * @param downloadInfo Information about the download.
     * @return true if the downloaded content should be opened, or false otherwise.
     */
    @VisibleForTesting
    static boolean shouldOpenAfterDownload(DownloadInfo downloadInfo) {
        String type = downloadInfo.getMimeType();
        return downloadInfo.hasUserGesture() && !isAttachment(downloadInfo.getContentDisposition())
                && MIME_TYPES_TO_OPEN.contains(type);
    }

    /**
     * Returns true if the download meant to be treated as an attachment.
     *
     * @param contentDisposition Content disposition of the download.
     * @return true if the downloaded is an attachment, or false otherwise.
     */
    public static boolean isAttachment(String contentDisposition) {
        return contentDisposition != null
                && contentDisposition.regionMatches(true, 0, "attachment", 0, 10);
    }

    /**
     * Launch the best activity for the given intent. If a default activity is provided,
     * choose the default one. Otherwise, return the Intent picker if there are more than one
     * capable activities. If the intent is pdf type, return the platform pdf viewer if
     * it is available so user don't need to choose it from Intent picker.
     *
     * @param context Context of the app.
     * @param intent Intent to open.
     * @param allowSelfOpen Whether chrome itself is allowed to open the intent.
     * @return true if an Intent is launched, or false otherwise.
     */
    public static boolean openIntent(Context context, Intent intent, boolean allowSelfOpen) {
        boolean activityResolved = ExternalNavigationDelegateImpl.resolveIntent(
                context, intent, allowSelfOpen);
        if (activityResolved) {
            try {
                context.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException ex) {
                Log.d(TAG, "activity not found for " + intent.getType()
                        + " over " + intent.getData().getScheme(), ex);
            }
        }
        return false;
    }

    /**
     * Return the intent to launch for a given download item.
     *
     * @param context Context of the app.
     * @param downloadId ID of the download item in DownloadManager.
     * @return the intent to launch for the given download item.
     */
    private static Intent getLaunchIntentFromDownloadId(Context context, long downloadId) {
        assert !ThreadUtils.runningOnUiThread();
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = manager.getUriForDownloadedFile(downloadId);
        if (uri == null) return null;
        Intent launchIntent = new Intent(Intent.ACTION_VIEW);
        launchIntent.setDataAndType(uri, manager.getMimeTypeForDownloadedFile(downloadId));
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri referrer = new Uri.Builder().scheme(
                IntentHandler.ANDROID_APP_REFERRER_SCHEME).authority(
                        context.getPackageName()).build();
        launchIntent.putExtra(Intent.EXTRA_REFERRER, referrer);
        return launchIntent;
    }

    /**
     * Return whether a download item can be resolved to any activity.
     *
     * @param context Context of the app.
     * @param downloadId ID of the download item in DownloadManager.
     * @return true if the download item can be resolved, or false otherwise.
     */
    private static boolean canResolveDownloadItem(Context context, long downloadId) {
        assert !ThreadUtils.runningOnUiThread();
        Intent intent = getLaunchIntentFromDownloadId(context, downloadId);
        return (intent == null) ? false : ExternalNavigationDelegateImpl.resolveIntent(
                context, intent, true);
    }

    /**
     * Launch the intent for a given download item.
     *
     * @param downloadId ID of the download item in DownloadManager.
     */
    protected void openDownloadedContent(final long downloadId) {
        new AsyncTask<Void, Void, Intent>() {
            @Override
            public Intent doInBackground(Void... params) {
                return getLaunchIntentFromDownloadId(mContext, downloadId);
            }

            @Override
            protected void onPostExecute(Intent intent) {
                if (intent != null) {
                    openIntent(mContext, intent, true);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Called when a download fails.
     *
     * @param fileName Name of the download file.
     */
    protected void onDownloadFailed(String fileName) {
        mDownloadSnackbarController.onDownloadFailed(fileName);
    }

    /**
     * Set the DownloadSnackbarController for testing purpose.
     */
    @VisibleForTesting
    protected void setDownloadSnackbarController(
            DownloadSnackbarController downloadSnackbarController) {
        mDownloadSnackbarController = downloadSnackbarController;
    }
}
