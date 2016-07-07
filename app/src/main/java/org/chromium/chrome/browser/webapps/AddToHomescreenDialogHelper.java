// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.graphics.Bitmap;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.WebContents;

/**
 * This class acts as a proxy for AddToHomescreenDialog to interact with C++.
 */
public class AddToHomescreenDialogHelper {

    /** Observes the data fetching pipeline. */
    public interface Observer {
        /** Called when the title of the page is available. */
        void onUserTitleAvailable(String title);

        /** Called when the icon to use in the launcher is available. */
        void onIconAvailable(Bitmap icon);
    }

    private final Context mAppContext;
    private final Tab mTab;

    private Observer mObserver;
    private boolean mIsInitialized;
    private long mNativeAddToHomescreenDialogHelper;

    public AddToHomescreenDialogHelper(Context appContext, Tab tab) {
        mAppContext = appContext;
        mTab = tab;
    }

    /**
     * Gets all the information required to initialize the UI.  The observer will be notified as
     * information required for the shortcut become available.
     * @param observer Observer to notify.
     */
    public void initialize(Observer observer) {
        mObserver = observer;
        mNativeAddToHomescreenDialogHelper = nativeInitialize(mTab.getWebContents());
    }

    /**
     * Returns whether the object is initialized.
     */
    public boolean isInitialized() {
        return mIsInitialized;
    }

    /**
     * Puts the object in a state where it is safe to be destroyed.
     */
    public void destroy() {
        nativeDestroy(mNativeAddToHomescreenDialogHelper);

        // Make sure the callback isn't run if the tear down happens before
        // onInitialized() is called.
        mObserver = null;
        mNativeAddToHomescreenDialogHelper = 0;
    }

    @CalledByNative
    private void onUserTitleAvailable(String title) {
        mObserver.onUserTitleAvailable(title);
    }

    @CalledByNative
    private void onIconAvailable(Bitmap icon) {
        mObserver.onIconAvailable(icon);
        mIsInitialized = true;
    }

    /**
     * Adds a shortcut for the current Tab.
     * @param userRequestedTitle Title of the shortcut displayed on the homescreen.
     */
    public void addShortcut(String userRequestedTitle) {
        nativeAddShortcut(mNativeAddToHomescreenDialogHelper, userRequestedTitle);
    }

    private native long nativeInitialize(WebContents webContents);
    private native void nativeAddShortcut(long nativeAddToHomescreenDialogHelper,
            String userRequestedTitle);
    private native void nativeDestroy(long nativeAddToHomescreenDialogHelper);
}
