// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.support.v7.widget.RecyclerView;

import org.chromium.base.VisibleForTesting;

/** Holds onto a View that displays information about a downloaded file. */
public class DownloadHistoryItemViewHolder extends RecyclerView.ViewHolder {
    private final DownloadItemView mItemView;

    public DownloadHistoryItemViewHolder(DownloadItemView itemView) {
        super(itemView);
        mItemView = itemView;
    }

    @VisibleForTesting
    public DownloadItemView getItemView() {
        return mItemView;
    }
}