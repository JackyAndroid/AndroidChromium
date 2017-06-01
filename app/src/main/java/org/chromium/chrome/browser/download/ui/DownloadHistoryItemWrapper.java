// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadItem;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadItem;
import org.chromium.chrome.browser.widget.DateDividedAdapter.TimedItem;
import org.chromium.content_public.browser.DownloadState;
import org.chromium.ui.widget.Toast;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Wraps different classes that contain information about downloads. */
public abstract class DownloadHistoryItemWrapper extends TimedItem {
    public static final Integer FILE_EXTENSION_OTHER = 0;
    public static final Integer FILE_EXTENSION_APK = 1;
    public static final Integer FILE_EXTENSION_CSV = 2;
    public static final Integer FILE_EXTENSION_DOC = 3;
    public static final Integer FILE_EXTENSION_DOCX = 4;
    public static final Integer FILE_EXTENSION_EXE = 5;
    public static final Integer FILE_EXTENSION_PDF = 6;
    public static final Integer FILE_EXTENSION_PPT = 7;
    public static final Integer FILE_EXTENSION_PPTX = 8;
    public static final Integer FILE_EXTENSION_PSD = 9;
    public static final Integer FILE_EXTENSION_RTF = 10;
    public static final Integer FILE_EXTENSION_TXT = 11;
    public static final Integer FILE_EXTENSION_XLS = 12;
    public static final Integer FILE_EXTENSION_XLSX = 13;
    public static final Integer FILE_EXTENSION_ZIP = 14;
    public static final Integer FILE_EXTENSION_BOUNDARY = 15;

    private static final Map<String, Integer> EXTENSIONS_MAP;
    static {
        Map<String, Integer> extensions = new HashMap<>();
        extensions.put("apk", FILE_EXTENSION_APK);
        extensions.put("csv", FILE_EXTENSION_CSV);
        extensions.put("doc", FILE_EXTENSION_DOC);
        extensions.put("docx", FILE_EXTENSION_DOCX);
        extensions.put("exe", FILE_EXTENSION_EXE);
        extensions.put("pdf", FILE_EXTENSION_PDF);
        extensions.put("ppt", FILE_EXTENSION_PPT);
        extensions.put("pptx", FILE_EXTENSION_PPTX);
        extensions.put("psd", FILE_EXTENSION_PSD);
        extensions.put("rtf", FILE_EXTENSION_RTF);
        extensions.put("txt", FILE_EXTENSION_TXT);
        extensions.put("xls", FILE_EXTENSION_XLS);
        extensions.put("xlsx", FILE_EXTENSION_XLSX);
        extensions.put("zip", FILE_EXTENSION_ZIP);

        EXTENSIONS_MAP = Collections.unmodifiableMap(extensions);
    }

    protected final BackendProvider mBackendProvider;
    protected final ComponentName mComponentName;
    private Long mStableId;

    private DownloadHistoryItemWrapper(BackendProvider provider, ComponentName component) {
        mBackendProvider = provider;
        mComponentName = component;
    }

    @Override
    public long getStableId() {
        if (mStableId == null) {
            // Generate a stable ID that combines the timestamp and the download ID.
            mStableId = (long) getId().hashCode();
            mStableId = (mStableId << 32) + (getTimestamp() & 0x0FFFFFFFF);
        }
        return mStableId;
    }

    /** @return Item that is being wrapped. */
    abstract Object getItem();

    /** @return ID representing the download. */
    abstract String getId();

    /** @return String showing where the download resides. */
    abstract String getFilePath();

    /** @return The file where the download resides. */
    public abstract File getFile();

    /** @return String to display for the file. */
    abstract String getDisplayFileName();

    /** @return Size of the file. */
    abstract long getFileSize();

    /** @return URL the file was downloaded from. */
    public abstract String getUrl();

    /** @return {@link DownloadFilter} that represents the file type. */
    public abstract int getFilterType();

    /** @return The mime type or null if the item doesn't have one. */
    public abstract String getMimeType();

    /** @return The file extension type. See list at the top of the file. */
    public abstract int getFileExtensionType();

    /** @return How much of the download has completed, or -1 if there is no progress. */
    public abstract int getDownloadProgress();

    /** @return Whether or not the file is completely downloaded. */
    public abstract boolean isComplete();

    /** Called when the user wants to open the file. */
    abstract void open();

    /**
     * Called when the user wants to remove the download from the backend. May also delete the file
     * associated with the download item.
     * @return Whether the file associated with the download item was deleted.
     */
    abstract boolean remove();

    /**
     * @return Whether the file associated with this item has been removed through an external
     *         action.
     */
    abstract boolean hasBeenExternallyRemoved();

    /**
     * @return Whether this download is associated with the off the record profile.
     */
    abstract boolean isOffTheRecord();

    protected void recordOpenSuccess() {
        RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.Item.OpenSucceeded",
                getFilterType(), DownloadFilter.FILTER_BOUNDARY);

