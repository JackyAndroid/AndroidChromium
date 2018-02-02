// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.graphics.Bitmap;

/** Provides thumbnails that represent different files. */
public interface ThumbnailProvider {
    /** Used to request the retrieval of a thumbnail. */
    public static interface ThumbnailRequest {
        /** Local storage path to the file. */
        String getFilePath();

        /** Called when a thumbnail is ready. */
        void onThumbnailRetrieved(String filePath, Bitmap thumbnail);
    }

    /** Destroys the class. */
    void destroy();

    /**
     * Synchronously returns a thumbnail if it is cached. Otherwise, asynchronously returns a
     * thumbnail via {@link ThumbnailRequest#onThumbnailRetrieved}.
     * @param request Parameters that describe the thumbnail being retrieved.
     */
    Bitmap getThumbnail(ThumbnailRequest request);

    /** Removes a particular request from the pending queue. */
    void cancelRetrieval(ThumbnailRequest request);
}
