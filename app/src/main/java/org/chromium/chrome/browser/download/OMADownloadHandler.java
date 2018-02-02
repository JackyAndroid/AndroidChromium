// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.Browser;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class handles OMA downloads according to the steps described in
 * http://xml.coverpages.org/OMA-Download-OTA-V10-20020620.pdf:
 * 1. Receives a download descriptor xml file.
 * 2. Parses all the contents.
 * 3. Checks device capability to see if it is able to handle the content.
 * 4. Find the objectURI value from the download descriptor and prompt user with
 *    a dialog to proceed with the download.
 * 5. On positive confirmation, sends a request to the download manager.
 * 6. Once the download is completed, sends a message to the server if installNotifyURI
 *    is present in the download descriptor.
 * 7. Prompts user with a dialog to open the NextURL specified in the download descriptor.
 * If steps 2 - 6 fails, a warning dialog will be prompted to the user to let them
 * know the error. Steps 6-7 will be executed afterwards.
 * If installNotifyURI is present in the download descriptor, the downloaded content will
 * be saved to the app directory first. If step 6 completes successfully, the content will
 * be moved to the public external storage. Otherwise, it will be removed from the device.
 */
public class OMADownloadHandler {
    private static final String TAG = "OMADownloadHandler";

    // MIME types for OMA downloads.
    public static final String OMA_DOWNLOAD_DESCRIPTOR_MIME = "application/vnd.oma.dd+xml";
    public static final String OMA_DRM_MESSAGE_MIME = "application/vnd.oma.drm.message";
    public static final String OMA_DRM_CONTENT_MIME = "application/vnd.oma.drm.content";
    public static final String OMA_DRM_RIGHTS_MIME = "application/vnd.oma.drm.rights+wbxml";

    // Valid download descriptor attributes.
    protected static final String OMA_TYPE = "type";
    protected static final String OMA_SIZE = "size";
    protected static final String OMA_OBJECT_URI = "objectURI";
    protected static final String OMA_INSTALL_NOTIFY_URI = "installNotifyURI";
    protected static final String OMA_NEXT_URL = "nextURL";
    protected static final String OMA_DD_VERSION = "DDVersion";
    protected static final String OMA_NAME = "name";
    protected static final String OMA_DESCRIPTION = "description";
    protected static final String OMA_VENDOR = "vendor";
    protected static final String OMA_INFO_URL = "infoURL";
    protected static final String OMA_ICON_URI = "iconURI";
    protected static final String OMA_INSTALL_PARAM = "installParam";

    // Error message to send to the notification server.
    private static final String DOWNLOAD_STATUS_SUCCESS = "900 Success \n\r";
    private static final String DOWNLOAD_STATUS_INSUFFICIENT_MEMORY =
            "901 insufficient memory \n\r";
    private static final String DOWNLOAD_STATUS_USER_CANCELLED = "902 User Cancelled \n\r";
    private static final String DOWNLOAD_STATUS_LOSS_OF_SERVICE = "903 Loss of Service \n\r";
    private static final String DOWNLOAD_STATUS_ATTRIBUTE_MISMATCH = "905 Attribute mismatch \n\r";
    private static final String DOWNLOAD_STATUS_INVALID_DESCRIPTOR = "906 Invalid descriptor \n\r";
    private static final String DOWNLOAD_STATUS_INVALID_DDVERSION = "951 Invalid DDVersion \n\r";
    private static final String DOWNLOAD_STATUS_DEVICE_ABORTED = "952 Device Aborted \n\r";
    private static final String DOWNLOAD_STATUS_NON_ACCEPTABLE_CONTENT =
            "953 Non-Acceptable Content \n\r";
    private static final String DOWNLOAD_STATUS_LOADER_ERROR = "954 Loader Error \n\r";

    private final Context mContext;
    private final LongSparseArray<OMAInfo> mPendingOMADownloads =
            new LongSparseArray<OMAInfo>();

    /**
     * Information about the OMA content. The object is parsed from the download
     * descriptor. There can be multiple MIME types for the object.
     */
    @VisibleForTesting
    protected static class OMAInfo {
        private final Map<String, String> mDescription;
        private final List<String> mTypes;

        OMAInfo() {
            mDescription = new HashMap<String, String>();
            mTypes = new ArrayList<String>();
        }

