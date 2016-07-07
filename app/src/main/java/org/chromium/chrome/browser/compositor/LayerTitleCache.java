// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.SparseArray;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.R;
import org.chromium.ui.resources.ResourceManager;
import org.chromium.ui.resources.dynamics.BitmapDynamicResource;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

/**
 * A version of the {@link LayerTitleCache} that builds native cc::Layer objects
 * that represent the cached title textures.
 */
@JNINamespace("chrome::android")
public class LayerTitleCache implements TitleCache {
    private long mNativeLayerTitleCache;
    private final SparseArray<Title> mTitles = new SparseArray<Title>();
    private ResourceManager mResourceManager;
    private static int sNextResourceId = 1;

    /**
     * Builds an instance of the LayerTitleCache.
     */
    public LayerTitleCache(Context context) {
        Resources res = context.getResources();
        final int fadeWidthPx = res.getDimensionPixelOffset(R.dimen.border_texture_title_fade);
        final int faviconStartPaddingPx =
                res.getDimensionPixelSize(R.dimen.tab_title_favicon_start_padding);
        final int faviconEndPaddingPx =
                res.getDimensionPixelSize(R.dimen.tab_title_favicon_end_padding);
        mNativeLayerTitleCache = nativeInit(fadeWidthPx, faviconStartPaddingPx, faviconEndPaddingPx,
                R.drawable.spinner, R.drawable.spinner_white);
    }

    /**
     * @param resourceManager The {@link ResourceManager} for registering title
     *                        resources.
     */
    public void setResourceManager(ResourceManager resourceManager) {
        mResourceManager = resourceManager;
    }

    /**
     * Destroys the native reference.
     */
    public void shutDown() {
        if (mNativeLayerTitleCache == 0) return;
        nativeDestroy(mNativeLayerTitleCache);
        mNativeLayerTitleCache = 0;
    }

    @CalledByNative
    private long getNativePtr() {
        return mNativeLayerTitleCache;
    }

    @Override
    public void put(int tabId, Bitmap titleBitmap, Bitmap faviconBitmap, boolean isIncognito,
            boolean isRtl) {
        Title title = mTitles.get(tabId);
        if (title == null) {
            title = new Title();
            mTitles.put(tabId, title);
            title.register();
        }

        title.update(titleBitmap, faviconBitmap);

        if (mNativeLayerTitleCache != 0) {
            nativeUpdateLayer(mNativeLayerTitleCache, tabId, title.getTitleResId(),
                    title.getFaviconResId(), isIncognito, isRtl);
        }
    }

    @Override
    public void remove(int tabId) {
        Title title = mTitles.get(tabId);
        if (title == null) return;
        title.unregister();
        mTitles.remove(tabId);
        if (mNativeLayerTitleCache == 0) return;
        nativeUpdateLayer(mNativeLayerTitleCache, tabId, -1, -1, false, false);
    }

    @Override
    public void clearExcept(int exceptId) {
        Title title = mTitles.get(exceptId);
        for (int i = 0; i < mTitles.size(); i++) {
            Title toDelete = mTitles.get(mTitles.keyAt(i));
            if (toDelete == title) continue;
            toDelete.unregister();
        }
        mTitles.clear();

        if (title != null) mTitles.put(exceptId, title);

        if (mNativeLayerTitleCache == 0) return;
        nativeClearExcept(mNativeLayerTitleCache, exceptId);
    }

    private class Title {
        private final BitmapDynamicResource mFavicon = new BitmapDynamicResource(sNextResourceId++);
        private final BitmapDynamicResource mTitle = new BitmapDynamicResource(sNextResourceId++);

        public Title() {}

        public void update(Bitmap titleBitmap, Bitmap faviconBitmap) {
            mFavicon.setBitmap(faviconBitmap);
            mTitle.setBitmap(titleBitmap);
        }

        public void register() {
            if (mResourceManager == null) return;
            DynamicResourceLoader loader = mResourceManager.getBitmapDynamicResourceLoader();
            loader.registerResource(mFavicon.getResId(), mFavicon);
            loader.registerResource(mTitle.getResId(), mTitle);
        }

        public void unregister() {
            if (mResourceManager == null) return;
            DynamicResourceLoader loader = mResourceManager.getBitmapDynamicResourceLoader();
            loader.unregisterResource(mFavicon.getResId());
            loader.unregisterResource(mTitle.getResId());
        }

        public int getFaviconResId() {
            return mFavicon.getResId();
        }

        public int getTitleResId() {
            return mTitle.getResId();
        }
    }

    private native long nativeInit(int fadeWidth, int faviconStartlPadding, int faviconEndPadding,
            int spinnerResId, int spinnerIncognitoResId);
    private static native void nativeDestroy(long nativeLayerTitleCache);
    private native void nativeClearExcept(long nativeLayerTitleCache, int exceptId);
    private native void nativeUpdateLayer(long nativeLayerTitleCache, int tabId, int titleResId,
            int faviconResId, boolean isIncognito, boolean isRtl);
}
