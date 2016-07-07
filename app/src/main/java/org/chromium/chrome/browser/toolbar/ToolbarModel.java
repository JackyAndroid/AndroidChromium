// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.content_public.browser.WebContents;

/**
 * Provides a way of accessing toolbar data and state.
 */
public class ToolbarModel {

    /**
     * Delegate for providing additional information to the model.
     */
    public interface ToolbarModelDelegate {
        /**
         * @return The currently active WebContents being used by the Toolbar.
         */
        @CalledByNative("ToolbarModelDelegate")
        WebContents getActiveWebContents();
    }

    private long mNativeToolbarModelAndroid;

    /**
     * Initialize the native counterpart of this model.
     * @param delegate The delegate that will be used by the model.
     */
    public void initialize(ToolbarModelDelegate delegate) {
        mNativeToolbarModelAndroid = nativeInit(delegate);
    }

    /**
     * Destroys the native ToolbarModel.
     */
    public void destroy() {
        if (mNativeToolbarModelAndroid == 0) return;
        nativeDestroy(mNativeToolbarModelAndroid);
        mNativeToolbarModelAndroid = 0;
    }

    /** @return The formatted text (URL or search terms) for display. */
    public String getText() {
        if (mNativeToolbarModelAndroid == 0) return null;
        return nativeGetText(mNativeToolbarModelAndroid);
    }

    /** @return The chip text from the search URL. */
    public String getCorpusChipText() {
        if (mNativeToolbarModelAndroid == 0) return null;
        return nativeGetCorpusChipText(mNativeToolbarModelAndroid);
    }

    /** @return Whether the URL is replaced by a search query. */
    public boolean wouldReplaceURL() {
        if (mNativeToolbarModelAndroid == 0) return false;
        return nativeWouldReplaceURL(mNativeToolbarModelAndroid);
    }

    private native long nativeInit(ToolbarModelDelegate delegate);
    private native void nativeDestroy(long nativeToolbarModelAndroid);
    private native String nativeGetText(long nativeToolbarModelAndroid);
    private native String nativeGetCorpusChipText(long nativeToolbarModelAndroid);
    private native boolean nativeWouldReplaceURL(long nativeToolbarModelAndroid);
}
