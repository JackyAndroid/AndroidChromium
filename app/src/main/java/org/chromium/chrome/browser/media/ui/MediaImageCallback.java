// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.ui;

import android.graphics.Bitmap;

/**
 * The callback when an image is downloaded. This class is different with
 * {@link ImageDownloadCallback} and is only used by {@link MediaImageManager}.
 */
public interface MediaImageCallback {
    /**
     * Called when image downloading is complete.
     * @param bitmap The downloaded image.
     */
    void onImageDownloaded(Bitmap image);
}
