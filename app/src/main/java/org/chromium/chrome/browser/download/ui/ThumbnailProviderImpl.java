// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.text.TextUtils;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Concrete implementation of {@link ThumbnailProvider}.
 *
 * Thumbnails are cached and shared across all ThumbnailProviderImpls.  The cache itself is LRU and
 * limited in size.  It is automatically garbage collected under memory pressure.
 *
 * A queue of requests is maintained in FIFO order.  Missing thumbnails are retrieved asynchronously
 * by the native ThumbnailProvider, which is owned and destroyed by the Java class.
 *
 * TODO(dfalcantara): Figure out how to send requests simultaneously to the utility process without
 *                    duplicating work to decode the same image for two different requests.
 */
public class ThumbnailProviderImpl implements ThumbnailProvider {
    /** 5 MB of thumbnails should be enough for everyone. */
    private static final int MAX_CACHE_BYTES = 5 * 1024 * 1024;

    /** Weakly referenced cache containing thumbnails that can be deleted under memory pressure. */
    private static WeakReference<LruCache<String, Bitmap>> sBitmapCache = new WeakReference<>(null);

    /** Enqueues requests. */
    private final Handler mHandler;

    /** Maximum size in pixels of the smallest side of the thumbnail. */
    private final int mIconSizePx;

    /** Queue of files to retrieve thumbnails for. */
    private final Deque<ThumbnailRequest> mRequestQueue;

    /** The native side pointer that is owned and destroyed by the Java class. */
    private long mNativeThumbnailProvider;

    /** Request that is currently having its thumbnail retrieved. */
    private ThumbnailRequest mCurrentRequest;

    public ThumbnailProviderImpl(int iconSizePx) {
        mIconSizePx = iconSizePx;
        mHandler = new Handler(Looper.getMainLooper());
        mRequestQueue = new ArrayDeque<>();
        mNativeThumbnailProvider = nativeInit();
    }

    @Override
    public void destroy() {
        ThreadUtils.assertOnUiThread();
        nativeDestroy(mNativeThumbnailProvider);
        mNativeThumbnailProvider = 0;
    }

    @Override
    public Bitmap getThumbnail(ThumbnailRequest request) {
        String filePath = request.getFilePath();
        if (TextUtils.isEmpty(filePath)) return null;

        Bitmap cachedBitmap = getBitmapCache().get(filePath);
        if (cachedBitmap != null) return cachedBitmap;

        mRequestQueue.offer(request);
        processQueue();
        return null;
    }

    /** Removes a particular file from the pending queue. */
    @Override
    public void cancelRetrieval(ThumbnailRequest request) {
        if (mRequestQueue.contains(request)) mRequestQueue.remove(request);
    }

    private void processQueue() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                processNextRequest();
            }
        });
    }

    private void processNextRequest() {
        if (!isInitialized() || mCurrentRequest != null || mRequestQueue.isEmpty()) return;

        mCurrentRequest = mRequestQueue.poll();
        String currentFilePath = mCurrentRequest.getFilePath();

        Bitmap cachedBitmap = getBitmapCache().get(currentFilePath);
        if (cachedBitmap == null) {
            // Asynchronously process the file to make a thumbnail.
            nativeRetrieveThumbnail(mNativeThumbnailProvider, currentFilePath, mIconSizePx);
        } else {
            // Send back the already-processed file.
            onThumbnailRetrieved(currentFilePath, cachedBitmap);
        }
    }

    @CalledByNative
    private void onThumbnailRetrieved(String filePath, @Nullable Bitmap bitmap) {
        if (bitmap != null) {
            getBitmapCache().put(filePath, bitmap);
            mCurrentRequest.onThumbnailRetrieved(filePath, bitmap);
        }

        mCurrentRequest = null;
        processQueue();
    }

    private boolean isInitialized() {
        return mNativeThumbnailProvider != 0;
    }

    private static LruCache<String, Bitmap> getBitmapCache() {
        ThreadUtils.assertOnUiThread();

        LruCache<String, Bitmap> cache = sBitmapCache == null ? null : sBitmapCache.get();
        if (cache != null) return cache;

        // Create a new weakly-referenced cache.
        cache = new LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap thumbnail) {
                return thumbnail == null ? 0 : thumbnail.getByteCount();
            }
        };
        sBitmapCache = new WeakReference<>(cache);
        return cache;
    }

    private native long nativeInit();
    private native void nativeDestroy(long nativeThumbnailProvider);
    private native void nativeRetrieveThumbnail(
            long nativeThumbnailProvider, String filePath, int thumbnailSize);
}
