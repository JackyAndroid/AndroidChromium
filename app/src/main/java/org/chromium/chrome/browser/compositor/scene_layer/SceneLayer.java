// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.scene_layer;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

/**
 * Java representation of a scene layer.
 */
@JNINamespace("chrome::android")
public class SceneLayer {
    private long mNativePtr;

    /**
     * Builds an instance of a {@link SceneLayer}.
     */
    public SceneLayer() {
        initializeNative();
    }

    /**
     * Initializes the native component of a {@link SceneLayer}.  Must be
     * overridden to have a custom native component.
     */
    protected void initializeNative() {
        if (mNativePtr == 0) {
            mNativePtr = nativeInit();
        }
        assert mNativePtr != 0;
    }

    /**
     * Destroys this object and the corresponding native component.
     */
    public void destroy() {
        assert mNativePtr != 0;
        nativeDestroy(mNativePtr);
        assert mNativePtr == 0;
    }

    @CalledByNative
    private void setNativePtr(long nativeSceneLayerPtr) {
        assert mNativePtr == 0 || nativeSceneLayerPtr == 0;
        mNativePtr = nativeSceneLayerPtr;
    }

    @CalledByNative
    private long getNativePtr() {
        return mNativePtr;
    }

    private native long nativeInit();
    private native void nativeDestroy(long nativeSceneLayer);
}
