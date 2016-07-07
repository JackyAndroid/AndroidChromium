// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.app.Activity;
import android.content.Context;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;

/**
 * Header shown in saved pages view to inform user of total used storage and offer freeing up
 * space.
 */
public class OfflinePageStorageSpaceHeader {
    OfflinePageBridge mOfflinePageBridge;
    OfflinePageStorageSpacePolicy mOfflinePageStorageSpacePolicy;
    Context mContext;
    OfflinePageFreeUpSpaceCallback mCallback;
    boolean mClicked = false;

    /**
     * @param offlinePageBridge An object to access offline page functionality.
     */
    public OfflinePageStorageSpaceHeader(Context context, OfflinePageBridge offlinePageBridge,
            OfflinePageFreeUpSpaceCallback callback) {
        assert offlinePageBridge != null;
        mOfflinePageBridge = offlinePageBridge;
        mOfflinePageStorageSpacePolicy = new OfflinePageStorageSpacePolicy(mOfflinePageBridge);
        mContext = context;
        mCallback = callback;
    }

    public void destroy() {
        if (!mClicked) RecordUserAction.record("OfflinePages.FreeUpSpaceHeaderNotClicked");
    }

    /** @return Whether the header should be shown. */
    public boolean shouldShow() {
        return mOfflinePageStorageSpacePolicy.shouldShowStorageSpaceHeader();
    }

    /** @return A view holder with the contents of the header. */
    public ViewHolder createHolder(ViewGroup parent) {
        // TODO(fgorski): Enable recalculation in case some pages were deleted.
        ViewGroup header = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.eb_offline_pages_storage_space_header, parent, false);

        ((TextView) header.findViewById(R.id.storage_header_message))
                .setText(mContext.getString(R.string.offline_pages_storage_space_message,
                        Formatter.formatFileSize(
                                mContext, mOfflinePageStorageSpacePolicy.getSizeOfAllPages())));

        header.findViewById(R.id.storage_header_button)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mClicked = true;
                        RecordUserAction.record("OfflinePages.FreeUpSpaceHeaderClicked");
                        OfflinePageFreeUpSpaceDialog dialog =
                                OfflinePageFreeUpSpaceDialog.newInstance(
                                        mOfflinePageBridge, mCallback);
                        dialog.show(((Activity) mContext).getFragmentManager(), null);
                    }
                });

        return new ViewHolder(header) {};
    }
}
