// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.Manifest.permission;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;

import java.io.File;

/**
 * Chrome implementation of the ContentViewDownloadDelegate interface.
 *
 * Listens to POST and GET download events. GET download requests are passed along to the
 * Android Download Manager. POST downloads are expected to be handled natively and listener
 * is responsible for adding the completed download to the download manager.
 *
 * Prompts the user when a dangerous file is downloaded. Auto-opens PDFs after downloading.
 */
public class ChromeDownloadDelegate {
    private static final String TAG = "Download";

    // The application context.
    private final Context mContext;
    private Tab mTab;

    /**
     * Creates ChromeDownloadDelegate.
     * @param context The application context.
     * @param tab The corresponding tab instance.
     */
    public ChromeDownloadDelegate(Context context, Tab tab) {
        mContext = context;
        mTab = tab;
        mTab.addObserver(new EmptyTabObserver() {
            @Override
            public void onDestroyed(Tab tab) {
                mTab = null;
            }
        });

        nativeInit(tab.getWebContents());
    }

    /**
     * Notify the host application a download should be done, even if there is a
     * streaming viewer available for this type.
     *
     * @param downloadInfo Information about the download.
     */
    protected void onDownloadStartNoStream(final DownloadInfo downloadInfo) {
        final String fileName = downloadInfo.getFileName();
        assert !TextUtils.isEmpty(fileName);
        final String newMimeType =
                remapGenericMimeType(downloadInfo.getMimeType(), downloadInfo.getUrl(), fileName);
        new AsyncTask<Void, Void, Object[]>() {
            @Override
            protected Object[] doInBackground(Void... params) {
                // Check to see if we have an SDCard.
                String status = Environment.getExternalStorageState();
                Pair<String, String> result = getDownloadDirectoryNameAndFullPath();
                String dirName = result.first;
                String fullDirPath = result.second;
                boolean fileExists = doesFileAlreadyExists(fullDirPath, fileName);

                return new Object[] {status, dirName, fullDirPath, fileExists};
            }

            @Override
            protected void onPostExecute(Object[] result) {
                String externalStorageState = (String) result[0];
                String dirName = (String) result[1];
                String fullDirPath = (String) result[2];
                Boolean fileExists = (Boolean) result[3];
                if (!checkExternalStorageAndNotify(
                        fileName, fullDirPath, externalStorageState)) {
                    return;
                }
                String url = sanitizeDownloadUrl(downloadInfo);
                if (url == null) return;
                DownloadInfo newInfo = DownloadInfo.Builder.fromDownloadInfo(downloadInfo)
                                               .setUrl(url)
                                               .setMimeType(newMimeType)
                                               .setDescription(url)
                                               .setFileName(fileName)
                                               .setIsGETRequest(true)
                                               .build();

                // TODO(acleung): This is a temp fix to disable auto downloading if flash files.
                // We want to avoid downloading flash files when it is linked as an iframe.
                // The proper fix would be to let chrome knows which frame originated the request.
                if ("application/x-shockwave-flash".equals(newInfo.getMimeType())) return;

                // Not a dangerous file, proceed.
                if (fileExists) {
                    launchDownloadInfoBar(newInfo, dirName, fullDirPath);
                } else {
                    enqueueDownloadManagerRequest(newInfo);
                }
            }
        }.execute();
    }

    /**
     * Sanitize the URL for the download item.
     *
     * @param downloadInfo Information about the download.
     */
    protected String sanitizeDownloadUrl(DownloadInfo downloadInfo) {
        return downloadInfo.getUrl();
    }