        if (getFilterType() == DownloadFilter.FILTER_OTHER) {
            RecordHistogram.recordEnumeratedHistogram(
                    "Android.DownloadManager.OtherExtensions.OpenSucceeded",
                    getFileExtensionType(), FILE_EXTENSION_BOUNDARY);
        }
    }

    protected void recordOpenFailure() {
        RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.Item.OpenFailed",
                getFilterType(), DownloadFilter.FILTER_BOUNDARY);

        if (getFilterType() == DownloadFilter.FILTER_OTHER) {
            RecordHistogram.recordEnumeratedHistogram(
                    "Android.DownloadManager.OtherExtensions.OpenFailed",
                    getFileExtensionType(), FILE_EXTENSION_BOUNDARY);
        }
    }

    /** Wraps a {@link DownloadItem}. */
    public static class DownloadItemWrapper extends DownloadHistoryItemWrapper {
        private final DownloadItem mItem;
        private File mFile;
        private Integer mFileExtensionType;

        DownloadItemWrapper(DownloadItem item, BackendProvider provider, ComponentName component) {
            super(provider, component);
            mItem = item;
        }

        @Override
        public DownloadItem getItem() {
            return mItem;
        }

        @Override
        public String getId() {
            return mItem.getId();
        }

        @Override
        public long getTimestamp() {
            return mItem.getStartTime();
        }

        @Override
        public String getFilePath() {
            return mItem.getDownloadInfo().getFilePath();
        }

        @Override
        public File getFile() {
            if (mFile == null) mFile = new File(getFilePath());
            return mFile;
        }

        @Override
        public String getDisplayFileName() {
            return mItem.getDownloadInfo().getFileName();
        }

        @Override
        public long getFileSize() {
            if (mItem.getDownloadInfo().state() == DownloadState.COMPLETE) {
                return mItem.getDownloadInfo().getContentLength();
            } else {
                return 0;
            }
        }

        @Override
        public String getUrl() {
            return mItem.getDownloadInfo().getUrl();
        }

        @Override
        public int getFilterType() {
            return DownloadFilter.fromMimeType(getMimeType());
        }

        @Override
        public String getMimeType() {
            return mItem.getDownloadInfo().getMimeType();
        }

        @Override
        public int getFileExtensionType() {
            if (mFileExtensionType == null) {
                int extensionIndex = getFilePath().lastIndexOf(".");
                if (extensionIndex == -1 || extensionIndex == getFilePath().length() - 1) {
                    mFileExtensionType = FILE_EXTENSION_OTHER;
                    return mFileExtensionType;
                }

                String extension = getFilePath().substring(extensionIndex + 1);
                if (!TextUtils.isEmpty(extension) && EXTENSIONS_MAP.containsKey(
                        extension.toLowerCase(Locale.getDefault()))) {
                    mFileExtensionType = EXTENSIONS_MAP.get(
                            extension.toLowerCase(Locale.getDefault()));
                } else {
                    mFileExtensionType = FILE_EXTENSION_OTHER;
                }
            }

            return mFileExtensionType;
        }

        @Override
        public int getDownloadProgress() {
            return mItem.getDownloadInfo().getPercentCompleted();
        }

        @Override
        public void open() {
            Context context = ContextUtils.getApplicationContext();

            if (mItem.hasBeenExternallyRemoved()) {
                Toast.makeText(context, context.getString(R.string.download_cant_open_file),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (DownloadUtils.openFile(getFile(), getMimeType(), isOffTheRecord())) {
                recordOpenSuccess();
            } else {
                recordOpenFailure();
            }
        }

        @Override
        public boolean remove() {
            // Tell the DownloadManager to remove the file from history.
            mBackendProvider.getDownloadDelegate().removeDownload(getId(), isOffTheRecord());
            return false;
        }

        @Override
        boolean hasBeenExternallyRemoved() {
            return mItem.hasBeenExternallyRemoved();
        }

        @Override
        boolean isOffTheRecord() {
            return mItem.getDownloadInfo().isOffTheRecord();
        }

        @Override
        public boolean isComplete() {
            return mItem.getDownloadInfo().state() == DownloadState.COMPLETE;
        }
    }

    /** Wraps a {@link OfflinePageDownloadItem}. */
    public static class OfflinePageItemWrapper extends DownloadHistoryItemWrapper {
        private final OfflinePageDownloadItem mItem;
        private File mFile;

        OfflinePageItemWrapper(OfflinePageDownloadItem item, BackendProvider provider,
                ComponentName component) {
            super(provider, component);
            mItem = item;
        }

        @Override
        public OfflinePageDownloadItem getItem() {
            return mItem;
        }

        @Override
        public String getId() {
            return mItem.getGuid();
        }

        @Override
        public long getTimestamp() {
            return mItem.getStartTimeMs();
        }

        @Override
        public String getFilePath() {
            return mItem.getTargetPath();
        }

        @Override
        public File getFile() {
            if (mFile == null) mFile = new File(getFilePath());
            return mFile;
        }

        @Override
        public String getDisplayFileName() {
            String title = mItem.getTitle();
            if (TextUtils.isEmpty(title)) {
                File path = new File(getFilePath());
                return path.getName();
            } else {
                return title;
            }
        }

        @Override
        public long getFileSize() {
            return mItem.getTotalBytes();
        }

        @Override
        public String getUrl() {
            return mItem.getUrl();
        }

        @Override
        public int getFilterType() {
            return DownloadFilter.FILTER_PAGE;
        }

        @Override
        public String getMimeType() {
            return "text/plain";
        }

        @Override
        public int getFileExtensionType() {
            return FILE_EXTENSION_OTHER;
        }

        @Override
        public int getDownloadProgress() {
            return -1;
        }

        @Override
        public void open() {
            mBackendProvider.getOfflinePageBridge().openItem(getId(), mComponentName);
            recordOpenSuccess();
        }

        @Override
        public boolean remove() {
            mBackendProvider.getOfflinePageBridge().deleteItem(getId());
            return true;
        }

        @Override
        boolean hasBeenExternallyRemoved() {
            // We don't currently detect when offline pages have been removed externally.
            return false;
        }

        @Override
        boolean isOffTheRecord() {
            return false;
        }

        @Override
        public boolean isComplete() {
            // Incomplete offline pages aren't shown yet.
            return true;
        }
    }
}
