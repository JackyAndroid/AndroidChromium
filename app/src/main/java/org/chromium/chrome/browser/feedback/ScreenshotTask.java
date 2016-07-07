// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feedback;

import android.app.Activity;
import android.graphics.Bitmap;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.content.browser.ContentReadbackHandler;
import org.chromium.content_public.browser.readback_types.ReadbackResponse;
import org.chromium.ui.UiUtils;

import javax.annotation.Nullable;

/**
 * A utility class to take a feedback-formatted screenshot of an {@link Activity}.
 */
public final class ScreenshotTask {
    /**
     * Maximum dimension for the screenshot to be sent to the feedback handler.  This size
     * ensures the size of bitmap < 1MB, which is a requirement of the handler.
     */
    private static final int MAX_FEEDBACK_SCREENSHOT_DIMENSION = 600;

    /**
     * A callback passed to {@link #create} which will get a bitmap version of the screenshot.
     */
    public interface ScreenshotTaskCallback {
        /**
         * Called when collection of the bitmap has completed.
         * @param bitmap the bitmap or null.
         * @param success whether the bitmap is valid.
         */
        void onGotBitmap(@Nullable Bitmap bitmap, boolean success);
    }

    /**
     * Prepares screenshot (possibly asynchronously) and invokes the callback when the screenshot
     * is available, or collection has failed. The asynchronous path is only taken when the activity
     * that is passed in is a {@link ChromeActivity}.
     * The callback is always invoked asynchronously.
     */
    public static void create(Activity activity, final ScreenshotTaskCallback callback) {
        if (activity instanceof ChromeActivity) {
            createCompositorActivityScreenshot((ChromeActivity) activity, callback);
            return;
        }

        final Bitmap bitmap = prepareScreenshot(activity, null);
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onGotBitmap(bitmap, bitmap != null);
            }
        });
    }

    private static void createCompositorActivityScreenshot(ChromeActivity activity,
            final ScreenshotTaskCallback callback) {
        ContentReadbackHandler.GetBitmapCallback getBitmapCallback =
                new ContentReadbackHandler.GetBitmapCallback() {
            @Override
            public void onFinishGetBitmap(Bitmap bitmap, int response) {
                callback.onGotBitmap(bitmap, response == ReadbackResponse.SUCCESS);
            }
        };
        activity.startTakingCompositorActivityScreenshot(getBitmapCallback);
    }

    /**
     * Prepares a given screenshot for sending with a feedback.
     * If no screenshot is given it creates one from the activity View if an activity is provided.
     * @param activity An activity or null
     * @param bitmap A screenshot or null
     * @return A feedback-ready screenshot or null
     */
    private static Bitmap prepareScreenshot(@Nullable Activity activity, @Nullable Bitmap bitmap) {
        if (bitmap == null) {
            if (activity == null) return null;
            return UiUtils.generateScaledScreenshot(
                    activity.getWindow().getDecorView().getRootView(),
                    MAX_FEEDBACK_SCREENSHOT_DIMENSION, Bitmap.Config.ARGB_8888);
        }

        int screenshotMaxDimension = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (screenshotMaxDimension <= MAX_FEEDBACK_SCREENSHOT_DIMENSION) return bitmap;

        float screenshotScale = (float) MAX_FEEDBACK_SCREENSHOT_DIMENSION / screenshotMaxDimension;
        int destWidth = (int) (bitmap.getWidth() * screenshotScale);
        int destHeight = (int) (bitmap.getHeight() * screenshotScale);
        return Bitmap.createScaledBitmap(bitmap, destWidth, destHeight, true);
    }

    // This is a utility class, so it should never be created.
    private ScreenshotTask() {}
}