        /**
         * Inserts an attribute-value pair about the OMA content. If the attribute already
         * exists, the new value will replace the old one. For MIME type, it will be appended
         * to the existing MIME types.
         *
         * @param attribute The attribute to be inserted.
         * @param value The new value of the attribute.
         */
        void addAttributeValue(String attribute, String value) {
            if (attribute.equals(OMA_TYPE)) {
                mTypes.add(value);
            } else {
                // TODO(qinmin): Handle duplicate attributes
                mDescription.put(attribute, value);
            }
        }

        /**
         * Gets the value for an attribute.
         *
         * @param attribute The attribute to be retrieved.
         * @return value of the attribute.
         */
        String getValue(String attribute) {
            return mDescription.get(attribute);
        }

        /**
         * Checks whether the value is empty for an attribute.
         *
         * @param attribute The attribute to be retrieved.
         * @return true if it is empty, or false otherwise.
         */
        boolean isValueEmpty(String attribute) {
            return TextUtils.isEmpty(getValue(attribute));
        }

        /**
         * Gets the list of MIME types of the OMA content.
         *
         * @return List of MIME types.
         */
        List<String> getTypes() {
            return mTypes;
        }

        /**
         * Checks whether the information about the OMA content is empty.
         *
         * @return true if all attributes are empty, or false otherwise.
         */
        boolean isEmpty() {
            return mDescription.isEmpty() && mTypes.isEmpty();
        }

        /**
         * Gets the DRM MIME type of this object.
         *
         * @return the DRM MIME type if it is found, or null otherwise.
         */
        String getDrmType() {
            for (String type : mTypes) {
                if (type.equalsIgnoreCase(OMA_DRM_MESSAGE_MIME)
                        || type.equalsIgnoreCase(OMA_DRM_CONTENT_MIME)) {
                    return type;
                }
            }
            return null;
        }
    }

    public OMADownloadHandler(Context context) {
        mContext = context;
    }

    /**
     * Starts handling the OMA download.
     *
     * @param downloadInfo The information about the download.
     * @param downloadId The unique identifier maintained by the Android DownloadManager.
     */
    public void handleOMADownload(DownloadInfo downloadInfo, long downloadId) {
        OMAParserTask task = new OMAParserTask(downloadInfo, downloadId);
        task.execute();
    }

    /**
     * Async task to parse an OMA download descriptor.
     */
    private class OMAParserTask extends AsyncTask<Void, Void, OMAInfo> {
        private final DownloadInfo mDownloadInfo;
        private final long mDownloadId;
        public OMAParserTask(DownloadInfo downloadInfo, long downloadId) {
            mDownloadInfo = downloadInfo;
            mDownloadId = downloadId;
        }

        @Override
        public OMAInfo doInBackground(Void...voids) {
            OMAInfo omaInfo = null;
            final DownloadManager manager =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            try {
                ParcelFileDescriptor fd = manager.openDownloadedFile(mDownloadId);
                if (fd != null) {
                    omaInfo = parseDownloadDescriptor(new FileInputStream(fd.getFileDescriptor()));
                    fd.close();
                }
            } catch (FileNotFoundException e) {
                Log.w(TAG, "File not found.", e);
            } catch (IOException e) {
                Log.w(TAG, "Cannot read file.", e);
            }
            manager.remove(mDownloadId);
            return omaInfo;
        }

        @Override
        protected void onPostExecute(OMAInfo omaInfo) {
            if (omaInfo == null) return;
            // Send notification if required attributes are missing.
            if (omaInfo.getTypes().isEmpty() || getSize(omaInfo) <= 0
                    || omaInfo.isValueEmpty(OMA_OBJECT_URI)) {
                sendNotification(omaInfo, mDownloadInfo, DownloadItem.INVALID_DOWNLOAD_ID,
                        DOWNLOAD_STATUS_INVALID_DESCRIPTOR);
                return;
            }
            // Check version. Null version are treated as 1.0.
            String version = omaInfo.getValue(OMA_DD_VERSION);
            if (version != null && !version.startsWith("1.")) {
                sendNotification(omaInfo, mDownloadInfo, DownloadItem.INVALID_DOWNLOAD_ID,
                        DOWNLOAD_STATUS_INVALID_DDVERSION);
                return;
            }
            // Check device capabilities.
            if (Environment.getExternalStorageDirectory().getUsableSpace() < getSize(omaInfo)) {
                showDownloadWarningDialog(
                        R.string.oma_download_insufficient_memory,
                        omaInfo, mDownloadInfo, DOWNLOAD_STATUS_INSUFFICIENT_MEMORY);
                return;
            }
            if (getOpennableType(mContext.getPackageManager(), omaInfo) == null) {
                showDownloadWarningDialog(
                        R.string.oma_download_non_acceptable_content,
                        omaInfo, mDownloadInfo, DOWNLOAD_STATUS_NON_ACCEPTABLE_CONTENT);
                return;
            }
            showOMAInfoDialog(mDownloadId, mDownloadInfo, omaInfo);
        }
    }

