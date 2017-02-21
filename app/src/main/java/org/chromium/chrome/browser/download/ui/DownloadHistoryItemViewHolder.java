// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.components.url_formatter.UrlFormatter;

/** Holds onto a View that displays information about a downloaded file. */
public class DownloadHistoryItemViewHolder
        extends RecyclerView.ViewHolder implements ThumbnailProvider.ThumbnailRequest {
    private final DownloadItemView mItemView;
    private final TextView mFilenameView;
    private final TextView mHostnameView;
    private final TextView mFilesizeView;

    private DownloadHistoryItemWrapper mItem;

    public DownloadHistoryItemViewHolder(View itemView) {
        super(itemView);

        assert itemView instanceof DownloadItemView;
        mItemView = (DownloadItemView) itemView;

        mFilenameView = (TextView) itemView.findViewById(R.id.filename_view);
        mHostnameView = (TextView) itemView.findViewById(R.id.hostname_view);
        mFilesizeView = (TextView) itemView.findViewById(R.id.filesize_view);
    }

    @Override
    public String getFilePath() {
        return mItem == null ? null : mItem.getFilePath();
    }

    @Override
    public void onThumbnailRetrieved(String filePath, Bitmap thumbnail) {
        if (TextUtils.equals(getFilePath(), filePath) && thumbnail != null
                && thumbnail.getWidth() != 0 && thumbnail.getHeight() != 0) {
            mItemView.setThumbnailBitmap(thumbnail);
        }
    }

    /** Set up the View for a particular download item. */
    void displayItem(BackendProvider provider, DownloadHistoryItemWrapper item) {
        // Cancel any previous thumbnail request for the previously displayed item.
        ThumbnailProvider thumbnailProvider = provider.getThumbnailProvider();
        thumbnailProvider.cancelRetrieval(this);

        Context context = mFilesizeView.getContext();
        mFilenameView.setText(item.getDisplayFileName());
        mHostnameView.setText(
                UrlFormatter.formatUrlForSecurityDisplay(item.getUrl(), false));
        mFilesizeView.setText(
                Formatter.formatFileSize(context, item.getFileSize()));
        mItem = item;

        // Asynchronously grab a thumbnail for the file if it might have one.
        int fileType = item.getFilterType();
        Bitmap thumbnail = null;
        if (fileType == DownloadFilter.FILTER_IMAGE) {
            thumbnail = thumbnailProvider.getThumbnail(this);
        } else {
            // TODO(dfalcantara): Get thumbnails for audio and video files when possible.
        }

        // Pick what icon to display for the item.
        int iconResource = R.drawable.ic_drive_file_white_24dp;
        switch (fileType) {
            case DownloadFilter.FILTER_PAGE:
                iconResource = R.drawable.ic_drive_site_white_24dp;
                break;
            case DownloadFilter.FILTER_VIDEO:
                iconResource = R.drawable.ic_play_arrow_white_24dp;
                break;
            case DownloadFilter.FILTER_AUDIO:
                iconResource = R.drawable.ic_music_note_white_24dp;
                break;
            case DownloadFilter.FILTER_IMAGE:
                iconResource = R.drawable.ic_image_white_24dp;
                break;
            case DownloadFilter.FILTER_DOCUMENT:
                iconResource = R.drawable.ic_drive_text_white_24dp;
                break;
            default:
        }

        // Initialize the DownloadItemView.
        mItemView.initialize(item, iconResource, thumbnail);
    }

    @VisibleForTesting
    public DownloadItemView getItemView() {
        return mItemView;
    }
}