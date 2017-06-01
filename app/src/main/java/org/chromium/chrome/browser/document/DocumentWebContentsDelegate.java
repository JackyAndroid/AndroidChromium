// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import org.chromium.components.web_contents_delegate_android.WebContentsDelegateAndroid;
import org.chromium.content_public.browser.WebContents;

/**
 * Stubs out calls to the WebContentsDelegateAndroid.  Attaching a WebContentsDelegateAndroid to a
 * newly created WebContents signals to Chrome that it was created properly, which is needed in
 * situations where Chrome on Android needs to create the Activity for the WebContents
 * asynchronously.
 */
public class DocumentWebContentsDelegate extends WebContentsDelegateAndroid {
    /**
     * Singleton instance of the WebContentsDelegate.  Delegates can be assigned to multiple
     * WebContents.
     */
    private static DocumentWebContentsDelegate sInstance;

    /**
     * Native side pointer to the stubbed WebContentsDelegate.
     */
    private long mNativePtr;

    /**
     * @return The Singleton instance, creating it if necessary.
     */
    public static DocumentWebContentsDelegate getInstance() {
        if (sInstance == null) sInstance = new DocumentWebContentsDelegate();
        return sInstance;
    }

    /**
     * Attaches the native side delegate to the native WebContents.
     * @param webContents The {@link WebContents} to attach to.
     */
    public void attachDelegate(WebContents webContents) {
        nativeAttachContents(mNativePtr, webContents);
    }

    private DocumentWebContentsDelegate() {
        mNativePtr = nativeInitialize();
    }

    private native long nativeInitialize();
    private native void nativeAttachContents(
            long nativeDocumentWebContentsDelegate, WebContents webContents);
}