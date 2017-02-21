// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feedback;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.WindowAndroid;

import javax.annotation.Nullable;

/**
 * A utility class to take a feedback-formatted screenshot of an {@link Activity}.
 */
@JNINamespace("chrome::android")
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
         */
        void onGotBitmap(@Nullable Bitmap bitmap);
    }

    /**
     * Prepares screenshot (possibly asynchronously) and invokes the callback when the screenshot
     * is available, or collection has failed. The asynchronous path is only taken when the activity
     * that is passed in is a {@link ChromeActivity}.
     * The callback is always invoked asynchronously.
     */
    public static void create(Activity activity, final ScreenshotTaskCallback callback) {
        if (activity instanceof ChromeActivity) {
            Rect rect = new Rect();
            activity.getWindow().getDecorView().getRootView().getWindowVisibleDisplayFrame(rect);
            createCompositorScreenshot(((ChromeActivity) activity).getWindowAndroid(), rect,
                    callback);
            return;
        }

        final Bitmap bitmap = prepareScreenshot(activity, null);
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onGotBitmap(bitmap);
            }
        });
    }

    /**
     * A callback passed to the native snapshot API which returns the result in PNG format.
     */
    private interface SnapshotResultCallback {
        /**
         * Called when collection of the bitmap has completed.
         * @param pngBytes PNG-formatted bitmap in byte array if successful; otherwise null
         */
        void onCompleted(@Nullable byte[] pngBytes);
    }

    private static void createCompositorScreenshot(WindowAndroid windowAndroid,
            Rect windowRect, final ScreenshotTaskCallback callback) {
        SnapshotResultCallback resultCallback = new SnapshotResultCallback() {
            @Override
            public void onCompleted(byte[] pngBytes) {
                callback.onGotBitmap(pngBytes != null
                        ? BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length) : null);
            }
        };
        nativeGrabWindowSnapshotAsync(resultCallback, windowAndroid.getNativePointer(),
                windowRect.width(), windowRect.height());
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

    @CalledByNative
    private static void notifySnapshotFinished(Object callback, byte[] pngBytes) {
        ((SnapshotResultCallback) callback).onCompleted(pngBytes);
    }

    // This is a utility class, so it should never be created.
    private ScreenshotTask() {}

    private static native void nativeGrabWindowSnapshotAsync(SnapshotResultCallback callback,
            long nativeWindowAndroid, int width, int height);
}