    @CalledByNative
    private void requestFileAccess(final long callbackId) {
        if (mTab == null || mTab.getWindowAndroid() == null) {
            // TODO(tedchoc): Show toast (only when activity is alive).
            DownloadController.getInstance().onRequestFileAccessResult(callbackId, false);
            return;
        }
        final String storagePermission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
        final Activity activity = mTab.getWindowAndroid().getActivity().get();

        if (activity == null) {
            DownloadController.getInstance().onRequestFileAccessResult(callbackId, false);
        } else if (mTab.getWindowAndroid().canRequestPermission(storagePermission)) {
            View view = activity.getLayoutInflater().inflate(
                    R.layout.update_permissions_dialog, null);
            TextView dialogText = (TextView) view.findViewById(R.id.text);
            dialogText.setText(R.string.missing_storage_permission_download_education_text);

            final PermissionCallback permissionCallback = new PermissionCallback() {
                @Override
                public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
                    DownloadController.getInstance().onRequestFileAccessResult(callbackId,
                            grantResults.length > 0
                                    && grantResults[0] == PackageManager.PERMISSION_GRANTED);
                }
            };

            AlertDialog.Builder builder =
                    new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
                    .setView(view)
                    .setPositiveButton(R.string.infobar_update_permissions_button_text,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    if (mTab == null) {
                                        dialog.cancel();
                                        return;
                                    }
                                    mTab.getWindowAndroid().requestPermissions(
                                            new String[] {storagePermission}, permissionCallback);
                                }
                            })
                     .setOnCancelListener(new DialogInterface.OnCancelListener() {
                             @Override
                             public void onCancel(DialogInterface dialog) {
                                 DownloadController.getInstance().onRequestFileAccessResult(
                                         callbackId, false);
                             }
                     });
            builder.create().show();
        } else if (!mTab.getWindowAndroid().isPermissionRevokedByPolicy(storagePermission)) {
            nativeLaunchPermissionUpdateInfoBar(mTab, storagePermission, callbackId);
        } else {
            // TODO(tedchoc): Show toast.
            DownloadController.getInstance().onRequestFileAccessResult(callbackId, false);
        }
    }

    /**
     * Return a pair of directory name and its full path. Note that we create the directory if
     * it does not already exist.
     *
     * @return A pair of directory name and its full path. A pair of <null, null> will be returned
     * in case of an error.
     */
    private static Pair<String, String> getDownloadDirectoryNameAndFullPath() {
        assert !ThreadUtils.runningOnUiThread();
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.mkdir() && !dir.isDirectory()) return new Pair<>(null, null);
        String dirName = dir.getName();
        String fullDirPath = dir.getPath();
        return new Pair<>(dirName, fullDirPath);
    }

    private static boolean doesFileAlreadyExists(String dirPath, final String fileName) {
        assert !ThreadUtils.runningOnUiThread();
        final File file = new File(dirPath, fileName);
        return file != null && file.exists();
    }

    private static void deleteFileForOverwrite(DownloadInfo info) {
        assert !ThreadUtils.runningOnUiThread();
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.isDirectory()) return;
        final File file = new File(dir, info.getFileName());
        if (!file.delete()) {
            Log.e(TAG, "Failed to delete a file: " + info.getFileName());
        }
    }

    /**
     * Enqueue download manager request, only from native side. Note that at this point
     * we don't need to show an infobar even when the file already exists.
     *
     * @param overwrite Whether or not we will overwrite the file.
     * @param downloadInfo The download info.
     * @return true iff this request resulted in the tab creating the download to close.
     */
    @CalledByNative
    private boolean enqueueDownloadManagerRequestFromNative(
            final boolean overwrite, final DownloadInfo downloadInfo) {
        if (overwrite) {
            // Android DownloadManager does not have an overwriting option.
            // We remove the file here instead.
            new AsyncTask<Void, Void, Void>() {
                @Override
                public Void doInBackground(Void... params) {
                    deleteFileForOverwrite(downloadInfo);
                    return null;
                }

                @Override
                public void onPostExecute(Void args) {
                    enqueueDownloadManagerRequest(downloadInfo);
                }
            }.execute();
        } else {
            enqueueDownloadManagerRequest(downloadInfo);
        }
        return closeBlankTab();
    }

    private void launchDownloadInfoBar(DownloadInfo info, String dirName, String fullDirPath) {
        if (mTab == null) return;
        nativeLaunchDownloadOverwriteInfoBar(
                ChromeDownloadDelegate.this, mTab, info, info.getFileName(), dirName, fullDirPath);
    }

    /**
     * Enqueue a request to download a file using Android DownloadManager.
     *
     * @param info Download information about the download.
     */
    private void enqueueDownloadManagerRequest(final DownloadInfo info) {
        DownloadManagerService.getDownloadManagerService(
                mContext.getApplicationContext()).enqueueDownloadManagerRequest(
                        new DownloadItem(true, info), true);
        closeBlankTab();
    }

    /**
     * Check the external storage and notify user on error.
     *
     * @param fullDirPath The dir path to download a file. Normally this is external storage.
     * @param externalStorageStatus The status of the external storage.
     * @return Whether external storage is ok for downloading.
     */
    private boolean checkExternalStorageAndNotify(
            String filename, String fullDirPath, String externalStorageStatus) {
        if (fullDirPath == null) {
            Log.e(TAG, "Download failed: no SD card");
            alertDownloadFailure(
                    filename, DownloadManager.ERROR_DEVICE_NOT_FOUND);
            return false;
        }
        if (!externalStorageStatus.equals(Environment.MEDIA_MOUNTED)) {
            int reason = DownloadManager.ERROR_DEVICE_NOT_FOUND;
            // Check to see if the SDCard is busy, same as the music app
            if (externalStorageStatus.equals(Environment.MEDIA_SHARED)) {
                Log.e(TAG, "Download failed: SD card unavailable");
                reason = DownloadManager.ERROR_FILE_ERROR;
            } else {
                Log.e(TAG, "Download failed: no SD card");
            }
            alertDownloadFailure(filename, reason);
            return false;
        }
        return true;
    }

    /**
     * Alerts user of download failure.
     *
     * @param fileName Name of the download file.
     * @param reason Reason of failure defined in {@link DownloadManager}
     */
    private void alertDownloadFailure(String fileName, int reason) {
        DownloadManagerService.getDownloadManagerService(
                mContext.getApplicationContext()).onDownloadFailed(fileName, reason);
    }

    /**
     * Called when download starts.
     *
     * @param filename Name of the file.
     * @param mimeType MIME type of the content.
     */
    @CalledByNative
    private void onDownloadStarted(String filename) {
        if (!isDangerousFile(filename)) {
            DownloadUtils.showDownloadStartToast(mContext);
            closeBlankTab();
        }
    }

    /**
     * If the given MIME type is null, or one of the "generic" types (text/plain
     * or application/octet-stream) map it to a type that Android can deal with.
     * If the given type is not generic, return it unchanged.
     *
     * We have to implement this ourselves as
     * MimeTypeMap.remapGenericMimeType() is not public.
     * See http://crbug.com/407829.
     *
     * @param mimeType MIME type provided by the server.
     * @param url URL of the data being loaded.
     * @param filename file name obtained from content disposition header
     * @return The MIME type that should be used for this data.
     */
    static String remapGenericMimeType(String mimeType, String url, String filename) {
        // If we have one of "generic" MIME types, try to deduce
        // the right MIME type from the file extension (if any):
        if (mimeType == null || mimeType.isEmpty() || "text/plain".equals(mimeType)
                || "application/octet-stream".equals(mimeType)
                || "binary/octet-stream".equals(mimeType)
                || "octet/stream".equals(mimeType)
                || "application/force-download".equals(mimeType)
                || "application/unknown".equals(mimeType)) {

            String extension = getFileExtension(url, filename);
            String newMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (newMimeType != null) {
                mimeType = newMimeType;
            } else if (extension.equals("dm")) {
                mimeType = OMADownloadHandler.OMA_DRM_MESSAGE_MIME;
            } else if (extension.equals("dd")) {
                mimeType = OMADownloadHandler.OMA_DOWNLOAD_DESCRIPTOR_MIME;
            }
        }
        return mimeType;
    }

    /**
     * Retrieve the file extension from a given file name or url.
     *
     * @param url URL to extract the extension.
     * @param filename File name to extract the extension.
     * @return If extension can be extracted from file name, use that. Or otherwise, use the
     *         extension extracted from the url.
     */
    static String getFileExtension(String url, String filename) {
        if (!TextUtils.isEmpty(filename)) {
            int index = filename.lastIndexOf(".");
            if (index > 0) return filename.substring(index + 1);
        }
        return MimeTypeMap.getFileExtensionFromUrl(url);
    }

    /**
     * Check whether a file is dangerous.
     *
     * @param filename Name of the file.
     * @return true if the file is dangerous, or false otherwise.
     */
    protected boolean isDangerousFile(String filename) {
        return nativeIsDownloadDangerous(filename);
    }

    /**
     * Discards a downloaded file.
     *
     * @param filepath File to be discarded.
     */
    private static void discardFile(final String filepath) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... params) {
                Log.d(TAG, "Discarding download: " + filepath);
                File file = new File(filepath);
                if (file.exists() && !file.delete()) {
                    Log.e(TAG, "Error discarding file: " + filepath);
                }
                return null;
            }
        }.execute();
    }

    /**
     * Close a blank tab just opened for the download purpose.
     * @return true iff the tab was (already) closed.
     */
    private boolean closeBlankTab() {
        if (mTab == null) {
            // We do not want caller to dismiss infobar.
            return true;
        }
        WebContents contents = mTab.getWebContents();
        boolean isInitialNavigation = contents == null
                || contents.getNavigationController().isInitialNavigation();
        if (isInitialNavigation) {
            // Tab is created just for download, close it.
            Activity activity = mTab.getWindowAndroid().getActivity().get();
            if (!(activity instanceof ChromeActivity)) return true;

            TabModelSelector selector = ((ChromeActivity) activity).getTabModelSelector();
            return selector == null ? true : selector.closeTab(mTab);
        }
        return false;
    }

    /**
     * For certain download types(OMA for example), android DownloadManager should
     * handle them. Call this function to intercept those downloads.
     *
     * @param url URL to be downloaded.
     * @return whether the DownloadManager should intercept the download.
     */
    public boolean shouldInterceptContextMenuDownload(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.normalizeScheme().getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) return false;
        String path = uri.getPath();
        // OMA downloads have extension "dm" or "dd". For the latter, it
        // can be handled when native download completes.
        if (path == null || !path.endsWith(".dm")) return false;
        if (mTab == null) return true;
        String fileName = URLUtil.guessFileName(
                url, null, OMADownloadHandler.OMA_DRM_MESSAGE_MIME);
        final DownloadInfo downloadInfo =
                new DownloadInfo.Builder().setUrl(url).setFileName(fileName).build();
        WindowAndroid window = mTab.getWindowAndroid();
        if (window.hasPermission(permission.WRITE_EXTERNAL_STORAGE)) {
            onDownloadStartNoStream(downloadInfo);
        } else if (window.canRequestPermission(permission.WRITE_EXTERNAL_STORAGE)) {
            PermissionCallback permissionCallback = new PermissionCallback() {
                @Override
                public void onRequestPermissionsResult(
                        String[] permissions, int[] grantResults) {
                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        onDownloadStartNoStream(downloadInfo);
                    }
                }
            };
            window.requestPermissions(
                    new String[] {permission.WRITE_EXTERNAL_STORAGE}, permissionCallback);
        }
        return true;
    }

    protected Context getContext() {
        return mContext;
    }

    private native void nativeInit(WebContents webContents);
    private static native String nativeGetDownloadWarningText(String filename);
    private static native boolean nativeIsDownloadDangerous(String filename);
    private static native void nativeLaunchDownloadOverwriteInfoBar(ChromeDownloadDelegate delegate,
            Tab tab, DownloadInfo downloadInfo, String fileName, String dirName,
            String dirFullPath);
    private static native void nativeLaunchPermissionUpdateInfoBar(
            Tab tab, String permission, long callbackId);
}
