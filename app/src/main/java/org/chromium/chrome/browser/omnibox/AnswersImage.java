// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.graphics.Bitmap;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * Provides access to images used by Answers in Suggest.
 */
public class AnswersImage {
    /**
     * Observer for updating an image when it is available.
     */
    public interface AnswersImageObserver {
        /**
         * Called when the image is updated.
         *
         * @param answersImage the image
         */
        @CalledByNative("AnswersImageObserver")
        public void onAnswersImageChanged(Bitmap bitmap);
    }

    /**
     * Request image, observer is notified when image is loaded.
     * @param profile     Profile that the request is for.
     * @param imageUrl    URL for image data.
     * @param observer    Observer to be notified when image is updated. The C++ side will hold a
     *                    strong reference to this.
     * @return            A request_id.
     */
    public static int requestAnswersImage(
            Profile profile, String imageUrl, AnswersImageObserver observer) {
        return nativeRequestAnswersImage(profile, imageUrl, observer);
    }

    /**
     * Cancel a pending image request.
     * @param profile    Profile the request was issued for.
     * @param requestId  The ID of the request to be cancelled.
     */
    public static void cancelAnswersImageRequest(Profile profile, int requestId) {
        nativeCancelAnswersImageRequest(profile, requestId);
    }

    /**
     * Requests an image at |imageUrl| for the given |profile| with |observer| being notified.
     * @returns an AnswersImageRequest
     */
    private static native int nativeRequestAnswersImage(
            Profile profile, String imageUrl, AnswersImageObserver observer);

    /**
     * Cancels a pending request.
     */
    private static native void nativeCancelAnswersImageRequest(Profile profile, int requestId);
}