    /**
     * Called when the content is successfully downloaded by the Android DownloadManager.
     *
     * @param downloadInfo The information about the download.
     * @param downloadId Download Id from the Android DownloadManager.
     * @param notifyURI The previously saved installNotifyURI attribute.
     */
    public void onDownloadCompleted(DownloadInfo downloadInfo, long downloadId, String notifyURI) {
        OMAInfo omaInfo = mPendingOMADownloads.get(downloadId);
        if (omaInfo == null) {
            omaInfo = new OMAInfo();
            omaInfo.addAttributeValue(OMA_INSTALL_NOTIFY_URI, notifyURI);
        }
        sendInstallNotificationAndNextStep(
                omaInfo, downloadInfo, downloadId, DOWNLOAD_STATUS_SUCCESS);
        mPendingOMADownloads.remove(downloadId);
    }

    /**
     * Called when android DownloadManager fails to download the content.
     *
     * @param downloadInfo The information about the download.
     * @param downloadId Download Id from the Android DownloadManager.
     * @param reason The reason of failure.
     * @param notifyURI The previously saved installNotifyURI attribute.
     */
    public void onDownloadFailed(
            DownloadInfo downloadInfo, long downloadId, int reason, String notifyURI) {
        String status = DOWNLOAD_STATUS_DEVICE_ABORTED;
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                status = DOWNLOAD_STATUS_LOSS_OF_SERVICE;
                break;
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                status = DOWNLOAD_STATUS_LOADER_ERROR;
                break;
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                status = DOWNLOAD_STATUS_INSUFFICIENT_MEMORY;
                break;
            default:
                break;
        }
        OMAInfo omaInfo = mPendingOMADownloads.get(downloadId);
        if (omaInfo == null) {
            // Just send the notification in this case.
            omaInfo = new OMAInfo();
            omaInfo.addAttributeValue(OMA_INSTALL_NOTIFY_URI, notifyURI);
            sendInstallNotificationAndNextStep(omaInfo, downloadInfo, downloadId, status);
            return;
        }
        showDownloadWarningDialog(
                R.string.oma_download_failed, omaInfo, downloadInfo, status);
        mPendingOMADownloads.remove(downloadId);
    }

    /**
     * Sends the install notification and then opens the nextURL if they are provided.
     * If the install notification is sent, nextURL will be opened after the server
     * response is received.
     *
     * @param omaInfo Information about the OMA content.
     * @param downloadInfo Information about the download.
     * @param downloadId Id of the download in Android DownloadManager.
     * @param statusMessage The message to send to the notification server.
     */
    private void sendInstallNotificationAndNextStep(
            OMAInfo omaInfo, DownloadInfo downloadInfo, long downloadId, String statusMessage) {
        if (!sendNotification(omaInfo, downloadInfo, downloadId, statusMessage)) {
            showNextUrlDialog(omaInfo);
        }
    }

    /**
     * Sends the install notification to the server.
     *
     * @param omaInfo Information about the OMA content.
     * @param downloadInfo Information about the download.
     * @param downloadId Id of the download in Android DownloadManager.
     * @param statusMessage The message to send to the notification server.
     * @return true if the notification ise sent, or false otherwise.
     */
    private boolean sendNotification(
            OMAInfo omaInfo, DownloadInfo downloadInfo, long downloadId, String statusMessage) {
        if (omaInfo == null) return false;
        if (omaInfo.isValueEmpty(OMA_INSTALL_NOTIFY_URI)) return false;
        PostStatusTask task = new PostStatusTask(omaInfo, downloadInfo, downloadId, statusMessage);
        task.execute();
        return true;
    }

    /**
     * Shows the OMA information to the user and ask whether user want to proceed.
     *
     * @param downloadId The unique identifier maintained by the Android DownloadManager.
     * @param downloadInfo Information about the download.
     * @param omaInfo Information about the OMA content.
     */
    private void showOMAInfoDialog(
            final long downloadId, final DownloadInfo downloadInfo, final OMAInfo omaInfo) {
        LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.confirm_oma_download, null);

        TextView textView = (TextView) v.findViewById(R.id.oma_download_name);
        textView.setText(omaInfo.getValue(OMA_NAME));
        textView = (TextView) v.findViewById(R.id.oma_download_vendor);
        textView.setText(omaInfo.getValue(OMA_VENDOR));
        textView = (TextView) v.findViewById(R.id.oma_download_size);
        textView.setText(omaInfo.getValue(OMA_SIZE));
        textView = (TextView) v.findViewById(R.id.oma_download_type);
        textView.setText(getOpennableType(mContext.getPackageManager(), omaInfo));
        textView = (TextView) v.findViewById(R.id.oma_download_description);
        textView.setText(omaInfo.getValue(OMA_DESCRIPTION));

        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    downloadOMAContent(downloadId, downloadInfo, omaInfo);
                } else {
                    sendNotification(omaInfo, downloadInfo,
                            DownloadItem.INVALID_DOWNLOAD_ID,
                            DOWNLOAD_STATUS_USER_CANCELLED);
                }
            }
        };
        new AlertDialog.Builder(
                ApplicationStatus.getLastTrackedFocusedActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.proceed_oma_download_message)
                .setPositiveButton(R.string.ok, clickListener)
                .setNegativeButton(R.string.cancel, clickListener)
                .setView(v)
                .setCancelable(false)
                .show();
    }

    /**
     * Shows a warning dialog indicating that download has failed. When user confirms
     * the warning, a message will be sent to the notification server to  inform about the
     * error.
     *
     * @param titleId The resource identifier for the title.
     * @param omaInfo Information about the OMA content.
     * @param downloadInfo Information about the download.
     * @param statusMessage Message to be sent to the notification server.
     */
    private void showDownloadWarningDialog(
            int titleId, final OMAInfo omaInfo, final DownloadInfo downloadInfo,
            final String statusMessage) {
        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    sendInstallNotificationAndNextStep(omaInfo, downloadInfo,
                            DownloadItem.INVALID_DOWNLOAD_ID, statusMessage);
                }
            }
        };
        new AlertDialog.Builder(
                ApplicationStatus.getLastTrackedFocusedActivity(), R.style.AlertDialogTheme)
                .setTitle(titleId)
                .setPositiveButton(R.string.ok, clickListener)
                .setCancelable(false)
                .show();
    }

    /**
     * Shows a dialog to ask whether user wants to open the nextURL.
     *
     * @param omaInfo Information about the OMA content.
     */
    private void showNextUrlDialog(OMAInfo omaInfo) {
        if (omaInfo.isValueEmpty(OMA_NEXT_URL)) {
            return;
        }
        final String nextUrl = omaInfo.getValue(OMA_NEXT_URL);
        final Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(nextUrl));
                    intent.putExtra(Browser.EXTRA_APPLICATION_ID, activity.getPackageName());
                    intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
                    intent.setPackage(mContext.getPackageName());
                    activity.startActivity(intent);
                }
            }
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.open_url_post_oma_download)
                .setPositiveButton(R.string.ok, clickListener)
                .setNegativeButton(R.string.cancel, clickListener)
                .setMessage(nextUrl)
                .setCancelable(false)
                .show();
    }

    /**
     * Returns the first MIME type in the OMA download that can be opened on the device.
     *
     * @param pm PackageManger for the current context.
     * @param omaInfo Information about the OMA content.
     * @return the MIME type can be opened by the device.
     */
    static String getOpennableType(PackageManager pm, OMAInfo omaInfo) {
        if (omaInfo.isValueEmpty(OMA_OBJECT_URI)) {
            return null;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(omaInfo.getValue(OMA_OBJECT_URI));
        for (String type : omaInfo.getTypes()) {
            if (!type.equalsIgnoreCase(OMA_DRM_MESSAGE_MIME)
                    && !type.equalsIgnoreCase(OMA_DRM_CONTENT_MIME)
                    && !type.equalsIgnoreCase(OMA_DOWNLOAD_DESCRIPTOR_MIME)
                    && !type.equalsIgnoreCase(OMA_DRM_RIGHTS_MIME)) {
                intent.setDataAndType(uri, type);
                if (!pm.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * Parses the input stream and returns the OMA information.
     *
     * @param is The input stream to the parser.
     * @return OMA information about the download content, or null if an error is found.
     */
    @VisibleForTesting
    static OMAInfo parseDownloadDescriptor(InputStream is) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(is, null);
            int eventType = parser.getEventType();
            String currentAttribute = null;
            OMAInfo info = new OMAInfo();
            StringBuilder sb = null;
            List<String> attributeList = new ArrayList<String>(Arrays.asList(
                    OMA_TYPE, OMA_SIZE, OMA_OBJECT_URI, OMA_INSTALL_NOTIFY_URI, OMA_NEXT_URL,
                    OMA_DD_VERSION, OMA_NAME, OMA_DESCRIPTION, OMA_VENDOR, OMA_INFO_URL,
                    OMA_ICON_URI, OMA_INSTALL_PARAM));
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_DOCUMENT) {
                    if (!info.isEmpty()) return null;
                } else if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if (attributeList.contains(tagName)) {
                        if (currentAttribute != null) {
                            Log.w(TAG, "Nested attributes was found in the download descriptor");
                            return null;
                        }
                        sb = new StringBuilder();
                        currentAttribute = tagName;
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (currentAttribute != null) {
                        if (!currentAttribute.equals(parser.getName())) {
                            Log.w(TAG, "Nested attributes was found in the download descriptor");
                            return null;
                        }
                        info.addAttributeValue(currentAttribute, sb.toString().trim());
                        currentAttribute = null;
                        sb = null;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (currentAttribute != null) {
                        sb.append(parser.getText());
                    }
                }
                eventType = parser.next();
            }
            return info;
        } catch (XmlPullParserException e) {
            Log.w(TAG, "Failed to parse download descriptor.", e);
            return null;
        } catch (IOException e) {
            Log.w(TAG, "Failed to read download descriptor.", e);
            return null;
        }
    }

    /**
     * Returns the size of the OMA content.
     *
     * @param omaInfo OMA information about the download content
     * @return size in bytes or 0 if the omaInfo doesn't contain size info.
     */
    @VisibleForTesting
    protected static long getSize(OMAInfo omaInfo) {
        String sizeString = omaInfo.getValue(OMA_SIZE);
        try {
            long size = sizeString == null ? 0 : Long.parseLong(sizeString.replace(",", ""));
            return size;
        } catch (NumberFormatException e) {
            Log.w(TAG, "Cannot parse size information.", e);
        }
        return 0;
    }

    /**
     * Enqueue a download request to the DownloadManager and starts downloading the OMA content.
     *
     * @param downloadId The unique identifier maintained by the Android DownloadManager.
     * @param downloadInfo Information about the download.
     * @param omaInfo Information about the OMA content.
     */
    private void downloadOMAContent(long downloadId, DownloadInfo downloadInfo, OMAInfo omaInfo) {
        if (omaInfo == null) return;
        String mimeType = omaInfo.getDrmType();
        if (mimeType == null) {
            mimeType = getOpennableType(mContext.getPackageManager(), omaInfo);
        }
        String fileName = omaInfo.getValue(OMA_NAME);
        String url = omaInfo.getValue(OMA_OBJECT_URI);
        if (TextUtils.isEmpty(fileName)) {
            fileName = URLUtil.guessFileName(url, null, mimeType);
        }
        DownloadInfo newInfo = DownloadInfo.Builder.fromDownloadInfo(downloadInfo)
                .setFileName(fileName)
                .setUrl(url)
                .setMimeType(mimeType)
                .setDescription(omaInfo.getValue(OMA_DESCRIPTION))
                .setContentLength(getSize(omaInfo))
                .build();
        // If installNotifyURI is not empty, the downloaded content cannot
        // be used until the PostStatusTask gets a 200-series response.
        // Don't show complete notification until that happens.
        DownloadItem item = new DownloadItem(true, newInfo);
        item.setSystemDownloadId(downloadId);
        DownloadManagerService.getDownloadManagerService(mContext).enqueueDownloadManagerRequest(
                item, omaInfo.isValueEmpty(OMA_INSTALL_NOTIFY_URI));
        mPendingOMADownloads.put(downloadId, omaInfo);
    }

    /**
     * Checks if an OMA download is currently pending.
     *
     * @param downloadId Download identifier.
     * @return true if the download is in progress, or false otherwise.
     */
    public boolean isPendingOMADownload(long downloadId) {
        return mPendingOMADownloads.get(downloadId) != null;
    }

    /**
     * Updates the download information with the new download Id.
     *
     * @param oldDownloadId Old download Id from the DownloadManager.
     * @param newDownloadId New download Id from the DownloadManager.
     */
    public void updateDownloadInfo(long oldDownloadId, long newDownloadId) {
        OMAInfo omaInfo = mPendingOMADownloads.get(oldDownloadId);
        mPendingOMADownloads.remove(oldDownloadId);
        mPendingOMADownloads.put(newDownloadId, omaInfo);
    }

    /**
     * Returns the installation notification URI for the OMA download.
     *
     * @param downloadId Download Identifier.
     * @return String containing the installNotifyURI.
     */
    public String getInstallNotifyInfo(long downloadId) {
        OMAInfo omaInfo = mPendingOMADownloads.get(downloadId);
        return omaInfo.getValue(OMA_INSTALL_NOTIFY_URI);
    }

    /**
     * This class is responsible for posting the status message to the notification server.
     */
    private class PostStatusTask extends AsyncTask<Void, Void, Boolean> {
        private static final String TAG = "PostStatusTask";
        private final OMAInfo mOMAInfo;
        private final DownloadInfo mDownloadInfo;
        private final String mStatusMessage;
        private final long mDownloadId;

        public PostStatusTask(
                OMAInfo omaInfo, DownloadInfo downloadInfo, long downloadId, String statusMessage) {
            mOMAInfo = omaInfo;
            mDownloadInfo = downloadInfo;
            mStatusMessage = statusMessage;
            mDownloadId = downloadId;
        }

        @Override
        protected Boolean doInBackground(Void...voids) {
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL(mOMAInfo.getValue(OMA_INSTALL_NOTIFY_URI));
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setUseCaches(false);
                urlConnection.setRequestMethod("POST");
                String userAgent = mDownloadInfo.getUserAgent();
                if (TextUtils.isEmpty(userAgent)) {
                    userAgent = ChromeApplication.getBrowserUserAgent();
                }
                urlConnection.setRequestProperty("User-Agent", userAgent);
                urlConnection.setRequestProperty("cookie", mDownloadInfo.getCookie());

                DataOutputStream dos = new DataOutputStream(urlConnection.getOutputStream());
                try {
                    dos.writeBytes(mStatusMessage);
                    dos.flush();
                } catch (IOException e) {
                    Log.w(TAG, "Cannot write status message.", e);
                } finally {
                    dos.close();
                }
                int responseCode = urlConnection.getResponseCode();
                if (responseCode == 200 || responseCode == -1) {
                    return true;
                }
                return false;
            } catch (MalformedURLException e) {
                Log.w(TAG, "Invalid notification URL.", e);
            } catch (IOException e) {
                Log.w(TAG, "Cannot connect to server.", e);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Cannot connect to server.", e);
            } finally {
                if (urlConnection != null) urlConnection.disconnect();
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            DownloadManager manager =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            if (success) {
                String path = mDownloadInfo.getFilePath();
                if (!TextUtils.isEmpty(path)) {
                    // Move the downloaded content from the app directory to public directory.
                    File fromFile = new File(path);
                    String fileName = fromFile.getName();
                    File toFile = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), fileName);
                    if (fromFile.renameTo(toFile)) {
                        manager.addCompletedDownload(
                                fileName, mDownloadInfo.getDescription(), false,
                                mDownloadInfo.getMimeType(), toFile.getPath(),
                                mDownloadInfo.getContentLength(), true);
                    } else if (fromFile.delete()) {
                        Log.w(TAG, "Failed to rename the file.");
                        return;
                    } else {
                        Log.w(TAG, "Failed to rename and delete the file.");
                    }
                }
                showNextUrlDialog(mOMAInfo);
            } else if (mDownloadId != DownloadItem.INVALID_DOWNLOAD_ID) {
                // Remove the downloaded content.
                manager.remove(mDownloadId);
            }
        }
    }
}
